package com.gfx

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [PT-BR] Gerenciador e gravador de diagnósticos, capturas de falha (Crash Reporter) e Bug Reports.
 *         Funciona como uma alternativa autônoma e nativa ao Firebase Crashlytics para ambientes offline e com Shizuku/Root.
 *
 * [EN]    Manager and recorder for diagnostics, crash captures (Crash Reporter), and Bug Reports.
 *         Acts as a self-contained, native offline alternative to Firebase Crashlytics with Shizuku/Root support.
 */
object CrashReporterHelper {

    private const val TAG = "CrashReporter"
    private const val CRASH_PREFS = "crash_reporter_prefs"
    private const val KEY_LAST_CRASH = "key_last_crash_summary"
    private const val KEY_LAST_CRASH_TIME = "key_last_crash_time"

    private var appContext: Context? = null
    private var defaultExceptionHandler: Thread.UncaughtExceptionHandler? = null

    private val _crashHistory = MutableStateFlow<List<String>>(emptyList())
    val crashHistory: StateFlow<List<String>> = _crashHistory.asStateFlow()

    private val _lastCrashAlert = MutableStateFlow<String?>(null)
    val lastCrashAlert: StateFlow<String?> = _lastCrashAlert.asStateFlow()

    /**
     * [PT-BR] Handler global para corrotinas não tratadas para evitar encerramento forçado do app (force close).
     * [EN]    Global unhandled coroutine exception handler preventing app force close.
     */
    val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        recordException("CoroutineGlobal", "Exceção não capturada em Coroutine: ${throwable.message}", throwable)
    }

    /**
     * [PT-BR] Inicializa o monitoramento de falhas e registra o UncaughtExceptionHandler global.
     * [EN]    Initializes crash monitoring and registers the global UncaughtExceptionHandler.
     */
    fun init(context: Context) {
        val app = context.applicationContext
        appContext = app

        // Armazena o handler padrão prévio do Android
        if (defaultExceptionHandler == null) {
            defaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        }

        // Carrega alertas de falha anterior caso o aplicativo tenha encerrado abruptamente
        loadSavedCrashReports(app)

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                handleUncaughtCrash(app, thread, throwable)
            } catch (e: Throwable) {
                Log.e(TAG, "Falha ao gravar relatório de crash: ${e.message}", e)
            } finally {
                defaultExceptionHandler?.uncaughtException(thread, throwable)
            }
        }

        Log.d(TAG, "CrashReporterHelper inicializado com sucesso.")
    }

    /**
     * [PT-BR] Processa uma falha crítica fatal capturada no UncaughtExceptionHandler.
     */
    private fun handleUncaughtCrash(context: Context, thread: Thread, throwable: Throwable) {
        val timestamp = System.currentTimeMillis()
        val dateFormatted = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
        val stackTrace = getStackTraceString(throwable)
        val deviceInfo = buildDeviceSummary(context)

        val reportBuilder = StringBuilder()
        reportBuilder.append("====================================================\n")
        reportBuilder.append("RELATÓRIO DE CRASH / FORCE CLOSE - GAME BOOSTER\n")
        reportBuilder.append("====================================================\n")
        reportBuilder.append("Data/Hora: $dateFormatted\n")
        reportBuilder.append("Thread: ${thread.name} (ID: ${thread.id})\n")
        reportBuilder.append("Exceção: ${throwable.javaClass.name}\n")
        reportBuilder.append("Mensagem: ${throwable.message ?: "Sem mensagem"}\n\n")
        reportBuilder.append("--- DISPOSITIVO E AMBIENTE ---\n")
        reportBuilder.append(deviceInfo)
        reportBuilder.append("\n--- STACK TRACE COMPLETO ---\n")
        reportBuilder.append(stackTrace)
        reportBuilder.append("\n====================================================\n")

        val reportContent = reportBuilder.toString()
        Log.e(TAG, reportContent)

        // 1. Salva no SharedPreferences para alerta no próximo início
        val prefs = context.getSharedPreferences(CRASH_PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_LAST_CRASH, reportContent)
            .putLong(KEY_LAST_CRASH_TIME, timestamp)
            .apply()

        // 2. Salva em arquivo físico interno
        try {
            val crashDir = File(context.filesDir, "crash_reports")
            if (!crashDir.exists()) crashDir.mkdirs()

            val latestFile = File(crashDir, "crash_latest.txt")
            latestFile.writeText(reportContent)

            val timeStampFile = File(crashDir, "crash_${System.currentTimeMillis()}.txt")
            timeStampFile.writeText(reportContent)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao gravar arquivo de crash: ${e.message}")
        }
    }

    /**
     * [PT-BR] Carrega registros de falhas anteriores gravados no dispositivo.
     */
    private fun loadSavedCrashReports(context: Context) {
        try {
            val prefs = context.getSharedPreferences(CRASH_PREFS, Context.MODE_PRIVATE)
            val lastSummary = prefs.getString(KEY_LAST_CRASH, null)
            if (!lastSummary.isNullOrBlank()) {
                _lastCrashAlert.value = lastSummary
            }

            val crashDir = File(context.filesDir, "crash_reports")
            if (crashDir.exists() && crashDir.isDirectory) {
                val files = crashDir.listFiles { file -> file.extension.equals("txt", ignoreCase = true) }
                    ?.sortedByDescending { it.lastModified() }
                    ?: emptyList()
                val list = files.take(10).map { file ->
                    val date = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(file.lastModified()))
                    "[$date] ${file.name}:\n${file.readText()}"
                }
                _crashHistory.value = list
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao carregar histórico de crashes: ${e.message}")
        }
    }

    /**
     * [PT-BR] Limpa o histórico de falhas e o alerta de crash pendente.
     */
    fun clearCrashReports(context: Context) {
        try {
            val prefs = context.getSharedPreferences(CRASH_PREFS, Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
            _lastCrashAlert.value = null

            val crashDir = File(context.filesDir, "crash_reports")
            if (crashDir.exists() && crashDir.isDirectory) {
                crashDir.deleteRecursively()
            }
            _crashHistory.value = emptyList()
            ShizukuHelper.addLog("Histórico de relatórios de crash limpo com sucesso.")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao limpar relatórios de crash: ${e.message}")
        }
    }

    /**
     * [PT-BR] Registra uma exceção não fatal no log do sistema e no buffer de diagnósticos (equivalente a Crashlytics.recordException).
     * [EN]    Records a non-fatal exception into system logs and diagnostics buffer.
     */
    fun recordException(tag: String, message: String, throwable: Throwable? = null) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val trace = if (throwable != null) "\n${getStackTraceString(throwable)}" else ""
        val entry = "[$time] [EXCEÇÃO] [$tag] $message$trace"

        Log.e(tag, message, throwable)
        ShizukuHelper.addLog("[$tag] $message", isError = true)

        _crashHistory.value = (_crashHistory.value + entry).takeLast(20)
    }

    /**
     * [PT-BR] Captura linhas recentes do Logcat do sistema em tempo real.
     * [EN]    Captures real-time recent lines from the system Logcat.
     */
    fun captureLogcat(maxLines: Int = 120): String {
        val sb = StringBuilder()
        try {
            // Tenta obter logcat com privilégios via Shizuku/Root se disponível, ou processo local do app
            val privilegedOutput = ShizukuHelper.executeCommand("logcat -d -v threadtime -t $maxLines")
            if (privilegedOutput.isNotBlank() && !privilegedOutput.startsWith("Error")) {
                return privilegedOutput
            }

            // Fallback via processo padrão do Android
            val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "threadtime", "-t", maxLines.toString()))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                sb.append(line).append("\n")
            }
            reader.close()
            process.waitFor()
        } catch (e: Exception) {
            sb.append("Falha ao coletar Logcat do sistema: ${e.message}\n")
        }
        return if (sb.isNotEmpty()) sb.toString() else "Nenhum log retornado pelo Logcat do sistema."
    }

    /**
     * [PT-BR] Gera um Bug Report completo consolidado (Hardware, Sistema, Shizuku, Root, Logs e Logcat).
     * [EN]    Generates a full consolidated Bug Report (Hardware, System, Shizuku, Root, Logs, and Logcat).
     */
    fun generateFullBugReport(context: Context): String {
        val dateFormatted = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val sb = StringBuilder()

        sb.append("=================================================================\n")
        sb.append("                 RELATÓRIO DE DIAGNÓSTICO E BUG REPORT           \n")
        sb.append("                          HUB GAME BOOSTER                       \n")
        sb.append("=================================================================\n")
        sb.append("Gerado em: $dateFormatted\n\n")

        sb.append("1. INFORMAÇÕES DO DISPOSITIVO E HARDWARE\n")
        sb.append("-----------------------------------------------------------------\n")
        sb.append(buildDeviceSummary(context))
        sb.append("\n")

        sb.append("2. STATUS DE PRIVILÉGIOS E ACESSO ELEVADO\n")
        sb.append("-----------------------------------------------------------------\n")
        val isShizukuPerm = ShizukuHelper.hasPermission(context)
        val isShizukuBinder = ShizukuHelper.isBinderAlive.value
        val isRoot = ShizukuHelper.isRootAvailable()
        sb.append("Acesso Root Liberado: ").append(if (isRoot) "SIM (Concedido)" else "NÃO").append("\n")
        sb.append("Shizuku Binder Ativo: ").append(if (isShizukuBinder) "SIM" else "NÃO").append("\n")
        sb.append("Permissão Shizuku: ").append(if (isShizukuPerm) "CONCEDIDA" else "NEGADA / PENDENTE").append("\n")
        sb.append("Resolução Nativa (wm size): ").append(ShellFunctions.getNativeResolution()).append("\n")
        sb.append("Temperatura da Bateria/CPU: ").append(ShellFunctions.getTemperature()).append(" °C\n\n")

        sb.append("3. HISTÓRICO DE ALERTAS E LOGS DO CONSOLE\n")
        sb.append("-----------------------------------------------------------------\n")
        val consoleLogs = ShizukuHelper.logEntries.value
        if (consoleLogs.isEmpty()) {
            sb.append("Nenhum log registrado na sessão atual.\n")
        } else {
            consoleLogs.takeLast(50).forEach { sb.append(it).append("\n") }
        }
        sb.append("\n")

        val lastCrash = _lastCrashAlert.value
        if (!lastCrash.isNullOrBlank()) {
            sb.append("4. ÚLTIMO CRASH / FORCE CLOSE DETECTADO\n")
            sb.append("-----------------------------------------------------------------\n")
            sb.append(lastCrash).append("\n\n")
        }

        sb.append("5. AMOSTRA DO LOGCAT DO SISTEMA (ÚLTIMAS LINHAS)\n")
        sb.append("-----------------------------------------------------------------\n")
        sb.append(captureLogcat(80))
        sb.append("\n=================================================================\n")

        return sb.toString()
    }

    /**
     * [PT-BR] Salva o Bug Report em arquivo no armazenamento interno e retorna o File gerado.
     * [EN]    Saves the Bug Report to internal storage and returns the generated File.
     */
    fun exportBugReportToFile(context: Context): File? {
        return try {
            val report = generateFullBugReport(context)
            val reportsDir = File(context.cacheDir, "bug_reports")
            if (!reportsDir.exists()) reportsDir.mkdirs()

            val file = File(reportsDir, "bugreport_${System.currentTimeMillis()}.txt")
            file.writeText(report)
            file
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao exportar bug report para arquivo: ${e.message}")
            null
        }
    }

    /**
     * [PT-BR] Monta um resumo das especificações do dispositivo, memória e processador.
     */
    fun buildDeviceSummary(context: Context): String {
        val sb = StringBuilder()
        sb.append("Fabricante: ${Build.MANUFACTURER}\n")
        sb.append("Modelo: ${Build.MODEL} (${Build.PRODUCT})\n")
        sb.append("Dispositivo / Hardware: ${Build.DEVICE} / ${Build.HARDWARE}\n")
        sb.append("Versão Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
        sb.append("Build ID: ${Build.DISPLAY}\n")
        sb.append("ABIs Suportadas: ${Build.SUPPORTED_ABIS.joinToString(", ")}\n")

        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        if (actManager != null) {
            val memInfo = ActivityManager.MemoryInfo()
            actManager.getMemoryInfo(memInfo)
            val totalMb = memInfo.totalMem / (1024 * 1024)
            val availMb = memInfo.availMem / (1024 * 1024)
            val usedMb = totalMb - availMb
            sb.append("Memória RAM: ${usedMb}MB usados de ${totalMb}MB (Disponível: ${availMb}MB, LowMem: ${memInfo.lowMemory})\n")
        }

        try {
            val statFs = StatFs(Environment.getDataDirectory().path)
            val availStorageMb = (statFs.availableBlocksLong * statFs.blockSizeLong) / (1024 * 1024)
            val totalStorageMb = (statFs.blockCountLong * statFs.blockSizeLong) / (1024 * 1024)
            sb.append("Armazenamento Interno: ${availStorageMb}MB livres de ${totalStorageMb}MB\n")
        } catch (e: Exception) {
            // Ignora erro de consulta de disco
        }

        return sb.toString()
    }

    private fun getStackTraceString(throwable: Throwable): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        throwable.printStackTrace(pw)
        pw.flush()
        return sw.toString()
    }
}
