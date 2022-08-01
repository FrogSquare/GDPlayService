# GDPlayService
	Android plugin to implement Google Play Service into your game


[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://github.com/FrogSquare/GDFirebase)
[![GodotEngine](https://img.shields.io/badge/Godot_Engine-3.3-blue.svg)](https://github.com/godotengine/godot)
![GitHub](https://img.shields.io/github/license/FrogSquare/GDPlayService)

# Depends on

> Godot game engine: `git clone https://github.com/godotengine/godot`

# Available Features

> Login / Logout

> Player Info

> Achievements

> Leaderboard

> In-app Update

# Getting Started
* Install Android build Template to your `GAME-PROJECT`

```
func _ready():
    if Engine.has_singleton("GDPlayService"):
        google = Engine.get_singleton("GDPlayService")
        google.initialize()
```

#### Login / Logout
```
fun isConnected(): Boolean
fun signIn()
fun signOut()
fun getPlayerInfo(): Dictionary
```

#### Achievements
```
fun increaseAchievement(name: String, value: Int)
fun unlockAchievement(name: String)
fun showAchievements()
```

#### Leaderboard
```
fun loadTopScore(name: String, max: Int)
fun loadCurrentPlayerScore(name: String) 
fun submitScore(name: String, value: Int)
fun showLeaderboard(name: String)
fun showAllLeaderboards()
```

#### Record
```
fun canRecord(): Boolean
fun record()
```

### GDPlayCoreLibrary
Implements In-App Update
```
func _ready():
    if Engine.has_singleton("GDPlayCoreLibrary"):
        playCore = Engine.get_singleton("GDPlayCoreLibrary")
        playCore.startAppUpdatedManager({
            immediate = false,
            flexible_days = 3
        })
        playCore.connect("update_available", self, "_update_available")

func _update_available(data: Dictionary):
    if data.mode == "IMMEDIATE":
        playCore.startUpdateImmediate(false)
    else:
        playCore.startUpdateFlexible(false)
```

```
fun isUpdateAvailable(): Boolean 
fun startAppUpdatedManager(params: Dictionary)
fun startUpdateImmediate(allow_remove_asset: Boolean)
fun startUpdateFlexible(allow_remove_asset: Boolean)
```
