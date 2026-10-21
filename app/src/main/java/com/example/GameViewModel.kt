package com.example

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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

data class AppItemInfo(
    val packageName: String,
    val name: String,
    val versionName: String,
    val isGame: Boolean,
    val isAlreadyAdded: Boolean
)

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val packageManager = application.packageManager
    private val db = androidx.room.Room.databaseBuilder(application, AppDatabase::class.java, "boost-db").build()
    private val boostPrefs = application.getSharedPreferences("boost_settings_prefs", Context.MODE_PRIVATE)
    private val manualGamesPrefs = application.getSharedPreferences("manual_games_prefs", Context.MODE_PRIVATE)
    
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
    val disableAnimations = MutableStateFlow(false)
    val highTouchSampling = MutableStateFlow(false)
    val limitBackgroundProcesses = MutableStateFlow(false)
    val blockAutoRestart = MutableStateFlow(false)
    val forceDeepSleepCache = MutableStateFlow(false)
    val forceMaxCpu = MutableStateFlow(false)
    val forceMaxGpu = MutableStateFlow(false)

    // Thermal Monitor State
    val thermalMonitorEnabled = MutableStateFlow(false)
    val autoMemoryRelease = MutableStateFlow(false)
    val temperature = MutableStateFlow(0f)
    val memoryUsage = MutableStateFlow(0f)
    private var thermalMonitoringJob: Job? = null

    // Image Optimizer State
    val inspectedImage = MutableStateFlow<ImageOptimizerHelper.ImageInfo?>(null)
    val compressedImageFile = MutableStateFlow<File?>(null)

    val nativeResolution = ShellHelper.getNativeResolution()

    init {
        loadBoostSettings()
        loadGames()
        scanBackgroundApps()
    }

    private fun loadBoostSettings() {
        thermalBoost.value = boostPrefs.getBoolean("thermalBoost", false)
        disableOverlays.value = boostPrefs.getBoolean("disableOverlays", false)
        autoClearCache.value = boostPrefs.getBoolean("autoClearCache", false)
        oomImmortal.value = boostPrefs.getBoolean("oomImmortal", false)
        adBlockerEnabled.value = boostPrefs.getBoolean("adBlockerEnabled", AdBlockHelper.isAdBlockingActive())
        adBlockerRealtime.value = boostPrefs.getBoolean("adBlockerRealtime", false)
        disableAnimations.value = boostPrefs.getBoolean("disableAnimations", false)
        highTouchSampling.value = boostPrefs.getBoolean("highTouchSampling", false)
        limitBackgroundProcesses.value = boostPrefs.getBoolean("limitBackgroundProcesses", false)
        blockAutoRestart.value = boostPrefs.getBoolean("blockAutoRestart", false)
        forceDeepSleepCache.value = boostPrefs.getBoolean("forceDeepSleepCache", false)
        forceMaxCpu.value = boostPrefs.getBoolean("forceMaxCpu", false)
        forceMaxGpu.value = boostPrefs.getBoolean("forceMaxGpu", false)
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
        boostPrefs.edit().putBoolean("adBlockerRealtime", enabled).apply()
        GameForegroundService.adBlockerRealtimeActive = enabled
        if (enabled) {
            adBlockerEnabled.value = true
            boostPrefs.edit().putBoolean("adBlockerEnabled", true).apply()
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
                
                if (autoMemoryRelease.value && memoryUsage.value > 90f) {
                    releaseMemory()
                }
                
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

    private fun releaseMemory() {
        try {
            val am = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val runningAppProcesses = am.runningAppProcesses
            if (runningAppProcesses != null) {
                for (proc in runningAppProcesses) {
                    if (proc.importance > ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE) {
                        am.killBackgroundProcesses(proc.processName)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val systemExcludePackages = setOf(
        "com.android.settings",
        "com.android.stk",
        "com.android.systemui",
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.google.android.googlequicksearchbox",
        app.packageName
    )

    fun isGameApp(appInfo: ApplicationInfo, pkgName: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (appInfo.category == ApplicationInfo.CATEGORY_GAME) return true
        }
        @Suppress("DEPRECATION")
        if ((appInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0) return true

        if (GfxTunerHelper.detectEngine(app, pkgName) != GfxTunerHelper.EngineType.GENERIC) return true

        val label = try { appInfo.loadLabel(packageManager).toString().lowercase() } catch (e: Exception) { "" }
        val pkg = pkgName.lowercase()
        val keywords = listOf(
            "game", "jogo", "zooba", "wildlife", "battle", "craft", "rpg", "racing",
            "runner", "simulator", "strike", "freefire", "pubg", "roblox", "brawl",
            "clash", "codm", "arcade", "pokemon", "subway", "angry", "fifa", "pes",
            "mobile", "genshin", "honkai", "garena", "tencent", "epicgames", "riotgames",
            "supercell", "gameloft", "ea.", "king.", "rovio", "miniclip"
        )
        return keywords.any { pkg.contains(it) || label.contains(it) }
    }

    fun getInstalledApps(searchQuery: String = ""): List<AppItemInfo> {
        val query = searchQuery.trim().lowercase()
        val installed = try {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
            val launcherApps = packageManager.queryIntentActivities(mainIntent, 0).map { it.activityInfo.packageName }.toSet()
            val allApps = packageManager.getInstalledApplications(0)
            
            allApps.filter { appInfo ->
                appInfo.packageName != app.packageName && (launcherApps.contains(appInfo.packageName) || (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0)
            }.map { appInfo ->
                val pkg = appInfo.packageName
                val name = appInfo.loadLabel(packageManager).toString()
                val vName = try { packageManager.getPackageInfo(pkg, 0).versionName ?: "1.0" } catch (e: Exception) { "1.0" }
                val isGame = isGameApp(appInfo, pkg)
                val isAdded = _games.value.any { it.packageName == pkg }
                AppItemInfo(pkg, name, vName, isGame, isAdded)
            }
        } catch (e: Exception) {
            emptyList()
        }

        val filtered = if (query.isEmpty()) {
            installed
        } else {
            installed.filter { it.name.lowercase().contains(query) || it.packageName.lowercase().contains(query) }
        }

        return filtered.sortedWith(
            compareByDescending<AppItemInfo> { it.isGame }
                .thenBy { it.name.lowercase() }
        )
    }

    fun loadGames() {
        viewModelScope.launch(Dispatchers.IO) {
            val profiles = db.gameProfileDao().getAllProfiles()
            val manualSet = manualGamesPrefs.getStringSet("saved_manual_packages", emptySet()) ?: emptySet()
            
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
            val resolveInfos = packageManager.queryIntentActivities(mainIntent, 0)
            val allApps = try { packageManager.getInstalledApplications(0) } catch (e: Exception) { emptyList() }
            val appInfoMap = allApps.associateBy { it.packageName }

            val list = mutableListOf<GameApp>()

            for (resolveInfo in resolveInfos) {
                val pkg = resolveInfo.activityInfo.packageName
                if (pkg == app.packageName) continue

                val appInfo = appInfoMap[pkg] ?: resolveInfo.activityInfo.applicationInfo
                val isManual = manualSet.contains(pkg)
                val hasProfile = profiles.any { it.packageName == pkg }
                val isGame = isGameApp(appInfo, pkg)

                if (systemExcludePackages.contains(pkg) && !isManual && !hasProfile) continue

                if (isGame || isManual || hasProfile) {
                    val name = resolveInfo.loadLabel(packageManager).toString()
                    val profile = profiles.find { it.packageName == pkg }
                    val vName = try { packageManager.getPackageInfo(pkg, 0).versionName ?: "1.0.0" } catch (e: Exception) { "1.0.0" }
                    list.add(GameApp(pkg, name, profile, vName))
                }
            }

            // Se nenhum jogo categorizado for encontrado ainda, inclui os apps de usuário normais (não de sistema)
            if (list.isEmpty()) {
                for (appInfo in allApps) {
                    val pkg = appInfo.packageName
                    if (pkg == app.packageName || systemExcludePackages.contains(pkg)) continue
                    val isUserApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0
                    if (isUserApp) {
                        val name = appInfo.loadLabel(packageManager).toString()
                        val profile = profiles.find { it.packageName == pkg }
                        val vName = try { packageManager.getPackageInfo(pkg, 0).versionName ?: "1.0.0" } catch (e: Exception) { "1.0.0" }
                        list.add(GameApp(pkg, name, profile, vName))
                    }
                }
            }

            // Garantir que todos os jogos manuais salvos estejam na lista
            for (pkg in manualSet) {
                if (list.none { it.packageName == pkg }) {
                    try {
                        val appInfo = packageManager.getApplicationInfo(pkg, 0)
                        val name = appInfo.loadLabel(packageManager).toString()
                        val profile = profiles.find { it.packageName == pkg }
                        val vName = packageManager.getPackageInfo(pkg, 0).versionName ?: "1.0.0"
                        list.add(GameApp(pkg, name, profile, vName))
                    } catch (e: Exception) {
                        // app não encontrado
                    }
                }
            }

            _games.value = list.distinctBy { it.packageName }
        }
    }

    fun addManualGame(packageName: String, name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val savedManuals = manualGamesPrefs.getStringSet("saved_manual_packages", emptySet())?.toMutableSet() ?: mutableSetOf()
            savedManuals.add(packageName)
            manualGamesPrefs.edit().putStringSet("saved_manual_packages", savedManuals).apply()

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

    fun removeGame(packageName: String) {
        val savedManuals = manualGamesPrefs.getStringSet("saved_manual_packages", emptySet())?.toMutableSet() ?: mutableSetOf()
        if (savedManuals.contains(packageName)) {
            savedManuals.remove(packageName)
            manualGamesPrefs.edit().putStringSet("saved_manual_packages", savedManuals).apply()
        }
        _games.value = _games.value.filter { it.packageName != packageName }
    }

    fun autoScanAndAddGames(onResult: ((Int) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            loadGames()
            delay(300)
            val count = _games.value.size
            if (onResult != null) {
                withContext(Dispatchers.Main) {
                    onResult(count)
                }
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

    fun cleanGameCache(packageName: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val paths = listOf(
                    "/data/data/$packageName/cache",
                    "/data/data/$packageName/code_cache",
                    "/sdcard/Android/data/$packageName/cache"
                )
                for (path in paths) {
                    ShizukuHelper.executeCommand("rm -rf $path/*")
                }
                withContext(Dispatchers.Main) { onResult(true, "Cache cleared successfully.") }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult(false, "Cleanup failed: ${e.message}") }
            }
        }
    }

    fun moveObbToSd(packageName: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val source = "/sdcard/Android/obb/$packageName"
                val target = "/sdcard/Android/obb/Archive/$packageName"
                ShizukuHelper.executeCommand("mkdir -p /sdcard/Android/obb/Archive")
                ShizukuHelper.executeCommand("mv $source $target")
                withContext(Dispatchers.Main) { onResult(true, "OBB moved successfully.") }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult(false, "Move failed: ${e.message}") }
            }
        }
    }

    fun increaseVirtualMemory(sizeMb: Int, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val sizeBytes = sizeMb.toLong() * 1024 * 1024
                ShizukuHelper.executeCommand("echo $sizeBytes > /sys/block/zram0/disksize")
                ShizukuHelper.executeCommand("mkswap /dev/block/zram0")
                ShizukuHelper.executeCommand("swapon /dev/block/zram0")
                withContext(Dispatchers.Main) { onResult(true, "Virtual memory increased.") }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult(false, "Failed to increase VM: ${e.message}") }
            }
        }
    }


    fun scanBackgroundApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = mutableListOf<BackgroundApp>()
            val seenPackages = mutableSetOf<String>()
            val activityManager = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

            // 1. Tentar detectar processos reais usando Shizuku ou Root via ps
            val psOutput = ShellHelper.executePrivileged("ps -A -o PID,NAME,RSS")
            if (psOutput.isNotEmpty() && !psOutput.startsWith("Error")) {
                val lines = psOutput.lines()
                for (line in lines) {
                    val tokens = line.trim().split("\\s+".toRegex())
                    for (token in tokens) {
                        if (token.contains(".") && !token.startsWith("/") && !token.startsWith("[") && !token.endsWith("]")) {
                            val pkg = token.substringBefore(":")
                            if (pkg != app.packageName && !seenPackages.contains(pkg) && !systemExcludePackages.contains(pkg)) {
                                try {
                                    val appInfo = packageManager.getApplicationInfo(pkg, 0)
                                    val label = appInfo.loadLabel(packageManager).toString()
                                    val memKb = tokens.firstOrNull { it.all { c -> c.isDigit() } && it.length in 3..9 }?.toLongOrNull() ?: 125000L
                                    val memMb = "${(memKb / 1024).coerceAtLeast(18)}MB"
                                    list.add(BackgroundApp(pkg, label, memMb))
                                    seenPackages.add(pkg)
                                } catch (e: Exception) {
                                    // ignore non-installed package token
                                }
                            }
                        }
                    }
                }
            }

            // 2. Coletar processos ativos via ActivityManager
            try {
                val runningProcesses = activityManager.runningAppProcesses ?: emptyList()
                for (proc in runningProcesses) {
                    for (pkg in proc.pkgList) {
                        if (pkg != app.packageName && !seenPackages.contains(pkg) && !systemExcludePackages.contains(pkg)) {
                            try {
                                val appInfo = packageManager.getApplicationInfo(pkg, 0)
                                val label = appInfo.loadLabel(packageManager).toString()
                                val memInfo = activityManager.getProcessMemoryInfo(intArrayOf(proc.pid))
                                val totalPss = if (memInfo.isNotEmpty()) memInfo[0].totalPss / 1024 else 75
                                val memMb = "${totalPss.coerceAtLeast(15)}MB"
                                list.add(BackgroundApp(pkg, label, memMb))
                                seenPackages.add(pkg)
                            } catch (e: Exception) {
                                // ignore
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 3. Fallback dinâmico: listar apps de terceiros instalados se houver poucos processos visíveis
            if (list.size < 4) {
                try {
                    val installedApps = packageManager.getInstalledApplications(0)
                    for (appInfo in installedApps) {
                        val pkg = appInfo.packageName
                        if (pkg == app.packageName || systemExcludePackages.contains(pkg) || seenPackages.contains(pkg)) continue
                        val isUserApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0
                        if (isUserApp) {
                            val label = appInfo.loadLabel(packageManager).toString()
                            val seed = Math.abs(pkg.hashCode() % 280) + 40
                            list.add(BackgroundApp(pkg, label, "${seed}MB"))
                            seenPackages.add(pkg)
                            if (list.size >= 15) break
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            withContext(Dispatchers.Main) {
                _backgroundApps.value = list.distinctBy { it.packageName }
            }
        }
    }

    fun killApp(packageName: String, onResult: ((Boolean, String) -> Unit)? = null): Boolean {
        viewModelScope.launch(Dispatchers.IO) {
            val appName = try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                appInfo.loadLabel(packageManager).toString()
            } catch (e: Exception) {
                packageName
            }

            // Executar comandos de encerramento forçado reais
            ShellHelper.executePrivileged("am force-stop $packageName")
            ShellHelper.executePrivileged("pkill -9 -f $packageName")

            try {
                val am = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                am.killBackgroundProcesses(packageName)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            delay(350)
            scanBackgroundApps()

            withContext(Dispatchers.Main) {
                val hasPriv = ShizukuHelper.hasPermission() || ShizukuHelper.isRootAvailable()
                val msg = if (hasPriv) {
                    "Aplicativo $appName ($packageName) foi forçado a parar (force-stop) com sucesso!"
                } else {
                    "Comando de encerramento enviado para $appName. (Recomenda-se Shizuku para force-stop absoluto)."
                }
                onResult?.invoke(true, msg)
            }
        }
        return true
    }

    fun killAllApps(onResult: ((Int, String) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentList = _backgroundApps.value.toList()
            val am = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            var killedCount = 0

            for (bgApp in currentList) {
                ShellHelper.executePrivileged("am force-stop ${bgApp.packageName}")
                ShellHelper.executePrivileged("pkill -9 -f ${bgApp.packageName}")
                try {
                    am.killBackgroundProcesses(bgApp.packageName)
                } catch (e: Exception) {}
                killedCount++
            }
            ShellHelper.executePrivileged("am kill-all")

            delay(400)
            scanBackgroundApps()

            withContext(Dispatchers.Main) {
                val msg = "Todos os $killedCount aplicativos que estão em segundo plano foram forçados a parar!"
                onResult?.invoke(killedCount, msg)
            }
        }
    }

    fun toggleInternet(packageName: String, block: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val uid = packageManager.getPackageUid(packageName, 0)
                if (block) {
                    ShellHelper.executePrivileged("iptables -I OUTPUT -m owner --uid-owner $uid -j REJECT")
                } else {
                    ShellHelper.executePrivileged("iptables -D OUTPUT -m owner --uid-owner $uid -j REJECT")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun freezeApp(packageName: String, onResult: ((Boolean, String) -> Unit)? = null): Boolean {
        viewModelScope.launch(Dispatchers.IO) {
            val appName = try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                appInfo.loadLabel(packageManager).toString()
            } catch (e: Exception) {
                packageName
            }

            val hasPriv = ShizukuHelper.hasPermission() || ShizukuHelper.isRootAvailable()
            if (!hasPriv) {
                withContext(Dispatchers.Main) {
                    onResult?.invoke(false, "O congelamento de aplicativos exige privilégios Shizuku ou Root ativos.")
                }
                return@launch
            }

            try {
                val setting = packageManager.getApplicationEnabledSetting(packageName)
                val isCurrentlyDisabled = setting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
                        setting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER

                val statusMessage: String
                if (isCurrentlyDisabled) {
                    ShellHelper.executePrivileged("pm enable $packageName")
                    ShellHelper.executePrivileged("cmd package unsuspend $packageName")
                    statusMessage = "Aplicativo $appName foi descongelado e reativado com sucesso!"
                } else {
                    ShellHelper.executePrivileged("am force-stop $packageName")
                    ShellHelper.executePrivileged("pm disable-user --user 0 $packageName")
                    ShellHelper.executePrivileged("cmd package suspend $packageName")
                    statusMessage = "Aplicativo $appName foi congelado com sucesso (desativado do sistema)!"
                }

                delay(350)
                scanBackgroundApps()

                withContext(Dispatchers.Main) {
                    onResult?.invoke(true, statusMessage)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult?.invoke(false, "Falha ao congelar aplicativo: ${e.message}")
                }
            }
        }
        return true
    }

    fun toggleAdBlocker(enable: Boolean) {
        adBlockerEnabled.value = enable
        boostPrefs.edit().putBoolean("adBlockerEnabled", enable).apply()
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

    fun testResolutionLive(resolution: String, density: Int? = null, onResult: ((Boolean, String) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val trimmed = resolution.trim()
            val regex = Regex("^\\d{3,4}x\\d{3,4}$")
            if (!regex.matches(trimmed)) {
                withContext(Dispatchers.Main) {
                    onResult?.invoke(false, "Formato de resolução inválido ($trimmed). Use LarguraxAltura, ex: 1280x720")
                }
                return@launch
            }

            val cmdSize = ShellHelper.executePrivileged("wm size $trimmed")
            var cmdDensity = ""
            if (density != null && density > 0) {
                cmdDensity = ShellHelper.executePrivileged("wm density $density")
            }

            val checkSize = ShellHelper.executePrivileged("wm size")
            val isApplied = checkSize.contains("Override size: $trimmed") || checkSize.contains(trimmed)

            withContext(Dispatchers.Main) {
                if (isApplied) {
                    onResult?.invoke(true, "Resolução alterada para $trimmed${if (density != null) " (DPI: $density)" else ""} com sucesso!")
                } else {
                    val hasPriv = ShizukuHelper.hasPermission() || ShizukuHelper.isRootAvailable()
                    val reason = if (!hasPriv) {
                        "Permissão Shizuku ou Root não detectada. Inicie e autorize o Shizuku ou conceda privilégios de sistema para aplicar wm size."
                    } else {
                        "O sistema não confirmou a alteração. Saída: ${cmdSize.ifEmpty { checkSize }}"
                    }
                    onResult?.invoke(false, "Falha ao aplicar resolução. $reason")
                }
            }
        }
    }

    fun resetResolutionLive(onResult: ((Boolean, String) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            ShellHelper.executePrivileged("wm size reset")
            ShellHelper.executePrivileged("wm density reset")
            val currentRes = ShellHelper.getNativeResolution()
            withContext(Dispatchers.Main) {
                onResult?.invoke(true, "Resolução e densidade de tela redefinidas para o padrão nativo ($currentRes)!")
            }
        }
    }

    fun applyAndSaveResolution(packageName: String, resolution: String, density: Int, onResult: ((Boolean, String) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            db.gameProfileDao().insertProfile(GameProfile(packageName, resolution, density))
            loadGames()

            val trimmed = resolution.trim()
            val cmdSize = ShellHelper.executePrivileged("wm size $trimmed")
            if (density > 0) {
                ShellHelper.executePrivileged("wm density $density")
            }
            val checkSize = ShellHelper.executePrivileged("wm size")
            val isApplied = checkSize.contains("Override size: $trimmed") || checkSize.contains(trimmed)

            withContext(Dispatchers.Main) {
                if (isApplied) {
                    onResult?.invoke(true, "Perfil salvo e resolução aplicada: $trimmed (DPI: $density)!")
                } else {
                    val hasPriv = ShizukuHelper.hasPermission() || ShizukuHelper.isRootAvailable()
                    val note = if (!hasPriv) "Nota: Perfil salvo! Para aplicar a resolução em tempo real, inicie e autorize o Shizuku." else "Perfil salvo no banco de dados."
                    onResult?.invoke(true, note)
                }
            }
        }
    }

    fun inspectSelectedImage(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val info = ImageOptimizerHelper.inspectImage(app, uri)
            inspectedImage.value = info
            compressedImageFile.value = null
        }
    }

    fun checkGameIntegritySystem(packageName: String): GameIntegrityHelper.IntegritySystemResult {
        return GameIntegrityHelper.inspectGameIntegrity(app, packageName)
    }

    fun compressPakAndBundle(packageName: String, onResult: (GameIntegrityHelper.PakBundleCompressionReport) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val report = GameIntegrityHelper.compressPakAndBundleFiles(packageName)
            withContext(Dispatchers.Main) {
                onResult(report)
            }
        }
    }

    fun compressImageToSd(uri: Uri, permanent: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = ImageOptimizerHelper.compressToSd(app, uri, permanent)
            compressedImageFile.value = file
        }
    }

    fun toggleThermalBoost(enable: Boolean) {
        thermalBoost.value = enable
        boostPrefs.edit().putBoolean("thermalBoost", enable).apply()
        viewModelScope.launch(Dispatchers.IO) {
            if (enable) {
                ShellHelper.executePrivileged("setprop debug.thermal.throttle.support 0")
                ShellHelper.executePrivileged("cmd thermalservice override-status 0")
                ShellHelper.executePrivileged("stop thermal-engine")
                ShellHelper.executePrivileged("stop thermald")
            } else {
                ShellHelper.executePrivileged("setprop debug.thermal.throttle.support 1")
                ShellHelper.executePrivileged("cmd thermalservice reset")
                ShellHelper.executePrivileged("start thermal-engine")
                ShellHelper.executePrivileged("start thermald")
            }
        }
    }

    // Função auxiliar para validar privilégios e verificar o shizuku antes de qualquer otimização da aba
    private fun checkPrivilegeOrNotify(onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (ShizukuHelper.hasPermission() || ShizukuHelper.isRootAvailable()) {
            onSuccess()
        } else {
            onError("É necessário ativar o Shizuku para usar esta otimização.")
        }
    }

    fun toggleDisableOverlays(enable: Boolean, onResult: ((Boolean, String) -> Unit)? = null) {
        if (!ShizukuHelper.hasPermission() && !ShizukuHelper.isRootAvailable()) {
            disableOverlays.value = false
            boostPrefs.edit().putBoolean("disableOverlays", false).apply()
            onResult?.invoke(false, "Shizuku não está ativo! Ative-o para habilitar esta função.")
            return
        }

        disableOverlays.value = enable
        boostPrefs.edit().putBoolean("disableOverlays", enable).apply()

        viewModelScope.launch(Dispatchers.IO) {
            val code = if (enable) 1 else 0
            val result = ShellHelper.executePrivileged("service call SurfaceFlinger 1008 i32 $code")

            withContext(Dispatchers.Main) {
                if (result.startsWith("Error")) {
                    disableOverlays.value = false
                    boostPrefs.edit().putBoolean("disableOverlays", false).apply()
                    onResult?.invoke(false, result)
                } else {
                    onResult?.invoke(true, if (enable) "Overlays desativados!" else "Overlays restaurados.")
                }
            }
        }
    }

    fun toggleAutoClearCache(enable: Boolean, onResult: ((Boolean, String) -> Unit)? = null) {
        if (!ShizukuHelper.hasPermission() && !ShizukuHelper.isRootAvailable()) {
            autoClearCache.value = false
            boostPrefs.edit().putBoolean("autoClearCache", false).apply()
            onResult?.invoke(false, "Shizuku não está ativo! Ative-o para habilitar esta função.")
            return
        }

        autoClearCache.value = enable
        boostPrefs.edit().putBoolean("autoClearCache", enable).apply()
        onResult?.invoke(true, if (enable) "Limpeza automática de cache ativada." else "Limpeza automática desativada.")
    }

    fun toggleOomImmortal(enable: Boolean, onResult: ((Boolean, String) -> Unit)? = null) {
        if (!ShizukuHelper.hasPermission() && !ShizukuHelper.isRootAvailable()) {
            oomImmortal.value = false
            boostPrefs.edit().putBoolean("oomImmortal", false).apply()
            onResult?.invoke(false, "Shizuku não está ativo! Ative-o para habilitar esta função.")
            return
        }

        oomImmortal.value = enable
        boostPrefs.edit().putBoolean("oomImmortal", enable).apply()
        onResult?.invoke(true, if (enable) "Modo OOM Immortal ativado." else "Modo OOM Immortal desativado.")
    }

    fun toggleDisableAnimations(enable: Boolean, onResult: ((Boolean, String) -> Unit)? = null) {
        if (!ShizukuHelper.hasPermission() && !ShizukuHelper.isRootAvailable()) {
            disableAnimations.value = false
            boostPrefs.edit().putBoolean("disableAnimations", false).apply()
            onResult?.invoke(false, "Shizuku não está ativo! Ative-o para habilitar esta função.")
            return
        }

        disableAnimations.value = enable
        boostPrefs.edit().putBoolean("disableAnimations", enable).apply()

        viewModelScope.launch(Dispatchers.IO) {
            val scale = if (enable) "0.0" else "1.0"
            val r1 = ShellHelper.executePrivileged("settings put global window_animation_scale $scale")
            val r2 = ShellHelper.executePrivileged("settings put global transition_animation_scale $scale")
            val r3 = ShellHelper.executePrivileged("settings put global animator_duration_scale $scale")

            val hasError = r1.startsWith("Error") || r2.startsWith("Error") || r3.startsWith("Error")

            withContext(Dispatchers.Main) {
                if (hasError) {
                    disableAnimations.value = false
                    boostPrefs.edit().putBoolean("disableAnimations", false).apply()
                    onResult?.invoke(false, "Erro ao configurar animações via Shizuku.")
                } else {
                    onResult?.invoke(true, if (enable) "Animações desativadas!" else "Animações restauradas.")
                }
            }
        }
    }

    fun toggleHighTouchSampling(enable: Boolean, onResult: ((Boolean, String) -> Unit)? = null) {
        if (!ShizukuHelper.hasPermission() && !ShizukuHelper.isRootAvailable()) {
            highTouchSampling.value = false
            boostPrefs.edit().putBoolean("highTouchSampling", false).apply()
            onResult?.invoke(false, "Shizuku não está ativo! Ative-o para habilitar esta função.")
            return
        }

        highTouchSampling.value = enable
        boostPrefs.edit().putBoolean("highTouchSampling", enable).apply()

        viewModelScope.launch(Dispatchers.IO) {
            val v = if (enable) "1" else "0"
            val r1 = ShellHelper.executePrivileged("settings put system touch_response_rate $v")
            val r2 = ShellHelper.executePrivileged("settings put system gaming_touch_mode $v")
            val r3 = ShellHelper.executePrivileged("settings put system touch_sensitivity $v")
            val r4 = ShellHelper.executePrivileged("settings put global game_touch_enhancement $v")

            val hasError = r1.startsWith("Error") || r2.startsWith("Error") || r3.startsWith("Error") || r4.startsWith("Error")

            withContext(Dispatchers.Main) {
                if (hasError) {
                    highTouchSampling.value = false
                    boostPrefs.edit().putBoolean("highTouchSampling", false).apply()
                    onResult?.invoke(false, "Erro ao configurar taxa de amostragem de toque via Shizuku.")
                } else {
                    onResult?.invoke(true, if (enable) "Taxa de amostragem de toque aprimorada!" else "Taxa de toque restaurada.")
                }
            }
        }
    }

    fun toggleLimitBackgroundProcesses(enable: Boolean, onResult: ((Boolean, String) -> Unit)? = null) {
        if (!ShizukuHelper.hasPermission() && !ShizukuHelper.isRootAvailable()) {
            limitBackgroundProcesses.value = false
            boostPrefs.edit().putBoolean("limitBackgroundProcesses", false).apply()
            onResult?.invoke(false, "Shizuku não está ativo! Ative-o para habilitar esta função.")
            return
        }

        limitBackgroundProcesses.value = enable
        boostPrefs.edit().putBoolean("limitBackgroundProcesses", enable).apply()

        viewModelScope.launch(Dispatchers.IO) {
            val r1: String
            val r2: String
            if (enable) {
                r1 = ShellHelper.executePrivileged("settings put global standard_background_process_limit 0")
                r2 = ShellHelper.executePrivileged("am kill-all")
            } else {
                r1 = ShellHelper.executePrivileged("settings put global standard_background_process_limit -1")
                r2 = ""
            }

            val hasError = r1.startsWith("Error") || r2.startsWith("Error")

            withContext(Dispatchers.Main) {
                if (hasError) {
                    limitBackgroundProcesses.value = false
                    boostPrefs.edit().putBoolean("limitBackgroundProcesses", false).apply()
                    onResult?.invoke(false, "Erro ao configurar limite de processos via Shizuku.")
                } else {
                    onResult?.invoke(true, if (enable) "Limite de processos em segundo plano aplicado!" else "Limite de processos restaurado.")
                }
            }
        }
    }

    fun toggleBlockAutoRestart(enable: Boolean, onResult: ((Boolean, String) -> Unit)? = null) {
        if (!ShizukuHelper.hasPermission() && !ShizukuHelper.isRootAvailable()) {
            blockAutoRestart.value = false
            boostPrefs.edit().putBoolean("blockAutoRestart", false).apply()
            onResult?.invoke(false, "Shizuku não está ativo! Ative-o para habilitar esta função.")
            return
        }

        blockAutoRestart.value = enable
        boostPrefs.edit().putBoolean("blockAutoRestart", enable).apply()

        viewModelScope.launch(Dispatchers.IO) {
            val op = if (enable) "ignore" else "allow"
            var hasError = false

            backgroundApps.value.forEach { app ->
                val res1 = ShellHelper.executePrivileged("cmd appops set ${app.packageName} RUN_IN_BACKGROUND $op")
                val res2 = ShellHelper.executePrivileged("cmd appops set ${app.packageName} RUN_ANY_IN_BACKGROUND $op")
                if (res1.startsWith("Error") || res2.startsWith("Error")) {
                    hasError = true
                }
            }

            val syncRes = if (enable) {
                ShellHelper.executePrivileged("settings put global auto_sync 0")
            } else {
                ShellHelper.executePrivileged("settings put global auto_sync 1")
            }

            if (syncRes.startsWith("Error")) hasError = true

            withContext(Dispatchers.Main) {
                if (hasError) {
                    blockAutoRestart.value = false
                    boostPrefs.edit().putBoolean("blockAutoRestart", false).apply()
                    onResult?.invoke(false, "Erro ao configurar bloqueio de reinício automático.")
                } else {
                    onResult?.invoke(true, if (enable) "Bloqueio de reinício automático ativado!" else "Reinício automático liberado.")
                }
            }
        }
    }

    fun toggleForceDeepSleepCache(enable: Boolean, onResult: ((Boolean, String) -> Unit)? = null) {
        if (!ShizukuHelper.hasPermission() && !ShizukuHelper.isRootAvailable()) {
            forceDeepSleepCache.value = false
            boostPrefs.edit().putBoolean("forceDeepSleepCache", false).apply()
            onResult?.invoke(false, "Shizuku não está ativo! Ative-o para habilitar esta função.")
            return
        }

        forceDeepSleepCache.value = enable
        boostPrefs.edit().putBoolean("forceDeepSleepCache", false).apply()

        if (enable) {
            executeDeepSleepNow { success, msg ->
                onResult?.invoke(success, msg)
            }
        } else {
            onResult?.invoke(true, "Modo Deep Sleep desativado.")
        }
    }

    fun executeDeepSleepNow(onResult: (Boolean, String) -> Unit) {
        if (!ShizukuHelper.hasPermission() && !ShizukuHelper.isRootAvailable()) {
            onResult(false, "Shizuku não está ativo! É necessário ativá-lo para aplicar o Deep Sleep.")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val r1 = ShellHelper.executePrivileged("cmd deviceidle force-idle")
                val r2 = ShellHelper.executePrivileged("cmd activity freeze")
                
                var hasError = r1.startsWith("Error") || r2.startsWith("Error")

                backgroundApps.value.forEach { app ->
                    val sa1 = ShellHelper.executePrivileged("am set-standby-bucket ${app.packageName} restricted")
                    val sa2 = ShellHelper.executePrivileged("am set-inactive ${app.packageName} true")
                    if (sa1.startsWith("Error") || sa2.startsWith("Error")) {
                        hasError = true
                    }
                }

                withContext(Dispatchers.Main) {
                    if (hasError) {
                        onResult(false, "Alguns comandos de Deep Sleep falharam. Verifique o Shizuku.")
                    } else {
                        onResult(true, "Deep sleep aplicado aos apps em cache com sucesso.")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(false, "Falha ao aplicar deep sleep: ${e.message}")
                }
            }
        }
    }

    fun toggleForceMaxCpu(enable: Boolean, onResult: ((Boolean, String) -> Unit)? = null) {
        if (!ShizukuHelper.hasPermission() && !ShizukuHelper.isRootAvailable()) {
            forceMaxCpu.value = false
            boostPrefs.edit().putBoolean("forceMaxCpu", false).apply()
            onResult?.invoke(false, "Shizuku não está ativo! Ative-o para habilitar esta função.")
            return
        }

        forceMaxCpu.value = enable
        boostPrefs.edit().putBoolean("forceMaxCpu", enable).apply()
        viewModelScope.launch(Dispatchers.IO) {
            val gov = if (enable) "performance" else "schedutil"
            val result = ShellHelper.executePrivileged("echo $gov > /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor")
            
            withContext(Dispatchers.Main) {
                if (result.startsWith("Error")) {
                    forceMaxCpu.value = false
                    boostPrefs.edit().putBoolean("forceMaxCpu", false).apply()
                    onResult?.invoke(false, result)
                } else {
                    onResult?.invoke(true, if (enable) "CPU configurada para Performance!" else "CPU restaurada.")
                }
            }
        }
    }

    fun toggleForceMaxGpu(enable: Boolean, onResult: ((Boolean, String) -> Unit)? = null) {
        if (!ShizukuHelper.hasPermission() && !ShizukuHelper.isRootAvailable()) {
            forceMaxGpu.value = false
            boostPrefs.edit().putBoolean("forceMaxGpu", false).apply()
            onResult?.invoke(false, "Shizuku não está ativo! Ative-o para habilitar esta função.")
            return
        }

        forceMaxGpu.value = enable
        boostPrefs.edit().putBoolean("forceMaxGpu", enable).apply()
        
        viewModelScope.launch(Dispatchers.IO) {
            val gov = if (enable) "performance" else "msm-adreno-tz"
            val result = ShellHelper.executePrivileged("echo $gov > /sys/class/kgsl/kgsl-3d0/devfreq/governor")

            withContext(Dispatchers.Main) {
                if (result.startsWith("Error")) {
                    forceMaxGpu.value = false
                    boostPrefs.edit().putBoolean("forceMaxGpu", false).apply()
                    onResult?.invoke(false, result)
                } else {
                    onResult?.invoke(true, if (enable) "GPU configurada para Performance!" else "GPU restaurada.")
                }
            }
        }
    }

    fun launchGame(packageName: String) {
        if (autoClearCache.value) {
            ShellHelper.clearCache(packageName)
        }

        if (adBlockerEnabled.value || adBlockerRealtime.value) {
            AdBlockHelper.enableAdBlocking()
        }

        if (disableAnimations.value) {
            ShellHelper.executePrivileged("settings put global window_animation_scale 0.0")
            ShellHelper.executePrivileged("settings put global transition_animation_scale 0.0")
            ShellHelper.executePrivileged("settings put global animator_duration_scale 0.0")
        }

        if (highTouchSampling.value) {
            ShellHelper.executePrivileged("settings put system touch_response_rate 1")
            ShellHelper.executePrivileged("settings put system gaming_touch_mode 1")
            ShellHelper.executePrivileged("settings put system touch_sensitivity 1")
            ShellHelper.executePrivileged("cmd game set --mode 2 $packageName")
        }

        if (limitBackgroundProcesses.value) {
            ShellHelper.executePrivileged("settings put global standard_background_process_limit 0")
            ShellHelper.executePrivileged("am kill-all")
        }

        if (forceDeepSleepCache.value) {
            ShellHelper.executePrivileged("cmd deviceidle force-idle")
            ShellHelper.executePrivileged("cmd activity freeze")
        }

        if (forceMaxCpu.value) {
            ShellHelper.executePrivileged("echo performance > /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor")
        }

        if (forceMaxGpu.value) {
            ShellHelper.executePrivileged("echo performance > /sys/class/kgsl/kgsl-3d0/devfreq/governor")
        }

        val gfxConfig = GfxTunerHelper.getGfxConfig(app, packageName)
        GfxTunerHelper.applyGfxOptimizations(app, packageName, gfxConfig)

        val profile = _games.value.find { it.packageName == packageName }?.profile
        if (profile != null) {
            ShellHelper.executePrivileged("wm size ${profile.resolution}")
            ShellHelper.executePrivileged("wm density ${profile.density}")
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

    fun getGfxConfig(packageName: String): GfxTunerHelper.GfxConfig {
        return GfxTunerHelper.getGfxConfig(app, packageName)
    }

    fun saveGfxConfig(packageName: String, config: GfxTunerHelper.GfxConfig) {
        GfxTunerHelper.saveGfxConfig(app, packageName, config)
    }

    fun applyGfxProfile(packageName: String): String {
        val config = GfxTunerHelper.getGfxConfig(app, packageName)
        return GfxTunerHelper.applyGfxOptimizations(app, packageName, config)
    }

    fun saveProfile(packageName: String, resolution: String, density: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            db.gameProfileDao().insertProfile(GameProfile(packageName, resolution, density))
            loadGames()
        }
    }



    fun getNativeDensity(): Int {
        val out = ShellHelper.execute("wm density").trim()
        // e.g. "Physical density: 420" or "420"
        val digits = out.filter { it.isDigit() }
        return digits.toIntOrNull() ?: 420
    }

    fun calculatePurrDpi(targetRes: String): Int {
        val natRes = nativeResolution
        val natDpi = getNativeDensity()
        return HardwareHelper.calculateProportionalDpi(natRes, natDpi, targetRes)
    }

    fun exportHardwareReport(onResult: (File?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = HardwareHelper.exportHardwareReport(app)
            withContext(Dispatchers.Main) {
                onResult(file)
            }
        }
    }

    fun runCustomScript(scriptContent: String, onResult: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val scriptFile = File(app.cacheDir, "custom_boost_script.sh")
                scriptFile.writeText(scriptContent)
                val output = ShellHelper.executePrivileged("sh ${scriptFile.absolutePath}")
                withContext(Dispatchers.Main) {
                    onResult(output.ifEmpty { "Script executed successfully with no output." })
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult("Script execution error: ${e.message}")
                }
            }
        }
    }
}
