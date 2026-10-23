package com.gfx

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

/**
 * [PT-BR] Helper utilitário para gerenciamento do Shizuku no aplicativo.
 * Gerencia a conexão do binder, estado de permissão, execução de comandos com privilégios elevados,
 * alertas para a interface do usuário e verificações de disponibilidade/root.
 *
 * [EN] Utility helper for managing Shizuku in the application.
 * Manages binder connection, permission status, elevated privileged command execution,
 * UI alert dispatching, and availability/root checks.
 */
object ShizukuHelper {

    /**
     * [PT-BR] Código de solicitação padrão para permissões do Shizuku.
     * [EN] Default request code for Shizuku permission requests.
     */
    const val SHIZUKU_PERMISSION_REQUEST_CODE = 1001

    /**
     * [PT-BR] Pacotes conhecidos do aplicativo gerenciador Shizuku / Sui.
     * [EN] Known package names for the Shizuku / Sui manager application.
     */
    private val KNOWN_SHIZUKU_PACKAGES = listOf(
        "moe.shizuku.privileged.api",
        "moe.shizuku.manager",
        "rikka.shizuku",
        "rikka.shizuku.manager",
        "dev.rikka.shizuku"
    )

    /**
     * [PT-BR] Contexto de aplicação armazenado para checagem de pacotes e inicialização de intents.
     * [EN] Application context stored for package checks and launching intents.
     */
    private var appContext: Context? = null

    /**
     * [PT-BR] Listener opcional para notificação direta de alteração de status da permissão (true = concedida).
     * [EN] Optional listener for direct notification when permission status changes (true = granted).
     */
    var onShizukuStatusChanged: ((Boolean) -> Unit)? = null

    /**
     * [PT-BR] Callback para disparar mensagens de alerta/erro diretamente para a UI.
     * [EN] Callback to dispatch alert/error messages directly to the UI.
     */
    var onShizukuAlert: ((String) -> Unit)? = null

    /**
     * [PT-BR] Callback para disparar mensagens de sucesso diretamente para a UI.
     * [EN] Callback to dispatch success messages directly to the UI.
     */
    var onShizukuSuccess: ((String) -> Unit)? = null

    private val _shizukuAlert = MutableStateFlow<String?>(null)

    /**
     * [PT-BR] Flow observável contendo a última mensagem de alerta/erro do Shizuku.
     * [EN] Observable flow holding the latest Shizuku alert/error message.
     */
    val shizukuAlert: StateFlow<String?> = _shizukuAlert.asStateFlow()

    private val _shizukuSuccess = MutableStateFlow<String?>(null)

    /**
     * [PT-BR] Flow observável contendo a última mensagem de sucesso do Shizuku.
     * [EN] Observable flow holding the latest Shizuku success message.
     */
    val shizukuSuccess: StateFlow<String?> = _shizukuSuccess.asStateFlow()

    private val _logEntries = MutableStateFlow<List<String>>(emptyList())

    /**
     * [PT-BR] Histórico em memória dos logs do Shizuku e do sistema para exibição na UI.
     * [EN] In-memory history of Shizuku and system logs for UI display.
     */
    val logEntries: StateFlow<List<String>> = _logEntries.asStateFlow()

    private val _isShizukuGranted = MutableStateFlow(false)

    /**
     * [PT-BR] StateFlow observável pelo Jetpack Compose indicando se a permissão do Shizuku foi concedida.
     * [EN] Observable StateFlow for Jetpack Compose indicating whether Shizuku permission is granted.
     */
    val isShizukuGranted: StateFlow<Boolean> = _isShizukuGranted.asStateFlow()

    private val _isBinderAlive = MutableStateFlow(false)

    /**
     * [PT-BR] StateFlow indicando se o serviço do Shizuku está em execução no dispositivo (binder ativo).
     * [EN] StateFlow indicating whether the Shizuku service is currently running on the device (binder alive).
     */
    val isBinderAlive: StateFlow<Boolean> = _isBinderAlive.asStateFlow()

    /**
     * [PT-BR] Listener acionado quando a conexão binder com o serviço do Shizuku é estabelecida.
     * [EN] Listener triggered when binder connection with the Shizuku service is established.
     */
    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        Log.d("ShizukuHelper", "Função OnBinderReceivedListener foi chamada!")
        updateStatus()
    }

    /**
     * [PT-BR] Listener acionado quando o serviço do Shizuku é finalizado ou morre.
     * [EN] Listener triggered when the Shizuku service dies or terminates.
     */
    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        Log.d("ShizukuHelper", "OnBinderDeadListener chamado!")
        updateStatus()
    }

    /**
     * [PT-BR] Listener acionado quando o resultado do pedido de permissão do Shizuku é retornado.
     * [EN] Listener triggered when the Shizuku permission request result is returned.
     */
    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        Log.d("ShizukuHelper", "OnRequestPermissionResultListener chamado! Code: $requestCode, Result: $grantResult")
        updateStatus()
    }

    /**
     * [PT-BR] Registra uma linha no histórico de logs da aplicação.
     * [EN] Records a line in the application's log history.
     */
    fun addLog(msg: String, isError: Boolean = false) {
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        val level = if (isError) "ERRO" else "INFO"
        val entry = "[$time] [$level] $msg"
        _logEntries.value = (_logEntries.value + entry).takeLast(300)
    }

    /**
     * [PT-BR] Emite uma mensagem de erro/alerta para o Logcat, para a interface do usuário e para o buffer de logs.
     * [EN] Dispatches an error/alert message to Logcat, UI, and log buffer.
     */
    fun dispatchAlert(errorMsg: String, cause: Throwable? = null) {
        if (cause != null) {
            Log.e("ShizukuHelper", errorMsg, cause)
            Log.e("ShizukuManager", errorMsg, cause)
        } else {
            Log.e("ShizukuHelper", errorMsg)
            Log.e("ShizukuManager", errorMsg)
        }
        addLog(errorMsg, isError = true)
        _shizukuAlert.value = errorMsg
        onShizukuAlert?.invoke(errorMsg)
    }

    /**
     * [PT-BR] Emite uma mensagem de sucesso para o Logcat, para a interface do usuário e para o buffer de logs.
     * [EN] Dispatches a success message to Logcat, UI, and log buffer.
     */
    fun dispatchSuccess(successMsg: String) {
        Log.d("ShizukuHelper", successMsg)
        Log.d("ShizukuManager", successMsg)
        addLog(successMsg, isError = false)
        _shizukuSuccess.value = successMsg
        onShizukuSuccess?.invoke(successMsg)
    }

    /**
     * [PT-BR] Limpa o alerta de erro atual exibido na interface.
     * [EN] Clears the current alert displayed on the UI.
     */
    fun clearAlert() {
        _shizukuAlert.value = null
    }

    /**
     * [PT-BR] Limpa o alerta de sucesso atual exibido na interface.
     * [EN] Clears the current success message displayed on the UI.
     */
    fun clearSuccess() {
        _shizukuSuccess.value = null
    }

    /**
     * [PT-BR] Limpa o histórico de logs.
     * [EN] Clears the log history.
     */
    fun clearLogs() {
        _logEntries.value = emptyList()
    }

    /**
     * [PT-BR] Inicializa o ShizukuHelper registrando listeners sticky de binder e permissão.
     *
     * [EN] Initializes ShizukuHelper by registering sticky binder and permission listeners.
     *
     * @param context Contexto do Android para inicialização.
     */
    fun initialize(context: Context) {
        appContext = context.applicationContext
        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionListener)
            addLog("ShizukuHelper inicializado com sucesso.")
            updateStatus()
        } catch (e: Exception) {
            dispatchAlert("Falha na inicialização do Shizuku: ${e.message}", e)
        }
    }

    /**
     * [PT-BR] Atualiza o estado da conexão (binder) e da permissão do Shizuku.
     * Notifica os fluxos de estado e callbacks cadastrados.
     *
     * [EN] Updates Shizuku binder connection and permission status.
     * Notifies state flows and registered callbacks.
     */
    fun updateStatus() {
        Log.d("ShizukuHelper", "Atualizando status do Shizuku...")

        val available = try {
            Shizuku.pingBinder()
        } catch (e: Throwable) {
            false
        }
        Log.d("ShizukuHelper", "Shizuku disponível (binder ok): $available")

        _isBinderAlive.value = available

        val granted = if (available) {
            try {
                if (!Shizuku.isPreV11()) {
                    val perm = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                    Log.d("ShizukuHelper", "Permissão Shizuku concedida: $perm")
                    perm
                } else {
                    val perm = appContext?.let {
                        it.checkSelfPermission("moe.shizuku.manager.permission.API_V23") == PackageManager.PERMISSION_GRANTED
                    } ?: false
                    Log.d("ShizukuHelper", "Permissão Shizuku tradicional pré-V11: $perm")
                    perm
                }
            } catch (e: Throwable) {
                false
            }
        } else {
            false
        }

        _isShizukuGranted.value = granted
        onShizukuStatusChanged?.invoke(granted)
    }

    /**
     * [PT-BR] Alias para verificar se o serviço do Shizuku está em execução no dispositivo.
     *
     * [EN] Alias checking whether Shizuku service is running.
     */
    fun isShizukuRunning(): Boolean {
        return isShizukuAvailable()
    }

    /**
     * [PT-BR] Verifica se o serviço do Shizuku está ativo e responsivo no dispositivo (via IPC binder).
     * Emite alerta de sucesso ou de erro para o Log e para a interface do aplicativo.
     *
     * [EN] Checks whether the Shizuku service is active and responsive on the device (via IPC binder).
     *
     * @return true se o binder do Shizuku responder com sucesso ao ping, false caso contrário.
     */
    fun isShizukuAvailable(): Boolean {
        // 1. Primeira tentativa: Verificar se o binder já está respondendo normalmente
        try {
            val available = Shizuku.pingBinder()
            if (available) {
                val successMsg = "Shizuku ativado e respondendo com sucesso (pingBinder OK)."
                Log.d("ShizukuHelper", successMsg)
                addLog("Shizuku ativado e respondendo com sucesso (pingBinder OK).")
                dispatchSuccess(successMsg)
                return true
            }
        } catch (e: Throwable) {
            Log.d("ShizukuHelper", "Shizuku.pingBinder() indisponível na primeira tentativa: ${e.message}")
            addLog("Shizuku indisponível na primeira tentativa (pingBinder com falha): ${e.message}.")
        }

        // 2. Se falhou, aciona o Fallback de Recuperação Automática
        Log.w("ShizukuHelper", "Shizuku indisponível, tentando recuperação automática do serviço...")
        addLog("Shizuku indisponível, tentando recuperação automática do serviço")
        
        val ctx = appContext
        if (ctx != null) {
            // Verifica se está instalado antes de tentar iniciar
            if (isShizukuInstalled(ctx)) {
                try {
                    // Tenta iniciar o serviço de forma automática (via root ou script local)
                    val bootResult = startShizukuService(ctx)
                    Log.d("ShizukuHelper", "Resultado da recuperação automática: $bootResult")
                    addLog("Resultado da recuperação automática... $bootResult")

                    // Aguarda uma fração de segundo para o binder subir e testa novamente
                    Thread.sleep(500)

                    val retryAvailable = Shizuku.pingBinder()
                    if (retryAvailable) {
                        val recoverySuccessMsg = "Shizuku recuperado e ativado com sucesso via fallback automático!"
                        Log.d("ShizukuHelper", recoverySuccessMsg)
                        addLog("Shizuku recuperado e ativado com sucesso via fallback automático!")
                        dispatchSuccess(recoverySuccessMsg)
                        return true
                    }
                } catch (recoveryEx: Throwable) {
                    Log.e("ShizukuHelper", "Falha durante o processo de recuperação automática: ${recoveryEx.message}", recoveryEx)
                    addLog("Falha durante o processo de recuperação automática: ${recoveryEx.message}.")
                }
            } else {
                val installError = "Impossível recuperar: o aplicativo Shizuku não está instalado."
                Log.e("ShizukuHelper", installError)
                addLog("Impossível recuperar: o aplicativo Shizuku não está instalado.")
                dispatchAlert(installError)
                return false
            }
        }

        // 3. Se todas as tentativas de recuperação falharem, despacha o alerta final
        val finalErrorMsg = "Shizuku indisponível e falha na recuperação automática. Inicie manualmente."
        Log.w("ShizukuHelper", finalErrorMsg)
        dispatchAlert(finalErrorMsg)
        return false
    }

    /**
     * [PT-BR] Verifica se o aplicativo gerenciador do Shizuku está instalado no dispositivo.
     * Emite alerta de sucesso ou de erro para o Log e para a interface do aplicativo.
     *
     * [EN] Checks whether the Shizuku manager application is installed on the device.
     *
     * @param context Contexto opcional do Android (utiliza [appContext] se omitido).
     * @return true se pelo menos um dos pacotes conhecidos do Shizuku for encontrado.
     */
    fun isShizukuInstalled(context: Context? = appContext): Boolean {
        val ctx = context ?: appContext
        if (ctx == null) {
            val errorMsg = "Contexto não disponível para verificar instalação do Shizuku."
            Log.e("ShizukuHelper", errorMsg)
            dispatchAlert(errorMsg)
            return false
        }
        for (pkg in KNOWN_SHIZUKU_PACKAGES) {
            try {
                ctx.packageManager.getPackageInfo(pkg, 0)
                val successMsg = "Aplicativo Shizuku encontrado instalado ($pkg)!"
                Log.d("ShizukuHelper", successMsg)
                dispatchSuccess(successMsg)
                return true
            } catch (e: PackageManager.NameNotFoundException) {
                // Tenta o próximo pacote
            } catch (e: Throwable) {
                // Ignora exceções de consulta genéricas
            }
        }
        val errorMsg = "Aplicativo gerenciador Shizuku não foi encontrado instalado no dispositivo."
        Log.w("ShizukuHelper", errorMsg)
        dispatchAlert(errorMsg)
        return false
    }

    /**
     * [PT-BR] Verifica se a permissão do Shizuku foi concedida para a aplicação.
     * Suporta o modelo tradicional pré-v11 via PackageManager ("moe.shizuku.manager.permission.API_V23")
     * e o modelo moderno (v11+) via Binder.
     * Emite alerta de sucesso ou erro para o Log e para a interface do aplicativo.
     *
     * [EN] Checks if Shizuku permission has been granted to the application.
     * Supports legacy pre-v11 and modern v11+ binder checks.
     *
     * @param context Contexto opcional do Android (utiliza [appContext] se omitido).
     * @return true se a permissão foi concedida, false caso contrário.
     */
    fun hasPermission(context: Context? = appContext): Boolean {
        val ctx = context ?: appContext
        if (!isShizukuRunning()) {
            val errorMsg = "Shizuku não está ativo ou em execução."
            Log.w("ShizukuManager", errorMsg)
            dispatchAlert(errorMsg)
            return false
        }

        val granted = if (Shizuku.isPreV11()) {
            // Modelo tradicional (pré-v11): verifica via PackageManager do Android
            try {
                if (ctx != null) {
                    ctx.checkSelfPermission("moe.shizuku.manager.permission.API_V23") == PackageManager.PERMISSION_GRANTED
                } else {
                    false
                }
            } catch (e: Exception) {
                val errorMsg = "Erro ao verificar permissão tradicional pré-v11: ${e.message}"
                Log.e("ShizukuManager", errorMsg)
                dispatchAlert(errorMsg, e)
                false
            }
        } else {
            // Versões modernas (V11+) via Binder do Shizuku
            try {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            } catch (e: Throwable) {
                val errorMsg = "Erro ao verificar permissão do Shizuku: ${e.message}"
                Log.e("ShizukuManager", errorMsg)
                dispatchAlert(errorMsg, e)
                false
            }
        }

        if (granted) {
            val successMsg = "Permissão do Shizuku verificada e concedida!"
            Log.d("ShizukuManager", successMsg)
            dispatchSuccess(successMsg)
        } else {
            val errorMsg = "Permissão do Shizuku não concedida ou negada."
            Log.w("ShizukuManager", errorMsg)
            dispatchAlert(errorMsg)
        }

        return granted
    }

    /**
     * [PT-BR] Solicita permissão ao Shizuku caso o serviço esteja disponível.
     *
     * [EN] Requests Shizuku permission if the service is available.
     *
     * @param requestCode Código de requisição customizado ou [SHIZUKU_PERMISSION_REQUEST_CODE].
     * @return true se o pedido foi enviado com sucesso ou se já estava concedido; false em caso de falha.
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
                    dispatchAlert("A versão do Shizuku instalada é anterior à V11 e não é suportada.")
                    false
                }
            } else {
                val errorMsg = if (isShizukuInstalled()) {
                    "O aplicativo Shizuku está instalado, mas o serviço não está em execução."
                } else {
                    "O aplicativo Shizuku não está instalado no dispositivo."
                }
                dispatchAlert(errorMsg)
                false
            }
        } catch (e: Throwable) {
            dispatchAlert("Erro ao solicitar permissão ao Shizuku: ${e.message}", e)
            false
        }
    }

    /**
     * [PT-BR] Tenta iniciar o serviço do Shizuku automaticamente via Root ou executando scripts start.sh locais.
     * Caso não consiga, abre o aplicativo do Shizuku para inicialização manual.
     *
     * [EN] Attempts to start the Shizuku service automatically via Root or local start.sh scripts.
     * If execution is not possible, opens the Shizuku app for manual setup.
     *
     * @param context Contexto do Android para execução e inicialização do app.
     * @return Mensagem descrevendo o resultado da operação de inicialização.
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
                "Aplicativo Shizuku foi aberto, inicie o serviço deste aplicativo ou via Depuração por Wi-Fi ou USB."
            } else {
                updateStatus()
                outputResult.ifEmpty { "Comando de inicialização enviado ao Shizuku." }
            }
        } catch (e: Exception) {
            val errorMsg = "Erro ao iniciar serviço do Shizuku: ${e.message}"
            dispatchAlert(errorMsg, e)
            errorMsg
        }
    }

    /**
     * [PT-BR] Abre diretamente o aplicativo do Shizuku instalado no dispositivo.
     *
     * [EN] Opens the installed Shizuku application on the device directly.
     *
     * @param context Contexto do Android para inicializar a Activity do Shizuku.
     * @return true se a intent de inicialização do Shizuku foi encontrada e executada; false caso contrário.
     */
    fun openShizukuApp(context: Context): Boolean {
        appContext = context.applicationContext
        for (pkg in KNOWN_SHIZUKU_PACKAGES) {
            try {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    return true
                }
            } catch (e: Exception) {
                // Tenta o próximo pacote
            }
        }
        val errorMsg = "Não foi possível abrir o app Shizuku. Verifique se ele está instalado."
        dispatchAlert(errorMsg)
        return false
    }

    /**
     * [PT-BR] Registra um listener personalizado para resultados de solicitação de permissão do Shizuku.
     *
     * [EN] Registers a custom listener for Shizuku permission request results.
     *
     * @param listener Instância de [Shizuku.OnRequestPermissionResultListener].
     */
    fun registerPermissionListener(listener: Shizuku.OnRequestPermissionResultListener) {
        try {
            Shizuku.addRequestPermissionResultListener(listener)
        } catch (e: Throwable) {
            dispatchAlert("Erro ao registrar listener de permissão do Shizuku: ${e.message}", e)
        }
    }

    /**
     * [PT-BR] Remove o registro de um listener de resultado de permissão do Shizuku.
     *
     * [EN] Unregisters a Shizuku permission result listener.
     *
     * @param listener Instância de [Shizuku.OnRequestPermissionResultListener].
     */
    fun unregisterPermissionListener(listener: Shizuku.OnRequestPermissionResultListener) {
        try {
            Shizuku.removeRequestPermissionResultListener(listener)
        } catch (e: Throwable) {
            dispatchAlert("Erro ao remover listener de permissão do Shizuku: ${e.message}", e)
        }
    }

    /**
     * [PT-BR] Executa um comando de shell com privilégios do Shizuku via reflexão (`Shizuku.newProcess`).
     *
     * [EN] Executes a shell command with Shizuku privileges via reflection (`Shizuku.newProcess`).
     *
     * @param command Comando shell a ser executado (ex: "settings put global ...").
     * @return A saída padrão (stdout) recortada do comando ou mensagem de erro formatada em caso de falha.
     */
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
            val errorMsg = "Erro ao executar comando via Shizuku: ${e.message}"
            dispatchAlert(errorMsg, e)
            "Error: ${e.message}"
        }
    }

    /**
     * [PT-BR] Verifica se o dispositivo possui acesso Root disponível via executável `su`.
     *
     * [EN] Checks whether the device has Root access available via the `su` binary.
     *
     * @return true se o comando `su` for executado com sucesso e retornar UID 0; false caso contrário.
     */
    fun isRootAvailable(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor() == 0 || output.contains("uid=0")
        } catch (e: Exception) {
            Log.d("ShizukuHelper", "Root (su) não disponível: ${e.message}")
            false
        }
    }
}
