package com.gfx

import android.app.Application
import android.util.Log
import com.gfx.data.AppDatabase
import com.gfx.data.InstalledAppRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GameBoosterApp : Application() {

    override fun onCreate() {
        super.onCreate()
        try {
            CrashReporterHelper.init(this)
            
            // Sincronização em background dos apps instalados
            val db = AppDatabase.getInstance(this)
            val repository = InstalledAppRepository(this, db.installedAppDao())
            CoroutineScope(Dispatchers.IO).launch {
                repository.syncApps()
            }
            
            Log.d("GameBoosterApp", "GameBoosterApp inicializado. Sincronização de apps iniciada.")
        } catch (e: Exception) {
            Log.e("GameBoosterApp", "Erro ao inicializar: ${e.message}", e)
        }
    }
}
