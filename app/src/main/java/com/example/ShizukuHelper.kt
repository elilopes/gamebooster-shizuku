package com.example

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

object ShizukuHelper {

    private const val SHIZUKU_PERMISSION_REQUEST_CODE = 1001

    fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Throwable) {
            false
        }
    }

    fun hasPermission(): Boolean {
        return if (isShizukuAvailable()) {
            try {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            } catch (e: Throwable) {
                false
            }
        } else {
            false
        }
    }

    fun requestPermission(requestCode: Int = SHIZUKU_PERMISSION_REQUEST_CODE) {
        if (isShizukuAvailable()) {
            try {
                Shizuku.requestPermission(requestCode)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
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
            val method = Shizuku::class.java.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
            method.isAccessible = true
            val process = method.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
            val stdOut = process.inputStream.bufferedReader().use { it.readText() }
            val stdErr = process.errorStream.bufferedReader().use { it.readText() }
            process.waitFor()
            val result = if (stdOut.isNotEmpty()) stdOut else stdErr
            result.trim()
        } catch (e: Throwable) {
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
