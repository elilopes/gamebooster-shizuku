package com.example

import java.io.BufferedReader
import java.io.InputStreamReader

object ShellHelper {

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

    fun executePrivileged(command: String): String {
        return if (ShizukuHelper.hasPermission()) {
            val out = ShizukuHelper.executeCommand(command).trim()
            if ((out.startsWith("Error:") || out.contains("Permission denied", ignoreCase = true)) && ShizukuHelper.isRootAvailable()) {
                executeRoot(command)
            } else {
                out
            }
        } else if (ShizukuHelper.isRootAvailable()) {
            executeRoot(command)
        } else {
            execute(command)
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
            execute(command)
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
