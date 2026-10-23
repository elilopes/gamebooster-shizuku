package com.gfx

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Gerenciador responsável pelas operações de aplicativos:
 * - Encerrar aplicativo (killApp com force-stop)
 * - Encerrar todos os aplicativos (killAllApps)
 * - Congelar / Descongelar aplicativo (freezeApp)
 * - Bloquear / Desbloquear acesso à internet (toggleInternet)
 */
class ManagerApps(
    private val context: Context,
    private val packageManager: PackageManager,
    private val scope: CoroutineScope,
    private val onRefreshRequested: (() -> Unit)? = null
) {

    /**
     * Encerra forçadamente um aplicativo individualmente usando Shizuku ou Root.
     */
    fun killApp(packageName: String, onResult: ((Boolean, String) -> Unit)? = null): Boolean {
        val hasPrivilege = ShizukuHelper.hasPermission() || ShizukuHelper.isRootAvailable()
        if (!hasPrivilege) {
            val errorMsg = "Não foi possível finalizar o aplicativo em segundo plano."
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

            delay(350)
            onRefreshRequested?.invoke()

            withContext(Dispatchers.Main) {
                val msg = "Aplicativo $appName ($packageName) foi finalizado (force-stop) com sucesso!"
                onResult?.invoke(true, msg)
            }
        }
        return true
    }

    /**
     * Encerra forçadamente todos os aplicativos detectados em segundo plano.
     */
    fun killAllApps(
        appList: List<BackgroundApp>,
        onResult: ((Int, String) -> Unit)? = null
    ) {
        val hasPrivilege = ShizukuHelper.hasPermission() || ShizukuHelper.isRootAvailable()
        if (!hasPrivilege) {
            val errorMsg = "Não foi possível finalizar o aplicativo em segundo plano."
            onResult?.invoke(0, errorMsg)
            return
        }

        scope.launch(Dispatchers.IO) {
            val currentList = appList.toList()
            var killedCount = 0

            for (bgApp in currentList) {
                // Encerramento forçado real de cada aplicativo
                ShellFunctions.executePrivileged("am force-stop ${bgApp.packageName}")
                ShellFunctions.executePrivileged("pkill -9 -f ${bgApp.packageName}")
                killedCount++
            }
            ShellFunctions.executePrivileged("am kill-all")

            delay(400)
            onRefreshRequested?.invoke()

            withContext(Dispatchers.Main) {
                val msg = "Todos os $killedCount aplicativos que estão em segundo plano foram forçados a parar com sucesso!"
                onResult?.invoke(killedCount, msg)
            }
        }
    }

    /**
     * Bloqueia ou libera o tráfego de internet do aplicativo usando iptables privilegiado.
     */
    fun toggleInternet(packageName: String, block: Boolean) {
        scope.launch(Dispatchers.IO) {
            try {
                val uid = packageManager.getPackageUid(packageName, 0)
                if (block) {
                    ShellFunctions.executePrivileged("iptables -I OUTPUT -m owner --uid-owner $uid -j REJECT")
                } else {
                    ShellFunctions.executePrivileged("iptables -D OUTPUT -m owner --uid-owner $uid -j REJECT")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Congela (desativa/suspende) ou descongela (ativa/reativa) um aplicativo do sistema.
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
                val setting = packageManager.getApplicationEnabledSetting(packageName)
                val isCurrentlyDisabled = setting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
                        setting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER

                val statusMessage: String
                if (isCurrentlyDisabled) {
                    ShellFunctions.executePrivileged("pm enable $packageName")
                    ShellFunctions.executePrivileged("cmd package unsuspend $packageName")
                    statusMessage = "Aplicativo $appName foi descongelado e reativado com sucesso!"
                } else {
                    ShellFunctions.executePrivileged("am force-stop $packageName")
                    ShellFunctions.executePrivileged("pm disable-user --user 0 $packageName")
                    ShellFunctions.executePrivileged("cmd package suspend $packageName")
                    statusMessage = "Aplicativo $appName foi congelado com sucesso (desativado do sistema)!"
                }

                delay(350)
                onRefreshRequested?.invoke()

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
}
