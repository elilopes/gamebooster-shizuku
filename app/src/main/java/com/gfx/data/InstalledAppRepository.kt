package com.gfx.data

import android.content.pm.PackageManager
import com.gfx.GfxTools
import com.gfx.AppDetectionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class InstalledAppRepository(
    private val context: android.app.Application,
    private val appDao: InstalledAppDao
) {
    val allApps = appDao.getAllApps()

    suspend fun syncApps() = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val installedPackages = pm.getInstalledPackages(PackageManager.GET_META_DATA)
        
        val apps = installedPackages.mapNotNull { pkg ->
            val appInfo = pkg.applicationInfo ?: return@mapNotNull null
            val isGame = AppDetectionHelper.isGameApp(appInfo, pkg.packageName, pm)
            val engine = GfxTools.detectEngine(context, pkg.packageName)
            
            InstalledAppEntity(
                packageName = pkg.packageName,
                appName = appInfo.loadLabel(pm).toString(),
                isGame = isGame,
                engine = engine.name
            )
        }
        
        appDao.deleteAllApps()
        appDao.insertApps(apps)
    }
}
