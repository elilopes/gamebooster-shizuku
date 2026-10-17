package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

object ImageOptimizerHelper {

    data class ImageInfo(
        val uri: Uri,
        val width: Int,
        val height: Int,
        val category: String,
        val sizeBytes: Long
    )

    fun inspectImage(context: Context, uri: Uri): ImageInfo? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
            val w = options.outWidth
            val h = options.outHeight
            if (w <= 0 || h <= 0) return null

            val category = when {
                w >= 3840 || h >= 2160 -> "4K Ultra HD"
                w >= 1920 || h >= 1080 -> "FHD (1080p)"
                w >= 1280 || h >= 720 -> "HD (720p)"
                else -> "SD (Standard)"
            }

            val sizeBytes = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use {
                it.length
            } ?: 0L

            ImageInfo(uri, w, h, category, sizeBytes)
        } catch (e: Exception) {
            null
        }
    }

    fun compressToSd(context: Context, uri: Uri, makePermanent: Boolean): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            val maxDim = 854
            val w = bitmap.width
            val h = bitmap.height
            val ratio = w.toFloat() / h.toFloat()
            
            val targetW: Int
            val targetH: Int
            if (ratio > 1) {
                targetW = maxDim
                targetH = (maxDim / ratio).toInt()
            } else {
                targetH = maxDim
                targetW = (maxDim * ratio).toInt()
            }

            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)

            if (makePermanent) {
                val backupDir = File(context.filesDir, "image_backups")
                if (!backupDir.exists()) backupDir.mkdirs()
                val originalFile = File(backupDir, "original_${System.currentTimeMillis()}.jpg")
                FileOutputStream(originalFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
            }

            val outputDir = File(context.filesDir, "optimized_images")
            if (!outputDir.exists()) outputDir.mkdirs()
            val outputFile = File(outputDir, "sd_${System.currentTimeMillis()}.jpg")
            FileOutputStream(outputFile).use { out ->
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }

            bitmap.recycle()
            scaledBitmap.recycle()
            outputFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun compactGameImages(packageName: String): Int {
        var count = 0
        try {
            val targetDir = File("/sdcard/Android/data/$packageName")
            if (targetDir.exists() && targetDir.isDirectory) {
                targetDir.walkTopDown().forEach { file ->
                    if (file.isFile && (file.extension.equals("png", true) || file.extension.equals("jpg", true) || file.extension.equals("jpeg", true))) {
                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(file.absolutePath, options)
                        if (options.outWidth >= 1280 || options.outHeight >= 720) {
                            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                            if (bitmap != null) {
                                val maxDim = 854
                                val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
                                val w = if (ratio > 1) maxDim else (maxDim * ratio).toInt()
                                val h = if (ratio > 1) (maxDim / ratio).toInt() else maxDim
                                val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
                                FileOutputStream(file).use { out ->
                                    scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
                                }
                                bitmap.recycle()
                                scaled.recycle()
                                count++
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return count
    }
}
