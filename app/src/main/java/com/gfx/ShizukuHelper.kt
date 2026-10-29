package com.gfx

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.IBinder
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.reflect.Method
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
        val infoMsg = "Função OnBinderReceivedListener foi chamada!"
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
            Log.d("ShizukuHelper", "$errorMsg: ${cause.message}")
            Log.d("ShizukuManager", "$errorMsg: ${cause.message}")
        } else {
            Log.d("ShizukuHelper", errorMsg)
            Log.d("ShizukuManager", errorMsg)
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
                    Log.d("ShizukuHelper", "Permissão do Shizuku concedida: $perm")
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
     * [PT-BR] Estado detalhado do Shizuku combinando instalação, status do serviço e permissão.
     * [EN] Detailed Shizuku status combining installation, service running status, and permission.
     */
    enum class ShizukuDetailedStatus {
        NOT_INSTALLED,
        INSTALLED_BUT_NOT_RUNNING,
        RUNNING_NOT_AUTHORIZED,
        RUNNING_AND_AUTHORIZED
    }

    /**
     * [PT-BR] Alias para verificar se o serviço do Shizuku está em execução no dispositivo.
     * Verifica via pingBinder oficial e fallbacks de provedor/reflexão/processo.
     *
     * [EN] Alias checking whether Shizuku service is running.
     * Checks via official pingBinder and provider/reflection/process fallbacks.
     */
    fun isShizukuRunning(context: Context? = appContext): Boolean {
        // 1. Verificação oficial via API pingBinder
        try {
            if (Shizuku.pingBinder()) {
                _isBinderAlive.value = true
                return true
            }
        } catch (e: Throwable) {
            // continua para outros métodos
        }

        // 2. Verificação via UID do Servidor Shizuku (0 = Root, 2000 = ADB)
        if (isShizukuRunningByUid()) {
            _isBinderAlive.value = true
            return true
        }

        // 3. Consulta ao BinderProvider do Shizuku (apenas se registrado no PackageManager)
        val ctx = context ?: appContext
        if (ctx != null) {
            val authorities = listOf(
                "moe.shizuku.privileged.api.provider.BinderProvider",
                "moe.shizuku.manager.provider.BinderProvider"
            )
            for (auth in authorities) {
                try {
                    val providerInfo = ctx.packageManager.resolveContentProvider(auth, 0)
                    if (providerInfo != null) {
                        val uri = Uri.parse("content://$auth")
                        val cursor = ctx.contentResolver.query(uri, null, null, null, null)
                        if (cursor != null) {
                            cursor.close()
                            _isBinderAlive.value = true
                            return true
                        }
                    }
                } catch (e: Throwable) {
                    // ignora falha de consulta
                }
            }
        }

        // 4. Fallback via reflexão de Binder interno
        if (isShizukuBinderAlive()) {
            _isBinderAlive.value = true
            return true
        }

        // 5. Fallback via pgrep/pidof
        if (isShizukuProcessRunning()) {
            _isBinderAlive.value = true
            return true
        }

        // 6. Fallback via ps | grep shizuku_server
        if (isShizukuRunningByPsGrep()) {
            _isBinderAlive.value = true
            return true
        }

        // 7. Fallback via service list
        if (isShizukuRunningByServiceList()) {
            _isBinderAlive.value = true
            return true
        }

        _isBinderAlive.value = false
        return false
    }

    /**
     * [PT-BR] Verifica se o aplicativo Shizuku está instalado de forma silenciosa sem alertas de erro na UI.
     * [EN] Quietly checks if Shizuku app is installed without error banners in the UI.
     */
    fun isShizukuInstalledQuiet(context: Context? = appContext): Boolean {
        val ctx = context ?: appContext ?: return false
        for (pkg in KNOWN_SHIZUKU_PACKAGES) {
            try {
                ctx.packageManager.getPackageInfo(pkg, 0)
                return true
            } catch (e: Exception) {
                // Tenta o próximo
            }
        }
        return false
    }

    /**
     * [PT-BR] Inicia a verificação completa do Shizuku emitindo o alerta solicitado e retornando o estado detalhado.
     * Dispara o alerta inicial "Verificando se shizuku está instalado" para a UI e logs.
     *
     * [EN] Starts complete verification of Shizuku emitting the requested alert and returning detailed state.
     * Dispatches the initial "Verificando se shizuku está instalado" alert to the UI and logs.
     */
    fun verifyShizukuInstallationAndState(context: Context, onFeedback: ((String, Boolean) -> Unit)? = null): ShizukuDetailedStatus {
        appContext = context.applicationContext
        
        // 1. Alerta obrigatório de início de verificação
        val checkingMsg = context.getString(R.string.checking_shizuku_installed)
        Log.d("ShizukuHelper", checkingMsg)
        addLog(checkingMsg, isError = false)
        onFeedback?.invoke(checkingMsg, false)

        // 2. Verifica se o pacote está instalado no Android
        val isInstalled = isShizukuInstalledQuiet(context)
        if (!isInstalled) {
            val notInstalledMsg = context.getString(R.string.shizuku_not_installed)
            Log.d("ShizukuHelper", notInstalledMsg)
            dispatchAlert(notInstalledMsg)
            onFeedback?.invoke(notInstalledMsg, true)
            return ShizukuDetailedStatus.NOT_INSTALLED
        }

        // 3. Atualiza o status de binder e permissão
        updateStatus()
        val isRunning = isShizukuRunning(context)

        if (!isRunning) {
            val notRunningMsg = context.getString(R.string.shizuku_installed_not_running)
            Log.d("ShizukuHelper", notRunningMsg)
            addLog(notRunningMsg, isError = false)
            onFeedback?.invoke(notRunningMsg, false)
            return ShizukuDetailedStatus.INSTALLED_BUT_NOT_RUNNING
        }

        // 4. Se o serviço estiver rodando, verifica se a permissão já foi dada
        val hasPerm = hasPermission(context, dispatchFeedback = false)
        return if (hasPerm) {
            val successMsg = context.getString(R.string.shizuku_running_authorized)
            Log.d("ShizukuHelper", successMsg)
            dispatchSuccess(successMsg)
            onFeedback?.invoke(successMsg, false)
            ShizukuDetailedStatus.RUNNING_AND_AUTHORIZED
        } else {
            val pendingMsg = context.getString(R.string.shizuku_installed_running)
            Log.d("ShizukuHelper", pendingMsg)
            dispatchSuccess(pendingMsg)
            onFeedback?.invoke(pendingMsg, false)
            ShizukuDetailedStatus.RUNNING_NOT_AUTHORIZED
        }
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
        return try {
            val available = Shizuku.pingBinder()
            if (available) {
                val infoMsg = "Shizuku ativado e respondendo, binder OK"
                val successMsg = "Shizuku ativado e respondendo com sucesso (pingBinder OK)."
                Log.d("ShizukuHelper", successMsg)
                dispatchSuccess(successMsg)
                true
            } else {
                val infoMsg = "Shizuku.pingBinder() indisponível: o serviço não ativo."
                Log.d("ShizukuHelper", infoMsg)
                addLog(infoMsg, isError = false)
                false
            }
        } catch (e: Throwable) {
            val infoMsg = "Shizuku.pingBinder() indisponível: ${e.message ?: "o serviço não ativo."}"
            Log.d("ShizukuHelper", infoMsg)
            addLog(infoMsg, isError = false)
            false
        }
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
            Log.d("ShizukuHelper", errorMsg)
            _shizukuAlert.value = errorMsg
            addLog(errorMsg, isError = true)
            onShizukuAlert?.invoke(errorMsg)
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
        Log.d("ShizukuHelper", errorMsg)
        _shizukuAlert.value = errorMsg
        addLog(errorMsg, isError = true)
        onShizukuAlert?.invoke(errorMsg)
        return false
    }

    /**
     * [PT-BR] Verifica se a permissão do Shizuku foi concedida para a aplicação.
     * Suporta o modelo tradicional pré-v11 via PackageManager ("moe.shizuku.manager.permission.API_V23")
     * e o modelo moderno (v11+) via Binder.
     * Quando [dispatchFeedback] for true, emite alerta de sucesso ou erro para o Log e interface.
     *
     * [EN] Checks if Shizuku permission has been granted to the application.
     * Supports legacy pre-v11 and modern v11+ binder checks.
     *
     * @param context Contexto opcional do Android (utiliza [appContext] se omitido).
     * @param dispatchFeedback Se deve emitir feedback visual/log na interface.
     * @return true se a permissão foi concedida, false caso contrário.
     */
    fun hasPermission(context: Context? = appContext, dispatchFeedback: Boolean = false): Boolean {
        val ctx = context ?: appContext
        if (!isShizukuRunning()) {
            if (dispatchFeedback) {
                val infoMsg = "Shizuku não está ativo ou em execução"
                val errorMsg = "Shizuku não está ativo ou em execução."
                Log.d("ShizukuManager", errorMsg)
                _shizukuAlert.value = errorMsg
                addLog(errorMsg, isError = true)
                onShizukuAlert?.invoke(errorMsg)
            }
            return false
        }

        val isPingOk = try { Shizuku.pingBinder() } catch (t: Throwable) { false }
        val granted = if (!isPingOk) {
            false
        } else if (try { Shizuku.isPreV11() } catch (e: Throwable) { false }) {
            // Modelo tradicional (pré-v11): verifica via PackageManager do Android
            try {
                if (ctx != null) {
                    ctx.checkSelfPermission("moe.shizuku.manager.permission.API_V23") == PackageManager.PERMISSION_GRANTED
                } else {
                    false
                }
            } catch (e: Exception) {
                Log.d("ShizukuManager", "Erro ao verificar permissão tradicional pré-v11: ${e.message}")
                false
            }
        } else {
            // Versões modernas (V11+) via Binder do Shizuku
            try {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            } catch (e: Throwable) {
                Log.d("ShizukuManager", "Erro ao verificar permissão do Shizuku: ${e.message}")
                false
            }
        }

        if (dispatchFeedback) {
            if (granted) {
                val successMsg = "Permissão do Shizuku verificada e concedida!"
                Log.d("ShizukuManager", successMsg)
                dispatchSuccess(successMsg)
            } else {
                val errorMsg = "Permissão do Shizuku não concedida ou negada."
                Log.d("ShizukuManager", errorMsg)
                _shizukuAlert.value = errorMsg
                addLog(errorMsg, isError = true)
                onShizukuAlert?.invoke(errorMsg)
            }
        }

        return granted
    }

    /**
     * [PT-BR] Forma alternativa 1: Verificação via Content Provider (Recomendada sem SDK).
     * O Shizuku utiliza um ContentProvider interno para comunicação rápida ("content://moe.shizuku.privileged.api.provider.BinderProvider").
     * Realiza uma consulta a esse provedor para inferir se o servidor está respondendo.
     * Emite alerta de sucesso ou erro para o Log e para a interface do aplicativo.
     *
     * [EN] Alternative method 1: Verification via Content Provider (Recommended without SDK).
     */
    fun isShizukuRunningProvider(context: Context? = appContext): Boolean {
        val ctx = context ?: appContext
        if (ctx == null) {
            val errorMsg = "Contexto indisponível para consulta via Content Provider."
            Log.d("ShizukuHelper", errorMsg)
            dispatchAlert(errorMsg)
            return false
        }
        return try {
            val auth = "moe.shizuku.privileged.api.provider.BinderProvider"
            val providerInfo = ctx.packageManager.resolveContentProvider(auth, 0)
            if (providerInfo == null) {
                val errorMsg = "Provedor Shizuku não registrado no sistema."
                Log.d("ShizukuHelper", errorMsg)
                dispatchAlert(errorMsg)
                false
            } else {
                val uri = Uri.parse("content://$auth")
                val cursor = ctx.contentResolver.query(uri, null, null, null, null)

                val running = if (cursor != null) {
                    cursor.close()
                    true // Se retornou um cursor válido, o serviço está de pé
                } else {
                    false
                }

                if (running) {
                    val successMsg = "Shizuku verificado com sucesso via Content Provider (BinderProvider ativo)!"
                    Log.d("ShizukuHelper", successMsg)
                    dispatchSuccess(successMsg)
                } else {
                    val errorMsg = "Shizuku não está respondendo via Content Provider (cursor nulo)."
                    Log.d("ShizukuHelper", errorMsg)
                    dispatchAlert(errorMsg)
                }
                running
            }
        } catch (e: Exception) {
            // Lança exceção se o provider não responder ou falhar
            val errorMsg = "Falha ao verificar Shizuku via Content Provider: ${e.message}"
            Log.d("ShizukuHelper", errorMsg)
            dispatchAlert(errorMsg, e)
            false
        }
    }

    /**
     * [PT-BR] Forma alternativa 2: Verificação via Reflection do Token (Uso do Binder Interno).
     * Caso queira verificar se o binder está ativo no sistema sem usar os métodos visíveis da API,
     * checa reflexivamente se a classe ShizukuService consegue dar um ping no Binder que gerencia a comunicação.
     * Emite alerta de sucesso ou erro para o Log e para a interface do aplicativo.
     *
     * [EN] Alternative method 2: Verification via Token Reflection (Internal Binder usage).
     */
    fun isShizukuBinderAlive(): Boolean {
        return try {
            // Tenta carregar a classe do ShizukuService em tempo de execução
            val clazz = Class.forName("rikka.shizuku.ShizukuService")
            val getBinderMethod: Method = clazz.getMethod("getBinder")
            val binder = getBinderMethod.invoke(null) as? IBinder

            // Verifica se o Binder existe e se a transação com ele está respondendo
            val alive = binder != null && binder.isBinderAlive

            if (alive) {
                val successMsg = "Shizuku verificado com sucesso via Reflection (Binder interno ativo e respondendo)!"
                Log.d("ShizukuHelper", successMsg)
                dispatchSuccess(successMsg)
            } else {
                val errorMsg = "Shizuku inativo via Reflection (Binder nulo ou não responsivo)."
                Log.d("ShizukuHelper", errorMsg)
                dispatchAlert(errorMsg)
            }
            alive
        } catch (e: Exception) {
            val errorMsg = "Falha ao verificar Shizuku via Reflection: ${e.message}"
            Log.d("ShizukuHelper", errorMsg)
            dispatchAlert(errorMsg, e)
            false
        }
    }

    /**
     * [PT-BR] Forma alternativa 3: Execução de Comando Shell (Método de Fallback).
     * Executa o comando para checar se o servidor do Shizuku (shizuku_server) está listado nos processos ativos via pgrep ou pidof.
     * Emite alerta de sucesso ou erro para o Log e para a interface do aplicativo.
     *
     * [EN] Alternative method 3: Shell Command Execution (Fallback method).
     */
    fun isShizukuProcessRunning(): Boolean {
        return try {
            var running = false
            var pid: String? = null

            // 1. Executa o comando pgrep para checar se o servidor do Shizuku está listado nos processos ativos
            try {
                val process = Runtime.getRuntime().exec(arrayOf("pgrep", "-f", "shizuku_server"))
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val output = reader.readLine()

                reader.close()
                process.destroy()

                if (!output.isNullOrBlank()) {
                    running = true
                    pid = output.trim()
                }
            } catch (e: Exception) {
                // pgrep pode não existir em alguns ambientes
            }

            // 2. Se não encontrou via pgrep, tenta via pidof como fallback adicional
            if (!running) {
                try {
                    val process = Runtime.getRuntime().exec(arrayOf("pidof", "shizuku_server"))
                    val reader = BufferedReader(InputStreamReader(process.inputStream))
                    val output = reader.readLine()

                    reader.close()
                    process.destroy()

                    if (!output.isNullOrBlank()) {
                        running = true
                        pid = output.trim()
                    }
                } catch (e: Exception) {
                    // pidof falhou
                }
            }

            // 3. Se pgrep e pidof não funcionarem, tenta ps | grep shizuku_server
            if (!running) {
                if (isShizukuRunningByPsGrep()) {
                    running = true
                    pid = "ps_grep"
                }
            }

            if (running) {
                val successMsg = "Servidor Shizuku ativo no sistema (identificação: $pid)!"
                Log.d("ShizukuHelper", successMsg)
                dispatchSuccess(successMsg)
            } else {
                val errorMsg = "Servidor do Shizuku não foi detectado em execução nos processos (shizuku_server inativo)."
                Log.d("ShizukuHelper", errorMsg)
                dispatchAlert(errorMsg)
            }
            running
        } catch (e: Exception) {
            val errorMsg = "Falha ao verificar processo do Shizuku via comando Shell: ${e.message}"
            Log.d("ShizukuHelper", errorMsg)
            dispatchAlert(errorMsg, e)
            false
        }
    }

    /**
     * [PT-BR] Processo de verificação do Shizuku ativo via UID: Shizuku.getUid() == 0 (Root) ou 2000 (ADB/Shell).
     * [EN] Verification of active Shizuku via UID: Shizuku.getUid() == 0 (Root) or 2000 (ADB/Shell).
     */
    fun isShizukuRunningByUid(): Boolean {
        return try {
            if (!Shizuku.pingBinder()) return false
            val uid = Shizuku.getUid()
            val running = (uid == 0 || uid == 2000)
            if (running) {
                val mode = if (uid == 0) "Root (uid=0)" else "ADB/Shell (uid=2000)"
                val successMsg = "Shizuku ativo verificado com sucesso via UID: $mode"
                Log.d("ShizukuHelper", successMsg)
                addLog(successMsg)
            }
            running
        } catch (e: Throwable) {
            Log.d("ShizukuHelper", "Falha ao verificar UID do Shizuku: ${e.message}")
            false
        }
    }

    /**
     * [PT-BR] Fallback de detecção de processo Shizuku via ps | grep shizuku_server.
     * [EN] Fallback Shizuku process detection via ps | grep shizuku_server.
     */
    fun isShizukuRunningByPsGrep(): Boolean {
        return try {
            val cmd = arrayOf("sh", "-c", "ps -A 2>/dev/null | grep -E 'shizuku_server|shizuku' || ps 2>/dev/null | grep -E 'shizuku_server|shizuku'")
            val process = Runtime.getRuntime().exec(cmd)
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            val found = output.contains("shizuku_server") || output.contains("moe.shizuku.privileged.api")
            if (found) {
                Log.d("ShizukuHelper", "Processo Shizuku detectado via ps | grep: ${output.lines().firstOrNull()?.trim()}")
                addLog("Processo Shizuku detectado via ps | grep")
            }
            found
        } catch (e: Throwable) {
            false
        }
    }

    /**
     * [PT-BR] Fallback de detecção de serviço do Shizuku via ServiceManager.
     * Utiliza 'service check' para consulta direta e segura ao ServiceManager pelo nome específico do serviço,
     * evitando varreduras cegas de listas inteiras que provocam erros nativos de Parcel com strings nulas em serviços restritos.
     *
     * [EN] Fallback Shizuku service detection via ServiceManager using safe 'service check'.
     */
    fun isShizukuRunningByServiceList(): Boolean {
        return try {
            val cmd = "service check moe.shizuku.privileged.api 2>/dev/null || service check moe.shizuku.manager 2>/dev/null"
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            val found = output.contains(": found") || (output.contains("found") && !output.contains("not found"))
            if (found) {
                Log.d("ShizukuHelper", "Serviço Shizuku localizado no ServiceManager via service check")
                addLog("Serviço Shizuku detectado via service check")
            }
            found
        } catch (e: Throwable) {
            false
        }
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
                    "Shizuku está instalado, mas o serviço não está ativo."
                } else {
                    "Shizuku não está instalado no dispositivo."
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
    /**
     * [PT-BR] Adiciona um listener para o evento de Binder recebido do Shizuku.
     * [EN] Adds a listener for the Shizuku Binder received event.
     */
    fun addBinderReceivedListener(listener: Shizuku.OnBinderReceivedListener) {
        try {
            Shizuku.addBinderReceivedListener(listener)
            addLog("Listener de Binder recebido registrado.")
        } catch (e: Throwable) {
            dispatchAlert("Erro ao registrar binderReceivedListener: ${e.message}", e)
        }
    }

    /**
     * [PT-BR] Remove o listener de Binder recebido do Shizuku.
     * [EN] Removes the Shizuku Binder received listener.
     */
    fun removeBinderReceivedListener(listener: Shizuku.OnBinderReceivedListener) {
        try {
            Shizuku.removeBinderReceivedListener(listener)
            addLog("Listener de Binder recebido desregistrado.")
        } catch (e: Throwable) {
            dispatchAlert("Erro ao remover binderReceivedListener: ${e.message}", e)
        }
    }

    /**
     * [PT-BR] Adiciona um listener para o evento de Binder desconectado/morto do Shizuku.
     * [EN] Adds a listener for the Shizuku Binder dead event.
     */
    fun addBinderDeadListener(listener: Shizuku.OnBinderDeadListener) {
        try {
            Shizuku.addBinderDeadListener(listener)
            addLog("Listener de Binder encerrado registrado.")
        } catch (e: Throwable) {
            dispatchAlert("Erro ao registrar binderDeadListener: ${e.message}", e)
        }
    }

    /**
     * [PT-BR] Remove o listener de Binder encerrado do Shizuku.
     * [EN] Removes the Shizuku Binder dead listener.
     */
    fun removeBinderDeadListener(listener: Shizuku.OnBinderDeadListener) {
        try {
            Shizuku.removeBinderDeadListener(listener)
            addLog("Listener de Binder encerrado desregistrado.")
        } catch (e: Throwable) {
            dispatchAlert("Erro ao remover binderDeadListener: ${e.message}", e)
        }
    }

    /**
     * Estrutura que encapsula o resultado de um teste detalhado de privilégio.
     */
    data class DiagnosticResult(
        val id: String,
        val title: String,
        val commandOrApi: String,
        val isSuccess: Boolean,
        val output: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    fun runTestPingBinder(): DiagnosticResult {
        val start = System.currentTimeMillis()
        return try {
            val alive = Shizuku.pingBinder()
            DiagnosticResult(
                id = "ping_binder",
                title = "Shizuku: Ping Binder",
                commandOrApi = "Shizuku.pingBinder()",
                isSuccess = alive,
                output = if (alive) {
                    "SUCCESS: Shizuku.pingBinder() retornou true.\nTempo de resposta: ${System.currentTimeMillis() - start}ms\nO servidor Shizuku está ativo e o canal IPC Binder está comunicando normalmente."
                } else {
                    "FAILURE: Shizuku.pingBinder() retornou false.\nO servidor Shizuku não está ativo no sistema ou o Binder ainda não foi recebido."
                }
            )
        } catch (e: Throwable) {
            DiagnosticResult(
                id = "ping_binder",
                title = "Shizuku: Ping Binder",
                commandOrApi = "Shizuku.pingBinder()",
                isSuccess = false,
                output = "EXCEPTION: ${e.javaClass.simpleName}: ${e.message}"
            )
        }
    }

    fun runTestGetUid(): DiagnosticResult {
        return try {
            if (!Shizuku.pingBinder()) {
                DiagnosticResult(
                    id = "get_uid",
                    title = "Shizuku: Servidor UID",
                    commandOrApi = "Shizuku.getUid()",
                    isSuccess = false,
                    output = "FAILURE: O Binder do Shizuku não está ativo (pingBinder=false). Não é possível obter o UID."
                )
            } else {
                val uid = Shizuku.getUid()
                val isPrivileged = (uid == 0 || uid == 2000)
                val role = when (uid) {
                    0 -> "Root (Superusuário uid=0)"
                    2000 -> "ADB / Shell (uid=2000)"
                    else -> "Outro usuário não padrão (uid=$uid)"
                }
                DiagnosticResult(
                    id = "get_uid",
                    title = "Shizuku: Servidor UID",
                    commandOrApi = "Shizuku.getUid()",
                    isSuccess = isPrivileged,
                    output = "RESULTADO:\n- UID retornado: $uid\n- Nível de Privilégio: $role\n- Verificação (uid == 0 || uid == 2000): $isPrivileged\nStatus: ${if (isPrivileged) "VÁLIDO (Servidor ativo com privilégios)" else "INVÁLIDO"}"
                )
            }
        } catch (e: Throwable) {
            DiagnosticResult(
                id = "get_uid",
                title = "Shizuku: Servidor UID",
                commandOrApi = "Shizuku.getUid()",
                isSuccess = false,
                output = "EXCEPTION: ${e.javaClass.simpleName}: ${e.message}"
            )
        }
    }

    fun runTestPermission(): DiagnosticResult {
        return try {
            if (!Shizuku.pingBinder()) {
                DiagnosticResult(
                    id = "perm_check",
                    title = "Shizuku: Permissão do App",
                    commandOrApi = "Shizuku.checkSelfPermission()",
                    isSuccess = false,
                    output = "FAILURE: Binder não recebido. Não é possível verificar permissão sem o serviço ativo."
                )
            } else {
                val code = Shizuku.checkSelfPermission()
                val granted = (code == PackageManager.PERMISSION_GRANTED)
                DiagnosticResult(
                    id = "perm_check",
                    title = "Shizuku: Permissão do App",
                    commandOrApi = "Shizuku.checkSelfPermission()",
                    isSuccess = granted,
                    output = if (granted) {
                        "SUCCESS: Permissão CONCEDIDA (Código: $code).\nEste aplicativo possui permissão ativa para executar comandos privilegiados."
                    } else {
                        "FAILURE: Permissão NEGADA / PENDENTE (Código: $code).\nO usuário precisa autorizar o aplicativo na tela do Shizuku Manager."
                    }
                )
            }
        } catch (e: Throwable) {
            DiagnosticResult(
                id = "perm_check",
                title = "Shizuku: Permissão do App",
                commandOrApi = "Shizuku.checkSelfPermission()",
                isSuccess = false,
                output = "EXCEPTION: ${e.javaClass.simpleName}: ${e.message}"
            )
        }
    }

    fun runTestPgrepPidof(): DiagnosticResult {
        return try {
            val cmd1 = "pgrep -f shizuku_server"
            val p1 = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd1))
            val out1 = p1.inputStream.bufferedReader().use { it.readText() }.trim()
            val code1 = p1.waitFor()

            val cmd2 = "pidof shizuku_server"
            val p2 = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd2))
            val out2 = p2.inputStream.bufferedReader().use { it.readText() }.trim()
            val code2 = p2.waitFor()

            val success = out1.isNotEmpty() || out2.isNotEmpty()
            val sb = StringBuilder()
            sb.append("COMANDO 1: $cmd1\n")
            sb.append("Saída: ${if (out1.isEmpty()) "(vazio)" else out1}\n")
            sb.append("Exit Code: $code1\n\n")
            sb.append("COMANDO 2: $cmd2\n")
            sb.append("Saída: ${if (out2.isEmpty()) "(vazio)" else out2}\n")
            sb.append("Exit Code: $code2\n\n")
            sb.append(if (success) "STATUS: Processo shizuku_server localizado com sucesso!" else "STATUS: Nenhum PID retornado por pgrep ou pidof.")

            DiagnosticResult(
                id = "pgrep_pidof",
                title = "Shizuku: pgrep & pidof",
                commandOrApi = "pgrep -f shizuku_server || pidof shizuku_server",
                isSuccess = success,
                output = sb.toString()
            )
        } catch (e: Throwable) {
            DiagnosticResult(
                id = "pgrep_pidof",
                title = "Shizuku: pgrep & pidof",
                commandOrApi = "pgrep -f shizuku_server",
                isSuccess = false,
                output = "EXCEPTION: ${e.javaClass.simpleName}: ${e.message}"
            )
        }
    }

    fun runTestPsGrep(): DiagnosticResult {
        return try {
            val cmd = "ps -A 2>/dev/null | grep -E 'shizuku_server|shizuku' || ps 2>/dev/null | grep -E 'shizuku_server|shizuku'"
            val p = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
            val out = p.inputStream.bufferedReader().use { it.readText() }.trim()
            val err = p.errorStream.bufferedReader().use { it.readText() }.trim()
            val code = p.waitFor()

            val success = out.contains("shizuku_server") || out.contains("moe.shizuku.privileged.api")
            val sb = StringBuilder()
            sb.append("COMANDO:\nps | grep shizuku_server\n\n")
            sb.append("EXIT CODE: $code\n\n")
            sb.append("SAÍDA PADRÃO (stdout):\n")
            sb.append(if (out.isEmpty()) "(Nenhum processo correspondente encontrado)\n" else "$out\n")
            if (err.isNotEmpty()) {
                sb.append("\nSAÍDA DE ERRO (stderr):\n$err\n")
            }
            sb.append("\nRESULTADO: ${if (success) "Processo shizuku_server ENCONTRADO na tabela de processos do Android!" else "Processo NÃO encontrado."}")

            DiagnosticResult(
                id = "ps_grep",
                title = "Shizuku: ps | grep shizuku_server",
                commandOrApi = "ps | grep shizuku_server",
                isSuccess = success,
                output = sb.toString()
            )
        } catch (e: Throwable) {
            DiagnosticResult(
                id = "ps_grep",
                title = "Shizuku: ps | grep shizuku_server",
                commandOrApi = "ps | grep shizuku_server",
                isSuccess = false,
                output = "EXCEPTION: ${e.javaClass.simpleName}: ${e.message}"
            )
        }
    }

    fun runTestServiceList(): DiagnosticResult {
        return try {
            val cmd = "service check moe.shizuku.privileged.api 2>/dev/null"
            val p = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
            val out = p.inputStream.bufferedReader().use { it.readText() }.trim()
            val code = p.waitFor()

            val success = out.contains(": found") || (out.contains("found") && !out.contains("not found"))
            val sb = StringBuilder()
            sb.append("COMANDO (service check):\nservice check moe.shizuku.privileged.api\n\n")
            sb.append("EXIT CODE: $code\n\n")
            sb.append("SAÍDA DO SERVICEMANAGER:\n")
            sb.append(if (out.isEmpty()) "(Nenhuma resposta do service check)\n" else "$out\n")
            sb.append("\nRESULTADO: ${if (success) "Serviço Shizuku ENCONTRADO e registrado no ServiceManager do Android!" else "Serviço NÃO encontrado no ServiceManager."}\n\n")
            sb.append("Nota: O comando 'service check' consulta diretamente o serviço desejado sem varrer serviços restritos por SELinux do sistema, garantindo compatibilidade e prevenindo leituras de strings nulas no Parcel.")

            DiagnosticResult(
                id = "service_list",
                title = "Shizuku: service check",
                commandOrApi = "service check moe.shizuku.privileged.api",
                isSuccess = success,
                output = sb.toString()
            )
        } catch (e: Throwable) {
            DiagnosticResult(
                id = "service_list",
                title = "Shizuku: service check",
                commandOrApi = "service check moe.shizuku.privileged.api",
                isSuccess = false,
                output = "EXCEPTION: ${e.javaClass.simpleName}: ${e.message}"
            )
        }
    }

    fun runTestContentProvider(context: Context): DiagnosticResult {
        return try {
            val auth = "moe.shizuku.privileged.api.provider.BinderProvider"
            val provider = context.packageManager.resolveContentProvider(auth, 0)
            val sb = StringBuilder()
            sb.append("CONSULTA PACKAGE MANAGER:\nresolveContentProvider(\"$auth\", 0)\n\n")
            if (provider != null) {
                sb.append("RESULTADO: Provedor encontrado!\n")
                sb.append("- Pacote: ${provider.packageName}\n")
                sb.append("- Autoridade: ${provider.authority}\n")
                sb.append("- Exportado: ${provider.exported}\n")
            } else {
                sb.append("RESULTADO: Provedor não registrado no sistema.\n(Normal no Android 11+ com Shizuku v11 via IPC Binder direto)")
            }

            DiagnosticResult(
                id = "content_provider",
                title = "Shizuku: ContentProvider Info",
                commandOrApi = "resolveContentProvider(\"$auth\", 0)",
                isSuccess = provider != null,
                output = sb.toString()
            )
        } catch (e: Throwable) {
            DiagnosticResult(
                id = "content_provider",
                title = "Shizuku: ContentProvider Info",
                commandOrApi = "resolveContentProvider",
                isSuccess = false,
                output = "EXCEPTION: ${e.javaClass.simpleName}: ${e.message}"
            )
        }
    }

    fun runTestRootBinary(): DiagnosticResult {
        return try {
            val paths = listOf(
                "/system/bin/su",
                "/system/xbin/su",
                "/sbin/su",
                "/vendor/bin/su",
                "/system/app/Superuser.apk"
            )
            val existing = paths.filter { java.io.File(it).exists() }

            val pWhich = Runtime.getRuntime().exec(arrayOf("sh", "-c", "which su"))
            val whichOut = pWhich.inputStream.bufferedReader().use { it.readText() }.trim()
            pWhich.waitFor()

            val success = existing.isNotEmpty() || whichOut.isNotEmpty()
            val sb = StringBuilder()
            sb.append("COMANDO: which su\n")
            sb.append("Saída: ${if (whichOut.isEmpty()) "(não encontrado no PATH)" else whichOut}\n\n")
            sb.append("BUSCA EM DIRETÓRIOS PADRÃO:\n")
            paths.forEach { path ->
                val exists = java.io.File(path).exists()
                sb.append("- $path: ${if (exists) "EXISTE" else "Não encontrado"}\n")
            }
            sb.append("\nRESULTADO: ${if (success) "Binário 'su' detectado no dispositivo!" else "Nenhum binário 'su' encontrado."}")

            DiagnosticResult(
                id = "root_binary",
                title = "Root: Verificação do Binário 'su'",
                commandOrApi = "which su & file inspection",
                isSuccess = success,
                output = sb.toString()
            )
        } catch (e: Throwable) {
            DiagnosticResult(
                id = "root_binary",
                title = "Root: Verificação do Binário 'su'",
                commandOrApi = "which su",
                isSuccess = false,
                output = "EXCEPTION: ${e.javaClass.simpleName}: ${e.message}"
            )
        }
    }

    fun runTestRootExecution(): DiagnosticResult {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val out = p.inputStream.bufferedReader().use { it.readText() }.trim()
            val err = p.errorStream.bufferedReader().use { it.readText() }.trim()
            val code = p.waitFor()

            val success = code == 0 && out.contains("uid=0")
            val sb = StringBuilder()
            sb.append("COMANDO EXECUTADO:\nsu -c id\n\n")
            sb.append("EXIT CODE: $code\n\n")
            sb.append("SAÍDA PADRÃO (stdout):\n${if (out.isEmpty()) "(vazio)" else out}\n")
            if (err.isNotEmpty()) {
                sb.append("\nSAÍDA DE ERRO (stderr):\n$err\n")
            }
            sb.append("\nRESULTADO: ${if (success) "ROOT LIBERADO! Execução com uid=0 confirmada." else "Acesso ROOT NÃO liberado ou negado pelo gerenciador."}")

            DiagnosticResult(
                id = "root_exec",
                title = "Root: Teste de Execução 'su -c id'",
                commandOrApi = "su -c id",
                isSuccess = success,
                output = sb.toString()
            )
        } catch (e: Throwable) {
            DiagnosticResult(
                id = "root_exec",
                title = "Root: Teste de Execução 'su -c id'",
                commandOrApi = "su -c id",
                isSuccess = false,
                output = "EXCEPTION: Falha ao invocar 'su': ${e.message}\n(Normal caso o dispositivo não possua Root)"
            )
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
     * [PT-BR] Verifica se o dispositivo possui acesso Root via com.topjohnwu.superuser.Shell.
     * [EN] Checks if device has root access via com.topjohnwu.superuser.Shell.
     */
    fun isRootAvailableByLibSu(): Boolean {
        return try {
            com.topjohnwu.superuser.Shell.getShell().isRoot
        } catch (e: Throwable) {
            false
        }
    }

    fun runTestLibSuShell(): DiagnosticResult {
        return try {
            val isRoot = com.topjohnwu.superuser.Shell.getShell().isRoot
            val status = com.topjohnwu.superuser.Shell.getShell().status
            val sb = StringBuilder()
            sb.append("BIBLIOTECA: com.topjohnwu.superuser.Shell\n")
            sb.append("Status do Shell libsu: $status\n")
            sb.append("É Root (isRoot): $isRoot\n\n")
            sb.append(if (isRoot) "RESULTADO: Acesso Root confirmado com sucesso via libsu Shell!" else "RESULTADO: libsu Shell inicializado, mas sem acesso root (status: $status).")

            DiagnosticResult(
                id = "libsu_shell",
                title = "Root: libsu Shell.getShell()",
                commandOrApi = "com.topjohnwu.superuser.Shell.getShell().isRoot",
                isSuccess = isRoot,
                output = sb.toString()
            )
        } catch (e: Throwable) {
            DiagnosticResult(
                id = "libsu_shell",
                title = "Root: libsu Shell.getShell()",
                commandOrApi = "com.topjohnwu.superuser.Shell",
                isSuccess = false,
                output = "EXCEPTION: ${e.javaClass.simpleName}: ${e.message}\n(A biblioteca libsu pode não estar inicializada ou o root não está disponível)"
            )
        }
    }

    fun runTestRootManagers(context: Context? = appContext): DiagnosticResult {
        val ctx = context ?: appContext
        return try {
            val rootManagers = listOf(
                "com.topjohnwu.magisk" to "Magisk",
                "me.weishu.kernelsu" to "KernelSU",
                "me.bmax.apatch" to "APatch",
                "eu.chainfire.supersu" to "SuperSU (legado)",
                "com.koushikdutta.superuser" to "Superuser (legado)"
            )

            val sb = StringBuilder()
            sb.append("LISTA DE GERENCIADORES ROOT VERIFICADOS:\n\n")
            var foundAny = false

            if (ctx != null) {
                for ((pkg, name) in rootManagers) {
                    val installed = try {
                        ctx.packageManager.getPackageInfo(pkg, 0)
                        true
                    } catch (e: Exception) {
                        false
                    }
                    if (installed) {
                        foundAny = true
                        sb.append("- [X] $name ($pkg): INSTALADO\n")
                    } else {
                        sb.append("- [ ] $name ($pkg): Não instalado\n")
                    }
                }
            } else {
                sb.append("Contexto nulo para verificar pacotes dos gerenciadores root.\n")
            }

            sb.append("\nRESULTADO: ${if (foundAny) "Pelo menos um gerenciador root foi encontrado instalado!" else "Nenhum gerenciador root conhecido foi encontrado no dispositivo."}")

            DiagnosticResult(
                id = "root_managers",
                title = "Root: Gerenciadores (Magisk/KernelSU/APatch)",
                commandOrApi = "PackageManager.getPackageInfo(rootManagers)",
                isSuccess = foundAny,
                output = sb.toString()
            )
        } catch (e: Throwable) {
            DiagnosticResult(
                id = "root_managers",
                title = "Root: Gerenciadores",
                commandOrApi = "rootManagers inspection",
                isSuccess = false,
                output = "EXCEPTION: ${e.javaClass.simpleName}: ${e.message}"
            )
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
            if (isRootAvailableByLibSu()) return true
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor() == 0 || output.contains("uid=0")
        } catch (e: Exception) {
            Log.d("ShizukuHelper", "Root (su) não disponível: ${e.message}")
            false
        }
    }

    /**
     * [PT-BR] Executa um novo processo privilegiado através da API do Shizuku com suporte a stdin/stdout/stderr.
     * Permite streaming direto de arquivos e APKs via processo com privilégios de Shell/ADB.
     *
     * [EN] Executes a new privileged process via Shizuku API with full stdin/stdout/stderr support.
     * Enables direct streaming of files and APKs via privileged Shell/ADB process.
     */
    fun newProcess(vararg cmd: String): Process {
        val m = Class.forName("rikka.shizuku.Shizuku").getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
        )
        m.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return m.invoke(null, arrayOf(*cmd), null, null) as Process
    }
}

