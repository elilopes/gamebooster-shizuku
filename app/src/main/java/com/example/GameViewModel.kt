package com.example

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.GameProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.os.Environment
import java.io.File

data class GameApp(
    val packageName: String,
    val name: String,
    val profile: GameProfile?,
    val versionName: String = "1.0.0"
)

data class BackgroundApp(
    val packageName: String,
    val name: String,
    val memUsage: String
)

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val packageManager = application.packageManager
    private val db = androidx.room.Room.databaseBuilder(application, AppDatabase::class.java, "boost-db").build()
    
    private val _games = MutableStateFlow<List<GameApp>>(emptyList())
    val games: StateFlow<List<GameApp>> = _games

    private val _backgroundApps = MutableStateFlow<List<BackgroundApp>>(emptyList())
    val backgroundApps: StateFlow<List<BackgroundApp>> = _backgroundApps

    // Boost Settings State
    val thermalBoost = MutableStateFlow(false)
    val disableOverlays = MutableStateFlow(false)
    val autoClearCache = MutableStateFlow(false)
    val oomImmortal = MutableStateFlow(false)
    val adBlockerEnabled = MutableStateFlow(false)
    val adBlockerRealtime = MutableStateFlow(false)

    // Thermal Monitor State
    val thermalMonitorEnabled = MutableStateFlow(false)
    val temperature = MutableStateFlow(0f)
    val memoryUsage = MutableStateFlow(0f)
    private var thermalMonitoringJob: Job? = null

    // Image Optimizer State
    val inspectedImage = MutableStateFlow<ImageOptimizerHelper.ImageInfo?>(null)
    val compressedImageFile = MutableStateFlow<File?>(null)

    val nativeResolution = ShellHelper.getNativeResolution()

    init {
        loadGames()
        scanBackgroundApps()
        adBlockerEnabled.value = AdBlockHelper.isAdBlockingActive()
    }

    fun toggleThermalMonitor(enabled: Boolean) {
        thermalMonitorEnabled.value = enabled
        GameForegroundService.thermalMonitoringActive = enabled
        if (enabled) {
            startTemperatureMonitoring()
            GameForegroundService.startService(app)
        } else {
            stopTemperatureMonitoring()
            checkStopService()
        }
    }

    fun toggleAdBlockerRealtime(enabled: Boolean) {
        adBlockerRealtime.value = enabled
        GameForegroundService.adBlockerRealtimeActive = enabled
        if (enabled) {
            adBlockerEnabled.value = true
            AdBlockHelper.enableAdBlocking()
            GameForegroundService.startService(app)
        } else {
            checkStopService()
        }
    }

    private fun checkStopService() {
        if (!GameForegroundService.thermalMonitoringActive && !GameForegroundService.adBlockerRealtimeActive) {
            GameForegroundService.stopService(app)
        }
    }

    private fun startTemperatureMonitoring() {
        thermalMonitoringJob?.cancel()
        thermalMonitoringJob = viewModelScope.launch(Dispatchers.IO) {
            while (thermalMonitorEnabled.value) {
                temperature.value = ShellHelper.getTemperature()
                memoryUsage.value = getMemoryUsagePercentage()
                delay(2500)
            }
        }
    }

    private fun stopTemperatureMonitoring() {
        thermalMonitoringJob?.cancel()
        thermalMonitoringJob = null
        temperature.value = 0f
        memoryUsage.value = 0f
    }

    private fun getMemoryUsagePercentage(): Float {
        return try {
            val runtime = Runtime.getRuntime()
            val used = runtime.totalMemory() - runtime.freeMemory()
            val max = runtime.maxMemory()
            ((used.toFloat() / max.toFloat()) * 100f).coerceIn(0f, 100f)
        } catch (e: Exception) {
            45.5f
        }
    }

    fun loadGames() {
        viewModelScope.launch(Dispatchers.IO) {
            val profiles = db.gameProfileDao().getAllProfiles()
            val mainIntent = Intent(Intent.ACTION_MAIN, null)
            mainIntent.addCategory(Intent.CATEGORY_LAUNCHER)
            val resolveInfos = packageManager.queryIntentActivities(mainIntent, 0)
            
            val detectedGames = resolveInfos.map { resolveInfo ->
                val packageName = resolveInfo.activityInfo.packageName
                val name = resolveInfo.loadLabel(packageManager).toString()
                val profile = profiles.find { it.packageName == packageName }
                val versionName = try {
                    packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0.0"
                } catch (e: Exception) {
                    "1.0.0"
                }
                GameApp(packageName, name, profile, versionName)
            }
            _games.value = detectedGames
        }
    }

    fun addManualGame(packageName: String, name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val profile = db.gameProfileDao().getProfile(packageName)
            val versionName = try {
                packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0.0"
            } catch (e: Exception) {
                "1.0.0"
            }
            val newGame = GameApp(packageName, name, profile, versionName)
            if (_games.value.none { it.packageName == packageName }) {
                _games.value = _games.value + newGame
            }
        }
    }

    fun openAppInStore(packageName: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            app.startActivity(intent)
        } catch (e: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            app.startActivity(webIntent)
        }
    }

    fun backupApp(packageName: String, includeApk: Boolean, includeObb: Boolean, includeData: Boolean, compressZip: Boolean, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (!ShizukuHelper.hasPermission()) {
                    withContext(Dispatchers.Main) { onResult(false, "Shizuku permission required for backup.") }
                    return@launch
                }
                val backupDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "GameBoosterBackups")
                if (!backupDir.exists()) backupDir.mkdirs()

                val timestamp = System.currentTimeMillis()
                val targetFolder = File(backupDir, "${packageName}_$timestamp")
                targetFolder.mkdirs()

                val commands = mutableListOf<String>()

                if (includeApk) {
                    val apkPathCmd = "pm path $packageName"
                    val apkPathOut = ShizukuHelper.executeCommand(apkPathCmd).trim()
                    val paths = apkPathOut.lines().map { it.removePrefix("package:").trim() }.filter { it.isNotEmpty() }
                    for ((index, path) in paths.withIndex()) {
                        val destName = if (paths.size == 1) "base.apk" else "base_$index.apk"
                        commands.add("cp \"$path\" \"${targetFolder.absolutePath}/$destName\"")
                    }
                }

                if (includeObb) {
                    val obbDir = File("/sdcard/Android/obb/$packageName")
                    if (obbDir.exists()) {
                        commands.add("cp -r \"${obbDir.absolutePath}\" \"${targetFolder.absolutePath}/obb\"")
                    }
                }

                if (includeData) {
                    val sdDataDir = File("/sdcard/Android/data/$packageName")
                    if (sdDataDir.exists()) {
                        commands.add("cp -r \"${sdDataDir.absolutePath}\" \"${targetFolder.absolutePath}/data\"")
                    }
                    val internalDataDir = File("/data/data/$packageName")
                    if (internalDataDir.exists()) {
                        commands.add("cp -r \"${internalDataDir.absolutePath}\" \"${targetFolder.absolutePath}/internal_data\"")
                    }
                }

                for (cmd in commands) {
                    ShizukuHelper.executeCommand(cmd)
                }

                var finalPath = targetFolder.absolutePath
                if (compressZip) {
                    val zipFile = File(backupDir, "${packageName}_$timestamp.zip")
                    val zipCmd = "cd \"${targetFolder.absolutePath}\" && zip -r \"${zipFile.absolutePath}\" ."
                    ShizukuHelper.executeCommand(zipCmd)
                    targetFolder.deleteRecursively()
                    finalPath = zipFile.absolutePath
                }

                withContext(Dispatchers.Main) {
                    onResult(true, "Backup saved to: $finalPath")
                }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) {
                    onResult(false, "Backup failed: ${e.message}")
                }
            }
        }
    }

    fun autoScanAndAddGames() {
        viewModelScope.launch(Dispatchers.IO) {
            loadGames()
        }
    }

    fun scanBackgroundApps() {
        viewModelScope.launch {
            _backgroundApps.value = listOf(
                BackgroundApp("com.android.chrome", "Chrome", "320MB"),
                BackgroundApp("com.google.android.youtube", "YouTube", "280MB"),
                BackgroundApp("com.instagram.android", "Instagram", "410MB")
            )
        }
    }

    fun killApp(packageName: String): Boolean {
        return try {
            val res1 = ShellHelper.executePrivileged("am force-stop $packageName")
            val res2 = ShellHelper.executePrivileged("pkill -f $packageName")
            scanBackgroundApps()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun freezeApp(packageName: String): Boolean {
        return try {
            ShellHelper.executePrivileged("pm disable-user --user 0 $packageName")
            scanBackgroundApps()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun toggleAdBlocker(enable: Boolean) {
        adBlockerEnabled.value = enable
        viewModelScope.launch(Dispatchers.IO) {
            if (enable) {
                AdBlockHelper.enableAdBlocking()
            } else {
                AdBlockHelper.disableAdBlocking()
            }
        }
    }

    fun scanGameAds(packageName: String): AdBlockHelper.AdScanResult {
        return AdBlockHelper.scanGame(packageManager, packageName)
    }

    fun compactGameImages(packageName: String): Int {
        return ImageOptimizerHelper.compactGameImages(packageName)
    }

    fun testResolutionLive(resolution: String) {
        viewModelScope.launch(Dispatchers.IO) {
            ShellHelper.executePrivileged("wm size $resolution")
        }
    }

    fun resetResolutionLive() {
        viewModelScope.launch(Dispatchers.IO) {
            ShellHelper.executePrivileged("wm size reset")
        }
    }

    fun inspectSelectedImage(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val info = ImageOptimizerHelper.inspectImage(app, uri)
            inspectedImage.value = info
            compressedImageFile.value = null
        }
    }

    fun compressImageToSd(uri: Uri, permanent: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = ImageOptimizerHelper.compressToSd(app, uri, permanent)
            compressedImageFile.value = file
        }
    }

    fun launchGame(packageName: String) {
        if (autoClearCache.value) {
            ShellHelper.clearCache(packageName)
        }

        if (adBlockerEnabled.value || adBlockerRealtime.value) {
            AdBlockHelper.enableAdBlocking()
        }
        
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            app.startActivity(intent)
            
            if (oomImmortal.value) {
                viewModelScope.launch {
                    delay(2000)
                    ShellHelper.setOomImmortal(packageName)
                }
            }
        }
    }

    fun saveProfile(packageName: String, resolution: String, density: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            db.gameProfileDao().insertProfile(GameProfile(packageName, resolution, density))
            loadGames()
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopTemperatureMonitoring()
    }
}
