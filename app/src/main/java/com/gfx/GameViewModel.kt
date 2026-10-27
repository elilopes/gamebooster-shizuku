package com.gfx

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
import com.gfx.data.AppDatabase
import com.gfx.data.GameProfile
import com.gfx.data.SavedGameEntity
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
    val versionName: String = "1.0.0",
    val engine: GfxTools.EngineType = GfxTools.EngineType.UNKNOWN
)

data class BackgroundApp(
    val packageName: String,
    val name: String,
    val memUsage: String,
    val isFrozen: Boolean = false,
    val isInternetBlocked: Boolean = false
)

data class AppItemInfo(
    val packageName: String,
    val name: String,
    val versionName: String,
    val isGame: Boolean,
    val isAlreadyAdded: Boolean,
    val engine: GfxTools.EngineType = GfxTools.EngineType.UNKNOWN
)

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val packageManager = application.packageManager
    private val db = androidx.room.Room.databaseBuilder(application, AppDatabase::class.java, "boost-db")
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()
    private val boostPrefs = application.getSharedPreferences("boost_settings_prefs", Context.MODE_PRIVATE)
    private val manualGamesPrefs = application.getSharedPreferences("manual_games_prefs", Context.MODE_PRIVATE)

    private val systemExcludePackages = setOf(
        "com.android.settings",
        "com.android.stk",
        "com.android.systemui",
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.google.android.googlequicksearchbox",
        application.packageName
    )
    
    val boostSettings = BoostSettings(application, viewModelScope, getBackgroundApps = { _backgroundApps.value })
    val appsBackground = AppsBackground(application, packageManager, viewModelScope, onRefreshRequested = { scanBackgroundApps() })
    val managerApps: AppsBackground get() = appsBackground
    val backupApp = BackupApp(application, viewModelScope)

    private val _games = MutableStateFlow<List<GameApp>>(emptyList())
    val games: StateFlow<List<GameApp>> = _games

    private val _backgroundApps = MutableStateFlow<List<BackgroundApp>>(emptyList())
    val backgroundApps: StateFlow<List<BackgroundApp>> = _backgroundApps

    // Boost Settings State (delegados de BoostSettings)
    val thermalBoost = boostSettings.thermalBoost
    val disableOverlays = boostSettings.disableOverlays
    val autoClearCache = boostSettings.autoClearCache
    val oomImmortal = boostSettings.oomImmortal
    val disableAnimations = boostSettings.disableAnimations
    val highTouchSampling = boostSettings.highTouchSampling
    val limitBackgroundProcesses = boostSettings.limitBackgroundProcesses
    val blockAutoRestart = boostSettings.blockAutoRestart
    val forceDeepSleepCache = boostSettings.forceDeepSleepCache
    val forceMaxCpu = boostSettings.forceMaxCpu
    val forceMaxGpu = boostSettings.forceMaxGpu
    val nativeGameMode = boostSettings.nativeGameMode
    val blockNotificationsGamer = boostSettings.blockNotificationsGamer
    val reduceLoggerBuffer = boostSettings.reduceLoggerBuffer
    val disablePrintSpooler = boostSettings.disablePrintSpooler
    val forceSystemGraphicsDriver = boostSettings.forceSystemGraphicsDriver
    val webViewGameBoost = boostSettings.webViewGameBoost
    val disable4xMsaa = boostSettings.disable4xMsaa

    val adBlockerEnabled = MutableStateFlow(false)
    val adBlockerRealtime = MutableStateFlow(false)

    // Thermal Monitor State
    val thermalMonitorEnabled = MutableStateFlow(false)
    val fpsAnalyticsEnabled = MutableStateFlow(boostPrefs.getBoolean("fpsAnalyticsEnabled", false))
    val autoMemoryRelease = MutableStateFlow(false)
    val temperature = MutableStateFlow(0f)
    val memoryUsage = MutableStateFlow(0f)
    private var thermalMonitoringJob: Job? = null

    fun toggleFpsAnalytics(enabled: Boolean) {
        fpsAnalyticsEnabled.value = enabled
        boostPrefs.edit().putBoolean("fpsAnalyticsEnabled", enabled).apply()
    }

    // Image Optimizer State
    val inspectedImage = MutableStateFlow<ImageOptimizerHelper.ImageInfo?>(null)
    val compressedImageFile = MutableStateFlow<File?>(null)

    // Custom Shell Scripts State (.sh)
    val customScripts = MutableStateFlow<List<CustomScript>>(emptyList())

    /**
     * [PT-BR] Salva e mantém permanentemente a resolução inicial padrão do dispositivo.
     */
    val defaultInitialResolution: String
        get() {
            val saved = boostPrefs.getString("default_initial_resolution", null)
            if (!saved.isNullOrBlank()) return saved

            val res = try {
                val nat = ShellFunctions.getNativeResolution()
                if (nat.isNotBlank() && nat != "SHIZUKU_OFF" && !nat.contains("Shizuku off") && !nat.contains("Error")) {
                    nat
                } else {
                    val dm = app.resources.displayMetrics
                    "${dm.widthPixels}x${dm.heightPixels}"
                }
            } catch (_: Exception) {
                val dm = app.resources.displayMetrics
                "${dm.widthPixels}x${dm.heightPixels}"
            }
            boostPrefs.edit().putString("default_initial_resolution", res).apply()
            return res
        }

    val nativeResolution: String
        get() {
            val res = ShellFunctions.getNativeResolution()
            return if (res == "SHIZUKU_OFF" || res.contains("Shizuku off")) {
                app.getString(R.string.shizuku_off_warning)
            } else {
                res
            }
        }

    fun isResolutionOnlyForGame(packageName: String): Boolean {
        return boostPrefs.getBoolean("res_only_game_$packageName", false)
    }

    fun setResolutionOnlyForGame(packageName: String, onlyGame: Boolean) {
        boostPrefs.edit().putBoolean("res_only_game_$packageName", onlyGame).apply()
    }

    fun restoreDefaultResolution(onResult: ((Boolean, String) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val defRes = defaultInitialResolution
            ShellFunctions.executePrivileged("wm size reset")
            ShellFunctions.executePrivileged("wm density reset")
            withContext(Dispatchers.Main) {
                onResult?.invoke(true, "Resolução padrão inicial ($defRes) restaurada com sucesso!")
            }
        }
    }

    init {
        // Assegura que a resolução padrão inicial seja registrada na primeira inicialização
        val _initial = defaultInitialResolution
        loadGames()
        scanBackgroundApps()
        loadCustomScripts()
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
                temperature.value = ShellFunctions.getTemperature()
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
        val hasPriv = ShizukuHelper.hasPermission() || ShizukuHelper.isRootAvailable()
        if (!hasPriv) return
        try {
            ShellFunctions.executePrivileged("am kill-all")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isGameApp(appInfo: ApplicationInfo, pkgName: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (appInfo.category == ApplicationInfo.CATEGORY_GAME) return true
        }
        @Suppress("DEPRECATION")
        if ((appInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0) return true

        if (GfxTools.detectEngine(app, pkgName) != GfxTools.EngineType.GENERIC) return true

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
                val engine = GfxTools.detectEngine(app, pkg)
                AppItemInfo(pkg, name, vName, isGame, isAdded, engine)
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

    /**
     * [PT-BR] Carrega na inicialização exclusivamente os jogos salvos pelo usuário no banco de dados Room.
     * Não realiza busca automática em todos os aplicativos do celular ao abrir o app.
     *
     * [EN] Loads on startup exclusively the games saved by the user in the Room database.
     * Does not perform an automatic scan of all phone applications when launching the app.
     */
    fun loadSavedGamesOnStartup() {
        viewModelScope.launch(Dispatchers.IO) {
            val profiles = db.gameProfileDao().getAllProfiles()
            val savedEntities = db.savedGameDao().getAllSavedGames()

            // Migração de segurança de preferências legadas para o Room
            val legacyManual = manualGamesPrefs.getStringSet("saved_manual_packages", emptySet()) ?: emptySet()
            if (legacyManual.isNotEmpty()) {
                val toMigrate = mutableListOf<SavedGameEntity>()
                for (pkg in legacyManual) {
                    if (savedEntities.none { it.packageName == pkg }) {
                        val name = try {
                            val appInfo = packageManager.getApplicationInfo(pkg, 0)
                            appInfo.loadLabel(packageManager).toString()
                        } catch (e: Exception) {
                            pkg
                        }
                        toMigrate.add(SavedGameEntity(pkg, name))
                    }
                }
                if (toMigrate.isNotEmpty()) {
                    db.savedGameDao().insertAll(toMigrate)
                }
            }

            val currentSaved = db.savedGameDao().getAllSavedGames()
            val list = mutableListOf<GameApp>()

            for (saved in currentSaved) {
                val pkg = saved.packageName
                val name = saved.name.ifEmpty {
                    try {
                        val appInfo = packageManager.getApplicationInfo(pkg, 0)
                        appInfo.loadLabel(packageManager).toString()
                    } catch (e: Exception) {
                        pkg
                    }
                }
                val profile = profiles.find { it.packageName == pkg }
                val vName = try {
                    packageManager.getPackageInfo(pkg, 0).versionName ?: "1.0.0"
                } catch (e: Exception) {
                    "1.0.0"
                }
                val engine = GfxTools.detectEngine(app, pkg)
                list.add(GameApp(pkg, name, profile, vName, engine))
            }

            // Garante que jogos que tenham perfis de resolução salvos também constem na lista
            for (p in profiles) {
                if (list.none { it.packageName == p.packageName }) {
                    val pkg = p.packageName
                    val name = try {
                        val appInfo = packageManager.getApplicationInfo(pkg, 0)
                        appInfo.loadLabel(packageManager).toString()
                    } catch (e: Exception) {
                        pkg
                    }
                    val vName = try {
                        packageManager.getPackageInfo(pkg, 0).versionName ?: "1.0.0"
                    } catch (e: Exception) {
                        "1.0.0"
                    }
                    val engine = GfxTools.detectEngine(app, pkg)
                    list.add(GameApp(pkg, name, p, vName, engine))
                    db.savedGameDao().insertSavedGame(SavedGameEntity(pkg, name))
                }
            }

            _games.value = list.distinctBy { it.packageName }
        }
    }

    fun loadGames() {
        loadSavedGamesOnStartup()
    }

    /**
     * [PT-BR] Adiciona manualmente um jogo, salvando-o de forma permanente no banco de dados Room.
     * Os jogos salvos são recarregados e exibidos na aba Games em todas as inicializações.
     *
     * [EN] Manually adds a game, permanently persisting it to the Room database.
     * Saved games are reloaded and displayed in the Games tab across all app launches.
     */
    fun addManualGame(packageName: String, name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            // Salva na base de dados Room
            val entity = SavedGameEntity(packageName, name)
            db.savedGameDao().insertSavedGame(entity)

            // Sincroniza em SharedPreferences para contingência
            val savedManuals = manualGamesPrefs.getStringSet("saved_manual_packages", emptySet())?.toMutableSet() ?: mutableSetOf()
            savedManuals.add(packageName)
            manualGamesPrefs.edit().putStringSet("saved_manual_packages", savedManuals).apply()

            val profile = db.gameProfileDao().getProfile(packageName)
            val versionName = try {
                packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0.0"
            } catch (e: Exception) {
                "1.0.0"
            }
            val engine = GfxTools.detectEngine(app, packageName)
            val newGame = GameApp(packageName, name, profile, versionName, engine)
            if (_games.value.none { it.packageName == packageName }) {
                _games.value = _games.value + newGame
            }
        }
    }

    /**
     * [PT-BR] Remove o jogo da lista e da base de dados Room.
     * [EN] Removes the game from the list and from the Room database.
     */
    fun removeGame(packageName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.savedGameDao().deleteSavedGame(packageName)
            val savedManuals = manualGamesPrefs.getStringSet("saved_manual_packages", emptySet())?.toMutableSet() ?: mutableSetOf()
            if (savedManuals.contains(packageName)) {
                savedManuals.remove(packageName)
                manualGamesPrefs.edit().putStringSet("saved_manual_packages", savedManuals).apply()
            }
            _games.value = _games.value.filter { it.packageName != packageName }
        }
    }

    /**
     * [PT-BR] Busca automática sob demanda: acionada apenas quando o usuário clica no botão "Busca automática".
     * Varre os jogos instalados, adiciona todos automaticamente e persiste no banco de dados Room.
     *
     * [EN] On-demand auto scan: triggered only when the user taps the "Auto scan" button.
     * Scans installed games, automatically adds them all, and persists them to the Room database.
     */
    fun autoScanAndAddGames(onResult: ((Int) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val profiles = db.gameProfileDao().getAllProfiles()
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
            val resolveInfos = packageManager.queryIntentActivities(mainIntent, 0)
            val allApps = try { packageManager.getInstalledApplications(0) } catch (e: Exception) { emptyList() }
            val appInfoMap = allApps.associateBy { it.packageName }

            val list = mutableListOf<GameApp>()
            val entitiesToSave = mutableListOf<SavedGameEntity>()

            for (resolveInfo in resolveInfos) {
                val pkg = resolveInfo.activityInfo.packageName
                if (pkg == app.packageName) continue

                val appInfo = appInfoMap[pkg] ?: resolveInfo.activityInfo.applicationInfo
                val isGame = isGameApp(appInfo, pkg)

                if (systemExcludePackages.contains(pkg) && !isGame) continue

                if (isGame) {
                    val name = resolveInfo.loadLabel(packageManager).toString()
                    val profile = profiles.find { it.packageName == pkg }
                    val vName = try { packageManager.getPackageInfo(pkg, 0).versionName ?: "1.0.0" } catch (e: Exception) { "1.0.0" }
                    val engine = GfxTools.detectEngine(app, pkg)
                    list.add(GameApp(pkg, name, profile, vName, engine))
                    entitiesToSave.add(SavedGameEntity(pkg, name))
                }
            }

            if (list.isEmpty()) {
                for (appInfo in allApps) {
                    val pkg = appInfo.packageName
                    if (pkg == app.packageName || systemExcludePackages.contains(pkg)) continue
                    val isUserApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0
                    if (isUserApp) {
                        val name = appInfo.loadLabel(packageManager).toString()
                        val profile = profiles.find { it.packageName == pkg }
                        val vName = try { packageManager.getPackageInfo(pkg, 0).versionName ?: "1.0.0" } catch (e: Exception) { "1.0.0" }
                        val engine = GfxTools.detectEngine(app, pkg)
                        list.add(GameApp(pkg, name, profile, vName, engine))
                        entitiesToSave.add(SavedGameEntity(pkg, name))
                    }
                }
            }

            // Persiste todos os jogos detectados no banco de dados Room
            if (entitiesToSave.isNotEmpty()) {
                db.savedGameDao().insertAll(entitiesToSave)
            }

            val merged = (_games.value + list).distinctBy { it.packageName }
            _games.value = merged

            withContext(Dispatchers.Main) {
                onResult?.invoke(merged.size)
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

    val updateCheckResults = MutableStateFlow<Map<String, WebUpdateInfo>>(emptyMap())
    val isCheckingAllUpdates = MutableStateFlow(false)

    fun checkUpdateForGame(game: GameApp) {
        viewModelScope.launch(Dispatchers.IO) {
            updateCheckResults.value = updateCheckResults.value + (game.packageName to WebUpdateInfo(
                packageName = game.packageName,
                installedVersion = game.versionName,
                status = UpdateBadgeStatus.CHECKING
            ))
            val result = WebUpdateChecker.checkUpdate(game.packageName, game.versionName)
            updateCheckResults.value = updateCheckResults.value + (game.packageName to result)
        }
    }

    fun checkAllGameUpdates() {
        viewModelScope.launch(Dispatchers.IO) {
            isCheckingAllUpdates.value = true
            val currentGames = _games.value
            val initial = updateCheckResults.value.toMutableMap()
            for (game in currentGames) {
                initial[game.packageName] = WebUpdateInfo(
                    packageName = game.packageName,
                    installedVersion = game.versionName,
                    status = UpdateBadgeStatus.CHECKING
                )
            }
            updateCheckResults.value = initial

            for (game in currentGames) {
                val res = WebUpdateChecker.checkUpdate(game.packageName, game.versionName)
                updateCheckResults.value = updateCheckResults.value + (game.packageName to res)
            }
            isCheckingAllUpdates.value = false
        }
    }

    fun openUpdateUrl(url: String) {
        WebUpdateChecker.openUrl(app, url)
    }

    fun searchGameOnWeb(packageName: String) {
        val searchUrl = "https://www.apkmirror.com/?post_type=app_release&searchtype=apk&s=" + java.net.URLEncoder.encode(packageName, "UTF-8")
        WebUpdateChecker.openUrl(app, searchUrl)
    }

    fun backupApp(packageName: String, includeApk: Boolean, includeObb: Boolean, includeData: Boolean, compressZip: Boolean, onResult: (Boolean, String) -> Unit) {
        backupApp.backupApp(packageName, includeApk, includeObb, includeData, compressZip, onResult)
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
        backupApp.moveObbToSd(packageName, onResult)
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
        viewModelScope.launch {
            val list = appsBackground.checkBackgroundApps(systemExcludePackages)
            withContext(Dispatchers.Main) {
                _backgroundApps.value = list
            }
        }
    }

    // Funções de gerenciamento de aplicativos em segundo plano (AppsBackground)
    fun killApp(packageName: String, onResult: ((Boolean, String) -> Unit)? = null): Boolean {
        return appsBackground.killApp(packageName, onResult)
    }

    fun killAllApps(onResult: ((Int, String) -> Unit)? = null) {
        appsBackground.killAllApps(_backgroundApps.value, onResult)
    }

    fun toggleInternet(packageName: String, block: Boolean, onResult: ((Boolean, String) -> Unit)? = null) {
        appsBackground.toggleInternet(packageName, block, onResult)
    }

    fun freezeApp(packageName: String, onResult: ((Boolean, String) -> Unit)? = null): Boolean {
        return appsBackground.freezeApp(packageName, onResult)
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

            val cmdSize = ShellFunctions.executePrivileged("wm size $trimmed")
            var cmdDensity = ""
            if (density != null && density > 0) {
                cmdDensity = ShellFunctions.executePrivileged("wm density $density")
            }

            val checkSize = ShellFunctions.executePrivileged("wm size")
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
            ShellFunctions.executePrivileged("wm size reset")
            ShellFunctions.executePrivileged("wm density reset")
            val currentRes = ShellFunctions.getNativeResolution()
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
            val cmdSize = ShellFunctions.executePrivileged("wm size $trimmed")
            if (density > 0) {
                ShellFunctions.executePrivileged("wm density $density")
            }
            val checkSize = ShellFunctions.executePrivileged("wm size")
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

    // Funções da aba Boost Settings delegadas para o novo arquivo BoostSettings
    fun toggleThermalBoost(enable: Boolean) = boostSettings.toggleThermalBoost(enable)

    fun toggleDisableOverlays(enable: Boolean, onResult: ((Boolean, String) -> Unit)? = null) =
        boostSettings.toggleDisableOverlays(enable, onResult)

    fun toggleAutoClearCache(enable: Boolean, onResult: ((Boolean, String) -> Unit)? = null) =
        boostSettings.toggleAutoClearCache(enable, onResult)

    fun toggleOomImmortal(enable: Boolean, onResult: ((Boolean, String) -> Unit)? = null) =
        boostSettings.toggleOomImmortal(enable, onResult)

    fun toggleDisableAnimations(enable: Boolean, onResult: ((Boolean, String) -> Unit)? = null) =
        boostSettings.toggleDisableAnimations(enable, onResult)

    fun toggleHighTouchSampling(enable: Boolean, onResult: ((Boolean, String) -> Unit)? = null) =
        boostSettings.toggleHighTouchSampling(enable, onResult)

    fun toggleLimitBackgroundProcesses(enable: Boolean, onResult: ((Boolean, String) -> Unit)? = null) =
        boostSettings.toggleLimitBackgroundProcesses(enable, onResult)

    fun toggleBlockAutoRestart(enable: Boolean, onResult: ((Boolean, String) -> Unit)? = null) =
        boostSettings.toggleBlockAutoRestart(enable, onResult)

    fun toggleForceDeepSleepCache(enable: Boolean, onResult: ((Boolean, String) -> Unit)? = null) =
        boostSettings.toggleForceDeepSleepCache(enable, onResult)

    fun executeDeepSleepNow(onResult: (Boolean, String) -> Unit) =
        boostSettings.executeDeepSleepNow(onResult)

    fun toggleForceMaxCpu(enable: Boolean, onResult: ((Boolean, String) -> Unit)? = null) =
        boostSettings.toggleForceMaxCpu(enable, onResult)

    fun toggleForceMaxGpu(enable: Boolean, onResult: ((Boolean, String) -> Unit)? = null) =
        boostSettings.toggleForceMaxGpu(enable, onResult)

    fun toggleNativeGameMode(enable: Boolean, onResult: ((Boolean, String) -> Unit)? = null) =
        boostSettings.toggleNativeGameMode(enable, onResult)

    fun toggleBlockNotificationsGamer(enable: Boolean, onResult: ((Boolean, String) -> Unit)? = null) =
        boostSettings.toggleBlockNotificationsGamer(enable, onResult)

    fun toggleReduceLoggerBuffer(enable: Boolean, onResult: ((Boolean, String) -> Unit)? = null) =
        boostSettings.toggleReduceLoggerBuffer(enable, onResult)

    fun toggleDisablePrintSpooler(enable: Boolean, onResult: ((Boolean, String) -> Unit)? = null) =
        boostSettings.toggleDisablePrintSpooler(enable, onResult)

    fun toggleForceSystemGraphicsDriver(enable: Boolean, onResult: ((Boolean, String) -> Unit)? = null) =
        boostSettings.toggleForceSystemGraphicsDriver(enable, onResult)

    fun toggleWebViewGameBoost(enable: Boolean, onResult: ((Boolean, String) -> Unit)? = null) =
        boostSettings.toggleWebViewGameBoost(enable, onResult)

    fun toggleDisable4xMsaa(enable: Boolean, onResult: ((Boolean, String) -> Unit)? = null) =
        boostSettings.toggleDisable4xMsaa(enable, onResult)

    fun enableAllBoosts(onComplete: () -> Unit) =
        boostSettings.enableAllBoosts(onComplete)

    fun launchGame(packageName: String) {
        if (autoClearCache.value) {
            ShellFunctions.clearCache(packageName)
        }

        if (adBlockerEnabled.value || adBlockerRealtime.value) {
            AdBlockHelper.enableAdBlocking()
        }

        if (webViewGameBoost.value || GfxTools.detectEngine(app, packageName) == GfxTools.EngineType.WEBVIEW) {
            val webViewFlags = "_ --enable-gpu-rasterization --enable-zero-copy --ignore-gpu-blocklist --enable-accelerated-2d-canvas --force-gpu-mem-available-mb=512 --disable-gpu-vsync --disable-background-timer-throttling"
            ShellFunctions.executePrivileged("mkdir -p /data/local/tmp")
            ShellFunctions.executePrivileged("echo \"$webViewFlags\" > /data/local/tmp/webview-command-line")
            ShellFunctions.executePrivileged("chmod 755 /data/local/tmp/webview-command-line")
            ShellFunctions.executePrivileged("setprop debug.hwui.renderer skiagl")
            ShellFunctions.executePrivileged("setprop debug.hwui.render_ahead 1")

            viewModelScope.launch(Dispatchers.IO) {
                delay(2000)
                ShellFunctions.executePrivileged("for p in \$(pgrep -f 'sandboxed_process'); do renice -n -20 -p \$p 2>/dev/null; echo -1000 > /proc/\$p/oom_score_adj 2>/dev/null; done")
            }
        }

        if (nativeGameMode.value) {
            ShellFunctions.executePrivileged("cmd game set --mode 2 $packageName")
            ShellFunctions.executePrivileged("settings put global low_power 0")
            ShellFunctions.executePrivileged("setprop debug.game.performance_mode 1")
        }

        if (blockNotificationsGamer.value) {
            ShellFunctions.executePrivileged("settings put global heads_up_notifications_enabled 0")
        }

        if (reduceLoggerBuffer.value) {
            ShellFunctions.executePrivileged("logcat -G 64K")
            ShellFunctions.executePrivileged("setprop persist.logd.size 64K")
        }

        if (disablePrintSpooler.value) {
            ShellFunctions.executePrivileged("pm disable-user com.android.printspooler")
        }

        if (forceSystemGraphicsDriver.value) {
            ShellFunctions.executePrivileged("settings put global updatable_driver_all_apps 1")
            ShellFunctions.executePrivileged("settings put global updatable_driver_production_opt_in_apps $packageName")
            ShellFunctions.executePrivileged("setprop debug.graphics.game_default_driver 1")
        }

        if (disableAnimations.value) {
            ShellFunctions.executePrivileged("settings put global window_animation_scale 0.0")
            ShellFunctions.executePrivileged("settings put global transition_animation_scale 0.0")
            ShellFunctions.executePrivileged("settings put global animator_duration_scale 0.0")
        }

        if (highTouchSampling.value) {
            ShellFunctions.executePrivileged("settings put system touch_response_rate 1")
            ShellFunctions.executePrivileged("settings put system gaming_touch_mode 1")
            ShellFunctions.executePrivileged("settings put system touch_sensitivity 1")
            ShellFunctions.executePrivileged("cmd game set --mode 2 $packageName")
        }

        if (limitBackgroundProcesses.value) {
            ShellFunctions.executePrivileged("settings put global standard_background_process_limit 0")
            ShellFunctions.executePrivileged("am kill-all")
        }

        if (forceDeepSleepCache.value) {
            ShellFunctions.executePrivileged("cmd deviceidle force-idle")
            ShellFunctions.executePrivileged("cmd activity freeze")
        }

        if (forceMaxCpu.value) {
            ShellFunctions.executePrivileged("echo performance > /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor")
        }

        if (forceMaxGpu.value) {
            ShellFunctions.executePrivileged("echo performance > /sys/class/kgsl/kgsl-3d0/devfreq/governor")
        }

        val gfxConfig = GfxTools.getGfxConfig(app, packageName)
        GfxTools.applyGfxOptimizations(app, packageName, gfxConfig)

        val profile = _games.value.find { it.packageName == packageName }?.profile
        if (profile != null) {
            ShellFunctions.executePrivileged("wm size ${profile.resolution}")
            ShellFunctions.executePrivileged("wm density ${profile.density}")
        }
        
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            GameForegroundService.activeGamePackage = packageName
            app.startActivity(intent)
            
            if (oomImmortal.value) {
                viewModelScope.launch {
                    delay(2000)
                    ShellFunctions.setOomImmortal(packageName)
                }
            }
        }
    }

    fun getGfxConfig(packageName: String): GfxTools.GfxConfig {
        return GfxTools.getGfxConfig(app, packageName)
    }

    fun saveGfxConfig(packageName: String, config: GfxTools.GfxConfig) {
        GfxTools.saveGfxConfig(app, packageName, config)
    }

    fun applyGfxProfile(packageName: String): String {
        val config = GfxTools.getGfxConfig(app, packageName)
        return GfxTools.applyGfxOptimizations(app, packageName, config)
    }

    fun saveProfile(packageName: String, resolution: String, density: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            db.gameProfileDao().insertProfile(GameProfile(packageName, resolution, density))
            loadGames()
        }
    }

    fun exportGameConfig(packageName: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val game = _games.value.find { it.packageName == packageName }
                val gfxConfig = GfxTools.getGfxConfig(app, packageName)
                val config = mapOf(
                    "packageName" to packageName,
                    "gameName" to (game?.name ?: packageName),
                    "resolution" to (game?.profile?.resolution ?: "1280x720"),
                    "density" to (game?.profile?.density ?: 320),
                    "gfx" to gfxConfig
                )
                val json = com.google.gson.Gson().toJson(config)
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(downloadsDir, "${packageName}_config.json")
                file.writeText(json)
                withContext(Dispatchers.Main) { onResult(true, app.getString(R.string.game_action_export_success, file.name)) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult(false, app.getString(R.string.game_action_import_error, e.message ?: "")) }
            }
        }
    }

    fun importGameConfig(uri: Uri, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = app.contentResolver.openInputStream(uri)
                val json = inputStream?.bufferedReader().use { it?.readText() }
                if (json == null) throw Exception("Empty file")
                val config = com.google.gson.Gson().fromJson(json, Map::class.java)
                val pkg = config["packageName"] as String
                val res = config["resolution"] as String
                val dpi = (config["density"] as Double).toInt()
                val gfx = com.google.gson.Gson().fromJson(com.google.gson.Gson().toJson(config["gfx"]), GfxTools.GfxConfig::class.java)

                db.gameProfileDao().insertProfile(GameProfile(pkg, res, dpi))
                GfxTools.saveGfxConfig(app, pkg, gfx)
                loadGames()
                withContext(Dispatchers.Main) { onResult(true, app.getString(R.string.game_action_import_success)) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult(false, app.getString(R.string.game_action_import_error, e.message ?: "")) }
            }
        }
    }



    fun getNativeDensity(): Int {
        if (!ShizukuHelper.hasPermission() && !ShizukuHelper.isRootAvailable()) {
            return 420
        }
        val out = ShellFunctions.executePrivileged("wm density").trim()
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
                scriptFile.setReadable(true, false)
                scriptFile.setExecutable(true, false)
                val output = ShellFunctions.executePrivileged("sh ${scriptFile.absolutePath}")
                withContext(Dispatchers.Main) {
                    onResult(output.ifEmpty { "Script executado com sucesso." })
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult("Erro na execução do script: ${e.message}")
                }
            }
        }
    }

    fun loadCustomScripts() {
        viewModelScope.launch(Dispatchers.IO) {
            val loaded = CustomScriptManager.loadScripts(app)
            customScripts.value = loaded

            // Executa rotinas marcadas para rodar na inicialização caso haja
            val autoRun = loaded.filter { it.runOnAppStart }
            for (s in autoRun) {
                CustomScriptManager.executeScript(app, s)
            }
        }
    }

    fun saveCustomScript(script: CustomScript) {
        viewModelScope.launch(Dispatchers.IO) {
            customScripts.value = CustomScriptManager.saveScript(app, script)
        }
    }

    fun deleteCustomScript(scriptId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            customScripts.value = CustomScriptManager.deleteScript(app, scriptId)
        }
    }

    fun resetCustomScriptsToPresets() {
        viewModelScope.launch(Dispatchers.IO) {
            customScripts.value = CustomScriptManager.resetToDefaults(app)
        }
    }

    fun executeCustomScript(script: CustomScript, onResult: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val output = CustomScriptManager.executeScript(app, script)
            withContext(Dispatchers.Main) {
                onResult(output)
            }
        }
    }
}
