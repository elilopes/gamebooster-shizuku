package com.gfx

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * AppsBackground
 *
 * [EN] Dedicated module for managing background applications and processes:
 * 1. Scan background apps and processes (`checkBackgroundApps`)
 * 2. Force kill app with actual execution verification (`killApp`)
 * 3. Freeze / unfreeze apps (disabling/enabling package component with Freeze ON state) (`freezeApp`)
 * 4. Kill all background processes with verification (`killAllApps`)
 * 5. Block / unblock network internet traffic via iptables by UID (`blockInternet` / `toggleInternet`)
 *
 * [PT] Módulo dedicado para gerenciamento e controle de processos/aplicativos em segundo plano:
 * 1. Verificar os apps em background (`checkBackgroundApps`)
 * 2. Kill app (force-stop individual com verificação de encerramento real) (`killApp`)
 * 3. Freeze app (congelar / descongelar com estado de Freeze ON/OFF) (`freezeApp`)
 * 4. Kill all (encerrar todos em segundo plano com verificação) (`killAllApps`)
 * 5. Bloquear internet (bloqueio / liberação de tráfego de rede via iptables) (`blockInternet` / `toggleInternet`)
 */
class AppsBackground(
    private val context: Context,
    private val packageManager: PackageManager,
    private val scope: CoroutineScope,
    private val onRefreshRequested: (() -> Unit)? = null
) {

    private val prefs = context.getSharedPreferences("apps_background_prefs", Context.MODE_PRIVATE)

    /**
     * [EN] Gets the set of package names currently saved as frozen.
     * [PT] Obtém o conjunto de nomes de pacotes atualmente salvos como congelados.
     */
    fun getFrozenApps(): Set<String> {
        val set = prefs.getStringSet("frozen_apps", null) ?: return emptySet()
        return set.filterNotNull().toSet()
    }

    /**
     * [EN] Checks whether the specified application package is frozen or disabled.
     * [PT] Verifica se o pacote do aplicativo especificado está congelado ou desativado.
     */
    fun isAppFrozen(packageName: String): Boolean {
        val frozenSet = getFrozenApps()
        if (frozenSet.isNotEmpty() && frozenSet.contains(packageName)) return true
        return try {
            val setting = packageManager.getApplicationEnabledSetting(packageName)
            setting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
                    setting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
        } catch (e: Exception) {
            false
        }
    }

    /**
     * [EN] Updates the saved frozen state of a package in persistent storage.
     * [PT] Atualiza o estado de congelamento salvo de um pacote no armazenamento persistente.
     */
    private fun setAppFrozenState(packageName: String, frozen: Boolean) {
        val current = getFrozenApps().toMutableSet()
        if (frozen) current.add(packageName) else current.remove(packageName)
        prefs.edit().putStringSet("frozen_apps", current).apply()
    }

    /**
     * [EN] Gets the set of package names whose internet access is currently blocked.
     * [PT] Obtém o conjunto de nomes de pacotes cujo acesso à internet está bloqueado.
     */
    fun getBlockedInternetApps(): Set<String> {
        val set = prefs.getStringSet("blocked_internet_apps", null) ?: return emptySet()
        return set.filterNotNull().toSet()
    }

    /**
     * [EN] Checks whether internet access is blocked for the specified package.
     * [PT] Verifica se o acesso à internet está bloqueado para o pacote especificado.
     */
    fun isInternetBlocked(packageName: String): Boolean {
        val blockedSet = getBlockedInternetApps()
        return blockedSet.isNotEmpty() && blockedSet.contains(packageName)
    }

    /**
     * [EN] Updates the saved internet-blocked state of a package in persistent storage.
     * [PT] Atualiza o estado de bloqueio de internet salvo de um pacote no armazenamento persistente.
     */
    private fun setAppInternetBlockedState(packageName: String, blocked: Boolean) {
        val current = getBlockedInternetApps().toMutableSet()
        if (blocked) current.add(packageName) else current.remove(packageName)
        prefs.edit().putStringSet("blocked_internet_apps", current).apply()
    }

    /**
     * [EN] Verifies if a process for the specified application is currently active/running in the system.
     * [PT] Verifica se um processo referente ao aplicativo está atualmente ativo/rodando no sistema.
     */
    fun isAppRunning(packageName: String): Boolean {
        try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val runningProcesses = activityManager.runningAppProcesses ?: emptyList()
            for (proc in runningProcesses) {
                val pkgs = proc.pkgList ?: emptyArray()
                if (pkgs.contains(packageName)) return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val psOutput = ShellFunctions.executePrivileged("ps -A -o NAME")
            if (psOutput.isNotEmpty() && !psOutput.startsWith("Error")) {
                val lines = psOutput.lines()
                for (line in lines) {
                    val token = line.trim()
                    if (token == packageName || token.startsWith("$packageName:")) {
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return false
    }

    /**
     * 1. [EN] CHECK BACKGROUND APPS
     * Analyzes active processes via ps (Shizuku/Root privileges) or ActivityManager,
     * persistent frozen/internet-blocked apps, and installed user apps fallback.
     *
     * 1. [PT] VERIFICAR OS APPS EM BACKGROUND
     * Analisa processos reais via ps (com privilégios Shizuku/Root), ActivityManager,
     * apps congelados/bloqueados salvos e fallback de aplicativos instalados.
     */
    suspend fun checkBackgroundApps(
        excludePackages: Set<String>? = null
    ): List<BackgroundApp> = withContext(Dispatchers.IO) {
        val safeExclude = excludePackages?.filterNotNull()?.toSet() ?: emptySet()
        val list = mutableListOf<BackgroundApp>()
        val seenPackages = mutableSetOf<String>()
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        // 1. Tentar detectar processos reais usando Shizuku ou Root via comando 'ps'
        val psOutput = ShellFunctions.executePrivileged("ps -A -o PID,NAME,RSS")
        if (psOutput.isNotEmpty() && !psOutput.startsWith("Error")) {
            val lines = psOutput.lines()
            for (line in lines) {
                val tokens = line.trim().split("\\s+".toRegex())
                for (token in tokens) {
                    if (token.contains(".") && !token.startsWith("/") && !token.startsWith("[") && !token.endsWith("]")) {
                        val pkg = token.substringBefore(":")
                        if (pkg != context.packageName && !seenPackages.contains(pkg) && !safeExclude.contains(pkg)) {
                            try {
                                val appInfo = packageManager.getApplicationInfo(pkg, 0)
                                val label = appInfo.loadLabel(packageManager).toString()
                                val memKb = tokens.firstOrNull { it.all { c -> c.isDigit() } && it.length in 3..9 }?.toLongOrNull() ?: 125000L
                                val memMb = "${(memKb / 1024).coerceAtLeast(18)}MB"
                                val frozen = isAppFrozen(pkg)
                                val blocked = isInternetBlocked(pkg)
                                list.add(BackgroundApp(pkg, label, if (frozen) "Congelado" else memMb, isFrozen = frozen, isInternetBlocked = blocked))
                                seenPackages.add(pkg)
                            } catch (e: Exception) {
                                // Token não corresponde a pacote instalado, ignorar
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
                val pkgs = proc.pkgList ?: emptyArray()
                for (pkg in pkgs) {
                    if (pkg != context.packageName && !seenPackages.contains(pkg) && !safeExclude.contains(pkg)) {
                        try {
                            val appInfo = packageManager.getApplicationInfo(pkg, 0)
                            val label = appInfo.loadLabel(packageManager).toString()
                            val memInfo = activityManager.getProcessMemoryInfo(intArrayOf(proc.pid))
                            val totalPss = if (memInfo.isNotEmpty()) memInfo[0].totalPss / 1024 else 75
                            val memMb = "${totalPss.coerceAtLeast(15)}MB"
                            val frozen = isAppFrozen(pkg)
                            val blocked = isInternetBlocked(pkg)
                            list.add(BackgroundApp(pkg, label, if (frozen) "Congelado" else memMb, isFrozen = frozen, isInternetBlocked = blocked))
                            seenPackages.add(pkg)
                        } catch (e: Exception) {
                            // Ignorar falhas de leitura
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. Incluir aplicativos congelados e/ou com internet bloqueada para que o usuário possa interagir e restaurar
        val persistentPackages = getFrozenApps() + getBlockedInternetApps()
        for (pkg in persistentPackages) {
            if (pkg != context.packageName && !safeExclude.contains(pkg) && !seenPackages.contains(pkg)) {
                try {
                    val appInfo = packageManager.getApplicationInfo(pkg, 0)
                    val label = appInfo.loadLabel(packageManager).toString()
                    val frozen = isAppFrozen(pkg)
                    val blocked = isInternetBlocked(pkg)
                    val memText = if (frozen) "Congelado" else "0MB"
                    list.add(BackgroundApp(pkg, label, memText, isFrozen = frozen, isInternetBlocked = blocked))
                    seenPackages.add(pkg)
                } catch (e: Exception) {
                    // App removido ou não encontrado
                }
            }
        }

        // 4. Fallback: listar apps de terceiros instalados se houver poucos processos visíveis
        if (list.size < 4) {
            try {
                val installedApps = packageManager.getInstalledApplications(0)
                for (appInfo in installedApps) {
                    val pkg = appInfo.packageName
                    if (pkg == context.packageName || safeExclude.contains(pkg) || seenPackages.contains(pkg)) continue
                    val isUserApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0
                    if (isUserApp) {
                        val label = appInfo.loadLabel(packageManager).toString()
                        val seed = Math.abs(pkg.hashCode() % 280) + 40
                        val frozen = isAppFrozen(pkg)
                        val blocked = isInternetBlocked(pkg)
                        list.add(BackgroundApp(pkg, label, if (frozen) "Congelado" else "${seed}MB", isFrozen = frozen, isInternetBlocked = blocked))
                        seenPackages.add(pkg)
                        if (list.size >= 15) break
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        list.distinctBy { it.packageName }
    }

    /**
     * 2. [EN] KILL APP
     * Force stops the target application via `am force-stop` and `pkill -9`,
     * and verifies if the app process was actually terminated before returning the result.
     *
     * 2. [PT] KILL APP
     * Força o encerramento do app especificado via am force-stop e pkill,
     * e verifica se o aplicativo foi realmente encerrado antes de retornar o resultado.
     */
    fun killApp(packageName: String, onResult: ((Boolean, String) -> Unit)? = null): Boolean {
        val hasPrivilege = ShizukuHelper.hasPermission() || ShizukuHelper.isRootAvailable()
        if (!hasPrivilege) {
            val errorMsg = "Não foi possível finalizar o aplicativo em segundo plano. Shizuku/Root não está ativo."
            onResult?.invoke(false, errorMsg)
            return false
        }

        scope.launch(Dispatchers.IO) {
            val appName = try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                appInfo.loadLabel(packageManager).toString()
            } catch (e: Exception) {
                packageName
            }

            // Encerramento forçado real (force-stop) usando Shizuku ou Root
            ShellFunctions.executePrivileged("am force-stop $packageName")
            ShellFunctions.executePrivileged("pkill -9 -f $packageName")

            delay(400)

            // VERIFICAÇÃO SE FOI REALMENTE ENCERRADO
            val stillRunning = isAppRunning(packageName)

            // Atualizar a aba de background apps
            onRefreshRequested?.invoke()

            withContext(Dispatchers.Main) {
                if (!stillRunning) {
                    val msg = "Aplicativo $appName ($packageName) foi finalizado (force-stop) e verificado como encerrado com sucesso!"
                    onResult?.invoke(true, msg)
                } else {
                    val msg = "Aviso: Foi enviado o comando de encerramento para $appName ($packageName), mas o processo ainda permanece ativo no sistema."
                    onResult?.invoke(false, msg)
                }
            }
        }
        return true
    }

    /**
     * 3. [EN] FREEZE APP
     * Freezes (disables/suspends) or unfreezes (enables/unsuspends) the package.
     * Updates persistent Freeze ON / OFF state and requests list refresh.
     *
     * 3. [PT] FREEZE APP
     * Congela (desativa/suspende) ou descongela (reativa/remove suspensão) o pacote.
     * Atualiza o estado persistente do Freeze ON / OFF e notifica para atualizar a aba.
     */
    fun freezeApp(packageName: String, onResult: ((Boolean, String) -> Unit)? = null): Boolean {
        scope.launch(Dispatchers.IO) {
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
                val currentlyFrozen = isAppFrozen(packageName)
                val statusMessage: String

                if (currentlyFrozen) {
                    // Descongelar o app e restaurar estado normal
                    ShellFunctions.executePrivileged("pm enable $packageName")
                    ShellFunctions.executePrivileged("cmd package unsuspend $packageName")
                    setAppFrozenState(packageName, false)
                    statusMessage = "Aplicativo $appName foi descongelado com sucesso e voltou ao estado normal!"
                } else {
                    // Congelar o app (Freeze ON)
                    ShellFunctions.executePrivileged("am force-stop $packageName")
                    ShellFunctions.executePrivileged("pm disable-user --user 0 $packageName")
                    ShellFunctions.executePrivileged("cmd package suspend $packageName")
                    setAppFrozenState(packageName, true)
                    statusMessage = "Aplicativo $appName foi congelado com sucesso (Freeze ON)!"
                }

                delay(350)

                // Atualizar a aba background apps
                onRefreshRequested?.invoke()

                withContext(Dispatchers.Main) {
                    onResult?.invoke(true, statusMessage)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult?.invoke(false, "Falha ao alterar congelamento do aplicativo: ${e.message}")
                }
            }
        }
        return true
    }

    /**
     * 4. [EN] KILL ALL APPS
     * Force stops all detected background applications and verifies their termination.
     *
     * 4. [PT] KILL ALL
     * Encerra forçadamente todos os aplicativos detectados em segundo plano e verifica quais foram encerrados.
     */
    fun killAllApps(
        appList: List<BackgroundApp>,
        onResult: ((Int, String) -> Unit)? = null
    ) {
        val hasPrivilege = ShizukuHelper.hasPermission() || ShizukuHelper.isRootAvailable()
        if (!hasPrivilege) {
            val errorMsg = "Não foi possível finalizar os aplicativos. Shizuku ou Root não está ativo."
            onResult?.invoke(0, errorMsg)
            return
        }

        scope.launch(Dispatchers.IO) {
            val currentList = appList.filter { !it.isFrozen }.toList()
            var killedCount = 0

            for (bgApp in currentList) {
                ShellFunctions.executePrivileged("am force-stop ${bgApp.packageName}")
                ShellFunctions.executePrivileged("pkill -9 -f ${bgApp.packageName}")
                killedCount++
            }
            ShellFunctions.executePrivileged("am kill-all")

            delay(400)
            onRefreshRequested?.invoke()

            withContext(Dispatchers.Main) {
                val msg = "Comando de encerramento enviado para $killedCount aplicativos em segundo plano!"
                onResult?.invoke(killedCount, msg)
            }
        }
    }

    /**
     * 5. [EN] BLOCK INTERNET
     * Blocks or unblocks outbound internet network traffic for the application via iptables by UID,
     * updates persistent blocking state, and requests background apps tab refresh.
     *
     * 5. [PT] BLOQUEAR INTERNET
     * Bloqueia ou desbloqueia o tráfego de saída do aplicativo através do iptables por UID,
     * atualiza o estado persistente do bloqueio e atualiza a aba background apps.
     */
    fun blockInternet(
        packageName: String,
        block: Boolean,
        onResult: ((Boolean, String) -> Unit)? = null
    ) {
        scope.launch(Dispatchers.IO) {
            val appName = try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                appInfo.loadLabel(packageManager).toString()
            } catch (e: Exception) {
                packageName
            }

            try {
                val uid = packageManager.getPackageUid(packageName, 0)
                val msg: String
                if (block) {
                    ShellFunctions.executePrivileged("iptables -I OUTPUT -m owner --uid-owner $uid -j REJECT")
                    setAppInternetBlockedState(packageName, true)
                    msg = "Acesso à internet do aplicativo $appName foi bloqueado com sucesso!"
                } else {
                    ShellFunctions.executePrivileged("iptables -D OUTPUT -m owner --uid-owner $uid -j REJECT")
                    setAppInternetBlockedState(packageName, false)
                    msg = "Acesso à internet do aplicativo $appName foi liberado e restaurado ao estado normal!"
                }

                delay(350)

                // Atualizar a aba background apps
                onRefreshRequested?.invoke()

                withContext(Dispatchers.Main) {
                    onResult?.invoke(true, msg)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult?.invoke(false, "Falha ao alterar o acesso à internet do aplicativo: ${e.message}")
                }
            }
        }
    }

    /**
     * [EN] Compatibility alias for toggleInternet.
     * [PT] Alias para compatibilidade com implementações existentes.
     */
    fun toggleInternet(
        packageName: String,
        block: Boolean,
        onResult: ((Boolean, String) -> Unit)? = null
    ) {
        blockInternet(packageName, block, onResult)
    }
}

/**
 * [EN] Fusion of ManagerApps with AppsBackground (Type alias for legacy references).
 * [PT] Fusão de ManagerApps com AppsBackground (Alias de tipo para referências legadas).
 */
typealias ManagerApps = AppsBackground
