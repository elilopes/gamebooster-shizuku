package com.example

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
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

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val packageManager = application.packageManager
    private val db = androidx.room.Room.databaseBuilder(application, AppDatabase::class.java, "boost-db").build()
    
    private val _games = MutableStateFlow<List<GameApp>>(emptyList())
    val games: StateFlow<List<GameApp>> = _games

    val nativeResolution = ShellHelper.getNativeResolution()

    init {
        loadGames()
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
