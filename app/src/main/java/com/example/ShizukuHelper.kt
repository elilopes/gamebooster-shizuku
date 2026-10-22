package com.example

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

object ShizukuHelper {

    const val SHIZUKU_PERMISSION_REQUEST_CODE = 1001

    // Contexto de aplicação armazenado para checagem de pacotes
    private var appContext: Context? = null

    // Listener opcional para notificação direta de status
    var onShizukuStatusChanged: ((Boolean) -> Unit)? = null

    // Callback e StateFlow para exibir alertas diretamente na UI do aplicativo
    var onShizukuAlert: ((String) -> Unit)? = null

    private val _shizukuAlert = MutableStateFlow<String?>(null)
    val shizukuAlert: StateFlow<String?> = _shizukuAlert.asStateFlow()

    // StateFlow observável pelo Jetpack Compose indicando se a permissão foi concedida
    private val _isShizukuGranted = MutableStateFlow(false)
    val isShizukuGranted: StateFlow<Boolean> = _isShizukuGranted.asStateFlow()

    // StateFlow indicando se o serviço do Shizuku está em execução no dispositivo (binder conectado)
    private val _isBinderAlive = MutableStateFlow(false)
    val isBinderAlive: StateFlow<Boolean> = _isBinderAlive.asStateFlow()

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        updateStatus()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        updateStatus()
    }

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, _ ->
        updateStatus()
    }

    fun initialize(context: Context) {
        appContext = context.applicationContext
        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionListener)
            updateStatus()
        } catch (e: Exception) {
            val errorMsg = "Falha na inicialização do Shizuku: ${e.message}"
            Log.e("ShizukuHelper", errorMsg, e)
            _shizukuAlert.value = errorMsg
            onShizukuAlert?.invoke(errorMsg)
        }
    }

    /**
     * Atualiza o estado da conexão e da permissão do Shizuku.
     */
    fun updateStatus() {
        Log.d("ShizukuHelper", "Atualizando status do Shizuku...")
        
        val available = isShizukuAvailable()
        Log.d("ShizukuHelper", "Shizuku disponível: $available")
        
        _isBinderAlive.value = available

        val granted = if (available) {
            try {
                if (!Shizuku.isPreV11()) {
                    val perm = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                    Log.d("ShizukuHelper", "Permissão concedida: $perm")
                    perm
                } else {
                    Log.w("ShizukuHelper", "Versão pré-V11 detectada")
                    false
                }
            } catch (e: Throwable) {
                val errorMsg = "Erro ao verificar permissão: ${e.message}"
                Log.e("ShizukuHelper", errorMsg, e)
                _shizukuAlert.value = errorMsg
                onShizukuAlert?.invoke(errorMsg)
                false
            }
        } else {
            false
        }

        _isShizukuGranted.value = granted
        onShizukuStatusChanged?.invoke(granted)
    }

    /**
     * Função unificada: verifica se o Shizuku está instalado e se o binder está ativo.
     * Em caso de erro ou ausência, registra no Log e dispara um alerta no aplicativo.
     */
    fun isShizukuAvailable(): Boolean {
        val context = appContext
        if (context != null) {
            try {
                context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
            } catch (e: PackageManager.NameNotFoundException) {
                val errorMsg = "Shizuku não está instalado no dispositivo."
                Log.e("ShizukuHelper", errorMsg, e)
                _shizukuAlert.value = errorMsg
                onShizukuAlert?.invoke(errorMsg)
                return false
            } catch (e: Throwable) {
                val errorMsg = "Erro ao verificar instalação do Shizuku: ${e.message}"
                Log.e("ShizukuHelper", errorMsg, e)
                _shizukuAlert.value = errorMsg
                onShizukuAlert?.invoke(errorMsg)
                return false
            }
        }

        return try {
            Shizuku.pingBinder()
        } catch (e: Throwable) {
            val errorMsg = "Erro ao verificar ativação do Shizuku: ${e.message}"
            Log.e("ShizukuHelper", errorMsg, e)
            _shizukuAlert.value = errorMsg
            onShizukuAlert?.invoke(errorMsg)
            false
        }
    }

    fun hasPermission(): Boolean {
        return if (isShizukuAvailable()) {
            try {
                if (!Shizuku.isPreV11()) {
                    Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                } else {
                    false
                }
            } catch (e: Throwable) {
                false
            }
        } else {
            false
        }
    }

    /**
     * Solicita permissão ao Shizuku. Retorna true se o pedido foi enviado com sucesso.
     */
    fun requestPermission(requestCode: Int = SHIZUKU_PERMISSION_REQUEST_CODE): Boolean {
        return try {
            if (isShizukuAvailable()) {
                if (!Shizuku.isPreV11()) {
                    if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                        updateStatus()
                        true
                    } else {
                        Shizuku.requestPermission(requestCode)
                        true
                    }
                } else {
                    false
                }
            } else {
                false
            }
        } catch (e: Throwable) {
            val errorMsg = "Erro ao solicitar permissão: ${e.message}"
            Log.e("ShizukuHelper", errorMsg, e)
            _shizukuAlert.value = errorMsg
            onShizukuAlert?.invoke(errorMsg)
            false
        }
    }

    /**
     * Tenta iniciar o serviço do Shizuku automaticamente no dispositivo.
     */
    fun startShizukuService(context: Context): String {
        appContext = context.applicationContext
        return try {
            val scriptPaths = listOf(
                "/storage/emulated/0/Android/data/moe.shizuku.privileged.api/files/start.sh",
                "/storage/emulated/0/Android/data/moe.shizuku.privileged.api/start.sh",
                "/sdcard/Android/data/moe.shizuku.privileged.api/start.sh",
                "/data/user_de/0/moe.shizuku.privileged.api/files/start.sh"
            )

            var executed = false
            var outputResult = ""

            if (isRootAvailable()) {
                for (path in scriptPaths) {
                    val f = java.io.File(path)
                    if (f.exists()) {
                        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "sh $path"))
                        val out = process.inputStream.bufferedReader().use { it.readText() }
                        val err = process.errorStream.bufferedReader().use { it.readText() }
                        process.waitFor()
                        outputResult = if (out.isNotEmpty()) out else err
                        executed = true
                        break
                    }
                }
                if (!executed) {
                    val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "sh /storage/emulated/0/Android/data/moe.shizuku.privileged.api/start.sh"))
                    outputResult = process.inputStream.bufferedReader().use { it.readText() }
                    executed = true
                }
            } else {
                for (path in scriptPaths) {
                    val f = java.io.File(path)
                    if (f.exists()) {
                        val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "sh $path"))
                        val out = process.inputStream.bufferedReader().use { it.readText() }
                        process.waitFor()
                        outputResult = out
                        executed = true
                        break
                    }
                }
            }

            if (!executed) {
                openShizukuApp(context)
                "Aplicativo Shizuku aberto. Certifique-se de iniciar o serviço nas Opções do Desenvolvedor."
            } else {
                updateStatus()
                outputResult.ifEmpty { "Comando de inicialização enviado ao Shizuku." }
            }
        } catch (e: Exception) {
            val errorMsg = "Erro ao iniciar Shizuku: ${e.message}"
            Log.e("ShizukuHelper", errorMsg, e)
            _shizukuAlert.value = errorMsg
            onShizukuAlert?.invoke(errorMsg)
            errorMsg
        }
    }

    /**
     * Abre diretamente o aplicativo Shizuku.
     */
    fun openShizukuApp(context: Context): Boolean {
        appContext = context.applicationContext
        return try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            val errorMsg = "Erro ao abrir o app Shizuku: ${e.message}"
            Log.e("ShizukuHelper", errorMsg, e)
            _shizukuAlert.value = errorMsg
            onShizukuAlert?.invoke(errorMsg)
            false
        }
    }

    fun registerPermissionListener(listener: Shizuku.OnRequestPermissionResultListener) {
        try {
            Shizuku.addRequestPermissionResultListener(listener)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    fun unregisterPermissionListener(listener: Shizuku.OnRequestPermissionResultListener) {
        try {
            Shizuku.removeRequestPermissionResultListener(listener)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    fun executeCommand(command: String): String {
        return try {
            val shizukuClass = Class.forName("rikka.shizuku.Shizuku")
            val newProcessMethod = shizukuClass.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
            newProcessMethod.isAccessible = true
            val process = newProcessMethod.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
            val stdOut = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            stdOut.trim()
        } catch (e: Exception) {
            val errorMsg = "Erro ao executar comando: ${e.message}"
            Log.e("ShizukuHelper", errorMsg, e)
            _shizukuAlert.value = errorMsg
            onShizukuAlert?.invoke(errorMsg)
            "Error: ${e.message}"
        }
    }

    fun isRootAvailable(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor() == 0 || output.contains("uid=0")
        } catch (e: Exception) {
            false
        }
    }
}