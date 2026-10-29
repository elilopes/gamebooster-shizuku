package com.gfx

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build

object AppDetectionHelper {
    fun isGameApp(appInfo: ApplicationInfo, pkgName: String, pm: PackageManager): Boolean {
        val categories = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appInfo.category
        } else {
            ApplicationInfo.CATEGORY_UNDEFINED
        }
        if (categories == ApplicationInfo.CATEGORY_GAME) return true
        
        val label = appInfo.loadLabel(pm).toString().lowercase()
        val keywords = listOf("game", "juego", "jogos", "play")
        return keywords.any { label.contains(it) }
    }
}
