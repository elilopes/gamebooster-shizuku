package com.gfx

import android.app.Application
import android.util.Log

/**
 * [PT-BR] Classe Application principal do aplicativo Game Booster.
 *         Inicializa o gerenciador global de relatórios de falha (CrashReporterHelper) e configurações de diagnóstico.
 *
 * [EN]    Main Application class for the Game Booster app.
 *         Initializes the global crash reporting manager (CrashReporterHelper) and diagnostic settings.
 */
class GameBoosterApp : Application() {

    override fun onCreate() {
        super.onCreate()
        try {
            // Inicializa captura global de erros, crashes e geração de Bug Reports
            CrashReporterHelper.init(this)
            Log.d("GameBoosterApp", "GameBoosterApp inicializado com proteção global contra falhas.")
        } catch (e: Exception) {
            Log.e("GameBoosterApp", "Erro ao inicializar CrashReporterHelper: ${e.message}", e)
        }
    }
}
