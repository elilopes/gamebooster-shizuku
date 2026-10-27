package com.gfx

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers

object ShellFunctions {

    private val _commandLog = MutableStateFlow<List<String>>(emptyList())
    val commandLog: StateFlow<List<String>> = _commandLog.asStateFlow()

    fun log(message: String) {
        runBlocking(Dispatchers.Default) {
            _commandLog.value = _commandLog.value + message
        }
    }

    fun clearLog() {
        runBlocking(Dispatchers.Default) {
            _commandLog.value = emptyList()
        }
    }

    // Apenas para comandos que NÃO exigem privilégios de sistema
    fun execute(command: String): String {
        log("$ $command")
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val error = process.errorStream.bufferedReader().use { it.readText() }
            process.waitFor()
            val result = if (output.isNotEmpty()) output.trim() else error.trim()
            if (result.isNotEmpty()) {
                log("> $result")
            }
            result
        } catch (e: Exception) {
            e.printStackTrace()
            log("> Error: [$command] ${e.message}")
            ""
        }
    }

    /**
     * Executa comandos privilegiados (como am force-stop, wm size, etc.)
     * REGRA: Exige obrigatoriamente Shizuku ativo ou Root.
     */
    fun executePrivileged(command: String): String {
        log("# $command")
        return try {
            // 1. Verifica rigorosamente se o Shizuku está rodando e tem permissão
            if (ShizukuHelper.hasPermission()) {
                val out = ShizukuHelper.executeCommand(command).trim()
                if (out.startsWith("Error", ignoreCase = true) || out.contains("Permission denied", ignoreCase = true)) {
                    // Se o Shizuku falhou mas há Root disponível, usa root como alternativa avançada
                    if (ShizukuHelper.isRootAvailable()) {
                        executeRoot(command)
                    } else {
                        log("> Error: [$command] Shizuku falhou ao executar o comando.")
                        "Error: [$command] Shizuku falhou ao executar o comando."
                    }
                } else {
                    if (out.isNotEmpty()) {
                        log("> $out")
                    }
                    out
                }
            } 
            // 2. Se o Shizuku não estiver ativo, mas o aparelho tiver Root, permite o root
            else if (ShizukuHelper.isRootAvailable()) {
                executeRoot(command)
            } 
            // 3. SE NENHUM DOS DOIS ESTIVER ATIVO: Bloqueia e impede o shell comum!
            else {
                log("> Error: [$command] Shizuku off, ative-o pra usar estas funções.")
                "Shizuku off, ative-o pra usar estas funções."
            }
        } catch (e: Exception) {
            log("> Error: [$command] ${e.message}")
            "Error: [$command] ${e.message}"
        }
    }

    fun executeRoot(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val err = process.errorStream.bufferedReader().use { it.readText() }
            process.waitFor()
            val result = if (output.isNotEmpty()) output.trim() else err.trim()
            if (result.isNotEmpty()) {
                log("> $result")
            }
            result
        } catch (e: Exception) {
            log("> Error: [$command] Root execution failed.")
            "Error: [$command] Root execution failed."
        }
    }

    fun getNativeResolution(): String {
        val output = executePrivileged("wm size")
        if (output.contains("Shizuku off") || output.contains("Error") || output.contains("falhou")) {
            return "SHIZUKU_OFF"
        }
        return output.substringAfter("Physical size: ").ifEmpty { 
            output.substringAfter("Override size: ").ifEmpty { "1080x1920" } 
        }.trim()
    }
    
    fun clearCache(packageName: String) {
        executePrivileged("rm -rf /data/data/$packageName/cache/*")
        executePrivileged("pm trim-caches 999G")
    }

    fun setOomImmortal(packageName: String) {
        executePrivileged("echo -1000 > /proc/$(pidof $packageName)/oom_score_adj")
    }

    fun getTemperature(): Float {
        var output = execute("cat /sys/class/thermal/thermal_zone0/temp")
        if (output.isEmpty()) {
            output = executePrivileged("cat /sys/class/thermal/thermal_zone0/temp")
        }
        val temp = output.toFloatOrNull() ?: 0f
        return if (temp > 1000) temp / 1000f else temp
    }
}

// Mantido para compatibilidade com classes existentes
typealias ShellHelper = ShellFunctions
