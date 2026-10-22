package com.example

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Gerenciador e detentor de estado das configurações da aba Boost Settings.
 */
class BoostSettings(
    private val context: Context,
    private val scope: CoroutineScope,
    private val getBackgroundApps: () -> List<BackgroundApp> = { emptyList() }
) {
    private val boostPrefs: SharedPreferences =
        context.getSharedPreferences("boost_settings_prefs", Context.MODE_PRIVATE)

    // Estados observáveis da aba Boost Settings
    val thermalBoost = MutableStateFlow(false)
    val disableOverlays = MutableStateFlow(false)
    val autoClearCache = MutableStateFlow(false)
    val oomImmortal = MutableStateFlow(false)
    val disableAnimations = MutableStateFlow(false)
    val highTouchSampling = MutableStateFlow(false)
    val limitBackgroundProcesses = MutableStateFlow(false)
    val blockAutoRestart = MutableStateFlow(false)
    val forceDeepSleepCache = MutableStateFlow(false)
    val forceMaxCpu = MutableStateFlow(false)
    val forceMaxGpu = MutableStateFlow(false)

    init {
        loadBoostSettings()
    }

    fun loadBoostSettings() {
        thermalBoost.value = boostPrefs.getBoolean("thermalBoost", false)
        disableOverlays.value = boostPrefs.getBoolean("disableOverlays", false)
        autoClearCache.value = boostPrefs.getBoolean("autoClearCache", false)
        oomImmortal.value = boostPrefs.getBoolean("oomImmortal", false)
        disableAnimations.value = boostPrefs.getBoolean("disableAnimations", false)
        highTouchSampling.value = boostPrefs.getBoolean("highTouchSampling", false)
        limitBackgroundProcesses.value = boostPrefs.getBoolean("limitBackgroundProcesses", false)
        blockAutoRestart.value = boostPrefs.getBoolean("blockAutoRestart", false)
        forceDeepSleepCache.value = boostPrefs.getBoolean("forceDeepSleepCache", false)
        forceMaxCpu.value = boostPrefs.getBoolean("forceMaxCpu", false)
        forceMaxGpu.value = boostPrefs.getBoolean("forceMaxGpu", false)
    }

    private fun hasPrivilege(): Boolean {
        val shizukuActive = ShizukuHelper.isBinderAlive.value && ShizukuHelper.hasPermission()
        val rootActive = ShizukuHelper.isRootAvailable()
        
        android.util.Log.d("BoostSettings", "Verificação de privilégios - Shizuku: $shizukuActive, Root: $rootActive")
        
        return shizukuActive || rootActive
    }

    fun toggleThermalBoost(enable: Boolean) {
        thermalBoost.value = enable
        boostPrefs.edit().putBoolean("thermalBoost", enable).apply()
        scope.launch(Dispatchers.IO) {
            if (enable) {
                ShellFunctions.executePrivileged("setprop debug.thermal.throttle.support 0")
                ShellFunctions.executePrivileged("cmd thermalservice override-status 0")
                ShellFunctions.executePrivileged("stop thermal-engine")
                ShellFunctions.executePrivileged("stop thermald")
            } else {
                ShellFunctions.executePrivileged("setprop debug.thermal.throttle.support 1")
                ShellFunctions.executePrivileged("cmd thermalservice reset")
                ShellFunctions.executePrivileged("start thermal-engine")
                ShellFunctions.executePrivileged("start thermald")
            }
        }
    }

    fun toggleDisableOverlays(enable: Boolean, onResult: ((Boolean, String) -> Unit)? = null) {
        if (!hasPrivilege()) {
            disableOverlays.value = false
            boostPrefs.edit().putBoolean("disableOverlays", false).apply()
            onResult?.invoke(false, "Shizuku off, ative-o pra usar estas funções.")
            return
        }

        disableOverlays.value = enable
        boostPrefs.edit().putBoolean("disableOverlays", enable).apply()

        scope.launch(Dispatchers.IO) {
            val code = if (enable) 1 else 0
            val result = ShellFunctions.executePrivileged("service call SurfaceFlinger 1008 i32 $code")

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
        if (!hasPrivilege()) {
            autoClearCache.value = false
            boostPrefs.edit().putBoolean("autoClearCache", false).apply()
            onResult?.invoke(false, "Shizuku off, ative-o pra usar estas funções.")
            return
        }

        autoClearCache.value = enable
        boostPrefs.edit().putBoolean("autoClearCache", enable).apply()
        onResult?.invoke(true, if (enable) "Limpeza automática de cache ativada." else "Limpeza automática desativada.")
    }

    fun toggleOomImmortal(enable: Boolean, onResult: ((Boolean, String) -> Unit)? = null) {
        if (!hasPrivilege()) {
            oomImmortal.value = false
            boostPrefs.edit().putBoolean("oomImmortal", false).apply()
            onResult?.invoke(false, "Shizuku off, ative-o pra usar estas funções.")
            return
        }

        oomImmortal.value = enable
        boostPrefs.edit().putBoolean("oomImmortal", enable).apply()
        onResult?.invoke(true, if (enable) "Modo OOM Immortal ativado." else "Modo OOM Immortal desativado.")
    }

    fun toggleDisableAnimations(enable: Boolean, onResult: ((Boolean, String) -> Unit)? = null) {
        if (!hasPrivilege()) {
            disableAnimations.value = false
            boostPrefs.edit().putBoolean("disableAnimations", false).apply()
            onResult?.invoke(false, "Shizuku off, ative-o pra usar estas funções.")
            return
        }

        disableAnimations.value = enable
        boostPrefs.edit().putBoolean("disableAnimations", enable).apply()

        scope.launch(Dispatchers.IO) {
            val scale = if (enable) "0.0" else "1.0"
            val r1 = ShellFunctions.executePrivileged("settings put global window_animation_scale $scale")
            val r2 = ShellFunctions.executePrivileged("settings put global transition_animation_scale $scale")
            val r3 = ShellFunctions.executePrivileged("settings put global animator_duration_scale $scale")

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
        if (!hasPrivilege()) {
            highTouchSampling.value = false
            boostPrefs.edit().putBoolean("highTouchSampling", false).apply()
            onResult?.invoke(false, "Shizuku off, ative-o pra usar estas funções.")
            return
        }

        highTouchSampling.value = enable
        boostPrefs.edit().putBoolean("highTouchSampling", enable).apply()

        scope.launch(Dispatchers.IO) {
            val v = if (enable) "1" else "0"
            val r1 = ShellFunctions.executePrivileged("settings put system touch_response_rate $v")
            val r2 = ShellFunctions.executePrivileged("settings put system gaming_touch_mode $v")
            val r3 = ShellFunctions.executePrivileged("settings put system touch_sensitivity $v")
            val r4 = ShellFunctions.executePrivileged("settings put global game_touch_enhancement $v")

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
        if (!hasPrivilege()) {
            limitBackgroundProcesses.value = false
            boostPrefs.edit().putBoolean("limitBackgroundProcesses", false).apply()
            onResult?.invoke(false, "Shizuku off, ative-o pra usar estas funções.")
            return
        }

        limitBackgroundProcesses.value = enable
        boostPrefs.edit().putBoolean("limitBackgroundProcesses", enable).apply()

        scope.launch(Dispatchers.IO) {
            val r1: String
            val r2: String
            if (enable) {
                r1 = ShellFunctions.executePrivileged("settings put global standard_background_process_limit 0")
                r2 = ShellFunctions.executePrivileged("am kill-all")
            } else {
                r1 = ShellFunctions.executePrivileged("settings put global standard_background_process_limit -1")
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
        if (!hasPrivilege()) {
            blockAutoRestart.value = false
            boostPrefs.edit().putBoolean("blockAutoRestart", false).apply()
            onResult?.invoke(false, "Shizuku off, ative-o pra usar estas funções.")
            return
        }

        blockAutoRestart.value = enable
        boostPrefs.edit().putBoolean("blockAutoRestart", enable).apply()

        scope.launch(Dispatchers.IO) {
            val op = if (enable) "ignore" else "allow"
            var hasError = false

            getBackgroundApps().forEach { app ->
                val res1 = ShellFunctions.executePrivileged("cmd appops set ${app.packageName} RUN_IN_BACKGROUND $op")
                val res2 = ShellFunctions.executePrivileged("cmd appops set ${app.packageName} RUN_ANY_IN_BACKGROUND $op")
                if (res1.startsWith("Error") || res2.startsWith("Error")) {
                    hasError = true
                }
            }

            val syncRes = if (enable) {
                ShellFunctions.executePrivileged("settings put global auto_sync 0")
            } else {
                ShellFunctions.executePrivileged("settings put global auto_sync 1")
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
        if (!hasPrivilege()) {
            forceDeepSleepCache.value = false
            boostPrefs.edit().putBoolean("forceDeepSleepCache", false).apply()
            onResult?.invoke(false, "Shizuku off, ative-o pra usar estas funções.")
            return
        }

        forceDeepSleepCache.value = enable
        boostPrefs.edit().putBoolean("forceDeepSleepCache", enable).apply()

        if (enable) {
            executeDeepSleepNow { success, msg ->
                onResult?.invoke(success, msg)
            }
        } else {
            onResult?.invoke(true, "Modo Deep Sleep desativado.")
        }
    }

    fun executeDeepSleepNow(onResult: (Boolean, String) -> Unit) {
        if (!hasPrivilege()) {
            onResult(false, "Shizuku off, ative-o pra usar estas funções.")
            return
        }

        scope.launch(Dispatchers.IO) {
            try {
                val r1 = ShellFunctions.executePrivileged("cmd deviceidle force-idle")
                val r2 = ShellFunctions.executePrivileged("cmd activity freeze")
                
                var hasError = r1.startsWith("Error") || r2.startsWith("Error")

                getBackgroundApps().forEach { app ->
                    val sa1 = ShellFunctions.executePrivileged("am set-standby-bucket ${app.packageName} restricted")
                    val sa2 = ShellFunctions.executePrivileged("am set-inactive ${app.packageName} true")
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
        if (!hasPrivilege()) {
            forceMaxCpu.value = false
            boostPrefs.edit().putBoolean("forceMaxCpu", false).apply()
            onResult?.invoke(false, "Shizuku off, ative-o pra usar estas funções.")
            return
        }

        forceMaxCpu.value = enable
        boostPrefs.edit().putBoolean("forceMaxCpu", enable).apply()
        scope.launch(Dispatchers.IO) {
            val gov = if (enable) "performance" else "schedutil"
            val result = ShellFunctions.executePrivileged("echo $gov > /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor")
            
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
        if (!hasPrivilege()) {
            forceMaxGpu.value = false
            boostPrefs.edit().putBoolean("forceMaxGpu", false).apply()
            onResult?.invoke(false, "Shizuku off, ative-o pra usar estas funções.")
            return
        }

        forceMaxGpu.value = enable
        boostPrefs.edit().putBoolean("forceMaxGpu", enable).apply()
        
        scope.launch(Dispatchers.IO) {
            val gov = if (enable) "performance" else "msm-adreno-tz"
            val result = ShellFunctions.executePrivileged("echo $gov > /sys/class/kgsl/kgsl-3d0/devfreq/governor")

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
}
