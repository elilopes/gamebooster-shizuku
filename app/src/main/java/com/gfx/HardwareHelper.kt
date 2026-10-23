package com.gfx

import android.content.Context
import android.os.Build
import android.os.Environment
import java.io.File
import java.io.FileWriter

object HardwareHelper {

    fun getHardwareInfo(context: Context): String {
        val sb = StringBuilder()
        sb.append("=== HARDWARE INFO REPORT ===\n")
        sb.append("Device Model: ${Build.MODEL}\n")
        sb.append("Manufacturer: ${Build.MANUFACTURER}\n")
        sb.append("Device Brand: ${Build.BRAND}\n")
        sb.append("Android Version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")
        sb.append("Hardware: ${Build.HARDWARE}\n")
        sb.append("Board: ${Build.BOARD}\n")
        sb.append("Product: ${Build.PRODUCT}\n")
        sb.append("CPU ABI: ${Build.SUPPORTED_ABIS.joinToString(", ")}\n")
        sb.append("CPU Cores: ${Runtime.getRuntime().availableProcessors()}\n")

        // Native Resolution & Density
        val res = ShellHelper.getNativeResolution()
        sb.append("Native Resolution: $res\n")
        val density = ShellHelper.execute("wm density").trim()
        sb.append("Display Density: $density\n")

        // CPU Info from proc/cpuinfo
        sb.append("\n--- CPU INFO (/proc/cpuinfo) ---\n")
        val cpuInfo = ShellHelper.execute("cat /proc/cpuinfo")
        sb.append(if (cpuInfo.isNotBlank()) cpuInfo else "Unavailable").append("\n")

        // GPU / Renderer & Platform Info
        sb.append("\n--- GPU & RENDERER INFO ---\n")
        val boardPlatform = ShellHelper.execute("getprop ro.board.platform").trim()
        val socModel = ShellHelper.execute("getprop ro.soc.model").trim()
        val hwEgl = ShellHelper.execute("getprop ro.hardware.egl").trim()
        val gpuNameProp = ShellHelper.execute("getprop ro.gpu.name").trim()
        
        sb.append("SOC Platform: ${socModel.ifEmpty { boardPlatform.ifEmpty { "Unknown" } }}\n")
        sb.append("GPU / EGL Hardware: ${gpuNameProp.ifEmpty { hwEgl.ifEmpty { "Adreno / Mali / PowerVR integrated" } }}\n")

        val gfxInfo = ShellHelper.execute("dumpsys SurfaceFlinger")
        if (gfxInfo.isNotBlank()) {
            val rendererLines = gfxInfo.lines().filter { 
                it.contains("OpenGL", ignoreCase = true) || 
                it.contains("Renderer", ignoreCase = true) || 
                it.contains("GLES", ignoreCase = true) || 
                it.contains("Adreno", ignoreCase = true) || 
                it.contains("Mali", ignoreCase = true) || 
                it.contains("PowerVR", ignoreCase = true) ||
                it.contains("Vulkan", ignoreCase = true)
            }
            if (rendererLines.isNotEmpty()) {
                rendererLines.forEach { sb.append("  $it\n") }
            } else {
                sb.append("SurfaceFlinger active (Hardware accelerated)\n")
            }
        } else {
            sb.append("GPU Renderer Info via dumpsys: Unavailable\n")
        }

        return sb.toString()
    }

    fun exportHardwareReport(context: Context): File? {
        return try {
            val report = getHardwareInfo(context)
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val file = File(downloadsDir, "GameBooster_Hardware_Report.txt")
            FileWriter(file).use { it.write(report) }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun calculateProportionalDpi(nativeRes: String, nativeDpi: Int, targetRes: String): Int {
        try {
            val nativeParts = nativeRes.lowercase().split("x")
            val targetParts = targetRes.lowercase().split("x")
            if (nativeParts.size == 2 && targetParts.size == 2) {
                val natW = nativeParts[0].trim().toFloatOrNull() ?: 1080f
                val natH = nativeParts[1].trim().toFloatOrNull() ?: 2400f
                val tgtW = targetParts[0].trim().toFloatOrNull() ?: 720f
                val tgtH = targetParts[1].trim().toFloatOrNull() ?: 1280f

                val scaleW = tgtW / natW
                val scaleH = tgtH / natH
                val scale = (scaleW + scaleH) / 2f
                return (nativeDpi * scale).toInt().coerceAtLeast(120)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return nativeDpi
    }
}
