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

    fun getNativeResolution(): String {
        val output = execute("wm size")
        // wm size output format: "Physical size: 1080x2400"
        return output.substringAfter("Physical size: ").ifEmpty { 
            execute("wm size").substringAfter("Override size: ").ifEmpty { "Unknown" } 
        }
    }
}
