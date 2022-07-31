package com.frogsquare.playcorelibrary

import android.app.Activity
import android.content.Intent
import android.util.Log
import com.google.android.material.snackbar.Snackbar
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.ActivityResult
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.ktx.clientVersionStalenessDays
import org.godotengine.godot.Dictionary
import org.godotengine.godot.Godot
import org.godotengine.godot.GodotLib
import org.godotengine.godot.plugin.GodotPlugin
import org.godotengine.godot.plugin.SignalInfo
import org.godotengine.godot.plugin.UsedByGodot

const val TAG: String = "PlayCoreLibrary"

@Suppress("UNUSED")
class GDPlayCoreLibrary constructor(godot: Godot): GodotPlugin(godot) {

    private var context = godot.requireContext()

    private val appUpdateManager = AppUpdateManagerFactory.create(context)
    private var appUpdateInfo : AppUpdateInfo? = null

    @UsedByGodot
    fun initialize(params: Dictionary) {

        Log.i(TAG, "Initialized Godot PlayCoreLibrary")
    }

    @UsedByGodot
    fun isUpdateAvailable(): Boolean {
        return appUpdateInfo != null
    }

    @UsedByGodot
    fun startAppUpdatedManager(params: Dictionary) {
        val immediate = params["immediate"] as? Boolean
        Log.i(TAG, "Checking Update in mode `${ if (immediate == true) "IMMEDIATE" else "FLEXIBLE" }`")

        if (immediate == false) {
            appUpdateManager.registerListener(listener)
        }

        appUpdateManager
            .appUpdateInfo
            .addOnSuccessListener { info ->
            appUpdateInfo = info

            if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                Log.i(TAG, "Update Available :: ${info.availableVersionCode()}")

                if (immediate == true) {
                    if (info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
                        val data = Dictionary()
                        data["package"] = info.packageName()
                        data["version"] = info.availableVersionCode()
                        data["mode"] = "IMMEDIATE"

                        Log.i(TAG, "Request Immediate Update")
                        emitSignal("update_available", data)
                    }
                } else {
                    Log.i(TAG, "Client Version Staleness Days :: ${info.clientVersionStalenessDays}")
                    val flexibleUpdateDays = (params["flexible_days"] as? Int) ?: 1
                    if (info.clientVersionStalenessDays ?: -1 >= flexibleUpdateDays
                        && info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) {

                        val data = Dictionary()
                        data["package"] = info.packageName()
                        data["version"] = info.availableVersionCode()
                        data["mode"] = "FLEXIBLE"

                        Log.i(TAG, "Request Flexible Update")
                        emitSignal("update_available", data)
                    }
                }
            } else {
                Log.i(TAG, "No Update Available!")
            }
        }
    }

    @UsedByGodot
    fun startUpdateImmediate(allow_remove_asset: Boolean) {
        if (appUpdateInfo == null) {
            Log.i(TAG, "AppUpdateInfo is null!")
            return
        }

        Log.i(TAG, "Starting update flow IMMEDIATE")
        if (allow_remove_asset) {
            appUpdateManager.startUpdateFlowForResult(
                appUpdateInfo!!,
                activity!!,
                AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE)
                    .setAllowAssetPackDeletion(true)
                    .build(),
                0x001
            )
        } else {
            appUpdateManager.startUpdateFlowForResult(
                appUpdateInfo!!,
                AppUpdateType.IMMEDIATE,
                activity!!,
                0x001
            )
        }
    }

    @UsedByGodot
    fun startUpdateFlexible(allow_remove_asset: Boolean) {
        if (appUpdateInfo == null) {
            Log.i(TAG, "AppUpdateInfo is null!")
            return
        }

        Log.i(TAG, "Starting update flow FLEXIBLE")
        if (allow_remove_asset) {
            appUpdateManager.startUpdateFlowForResult(
                appUpdateInfo!!,
                activity!!,
                AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE)
                    .setAllowAssetPackDeletion(true)
                    .build(),
                0x001
            )
        } else {
            appUpdateManager.startUpdateFlowForResult(
                appUpdateInfo!!,
                AppUpdateType.FLEXIBLE,
                activity!!,
                0x001
            )
        }
    }

    // Displays the snackbar notification and call to action.
    private fun popupSnackbarForCompleteUpdate() {
        Snackbar.make(
            super.getGodot().mView,
            "An update has just been downloaded.",
            Snackbar.LENGTH_INDEFINITE
        ).apply {
            setAction("RESTART") { appUpdateManager.completeUpdate() }
            show()
        }
    }

    // Create a listener to track request state updates.
    private val listener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) {
            popupSnackbarForCompleteUpdate()
        }
    }

    override fun onMainResume() {
        super.onMainResume()

        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                // If an in-app update is already running, resume the update.
                Log.i(TAG, "Resume Update Process")
                appUpdateManager.startUpdateFlowForResult(
                    appUpdateInfo!!,
                    AppUpdateType.IMMEDIATE,
                    activity!!,
                    0x001
                )
            }

            if (info.installStatus() == InstallStatus.DOWNLOADED) {
                popupSnackbarForCompleteUpdate()
            }
        }
    }

    override fun onMainDestroy() {
        appUpdateManager.unregisterListener(listener)
        appUpdateInfo = null

        super.onMainDestroy()
    }

    override fun onMainActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 0x001) {
            when (resultCode) {
                Activity.RESULT_OK -> {
                    Log.i(TAG, "Update flow success")
                    emitSignal("update_success")
                }
                Activity.RESULT_CANCELED -> {
                    Log.i(TAG, "Update flow canceled, $resultCode")
                    emitSignal("update_canceled")
                }
                ActivityResult.RESULT_IN_APP_UPDATE_FAILED -> {
                    Log.i(TAG, "Update flow failed, $resultCode")
                    emitSignal("update_failed")
                }
            }
        }

        super.onMainActivityResult(requestCode, resultCode, data)
    }

    override fun getPluginSignals(): MutableSet<SignalInfo> {
        return mutableSetOf(
            SignalInfo("update_available", Dictionary::class.javaObjectType),
            SignalInfo("update_success"),
            SignalInfo("update_failed"),
            SignalInfo("update_canceled")
        )
    }

    override fun getPluginName(): String {
        return "GDPlayCoreLibrary"
    }
}
