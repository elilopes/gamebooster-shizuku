package com.gfx

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * [PT-BR] Gerenciador e detentor de estado das configurações da aba Boost Settings.
 * Aplica otimizações de sistema com verificação prévia obrigatória de privilégios do Shizuku ou Root.
 *
 * [EN] State holder and manager for the Boost Settings tab.
 * Applies system optimizations with mandatory prior verification of Shizuku or Root privileges.
 *
 * @param context Contexto do Android para acesso ao SharedPreferences.
 * @param scope CoroutineScope para execução de comandos assíncronos.
 * @param getBackgroundApps Função lambda para obter a lista atual de aplicativos em segundo plano.
 */
class BoostSettings(
    private val context: Context,
    private val scope: CoroutineScope,
    private val getBackgroundApps: () -> List<BackgroundApp> = { emptyList() }
) {
    private val boostPrefs: SharedPreferences =
        context.getSharedPreferences("boost_settings_prefs", Context.MODE_PRIVATE)

    /**
     * [PT-BR] Emite uma mensagem de erro/alerta para o Logcat e envia para a interface do aplicativo via ShizukuHelper.
     * [EN] Dispatches an error/alert message to Logcat and sends it to the application UI via ShizukuHelper.
     *
     * @param errorMsg Mensagem de erro a ser registrada e exibida.
     * @param cause Exceção opcional associada ao erro.
     */
    private fun dispatchAlert(errorMsg: String, cause: Throwable? = null) {
        if (cause != null) {
            Log.e("BoostSettings", errorMsg, cause)
            CrashReporterHelper.recordException("BoostSettings", errorMsg, cause)
        } else {
            Log.e("BoostSettings", errorMsg)
            CrashReporterHelper.recordException("BoostSettings", errorMsg)
        }
        // Dispara o alerta diretamente para a interface do aplicativo via ShizukuHelper
        ShizukuHelper.onShizukuAlert?.invoke(errorMsg)
    }

    /** [PT-BR] Estado do Thermal Boost. [EN] Thermal Boost state. */
    val thermalBoost = MutableStateFlow(false)

    /** [PT-BR] Estado da desativação de sobreposições HW. [EN] Disable HW overlays state. */
    val disableOverlays = MutableStateFlow(false)

    /** [PT-BR] Estado da limpeza automática de cache. [EN] Auto clear cache state. */
    val autoClearCache = MutableStateFlow(false)

    /** [PT-BR] Estado do modo OOM Immortal. [EN] OOM Immortal mode state. */
    val oomImmortal = MutableStateFlow(false)

    /** [PT-BR] Estado da desativação de animações. [EN] Disable animations state. */
    val disableAnimations = MutableStateFlow(false)

    /** [PT-BR] Estado da taxa de amostragem de toque aprimorada. [EN] High touch sampling rate state. */
    val highTouchSampling = MutableStateFlow(false)

    /** [PT-BR] Estado do limite de processos em segundo plano. [EN] Limit background processes state. */
    val limitBackgroundProcesses = MutableStateFlow(false)

    /** [PT-BR] Estado do bloqueio de reinício automático de apps. [EN] Block auto-restart state. */
    val blockAutoRestart = MutableStateFlow(false)

    /** [PT-BR] Estado do modo Force Deep Sleep no cache. [EN] Force Deep Sleep cache mode state. */
    val forceDeepSleepCache = MutableStateFlow(false)

    /** [PT-BR] Estado do modo de frequência máxima da CPU. [EN] Force maximum CPU frequency state. */
    val forceMaxCpu = MutableStateFlow(false)

    /** [PT-BR] Estado do modo de frequência máxima da GPU. [EN] Force maximum GPU frequency state. */
    val forceMaxGpu = MutableStateFlow(false)

    /** [PT-BR] Estado do Modo de Jogo Nativo / Gaming Hub (Android Core Game Mode 2). [EN] Native Game Mode state. */
    val nativeGameMode = MutableStateFlow(false)

    /** [PT-BR] Estado do bloqueio de notificações flutuantes em jogos. [EN] Block heads-up notifications during gaming state. */
    val blockNotificationsGamer = MutableStateFlow(false)

    /** [PT-BR] Estado da redução do buffer de logger do sistema (64K). [EN] Reduce system logger buffer size state. */
    val reduceLoggerBuffer = MutableStateFlow(false)

    /** [PT-BR] Estado da desativação de serviços desnecessários (Print Spooler). [EN] Disable unnecessary services (Print Spooler) state. */
    val disablePrintSpooler = MutableStateFlow(false)

    /** [PT-BR] Estado da imposição de driver gráfico do sistema / Game Driver. [EN] Force system graphics driver state. */
    val forceSystemGraphicsDriver = MutableStateFlow(false)

    /** [PT-BR] Estado da aceleração de jogos Web / WebView (GPU Rasterization, Zero-Copy e Sandboxed Boost). [EN] Web / WebView Game Boost state. */
    val webViewGameBoost = MutableStateFlow(false)

    /** [PT-BR] Estado de desativação de 4x MSAA forçado. [EN] Disable forced 4x MSAA state. */
    val disable4xMsaa = MutableStateFlow(false)

    init {
        loadBoostSettings()
    }

    /**
     * [PT-BR] Carrega as configurações salvas do SharedPreferences para os StateFlows.
     * [EN] Loads saved settings from SharedPreferences into StateFlows.
     */
    fun loadBoostSettings() {
        try {
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
            nativeGameMode.value = boostPrefs.getBoolean("nativeGameMode", false)
            blockNotificationsGamer.value = boostPrefs.getBoolean("blockNotificationsGamer", false)
            reduceLoggerBuffer.value = boostPrefs.getBoolean("reduceLoggerBuffer", false)
            disablePrintSpooler.value = boostPrefs.getBoolean("disablePrintSpooler", false)
            forceSystemGraphicsDriver.value = boostPrefs.getBoolean("forceSystemGraphicsDriver", false)
            webViewGameBoost.value = boostPrefs.getBoolean("webViewGameBoost", false)
            disable4xMsaa.value = boostPrefs.getBoolean("disable4xMsaa", false)
        } catch (e: Exception) {
            dispatchAlert("Erro ao carregar configurações de boost salvas: ${e.message}", e)
        }
    }

    /**
     * [PT-BR] Verifica obrigatoriamente se o Shizuku está ativo e com permissão concedida OU se o acesso Root está liberado no dispositivo.
     * [EN] Mandatorily verifies if Shizuku is active with permission granted OR if Root access is granted on the device.
     *
     * @return true se Shizuku ou Root estiverem disponíveis e autorizados no dispositivo.
     */
    private fun hasPrivilege(): Boolean {
        return try {
            val shizukuActive = ShizukuHelper.isBinderAlive.value && ShizukuHelper.hasPermission()
            val rootActive = ShizukuHelper.isRootAvailable()

            Log.d("BoostSettings", "Verificação de privilégios de sistema - Shizuku Ativo: $shizukuActive, Root Liberado: $rootActive")

            shizukuActive || rootActive
        } catch (e: Exception) {
            dispatchAlert("Erro ao verificar privilégios de Shizuku/Root: ${e.message}", e)
            false
        }
    }

    /**
     * [PT-BR] Alterna a otimização de controle térmico (Thermal Boost).
     * [EN] Toggles thermal throttling optimization (Thermal Boost).
     */
    fun toggleThermalBoost(enable: Boolean, onResult: ((Boolean, String) -> Unit)? = null) {
        if (!hasPrivilege()) {
            thermalBoost.value = false
            boostPrefs.edit().putBoolean("thermalBoost", false).apply()
            val msg = "Shizuku/Root inativo. Ative o Shizuku ou liberte acesso Root para usar esta função."
            dispatchAlert(msg)
            onResult?.invoke(false, msg)
            return
        }

        thermalBoost.value = enable
        boostPrefs.edit().putBoolean("thermalBoost", enable).apply()

        scope.launch(Dispatchers.IO) {
            try {
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
                withContext(Dispatchers.Main) {
                    onResult?.invoke(true, if (enable) "Thermal Boost ativado!" else "Thermal Boost desativado.")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    thermalBoost.value = false
                    boostPrefs.edit().putBoolean("thermalBoost", false).apply()
                    val msg = "Erro ao configurar Thermal Boost: ${e.message}"
                    dispatchAlert(msg, e)
                    onResult?.invoke(false, msg)
                }
            }
        }
    }

    /**
     * [PT-BR] Alterna a desativação de sobreposições de tela (HW Overlays).
     * [EN] Toggles disabling hardware screen overlays (HW Overlays).
     */
    fun toggleDisableOverlays(enable: Boolean, onResult: ((Boolean, String) -> Unit)? = null) {
        if (!hasPrivilege()) {
            disableOverlays.value = false
            boostPrefs.edit().putBoolean("disableOverlays", false).apply()
            val msg = "Shizuku/Root inativo. Ative o Shizuku ou liberte acesso Root para alterar sobreposições de tela."
            dispatchAlert(msg)
            onResult?.invoke(false, msg)
            return
        }

        disableOverlays.value = enable
        boostPrefs.edit().putBoolean("disableOverlays", enable).apply()

        scope.launch(Dispatchers.IO) {
            try {
                val code = if (enable) 1 else 0
                val result = ShellFunctions.executePrivileged("service call SurfaceFlinger 1008 i32 $code")

                withContext(Dispatchers.Main) {
                    if (result.startsWith("Error")) {
                        disableOverlays.value = false
                        boostPrefs.edit().putBoolean("disableOverlays", false).apply()
                        val msg = "Erro ao alterar sobreposições de tela: $result"
                        dispatchAlert(msg)
                        onResult?.invoke(false, msg)
                    } else {
                        onResult?.invoke(true, if (enable) "Overlays desativados!" else "Overlays restaurados.")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    disableOverlays.value = false
                    boostPrefs.edit().putBoolean("disableOverlays", false).apply()
                    val msg = "Erro ao alternar sobreposições de tela: ${e.message}"
                    dispatchAlert(msg, e)
                    onResult?.invoke(false, msg)
                }
            }
        }
    }

    /**
     * [PT-BR] Alterna o modo de limpeza automática de cache de aplicativos.
     * [EN] Toggles automatic application cache clearing mode.
     */
    fun toggleAutoClearCache(enable: Boolean, onResult: ((Boolean, String) -> Unit)? = null) {
        if (!hasPrivilege()) {
            autoClearCache.value = false
            boostPrefs.edit().putBoolean("autoClearCache", false).apply()
            val msg = "Shizuku/Root inativo. Ative o Shizuku ou liberte acesso Root para usar limpeza de cache."
            dispatchAlert(msg)
            onResult?.invoke(false, msg)
            return
        }

        autoClearCache.value = enable
        boostPrefs.edit().putBoolean("autoClearCache", enable).apply()
        onResult?.invoke(true, if (enable) "Limpeza automática de cache ativada." else "Limpeza automática desativada.")
    }

    /**
     * [PT-BR] Alterna o modo OOM Immortal para evitar que o jogo seja encerrado pelo sistema por falta de memória.
     * [EN] Toggles OOM Immortal mode to prevent games from being killed by low memory killer.
     */
    fun toggleOomImmortal(enable: Boolean, onResult: ((Boolean, String) -> Unit)? = null) {
        if (!hasPrivilege()) {
            oomImmortal.value = false
            boostPrefs.edit().putBoolean("oomImmortal", false).apply()
            val msg = "Shizuku/Root inativo. Ative o Shizuku ou liberte acesso Root para usar o modo OOM Immortal."
            dispatchAlert(msg)
            onResult?.invoke(false, msg)
            return
        }

        oomImmortal.value = enable
        boostPrefs.edit().putBoolean("oomImmortal", enable).apply()
        onResult?.invoke(true, if (enable) "Modo OOM Immortal ativado." else "Modo OOM Immortal desativado.")
    }

    /**
     * [PT-BR] Alterna a desativação global das animações de janela, transição e animadores do sistema.
     * [EN] Toggles disabling system-wide window, transition, and animator animations.
     */
    fun toggleDisableAnimations(enable: Boolean, onResult: ((Boolean, String) -> Unit)? = null) {
        if (!hasPrivilege()) {
            disableAnimations.value = false
            boostPrefs.edit().putBoolean("disableAnimations", false).apply()
            val msg = "Shizuku/Root inativo. Ative o Shizuku ou liberte acesso Root para alterar animações do sistema."
            dispatchAlert(msg)
            onResult?.invoke(false, msg)
            return
        }

        disableAnimations.value = enable
        boostPrefs.edit().putBoolean("disableAnimations", enable).apply()

        scope.launch(Dispatchers.IO) {
            try {
                val scale = if (enable) "0.0" else "1.0"
                val r1 = ShellFunctions.executePrivileged("settings put global window_animation_scale $scale")
                val r2 = ShellFunctions.executePrivileged("settings put global transition_animation_scale $scale")
                val r3 = ShellFunctions.executePrivileged("settings put global animator_duration_scale $scale")

                val hasError = r1.startsWith("Error") || r2.startsWith("Error") || r3.startsWith("Error")

                withContext(Dispatchers.Main) {
                    if (hasError) {
                        disableAnimations.value = false
                        boostPrefs.edit().putBoolean("disableAnimations", false).apply()
                        val msg = "Erro ao configurar animações via Shizuku/Root."
                        dispatchAlert(msg)
                        onResult?.invoke(false, msg)
                    } else {
                        onResult?.invoke(true, if (enable) "Animações desativadas!" else "Animações restauradas.")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    disableAnimations.value = false
                    boostPrefs.edit().putBoolean("disableAnimations", false).apply()
                    val msg = "Erro ao configurar animações: ${e.message}"
                    dispatchAlert(msg, e)
                    onResult?.invoke(false, msg)
                }
            }
        }
    }

    /**
     * [PT-BR] Alterna a otimização da taxa de amostragem de toque da tela.
     * [EN] Toggles screen touch sampling rate optimization.
     */
    fun toggleHighTouchSampling(enable: Boolean, onResult: ((Boolean, String) -> Unit)? = null) {
        if (!hasPrivilege()) {
            highTouchSampling.value = false
            boostPrefs.edit().putBoolean("highTouchSampling", false).apply()
            val msg = "Shizuku/Root inativo. Ative o Shizuku ou liberte acesso Root para alterar a taxa de toque."
            dispatchAlert(msg)
            onResult?.invoke(false, msg)
            return
        }

        highTouchSampling.value = enable
        boostPrefs.edit().putBoolean("highTouchSampling", enable).apply()

        scope.launch(Dispatchers.IO) {
            try {
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
                        val msg = "Erro ao configurar taxa de amostragem de toque via Shizuku/Root."
                        dispatchAlert(msg)
                        onResult?.invoke(false, msg)
                    } else {
                        onResult?.invoke(true, if (enable) "Taxa de amostragem de toque aprimorada!" else "Taxa de toque restaurada.")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    highTouchSampling.value = false
                    boostPrefs.edit().putBoolean("highTouchSampling", false).apply()
                    val msg = "Erro ao configurar taxa de amostragem de toque: ${e.message}"
                    dispatchAlert(msg, e)
                    onResult?.invoke(false, msg)
                }
            }
        }
    }

    /**
     * [PT-BR] Alterna a limitação de processos executados em segundo plano no sistema.
     * [EN] Toggles background process execution limit in the system.
     */
    fun toggleLimitBackgroundProcesses(enable: Boolean, onResult: ((Boolean, String) -> Unit)? = null) {
        if (!hasPrivilege()) {
            limitBackgroundProcesses.value = false
            boostPrefs.edit().putBoolean("limitBackgroundProcesses", false).apply()
            val msg = "Shizuku/Root inativo. Ative o Shizuku ou liberte acesso Root para limitar processos em segundo plano."
            dispatchAlert(msg)
            onResult?.invoke(false, msg)
            return
        }

        limitBackgroundProcesses.value = enable
        boostPrefs.edit().putBoolean("limitBackgroundProcesses", enable).apply()

        scope.launch(Dispatchers.IO) {
            try {
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
                        val msg = "Erro ao configurar limite de processos via Shizuku/Root."
                        dispatchAlert(msg)
                        onResult?.invoke(false, msg)
                    } else {
                        onResult?.invoke(true, if (enable) "Limite de processos em segundo plano aplicado!" else "Limite de processos restaurado.")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    limitBackgroundProcesses.value = false
                    boostPrefs.edit().putBoolean("limitBackgroundProcesses", false).apply()
                    val msg = "Erro ao configurar limite de processos: ${e.message}"
                    dispatchAlert(msg, e)
                    onResult?.invoke(false, msg)
                }
            }
        }
    }

    /**
     * [PT-BR] Alterna o bloqueio de reinício automático e execução em segundo plano de aplicativos.
     * [EN] Toggles blocking application auto-restart and background execution.
     */
    fun toggleBlockAutoRestart(enable: Boolean, onResult: ((Boolean, String) -> Unit)? = null) {
        if (!hasPrivilege()) {
            blockAutoRestart.value = false
            boostPrefs.edit().putBoolean("blockAutoRestart", false).apply()
            val msg = "Shizuku/Root inativo. Ative o Shizuku ou liberte acesso Root para bloquear reinício automático de apps."
            dispatchAlert(msg)
            onResult?.invoke(false, msg)
            return
        }

        blockAutoRestart.value = enable
        boostPrefs.edit().putBoolean("blockAutoRestart", enable).apply()

        scope.launch(Dispatchers.IO) {
            try {
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
                        val msg = "Erro ao configurar bloqueio de reinício automático via Shizuku/Root."
                        dispatchAlert(msg)
                        onResult?.invoke(false, msg)
                    } else {
                        onResult?.invoke(true, if (enable) "Bloqueio de reinício automático ativado!" else "Reinício automático liberado.")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    blockAutoRestart.value = false
                    boostPrefs.edit().putBoolean("blockAutoRestart", false).apply()
                    val msg = "Erro ao configurar bloqueio de reinício automático: ${e.message}"
                    dispatchAlert(msg, e)
                    onResult?.invoke(false, msg)
                }
            }
        }
    }

    /**
     * [PT-BR] Alterna o modo Force Deep Sleep no cache.
     * [EN] Toggles Force Deep Sleep mode for cached processes.
     */
    fun toggleForceDeepSleepCache(enable: Boolean, onResult: ((Boolean, String) -> Unit)? = null) {
        if (!hasPrivilege()) {
            forceDeepSleepCache.value = false
            boostPrefs.edit().putBoolean("forceDeepSleepCache", false).apply()
            val msg = "Shizuku/Root inativo. Ative o Shizuku ou liberte acesso Root para usar o modo Deep Sleep."
            dispatchAlert(msg)
            onResult?.invoke(false, msg)
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

    /**
     * [PT-BR] Executa a suspensão profunda (Deep Sleep) imediatamente em todos os apps em cache em segundo plano.
     * [EN] Executes immediate deep sleep suspension on all cached background applications.
     */
    fun executeDeepSleepNow(onResult: (Boolean, String) -> Unit) {
        if (!hasPrivilege()) {
            val msg = "Shizuku/Root inativo. Ative o Shizuku ou liberte acesso Root para executar o Deep Sleep."
            dispatchAlert(msg)
            onResult(false, msg)
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
                        val msg = "Alguns comandos de Deep Sleep falharam. Verifique o Shizuku/Root."
                        dispatchAlert(msg)
                        onResult(false, msg)
                    } else {
                        onResult(true, "Deep sleep aplicado aos apps em cache com sucesso.")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val msg = "Falha ao aplicar deep sleep: ${e.message}"
                    dispatchAlert(msg, e)
                    onResult(false, msg)
                }
            }
        }
    }

    /**
     * [PT-BR] Alterna a força de frequência máxima para o governor de CPU (Performance).
     * [EN] Toggles maximum frequency override for the CPU governor (Performance).
     */
    fun toggleForceMaxCpu(enable: Boolean, onResult: ((Boolean, String) -> Unit)? = null) {
        if (!hasPrivilege()) {
            forceMaxCpu.value = false
            boostPrefs.edit().putBoolean("forceMaxCpu", false).apply()
            val msg = "Shizuku/Root inativo. Ative o Shizuku ou liberte acesso Root para forçar frequência máxima da CPU."
            dispatchAlert(msg)
            onResult?.invoke(false, msg)
            return
        }

        forceMaxCpu.value = enable
        boostPrefs.edit().putBoolean("forceMaxCpu", enable).apply()

        scope.launch(Dispatchers.IO) {
            try {
                val gov = if (enable) "performance" else "schedutil"
                val result = ShellFunctions.executePrivileged("echo $gov > /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor")

                withContext(Dispatchers.Main) {
                    if (result.startsWith("Error")) {
                        forceMaxCpu.value = false
                        boostPrefs.edit().putBoolean("forceMaxCpu", false).apply()
                        val msg = "Erro ao alterar frequência da CPU: $result"
                        dispatchAlert(msg)
                        onResult?.invoke(false, msg)
                    } else {
                        onResult?.invoke(true, if (enable) "CPU configurada para Performance!" else "CPU restaurada.")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    forceMaxCpu.value = false
                    boostPrefs.edit().putBoolean("forceMaxCpu", false).apply()
                    val msg = "Erro ao configurar frequência da CPU: ${e.message}"
                    dispatchAlert(msg, e)
                    onResult?.invoke(false, msg)
                }
            }
        }
    }

    /**
     * [PT-BR] Alterna a força de frequência máxima para o governor de GPU (Performance).
     * [EN] Toggles maximum frequency override for the GPU governor (Performance).
     */
    fun toggleForceMaxGpu(enable: Boolean, onResult: ((Boolean, String) -> Unit)? = null) {
        if (!hasPrivilege()) {
            forceMaxGpu.value = false
            boostPrefs.edit().putBoolean("forceMaxGpu", false).apply()
            val msg = "Shizuku/Root inativo. Ative o Shizuku ou liberte acesso Root para forçar frequência máxima da GPU."
            dispatchAlert(msg)
            onResult?.invoke(false, msg)
            return
        }

        forceMaxGpu.value = enable
        boostPrefs.edit().putBoolean("forceMaxGpu", enable).apply()

        scope.launch(Dispatchers.IO) {
            try {
                val gov = if (enable) "performance" else "msm-adreno-tz"
                val result = ShellFunctions.executePrivileged("echo $gov > /sys/class/kgsl/kgsl-3d0/devfreq/governor")

                withContext(Dispatchers.Main) {
                    if (result.startsWith("Error")) {
                        forceMaxGpu.value = false
                        boostPrefs.edit().putBoolean("forceMaxGpu", false).apply()
                        val msg = "Erro ao alterar frequência da GPU: $result"
                        dispatchAlert(msg)
                        onResult?.invoke(false, msg)
                    } else {
                        onResult?.invoke(true, if (enable) "GPU configurada para Performance!" else "GPU restaurada.")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    forceMaxGpu.value = false
                    boostPrefs.edit().putBoolean("forceMaxGpu", false).apply()
                    val msg = "Erro ao configurar frequência da GPU: ${e.message}"
                    dispatchAlert(msg, e)
                    onResult?.invoke(false, msg)
                }
            }
        }
    }

    /**
     * [PT-BR] Alterna o Modo de Jogo Nativo / Gaming Hub (Android Core Game Mode 2).
     * [EN] Toggles Native Game Mode / Gaming Hub (Android Core Game Mode 2).
     */
    fun toggleNativeGameMode(enable: Boolean, onResult: ((Boolean, String) -> Unit)? = null) {
        if (!hasPrivilege()) {
            nativeGameMode.value = false
            boostPrefs.edit().putBoolean("nativeGameMode", false).apply()
            val msg = "Shizuku/Root inativo. Ative o Shizuku ou liberte acesso Root para ativar o Modo Jogo Nativo."
            dispatchAlert(msg)
            onResult?.invoke(false, msg)
            return
        }

        nativeGameMode.value = enable
        boostPrefs.edit().putBoolean("nativeGameMode", enable).apply()

        scope.launch(Dispatchers.IO) {
            try {
                if (enable) {
                    ShellFunctions.executePrivileged("settings put global low_power 0")
                    ShellFunctions.executePrivileged("setprop debug.game.performance_mode 1")
                } else {
                    ShellFunctions.executePrivileged("setprop debug.game.performance_mode 0")
                }
                withContext(Dispatchers.Main) {
                    val msg = if (enable) "Modo de Jogo Nativo ativado! Economia de bateria desligada." else "Modo de Jogo Nativo desativado."
                    onResult?.invoke(true, msg)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    nativeGameMode.value = false
                    boostPrefs.edit().putBoolean("nativeGameMode", false).apply()
                    val msg = "Erro ao configurar Modo de Jogo Nativo: ${e.message}"
                    dispatchAlert(msg, e)
                    onResult?.invoke(false, msg)
                }
            }
        }
    }

    /**
     * [PT-BR] Alterna o bloqueio de notificações flutuantes (heads-up) durante jogos.
     * [EN] Toggles blocking of heads-up notifications during gaming.
     */
    fun toggleBlockNotificationsGamer(enable: Boolean, onResult: ((Boolean, String) -> Unit)? = null) {
        if (!hasPrivilege()) {
            blockNotificationsGamer.value = false
            boostPrefs.edit().putBoolean("blockNotificationsGamer", false).apply()
            val msg = "Shizuku/Root inativo. Ative o Shizuku ou liberte acesso Root para bloquear notificações."
            dispatchAlert(msg)
            onResult?.invoke(false, msg)
            return
        }

        blockNotificationsGamer.value = enable
        boostPrefs.edit().putBoolean("blockNotificationsGamer", enable).apply()

        scope.launch(Dispatchers.IO) {
            try {
                val state = if (enable) "0" else "1"
                ShellFunctions.executePrivileged("settings put global heads_up_notifications_enabled $state")
                withContext(Dispatchers.Main) {
                    val msg = if (enable) "Notificações flutuantes bloqueadas durante jogos." else "Notificações flutuantes restauradas."
                    onResult?.invoke(true, msg)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    blockNotificationsGamer.value = false
                    boostPrefs.edit().putBoolean("blockNotificationsGamer", false).apply()
                    val msg = "Erro ao alternar bloqueio de notificações: ${e.message}"
                    dispatchAlert(msg, e)
                    onResult?.invoke(false, msg)
                }
            }
        }
    }

    /**
     * [PT-BR] Alterna a redução do buffer de logger do sistema (64K) para diminuir sobrecarga de CPU e I/O.
     * [EN] Toggles logger buffer reduction (64K) to reduce CPU and I/O overhead.
     */
    fun toggleReduceLoggerBuffer(enable: Boolean, onResult: ((Boolean, String) -> Unit)? = null) {
        if (!hasPrivilege()) {
            reduceLoggerBuffer.value = false
            boostPrefs.edit().putBoolean("reduceLoggerBuffer", false).apply()
            val msg = "Shizuku/Root inativo. Ative o Shizuku ou liberte acesso Root para alterar buffer de logger."
            dispatchAlert(msg)
            onResult?.invoke(false, msg)
            return
        }

        reduceLoggerBuffer.value = enable
        boostPrefs.edit().putBoolean("reduceLoggerBuffer", enable).apply()

        scope.launch(Dispatchers.IO) {
            try {
                val size = if (enable) "64K" else "256K"
                ShellFunctions.executePrivileged("logcat -G $size")
                ShellFunctions.executePrivileged("setprop persist.logd.size $size")
                withContext(Dispatchers.Main) {
                    val msg = if (enable) "Buffer do Logger reduzido para 64K (Menos sobrecarga de CPU/IO)." else "Buffer do Logger restaurado para padrão (256K)."
                    onResult?.invoke(true, msg)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    reduceLoggerBuffer.value = false
                    boostPrefs.edit().putBoolean("reduceLoggerBuffer", false).apply()
                    val msg = "Erro ao configurar buffer do logger: ${e.message}"
                    dispatchAlert(msg, e)
                    onResult?.invoke(false, msg)
                }
            }
        }
    }

    /**
     * [PT-BR] Alterna a desativação de serviços desnecessários em segundo plano (como o Print Spooler).
     * [EN] Toggles disabling of unnecessary background services (such as Print Spooler).
     */
    fun toggleDisablePrintSpooler(enable: Boolean, onResult: ((Boolean, String) -> Unit)? = null) {
        if (!hasPrivilege()) {
            disablePrintSpooler.value = false
            boostPrefs.edit().putBoolean("disablePrintSpooler", false).apply()
            val msg = "Shizuku/Root inativo. Ative o Shizuku ou liberte acesso Root para gerenciar serviços."
            dispatchAlert(msg)
            onResult?.invoke(false, msg)
            return
        }

        disablePrintSpooler.value = enable
        boostPrefs.edit().putBoolean("disablePrintSpooler", enable).apply()

        scope.launch(Dispatchers.IO) {
            try {
                val cmd = if (enable) "pm disable-user com.android.printspooler" else "pm enable com.android.printspooler"
                ShellFunctions.executePrivileged(cmd)
                withContext(Dispatchers.Main) {
                    val msg = if (enable) "Serviço de impressão desativado (Liberação de RAM/CPU)." else "Serviço de impressão reativado."
                    onResult?.invoke(true, msg)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    disablePrintSpooler.value = false
                    boostPrefs.edit().putBoolean("disablePrintSpooler", false).apply()
                    val msg = "Erro ao alterar serviço de impressão: ${e.message}"
                    dispatchAlert(msg, e)
                    onResult?.invoke(false, msg)
                }
            }
        }
    }

    /**
     * [PT-BR] Alterna a imposição do Driver Gráfico do Sistema (Game Driver / Atualizável) para todos os jogos.
     * [EN] Toggles forcing System Graphics Driver (Game Driver / Updatable) for all games.
     */
    fun toggleForceSystemGraphicsDriver(enable: Boolean, onResult: ((Boolean, String) -> Unit)? = null) {
        if (!hasPrivilege()) {
            forceSystemGraphicsDriver.value = false
            boostPrefs.edit().putBoolean("forceSystemGraphicsDriver", false).apply()
            val msg = "Shizuku/Root inativo. Ative o Shizuku ou liberte acesso Root para configurar Game Driver."
            dispatchAlert(msg)
            onResult?.invoke(false, msg)
            return
        }

        forceSystemGraphicsDriver.value = enable
        boostPrefs.edit().putBoolean("forceSystemGraphicsDriver", enable).apply()

        scope.launch(Dispatchers.IO) {
            try {
                val state = if (enable) "1" else "0"
                ShellFunctions.executePrivileged("settings put global updatable_driver_all_apps $state")
                ShellFunctions.executePrivileged("setprop debug.graphics.game_default_driver $state")
                withContext(Dispatchers.Main) {
                    val msg = if (enable) "Game Driver do Sistema forçado para renderização de jogos!" else "Driver gráfico restaurado para o padrão."
                    onResult?.invoke(true, msg)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    forceSystemGraphicsDriver.value = false
                    boostPrefs.edit().putBoolean("forceSystemGraphicsDriver", false).apply()
                    val msg = "Erro ao configurar driver gráfico: ${e.message}"
                    dispatchAlert(msg, e)
                    onResult?.invoke(false, msg)
                }
            }
        }
    }

    /**
     * [PT-BR] Alterna a aceleração de jogos Web / WebView (GPU Rasterization, Zero-Copy e Prioridade Sandboxed).
     * [EN] Toggles Web / WebView game acceleration (GPU Rasterization, Zero-Copy and Sandboxed Priority).
     */
    fun toggleWebViewGameBoost(enable: Boolean, onResult: ((Boolean, String) -> Unit)? = null) {
        if (!hasPrivilege()) {
            webViewGameBoost.value = false
            boostPrefs.edit().putBoolean("webViewGameBoost", false).apply()
            val msg = "Shizuku/Root inativo. Ative o Shizuku ou liberte acesso Root para acelerar jogos WebView."
            dispatchAlert(msg)
            onResult?.invoke(false, msg)
            return
        }

        webViewGameBoost.value = enable
        boostPrefs.edit().putBoolean("webViewGameBoost", enable).apply()

        scope.launch(Dispatchers.IO) {
            try {
                if (enable) {
                    val flags = "_ --enable-gpu-rasterization --enable-zero-copy --ignore-gpu-blocklist --enable-accelerated-2d-canvas --force-gpu-mem-available-mb=512 --disable-gpu-vsync --disable-background-timer-throttling"
                    ShellFunctions.executePrivileged("mkdir -p /data/local/tmp")
                    ShellFunctions.executePrivileged("echo \"$flags\" > /data/local/tmp/webview-command-line")
                    ShellFunctions.executePrivileged("chmod 755 /data/local/tmp/webview-command-line")

                    ShellFunctions.executePrivileged("setprop debug.hwui.renderer skiagl")
                    ShellFunctions.executePrivileged("setprop debug.hwui.render_ahead 1")
                    ShellFunctions.executePrivileged("setprop debug.hwui.texture_cache_size 72")
                    ShellFunctions.executePrivileged("setprop debug.hwui.texture_cache_flushrate 0.8")

                    ShellFunctions.executePrivileged("for p in \$(pgrep -f 'sandboxed_process'); do renice -n -20 -p \$p 2>/dev/null; echo -1000 > /proc/\$p/oom_score_adj 2>/dev/null; done")
                } else {
                    ShellFunctions.executePrivileged("rm -f /data/local/tmp/webview-command-line")
                }
                withContext(Dispatchers.Main) {
                    val msg = if (enable) "Aceleração WebGL/WebView ativada (GPU Rasterization & Zero-Copy)!" else "Aceleração de WebView restaurada ao padrão."
                    onResult?.invoke(true, msg)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    webViewGameBoost.value = false
                    boostPrefs.edit().putBoolean("webViewGameBoost", false).apply()
                    val msg = "Erro ao configurar aceleração WebView: ${e.message}"
                    dispatchAlert(msg, e)
                    onResult?.invoke(false, msg)
                }
            }
        }
    }

    /**
     * [PT-BR] Alterna a desativação forçada de 4x MSAA para maximizar a taxa de quadros (FPS).
     * [EN] Toggles disabling forced 4x MSAA to maximize framerate (FPS).
     */
    fun toggleDisable4xMsaa(enable: Boolean, onResult: ((Boolean, String) -> Unit)? = null) {
        if (!hasPrivilege()) {
            disable4xMsaa.value = false
            boostPrefs.edit().putBoolean("disable4xMsaa", false).apply()
            val msg = "Shizuku/Root inativo. Ative o Shizuku ou liberte acesso Root para usar esta função."
            dispatchAlert(msg)
            onResult?.invoke(false, msg)
            return
        }

        disable4xMsaa.value = enable
        boostPrefs.edit().putBoolean("disable4xMsaa", enable).apply()

        scope.launch(Dispatchers.IO) {
            try {
                if (enable) {
                    ShellFunctions.executePrivileged("setprop debug.egl.force_msaa 0")
                    ShellFunctions.executePrivileged("settings put global debug.egl.force_msaa 0")
                    ShellFunctions.executePrivileged("setprop persist.sys.egl.force_msaa 0")
                } else {
                    ShellFunctions.executePrivileged("settings delete global debug.egl.force_msaa")
                    ShellFunctions.executePrivileged("setprop debug.egl.force_msaa 0")
                }
                withContext(Dispatchers.Main) {
                    onResult?.invoke(true, if (enable) "Desativação de 4x MSAA ativada!" else "Desativação de 4x MSAA desativada.")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    disable4xMsaa.value = false
                    boostPrefs.edit().putBoolean("disable4xMsaa", false).apply()
                    val msg = "Erro ao configurar 4x MSAA: ${e.message}"
                    dispatchAlert(msg, e)
                    onResult?.invoke(false, msg)
                }
            }
        }
    }

    /**
     * [PT-BR] Ativa todas as configurações e modos de boost simultaneamente.
     * [EN] Enables all boost configurations and modes simultaneously.
     */
    fun enableAllBoosts(onComplete: () -> Unit) {
        toggleNativeGameMode(true)
        toggleForceSystemGraphicsDriver(true)
        toggleWebViewGameBoost(true)
        toggleBlockNotificationsGamer(true)
        toggleReduceLoggerBuffer(true)
        toggleDisablePrintSpooler(true)
        toggleForceMaxCpu(true)
        toggleForceMaxGpu(true)
        toggleDisableAnimations(true)
        toggleHighTouchSampling(true)
        toggleLimitBackgroundProcesses(true)
        toggleBlockAutoRestart(true)
        toggleForceDeepSleepCache(true)
        toggleThermalBoost(true)
        toggleDisableOverlays(true)
        toggleAutoClearCache(true)
        toggleOomImmortal(true)
        toggleDisable4xMsaa(true)
        onComplete()
    }
}
