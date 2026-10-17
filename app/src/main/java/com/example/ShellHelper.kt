package com.example

import java.io.BufferedReader
import java.io.InputStreamReader

object ShellHelper {

    fun execute(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(command)
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            process.waitFor()
            output.trim()
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    fun executePrivileged(command: String): String {
        // Fallback for demonstration: in a production Shizuku app, 
        // you would use Shizuku's specific binder interface.
        return execute(command)
    }

    fun getNativeResolution(): String {
        val output = execute("wm size")
        return output.substringAfter("Physical size: ").ifEmpty { 
            execute("wm size").substringAfter("Override size: ").ifEmpty { "Unknown" } 
        }
    }
    
    fun clearCache(packageName: String) {
        executePrivileged("rm -rf /data/data/$packageName/cache/*")
    }

    fun setOomImmortal(packageName: String) {
        executePrivileged("echo -1000 > /proc/$(pidof $packageName)/oom_score_adj")
    }

    fun getTemperature(): Float {
        // Simple fallback/demonstration reading
        val output = execute("cat /sys/class/thermal/thermal_zone0/temp")
        val temp = output.toFloatOrNull() ?: 0f
        return if (temp > 1000) temp / 1000f else temp
    }
}
