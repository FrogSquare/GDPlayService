package com.frogsquare.googleplay

import android.content.Intent
import android.content.IntentSender
import android.util.Log
import com.google.android.gms.auth.api.Auth
import com.google.android.gms.auth.api.signin.*
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.games.*
import com.google.android.gms.games.leaderboard.LeaderboardScore
import com.google.android.gms.games.leaderboard.LeaderboardVariant
import org.godotengine.godot.Dictionary
import org.godotengine.godot.Godot
import org.godotengine.godot.plugin.GodotPlugin
import org.godotengine.godot.plugin.SignalInfo
import org.godotengine.godot.plugin.UsedByGodot

private const val TAG: String = "GooglePlay"

@Suppress("UNUSED")
class GDPlayService constructor(godot: Godot): GodotPlugin(godot) {

    private val context = godot.requireContext()

    private var client: GoogleSignInClient? = null
    private var achievementClient: AchievementsClient? = null
    private var leaderboardsClient: LeaderboardsClient? = null
    private var playersClient: PlayersClient? = null
    private var videosClient: VideosClient? = null

    private var canRecord: Boolean = false
    private var playerDetails = Dictionary()

    private var isIntentInProgress: Boolean = false
    private var isResolvingConnectionFailure: Boolean = false

    private var _connected: Boolean = false

    @UsedByGodot
    fun initialize() {
        if (!isAvailable()) {
            Log.d(TAG, "Google play service is not available in this device.")
        } else {
            val builder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_GAMES_SIGN_IN)
            client = GoogleSignIn.getClient(godot.requireActivity(), builder.build())
        }

        val account = GoogleSignIn.getLastSignedInAccount(context)
        if (account != null) {
            Log.d(TAG, "Already Connected")
            onSignInSuccess(account)
        } else {
            signInSilently()
        }
    }

    @UsedByGodot
    fun isConnected(): Boolean {
        return GoogleSignIn.getLastSignedInAccount(context) != null
    }

    @UsedByGodot
    fun signIn() {
        if (this.client == null) {
            Log.d(TAG, "GoogleSignIn Client is not initialized.")
            return
        }

        if (isConnected()) {
            Log.d(TAG, "Google service is already connected.")
            return
        }

        val intent = client?.signInIntent
        activity?.startActivityForResult(intent, Common.RC_GOOGLE)
    }

    @UsedByGodot
    fun signOut() {
        client?.let {
            it.signOut().addOnCompleteListener {
                Log.d(TAG, "Signed out.")

                achievementClient = null
                leaderboardsClient = null
                playersClient = null
                videosClient = null

                playerDetails.clear()

                _connected = false
                emitSignal("signed_out")
            }
        }
    }

    @UsedByGodot
    fun increaseAchievement(name: String, value: Int) {
        if (!_connected) return

        runOnUiThread {
            achievementClient?.increment(name, value)
        }
    }

    @UsedByGodot
    fun unlockAchievement(name: String) {
        if (!_connected) return

        runOnUiThread {
            achievementClient?.unlock(name)
        }
    }

    @UsedByGodot
    fun loadTopScore(name: String, max: Int) {
        if (!isConnected()) { return }

        leaderboardsClient?.loadTopScores(
            name,
            LeaderboardVariant.TIME_SPAN_ALL_TIME,
            LeaderboardVariant.COLLECTION_PUBLIC,
            max
        )?.addOnSuccessListener {
            it.get()?.let { boards ->
                val scores: ArrayList<Dictionary> = arrayListOf()
                for (score in boards.scores) {
                    scores.add(convertToDict(score, name))
                }

                emitSignal("scores_loaded", scores)
            }
        }
    }

    @UsedByGodot
    fun loadCurrentPlayerScore(name: String) {
        if (!isConnected()) { return }

        leaderboardsClient?.loadCurrentPlayerLeaderboardScore(
            name,
            LeaderboardVariant.TIME_SPAN_ALL_TIME,
            LeaderboardVariant.COLLECTION_PUBLIC
        )?.addOnSuccessListener {
          it.get()?.let { score ->
              emitSignal("score_loaded", convertToDict(score, name))
          }
        }
    }

    @UsedByGodot
    fun submitScore(name: String, value: Int) {
        if (!_connected) return

        runOnUiThread {
            leaderboardsClient?.submitScore(name, value.toLong())
        }
    }

    @UsedByGodot
    fun showAchievements() {
        if (!_connected) return

        achievementClient?.let {
            it.achievementsIntent
                .addOnSuccessListener { intent ->
                    activity?.startActivityForResult(intent, Common.RC_ACHIEVEMENT)
                }
                .addOnFailureListener { exception ->
                    Log.d(TAG, "Showing::Achievements::Failed ${exception.message}")
                }
        }
    }

    @UsedByGodot
    fun showLeaderboard(name: String) {
        if (!_connected) return

        leaderboardsClient?.let {
            it.getLeaderboardIntent(name)
                .addOnSuccessListener { intent ->
                    activity?.startActivityForResult(intent, Common.RC_LEADERBOARD)
                }
                .addOnFailureListener { exception ->
                    Log.d(TAG, "Showing::Leaderboard::Failed ${exception.message}")
                }
        }
    }

    @UsedByGodot
    fun showAllLeaderboards() {
        if (!_connected) return

        leaderboardsClient?.let {
            it.allLeaderboardsIntent
                .addOnSuccessListener { intent ->
                    activity?.startActivityForResult(intent, Common.RC_LEADERBOARD)
                }
                .addOnFailureListener { exception ->
                    Log.d(TAG, "Showing::All::Leaderboard::Failed ${exception.message}")
                }
        }
    }

    @UsedByGodot
    fun canRecord(): Boolean {
        if (!_connected) return false

        if (videosClient == null) {
            Log.d(TAG, "Play Service Video Client is not initialized.")
            return false
        }

        return canRecord
    }

    @UsedByGodot
    fun record() {
        if (!_connected || !canRecord) return

        val account = GoogleSignIn.getLastSignedInAccount(context)
        account?.let {
            val client = Games.getVideosClient(context, it)
            client.captureOverlayIntent.addOnSuccessListener { intent ->
                emitSignal("recording_started")
                activity?.startActivityForResult(intent, 0)
            }
        }
    }

    @UsedByGodot
    fun isAvailable(): Boolean {
        val googleApiAvailability = GoogleApiAvailability.getInstance()
        val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(context)

        return resultCode == ConnectionResult.SUCCESS
    }

    @UsedByGodot
    fun getPlayerInfo(): Dictionary {
        if (!_connected) return Dictionary()

        return playerDetails
    }

    private fun onSignInSuccess(account: GoogleSignInAccount?) {
        Log.d(TAG, "SignIn Complete")

        account?.let {
            achievementClient = Games.getAchievementsClient(context, it)
            leaderboardsClient = Games.getLeaderboardsClient(context, it)
            playersClient = Games.getPlayersClient(context, it)
            videosClient = Games.getVideosClient(context, it)

            _connected = true

            emitSignal("signed_in")

            playersClient?.currentPlayer?.let { current ->
                current.addOnCompleteListener { player ->
                    if (player.isSuccessful) {
                        playerDetails.clear()

                        val details = player.result

                        playerDetails["name"] = details.name
                        playerDetails["title"] = details.title
                        playerDetails["player_id"] = details.playerId
                        playerDetails["display_name"] = details.displayName
                        playerDetails["icon_uri"] = details.iconImageUri?.toString()

                        emitSignal("profile_updated", playerDetails)
                    }
                }
            }

            isResolvingConnectionFailure = false
        }
    }

    private fun convertToDict(score: LeaderboardScore, name: String): Dictionary {
        val dict = Dictionary()
        dict["name"] = name
        dict["rank"] = score.rank
        dict["display_rank"] = score.displayRank
        dict["display_score"] = score.displayScore
        dict["raw_score"] = score.rawScore
        dict["tag"] = score.scoreTag
        dict["timestamp"] = score.timestampMillis

        return dict
    }

    private fun handleSignInResult(result: GoogleSignInResult) {
        if (result.isSuccess) {
            onSignInSuccess(result.signInAccount)
        } else {
            val s = result.status
            Log.e(TAG, "SignInResult::status::Code, Message: ${s.statusCode}, ${s.statusMessage}")

            if (isResolvingConnectionFailure) { return }
            if (!isIntentInProgress && result.status.hasResolution()) {
                try {
                    isIntentInProgress = true

                    s.resolution?.let {
                        activity?.startIntentSenderForResult(
                            it.intentSender,
                            Common.RC_GOOGLE,
                            null,
                            0,
                            0,
                            0
                        )
                    }

                } catch (e: IntentSender.SendIntentException) {
                    signIn()
                }

                isResolvingConnectionFailure = true
            }
        }
    }

    private fun signInSilently() {
        if (isConnected()) return

        val client = GoogleSignIn.getClient(context, GoogleSignInOptions.DEFAULT_GAMES_SIGN_IN)
        client.silentSignIn()
            .addOnSuccessListener { account ->
                onSignInSuccess(account)
            }
            .addOnFailureListener {
                Log.e(TAG, "SignInResult::Failed::${it.message}\n"+Log.getStackTraceString(it))
            }
    }

    override fun onMainActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == Common.RC_GOOGLE) {
            isIntentInProgress = false

            data?.let {
                val result = Auth.GoogleSignInApi.getSignInResultFromIntent(it)
                if (result != null) {
                    handleSignInResult(result)
                }
            }
        }
    }

    override fun getPluginSignals(): MutableSet<SignalInfo> {
        return mutableSetOf(
            SignalInfo("signed_in"),
            SignalInfo("signed_out"),
            SignalInfo("scores_loaded", ArrayList::class.javaObjectType),
            SignalInfo("score_loaded", Dictionary::class.javaObjectType),
            SignalInfo("profile_updated", Dictionary::class.javaObjectType),
            SignalInfo("recording_started")
        )
    }

    override fun getPluginName(): String {
        return "GDPlayService"
    }
}
