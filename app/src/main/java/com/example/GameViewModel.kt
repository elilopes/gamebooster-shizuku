package com.example

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.GameProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class GameApp(
    val packageName: String,
    val name: String,
    val profile: GameProfile?
)

data class BackgroundApp(
    val packageName: String,
    val name: String,
    val memUsage: String
)

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val packageManager = application.packageManager
    private val db = androidx.room.Room.databaseBuilder(application, AppDatabase::class.java, "boost-db").build()
    
    private val _games = MutableStateFlow<List<GameApp>>(emptyList())
    val games: StateFlow<List<GameApp>> = _games

    private val _backgroundApps = MutableStateFlow<List<BackgroundApp>>(emptyList())
    val backgroundApps: StateFlow<List<BackgroundApp>> = _backgroundApps

    val nativeResolution = ShellHelper.getNativeResolution()

    init {
        loadGames()
        scanBackgroundApps()
    }

    private fun loadGames() {
        viewModelScope.launch {
            val mainIntent = Intent(Intent.ACTION_MAIN, null)
            mainIntent.addCategory(Intent.CATEGORY_LAUNCHER)
            val resolveInfos = packageManager.queryIntentActivities(mainIntent, 0)
            
            val gameList = resolveInfos.map { resolveInfo ->
                val packageName = resolveInfo.activityInfo.packageName
                val name = resolveInfo.loadLabel(packageManager).toString()
                val profile = db.gameProfileDao().getProfile(packageName)
                GameApp(packageName, name, profile)
            }
            _games.value = gameList
        }
    }

    fun scanBackgroundApps() {
        viewModelScope.launch {
            // Simplified scan for now: get list of running processes via dumpsys
            val output = ShellHelper.executePrivileged("ps -ef | grep -v root | grep -v system")
            // Parsing ps output is complex, placeholder list for demonstration:
            _backgroundApps.value = listOf(BackgroundApp("com.example.app1", "App 1", "500MB"))
        }
    }

    fun killApp(packageName: String) {
        ShellHelper.executePrivileged("am force-stop $packageName")
        scanBackgroundApps()
    }

    fun freezeApp(packageName: String) {
        ShellHelper.executePrivileged("pm disable-user --user 0 $packageName")
        scanBackgroundApps()
    }

    fun launchGame(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            getApplication<Application>().startActivity(intent)
        }
    }

    fun saveProfile(packageName: String, resolution: String, density: Int) {
        viewModelScope.launch {
            db.gameProfileDao().insertProfile(GameProfile(packageName, resolution, density))
            loadGames()
        }
    }
}
