package com.example

import java.io.BufferedReader
import java.io.InputStreamReader

object ShellHelper {

// Apenas para comandos que NÃO exigem privilégios de sistema
    fun execute(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val error = process.errorStream.bufferedReader().use { it.readText() }
            process.waitFor()
            if (output.isNotEmpty()) output.trim() else error.trim()
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    /**
     * Executa comandos privilegiados (como am force-stop, wm size, etc.)
     * REGRA: Exige obrigatoriamente Shizuku ativo. NUNCA cai para o shell comum do app.
     */
    fun executePrivileged(command: String): String {
        return try {
            // 1. Verifica rigorosamente se o Shizuku está rodando e tem permissão
            if (ShizukuHelper.hasPermission()) {
                val out = ShizukuHelper.executeCommand(command).trim()
                if (out.startsWith("Error", ignoreCase = true) || out.contains("Permission denied", ignoreCase = true)) {
                    // Se o Shizuku falhou mas há Root disponível, usa root como alternativa avançada
                    if (ShizukuHelper.isRootAvailable()) {
                        executeRoot(command)
                    } else {
                        "Error: Shizuku falhou ao executar o comando."
                    }
                } else {
                    out
                }
            } 
            // 2. Se o Shizuku não estiver ativo, mas o aparelho tiver Root, permite o root
            else if (ShizukuHelper.isRootAvailable()) {
                executeRoot(command)
            } 
            // 3. SE NENHUM DOS DOIS ESTIVER ATIVO: Bloqueia e impede o shell comum!
            else {
                "Error: Shizuku desativado. É necessário ativar o Shizuku para forçar a parada de aplicativos."
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    fun executeRoot(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val err = process.errorStream.bufferedReader().use { it.readText() }
            process.waitFor()
            if (output.isNotEmpty()) output.trim() else err.trim()
        } catch (e: Exception) {
            "Error: Root execution failed."
        }
    }

    fun getNativeResolution(): String {
        var output = execute("wm size")
        if (output.isEmpty() || !output.contains("size")) {
            output = executePrivileged("wm size")
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
