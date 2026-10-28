package com.gfx

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * BackupApp
 * Módulo dedicado para gerenciamento de backup e restauração de aplicativos e jogos:
 * 1. Backup de APK, OBB e Dados (Data e Internal Data)
 * 2. Compressão opcional em formato ZIP
 * 3. Migração de arquivos OBB para armazenamento secundário / Archive
 * 4. Listagem e gerenciamento de arquivos de backup salvos
 * 5. Restauração de backups
 */
class BackupApp(
    private val context: Context,
    private val scope: CoroutineScope
) {

    /**
     * Retorna o diretório base para armazenamento de backups
     */
    fun getBackupDirectory(): File {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "GameBoosterBackups")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Retorna a lista de backups salvos no dispositivo
     */
    fun listSavedBackups(): List<File> {
        val dir = getBackupDirectory()
        return dir.listFiles()?.filter { it.isFile || it.isDirectory }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    /**
     * Exclui um arquivo ou pasta de backup
     */
    fun deleteBackup(file: File): Boolean {
        return try {
            if (file.isDirectory) file.deleteRecursively() else file.delete()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Compacta uma pasta em arquivo ZIP usando API Java padrão (não requer binário externo ou Shizuku)
     */
    private fun zipFolder(sourceDir: File, zipFile: File) {
        java.util.zip.ZipOutputStream(java.io.FileOutputStream(zipFile)).use { zos ->
            sourceDir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    val relativePath = file.relativeTo(sourceDir).path.replace('\\', '/')
                    val entry = java.util.zip.ZipEntry(relativePath)
                    zos.putNextEntry(entry)
                    file.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        }
    }

    /**
     * Executa o backup de um aplicativo selecionado.
     * Não bloqueia caso o Shizuku não esteja em execução:
     * O APK é copiado diretamente via PackageManager e sistema de arquivos.
     * Se privilégios (Shizuku/Root) estiverem presentes, copia também dados protegidos.
     */
    fun backupApp(
        packageName: String,
        includeApk: Boolean,
        includeObb: Boolean,
        includeData: Boolean,
        compressZip: Boolean,
        onResult: (Boolean, String) -> Unit
    ) {
        scope.launch(Dispatchers.IO) {
            try {
                val hasPriv = ShizukuHelper.hasPermission() || ShizukuHelper.isRootAvailable()

                val backupDir = getBackupDirectory()
                val timestamp = System.currentTimeMillis()
                val targetFolder = File(backupDir, "${packageName}_$timestamp")
                targetFolder.mkdirs()

                var filesCopied = 0

                // 1. Backup do APK: acessível diretamente via PackageManager do Android sem necessidade de Shizuku
                if (includeApk) {
                    var apkCopied = false
                    try {
                        val pm = context.packageManager
                        val appInfo = pm.getApplicationInfo(packageName, 0)
                        val baseApk = File(appInfo.sourceDir)
                        if (baseApk.exists()) {
                            baseApk.copyTo(File(targetFolder, "base.apk"), overwrite = true)
                            filesCopied++
                            apkCopied = true
                        }
                        appInfo.splitSourceDirs?.forEachIndexed { index, splitPath ->
                            val splitFile = File(splitPath)
                            if (splitFile.exists()) {
                                splitFile.copyTo(File(targetFolder, "split_$index.apk"), overwrite = true)
                                filesCopied++
                            }
                        }
                    } catch (e: Throwable) {
                        // Fallback caso getApplicationInfo lance exceção
                    }

                    // Fallback via comando privilegiado se disponível e o método direto não copiou
                    if (!apkCopied && hasPriv) {
                        try {
                            val apkPathCmd = "pm path $packageName"
                            val apkPathOut = ShellFunctions.executePrivileged(apkPathCmd).trim()
                            val paths = apkPathOut.lines().map { it.removePrefix("package:").trim() }.filter { it.isNotEmpty() }
                            for ((index, path) in paths.withIndex()) {
                                val destName = if (paths.size == 1) "base.apk" else "base_$index.apk"
                                ShellFunctions.executePrivileged("cp \"$path\" \"${targetFolder.absolutePath}/$destName\"")
                                filesCopied++
                            }
                        } catch (e: Throwable) {
                            // Ignora erro do shell
                        }
                    }
                }

                // 2. Backup de OBB
                if (includeObb) {
                    val obbDir = File("/sdcard/Android/obb/$packageName")
                    if (obbDir.exists()) {
                        if (hasPriv) {
                            ShellFunctions.executePrivileged("cp -r \"${obbDir.absolutePath}\" \"${targetFolder.absolutePath}/obb\"")
                            filesCopied++
                        } else {
                            try {
                                val destObb = File(targetFolder, "obb")
                                obbDir.copyRecursively(destObb, overwrite = true)
                                filesCopied++
                            } catch (e: Throwable) {
                                // O Android 11+ pode limitar acesso direto a /Android/obb sem SAF ou root
                            }
                        }
                    }
                }

                // 3. Backup de Dados (Data e Internal Data)
                if (includeData) {
                    val sdDataDir = File("/sdcard/Android/data/$packageName")
                    if (sdDataDir.exists()) {
                        if (hasPriv) {
                            ShellFunctions.executePrivileged("cp -r \"${sdDataDir.absolutePath}\" \"${targetFolder.absolutePath}/data\"")
                            filesCopied++
                        } else {
                            try {
                                val destData = File(targetFolder, "data")
                                sdDataDir.copyRecursively(destData, overwrite = true)
                                filesCopied++
                            } catch (e: Throwable) {
                                // ignora se não tiver acesso
                            }
                        }
                    }
                    val internalDataDir = File("/data/data/$packageName")
                    if (internalDataDir.exists() && hasPriv) {
                        try {
                            ShellFunctions.executePrivileged("cp -r \"${internalDataDir.absolutePath}\" \"${targetFolder.absolutePath}/internal_data\"")
                            filesCopied++
                        } catch (e: Throwable) {
                            // ignora se falhar
                        }
                    }
                }

                var finalPath = targetFolder.absolutePath
                if (compressZip) {
                    val zipFile = File(backupDir, "${packageName}_$timestamp.zip")
                    var zipSuccess = false
                    try {
                        zipFolder(targetFolder, zipFile)
                        targetFolder.deleteRecursively()
                        finalPath = zipFile.absolutePath
                        zipSuccess = true
                    } catch (e: Throwable) {
                        if (hasPriv) {
                            try {
                                val zipCmd = "cd \"${targetFolder.absolutePath}\" && zip -r \"${zipFile.absolutePath}\" ."
                                ShellFunctions.executePrivileged(zipCmd)
                                targetFolder.deleteRecursively()
                                finalPath = zipFile.absolutePath
                                zipSuccess = true
                            } catch (e2: Throwable) {
                                // mantém pasta original se zip falhar
                            }
                        }
                    }
                }

                val savedFile = File(finalPath)
                val hasResult = if (savedFile.isDirectory) {
                    (savedFile.listFiles()?.size ?: 0) > 0
                } else {
                    savedFile.exists() && savedFile.length() > 0
                }

                withContext(Dispatchers.Main) {
                    if (hasResult) {
                        onResult(true, "Backup salvo com sucesso em: $finalPath")
                    } else {
                        onResult(false, "Nenhum arquivo pôde ser copiado para o backup.")
                    }
                }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) {
                    onResult(false, "Falha ao realizar backup: ${e.message}")
                }
            }
        }
    }

    /**
     * Move os arquivos OBB de um jogo para o armazenamento secundário (Archive)
     */
    fun moveObbToSd(packageName: String, onResult: (Boolean, String) -> Unit) {
        scope.launch(Dispatchers.IO) {
            try {
                val hasPriv = ShizukuHelper.hasPermission() || ShizukuHelper.isRootAvailable()
                if (!hasPriv) {
                    withContext(Dispatchers.Main) {
                        onResult(false, "Permissão Shizuku ou Root necessária para mover OBB.")
                    }
                    return@launch
                }
                val source = "/sdcard/Android/obb/$packageName"
                val target = "/sdcard/Android/obb/Archive/$packageName"
                ShellFunctions.executePrivileged("mkdir -p /sdcard/Android/obb/Archive")
                ShellFunctions.executePrivileged("mv $source $target")
                withContext(Dispatchers.Main) {
                    onResult(true, "OBB movido para Archive com sucesso.")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(false, "Falha ao mover OBB: ${e.message}")
                }
            }
        }
    }

    /**
     * Restaura um aplicativo a partir de um backup (ZIP ou pasta)
     */
    fun restoreBackup(backupFile: File, packageName: String, onResult: (Boolean, String) -> Unit) {
        scope.launch(Dispatchers.IO) {
            try {
                val hasPriv = ShizukuHelper.hasPermission() || ShizukuHelper.isRootAvailable()
                if (!hasPriv) {
                    withContext(Dispatchers.Main) {
                        onResult(false, "Permissão Shizuku ou Root necessária para restauração.")
                    }
                    return@launch
                }

                if (!backupFile.exists()) {
                    withContext(Dispatchers.Main) {
                        onResult(false, "Arquivo de backup não encontrado.")
                    }
                    return@launch
                }

                val tempExtract = File(context.cacheDir, "restore_${System.currentTimeMillis()}")
                tempExtract.mkdirs()

                if (backupFile.name.endsWith(".zip")) {
                    ShellFunctions.executePrivileged("unzip -q -o \"${backupFile.absolutePath}\" -d \"${tempExtract.absolutePath}\"")
                } else if (backupFile.isDirectory) {
                    ShellFunctions.executePrivileged("cp -r \"${backupFile.absolutePath}/.\" \"${tempExtract.absolutePath}/\"")
                }

                // 1. Instalar APKs se existirem
                val apks = tempExtract.listFiles { f -> f.name.endsWith(".apk") } ?: emptyArray()
                if (apks.isNotEmpty()) {
                    if (apks.size == 1) {
                        ShellFunctions.executePrivileged("pm install -r \"${apks[0].absolutePath}\"")
                    } else {
                        val allApkPaths = apks.joinToString(" ") { "\"${it.absolutePath}\"" }
                        ShellFunctions.executePrivileged("pm install-multiple -r $allApkPaths")
                    }
                }

                // 2. Restaurar OBB se existir
                val obbFolder = File(tempExtract, "obb")
                if (obbFolder.exists()) {
                    ShellFunctions.executePrivileged("mkdir -p /sdcard/Android/obb/$packageName")
                    ShellFunctions.executePrivileged("cp -r \"${obbFolder.absolutePath}/.\" \"/sdcard/Android/obb/$packageName/\"")
                }

                // 3. Restaurar dados se existirem
                val dataFolder = File(tempExtract, "data")
                if (dataFolder.exists()) {
                    ShellFunctions.executePrivileged("mkdir -p /sdcard/Android/data/$packageName")
                    ShellFunctions.executePrivileged("cp -r \"${dataFolder.absolutePath}/.\" \"/sdcard/Android/data/$packageName/\"")
                }

                val internalDataFolder = File(tempExtract, "internal_data")
                if (internalDataFolder.exists()) {
                    ShellFunctions.executePrivileged("cp -r \"${internalDataFolder.absolutePath}/.\" \"/data/data/$packageName/\"")
                }

                tempExtract.deleteRecursively()

                withContext(Dispatchers.Main) {
                    onResult(true, "Restauração do backup concluída com sucesso!")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(false, "Erro ao restaurar backup: ${e.message}")
                }
            }
        }
    }

    /**
     * Restaura seletivamente partes específicas de um backup (APK, OBB, Data)
     */
    fun restoreSpecific(backupFile: File, packageName: String, restoreApk: Boolean, restoreObb: Boolean, restoreData: Boolean, onResult: (Boolean, String) -> Unit) {
        scope.launch(Dispatchers.IO) {
            try {
                val hasPriv = ShizukuHelper.hasPermission() || ShizukuHelper.isRootAvailable()
                if (!hasPriv) {
                    withContext(Dispatchers.Main) {
                        onResult(false, "Permissão Shizuku ou Root necessária para restauração.")
                    }
                    return@launch
                }

                if (!backupFile.exists()) {
                    withContext(Dispatchers.Main) {
                        onResult(false, "Arquivo de backup não encontrado.")
                    }
                    return@launch
                }

                val tempExtract = File(context.cacheDir, "restore_${System.currentTimeMillis()}")
                tempExtract.mkdirs()

                if (backupFile.name.endsWith(".zip")) {
                    ShellFunctions.executePrivileged("unzip -q -o \"${backupFile.absolutePath}\" -d \"${tempExtract.absolutePath}\"")
                } else if (backupFile.isDirectory) {
                    ShellFunctions.executePrivileged("cp -r \"${backupFile.absolutePath}/.\" \"${tempExtract.absolutePath}/\"")
                }

                if (restoreApk) {
                    val apks = tempExtract.listFiles { f -> f.name.endsWith(".apk") } ?: emptyArray()
                    if (apks.isNotEmpty()) {
                        if (apks.size == 1) {
                            ShellFunctions.executePrivileged("pm install -r \"${apks[0].absolutePath}\"")
                        } else {
                            val allApkPaths = apks.joinToString(" ") { "\"${it.absolutePath}\"" }
                            ShellFunctions.executePrivileged("pm install-multiple -r $allApkPaths")
                        }
                    }
                }

                if (restoreObb) {
                    val obbFolder = File(tempExtract, "obb")
                    if (obbFolder.exists()) {
                        ShellFunctions.executePrivileged("mkdir -p /sdcard/Android/obb/$packageName")
                        ShellFunctions.executePrivileged("cp -r \"${obbFolder.absolutePath}/.\" \"/sdcard/Android/obb/$packageName/\"")
                    }
                }

                if (restoreData) {
                    val dataFolder = File(tempExtract, "data")
                    if (dataFolder.exists()) {
                        ShellFunctions.executePrivileged("mkdir -p /sdcard/Android/data/$packageName")
                        ShellFunctions.executePrivileged("cp -r \"${dataFolder.absolutePath}/.\" \"/sdcard/Android/data/$packageName/\"")
                    }
                    val internalDataFolder = File(tempExtract, "internal_data")
                    if (internalDataFolder.exists()) {
                        ShellFunctions.executePrivileged("cp -r \"${internalDataFolder.absolutePath}/.\" \"/data/data/$packageName/\"")
                    }
                }

                tempExtract.deleteRecursively()

                withContext(Dispatchers.Main) {
                    onResult(true, "Restauração concluída com sucesso!")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(false, "Erro ao restaurar: ${e.message}")
                }
            }
        }
    }

    /**
     * Extrai e copia o APK principal de um backup diretamente para o OutputStream do SAF
     */
    fun exportApkToStream(backupFile: File, outputStream: java.io.OutputStream): Boolean {
        return try {
            if (backupFile.name.endsWith(".apk", ignoreCase = true)) {
                backupFile.inputStream().use { it.copyTo(outputStream) }
                true
            } else if (backupFile.name.endsWith(".zip", ignoreCase = true)) {
                java.util.zip.ZipFile(backupFile).use { zip ->
                    val apkEntry = zip.entries().asSequence().firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                    if (apkEntry != null) {
                        zip.getInputStream(apkEntry).use { it.copyTo(outputStream) }
                        true
                    } else {
                        false
                    }
                }
            } else if (backupFile.isDirectory) {
                val apk = backupFile.walkTopDown().firstOrNull { it.isFile && it.name.endsWith(".apk", ignoreCase = true) }
                if (apk != null) {
                    apk.inputStream().use { it.copyTo(outputStream) }
                    true
                } else {
                    false
                }
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Copia o arquivo completo de backup para o OutputStream do SAF
     */
    fun exportBackupToStream(backupFile: File, outputStream: java.io.OutputStream): Boolean {
        return try {
            if (backupFile.isFile) {
                backupFile.inputStream().use { it.copyTo(outputStream) }
                true
            } else if (backupFile.isDirectory) {
                java.util.zip.ZipOutputStream(outputStream).use { zos ->
                    backupFile.walkTopDown().forEach { file ->
                        if (file.isFile) {
                            val rel = file.relativeTo(backupFile).path.replace('\\', '/')
                            zos.putNextEntry(java.util.zip.ZipEntry(rel))
                            file.inputStream().use { it.copyTo(zos) }
                            zos.closeEntry()
                        }
                    }
                }
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Reinstala apenas o APK contido no backup com substituição forçada (-r)
     */
    fun reinstallApkFromBackup(backupFile: File, packageName: String, onResult: (Boolean, String) -> Unit) {
        scope.launch(Dispatchers.IO) {
            try {
                val tempExtract = File(context.cacheDir, "reinstall_${System.currentTimeMillis()}")
                tempExtract.mkdirs()

                if (backupFile.name.endsWith(".zip", ignoreCase = true)) {
                    java.util.zip.ZipFile(backupFile).use { zip ->
                        for (entry in zip.entries().asSequence()) {
                            if (entry.name.endsWith(".apk", ignoreCase = true)) {
                                val outFile = File(tempExtract, File(entry.name).name)
                                zip.getInputStream(entry).use { inS ->
                                    outFile.outputStream().use { outS -> inS.copyTo(outS) }
                                }
                            }
                        }
                    }
                } else if (backupFile.name.endsWith(".apk", ignoreCase = true)) {
                    backupFile.copyTo(File(tempExtract, backupFile.name), overwrite = true)
                } else if (backupFile.isDirectory) {
                    backupFile.walkTopDown().filter { it.isFile && it.name.endsWith(".apk", ignoreCase = true) }.forEach {
                        it.copyTo(File(tempExtract, it.name), overwrite = true)
                    }
                }

                val apks = tempExtract.listFiles { f -> f.name.endsWith(".apk", ignoreCase = true) } ?: emptyArray()
                if (apks.isEmpty()) {
                    tempExtract.deleteRecursively()
                    withContext(Dispatchers.Main) {
                        onResult(false, "Nenhum arquivo APK encontrado dentro deste backup.")
                    }
                    return@launch
                }

                val hasPriv = ShizukuHelper.hasPermission() || ShizukuHelper.isRootAvailable()
                if (hasPriv) {
                    val cmd = if (apks.size == 1) {
                        "pm install -r \"${apks[0].absolutePath}\""
                    } else {
                        val paths = apks.joinToString(" ") { "\"${it.absolutePath}\"" }
                        "pm install-multiple -r $paths"
                    }
                    val output = ShellFunctions.executePrivileged(cmd).trim()
                    tempExtract.deleteRecursively()

                    withContext(Dispatchers.Main) {
                        if (output.contains("Success", ignoreCase = true)) {
                            onResult(true, "APK reinstalado com sucesso pelo PackageManager!")
                        } else {
                            onResult(false, "Falha na reinstalação: $output")
                        }
                    }
                } else {
                    // Sem privilégios, abre intent de instalação padrão com o primeiro APK
                    val baseApk = apks[0]
                    val apkUri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        baseApk
                    )
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                        setDataAndType(apkUri, "application/vnd.android.package-archive")
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    withContext(Dispatchers.Main) {
                        onResult(true, "Instalador do sistema aberto para reinstalação do APK.")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(false, "Erro ao reinstalar APK do backup: ${e.message}")
                }
            }
        }
    }
}
