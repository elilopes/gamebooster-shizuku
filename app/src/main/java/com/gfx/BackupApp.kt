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
     * Descompacta um arquivo ZIP usando a API Java padrão (evita restrições de permissão do comando unzip no shell)
     */
    private fun extractZip(zipFile: File, destDir: File) {
        java.util.zip.ZipFile(zipFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val outFile = File(destDir, entry.name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { inS ->
                        outFile.outputStream().use { outS -> inS.copyTo(outS) }
                    }
                }
            }
        }
    }

    /**
     * Instala um ou mais arquivos APK usando streaming via stdin (ShizukuHelper.newProcess ou Root).
     * Evita problemas de leitura de diretórios privados do aplicativo pelo usuário shell do ADB.
     */
    private fun installApkFiles(apks: Array<File>): Pair<Boolean, String> {
        if (apks.isEmpty()) return Pair(false, "Nenhum arquivo APK para instalar.")
        return try {
            if (apks.size == 1) {
                val apk = apks[0]
                val expected = apk.length()
                val cmd = "pm install -r -S $expected -"
                val output = if (ShizukuHelper.hasPermission()) {
                    val p = ShizukuHelper.newProcess("sh", "-c", cmd)
                    p.outputStream.use { out -> apk.inputStream().use { it.copyTo(out, 64 * 1024) } }
                    val out = p.inputStream.bufferedReader().readText()
                    val err = p.errorStream.bufferedReader().readText()
                    p.waitFor()
                    (out + err).trim()
                } else {
                    ShellFunctions.executePrivileged("cat \"${apk.absolutePath}\" | $cmd").trim()
                }
                if (output.contains("Success", ignoreCase = true)) {
                    Pair(true, output)
                } else {
                    Pair(false, output)
                }
            } else {
                val createOut = ShellFunctions.executePrivileged("pm install-create -r").trim()
                val sessionIdRegex = Regex("(?i)session\\s*\\[?(\\d+)\\]?|Success:\\s*\\[?(\\d+)\\]?|\\b(\\d{5,})\\b")
                val sessionId = sessionIdRegex.find(createOut)?.groupValues?.drop(1)?.firstOrNull { it.isNotBlank() }
                if (sessionId == null) {
                    return Pair(false, "Falha ao criar sessão do PackageManager: $createOut")
                }
                for (apk in apks) {
                    val expected = apk.length()
                    val writeOut = if (ShizukuHelper.hasPermission()) {
                        val p = ShizukuHelper.newProcess("sh", "-c", "pm install-write -S $expected $sessionId \"${apk.name}\" -")
                        p.outputStream.use { out -> apk.inputStream().use { it.copyTo(out, 64 * 1024) } }
                        val out = p.inputStream.bufferedReader().readText()
                        val err = p.errorStream.bufferedReader().readText()
                        p.waitFor()
                        (out + err).trim()
                    } else {
                        ShellFunctions.executePrivileged("cat \"${apk.absolutePath}\" | pm install-write -S $expected $sessionId \"${apk.name}\" -").trim()
                    }
                    if (!writeOut.contains("Success", ignoreCase = true)) {
                        ShellFunctions.executePrivileged("pm install-abandon $sessionId")
                        return Pair(false, "Falha ao gravar ${apk.name}: $writeOut")
                    }
                }
                val commitOut = ShellFunctions.executePrivileged("pm install-commit $sessionId").trim()
                if (commitOut.contains("Success", ignoreCase = true)) {
                    Pair(true, commitOut)
                } else {
                    Pair(false, commitOut)
                }
            }
        } catch (e: Exception) {
            Pair(false, "Exceção ao instalar APK: ${e.message}")
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

                    // [PT-BR] Se o APK foi selecionado, mantém uma cópia do APK fora do zip na mesma pasta backupDir.
                    // Isso permite reinstalar ou exportar o APK diretamente sem descompactar o arquivo ZIP completo.
                    if (includeApk) {
                        val baseApkInFolder = File(targetFolder, "base.apk")
                        if (baseApkInFolder.exists()) {
                            baseApkInFolder.copyTo(File(backupDir, "${packageName}_$timestamp.apk"), overwrite = true)
                        } else {
                            val anyApk = targetFolder.listFiles { f -> f.name.endsWith(".apk") }?.firstOrNull()
                            anyApk?.copyTo(File(backupDir, "${packageName}_$timestamp.apk"), overwrite = true)
                        }
                    }

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
                        val extraMsg = if (compressZip && includeApk) " (APK mantido também avulso na mesma pasta para reinstalação rápida)" else ""
                        onResult(true, "Backup salvo com sucesso em: $finalPath$extraMsg")
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

                if (backupFile.name.endsWith(".zip", ignoreCase = true)) {
                    extractZip(backupFile, tempExtract)
                } else if (backupFile.isDirectory) {
                    backupFile.walkTopDown().forEach { file ->
                        if (file.isFile) {
                            val rel = file.relativeTo(backupFile).path
                            val dest = File(tempExtract, rel)
                            dest.parentFile?.mkdirs()
                            file.copyTo(dest, overwrite = true)
                        }
                    }
                }

                // 1. Instalar APKs se existirem (ou verificar se existe APK irmão avulso na pasta do backup)
                val apks = tempExtract.listFiles { f -> f.name.endsWith(".apk") } ?: emptyArray()
                val effectiveApks = if (apks.isEmpty() && backupFile.isFile) {
                    val sibling = File(backupFile.parentFile, backupFile.nameWithoutExtension + ".apk")
                    if (sibling.exists()) arrayOf(sibling) else emptyArray()
                } else apks

                if (effectiveApks.isNotEmpty()) {
                    val (success, installOut) = installApkFiles(effectiveApks)
                    if (!success) {
                        tempExtract.deleteRecursively()
                        val err = ApkInstallerManager.translateInstallError(installOut, "pm install")
                        withContext(Dispatchers.Main) {
                            onResult(false, "Falha na instalação do APK durante a restauração:\n$err")
                        }
                        return@launch
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
                    if (ShizukuHelper.isRootAvailable()) {
                        ShellFunctions.executePrivileged("cp -r \"${internalDataFolder.absolutePath}/.\" \"/data/data/$packageName/\"")
                    } else {
                        android.util.Log.w("BackupApp", "Restauração de /data/data/$packageName requer Root (Superusuário). Shizuku em modo ADB não tem permissão para gravar na pasta privada de outros apps.")
                    }
                }

                tempExtract.deleteRecursively()

                withContext(Dispatchers.Main) {
                    val rootNotice = if (internalDataFolder.exists() && !ShizukuHelper.isRootAvailable()) {
                        "\n⚠️ Aviso: Dados internos de /data/data requerem Root para restauração completa."
                    } else ""
                    onResult(true, "Restauração do backup concluída com sucesso!$rootNotice")
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

                if (backupFile.name.endsWith(".zip", ignoreCase = true)) {
                    extractZip(backupFile, tempExtract)
                } else if (backupFile.isDirectory) {
                    backupFile.walkTopDown().forEach { file ->
                        if (file.isFile) {
                            val rel = file.relativeTo(backupFile).path
                            val dest = File(tempExtract, rel)
                            dest.parentFile?.mkdirs()
                            file.copyTo(dest, overwrite = true)
                        }
                    }
                }

                if (restoreApk) {
                    val apks = tempExtract.listFiles { f -> f.name.endsWith(".apk") } ?: emptyArray()
                    val effectiveApks = if (apks.isEmpty() && backupFile.isFile) {
                        val sibling = File(backupFile.parentFile, backupFile.nameWithoutExtension + ".apk")
                        if (sibling.exists()) arrayOf(sibling) else emptyArray()
                    } else apks

                    if (effectiveApks.isNotEmpty()) {
                        val (success, installOut) = installApkFiles(effectiveApks)
                        if (!success) {
                            tempExtract.deleteRecursively()
                            val err = ApkInstallerManager.translateInstallError(installOut, "pm install")
                            withContext(Dispatchers.Main) {
                                onResult(false, "Falha na instalação do APK durante a restauração:\n$err")
                            }
                            return@launch
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
                        if (ShizukuHelper.isRootAvailable()) {
                            ShellFunctions.executePrivileged("cp -r \"${internalDataFolder.absolutePath}/.\" \"/data/data/$packageName/\"")
                        } else {
                            android.util.Log.w("BackupApp", "Restauração de /data/data/$packageName requer Root (Superusuário).")
                        }
                    }
                }

                tempExtract.deleteRecursively()

                withContext(Dispatchers.Main) {
                    onResult(true, "Restauração seletiva concluída com sucesso!")
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
                // 1. Se o backup selecionado for um .zip, verificar se existe o .apk correspondente fora do zip na mesma pasta
                val standaloneApk = if (backupFile.name.endsWith(".zip", ignoreCase = true)) {
                    val candidate = File(backupFile.parentFile, backupFile.nameWithoutExtension + ".apk")
                    if (candidate.exists()) candidate else null
                } else if (backupFile.name.endsWith(".apk", ignoreCase = true)) {
                    backupFile
                } else null

                val apksToInstall: Array<File>
                val tempExtract = File(context.cacheDir, "reinstall_${System.currentTimeMillis()}")

                if (standaloneApk != null) {
                    apksToInstall = arrayOf(standaloneApk)
                } else {
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
                    } else if (backupFile.isDirectory) {
                        backupFile.walkTopDown().filter { it.isFile && it.name.endsWith(".apk", ignoreCase = true) }.forEach {
                            it.copyTo(File(tempExtract, it.name), overwrite = true)
                        }
                    }
                    apksToInstall = tempExtract.listFiles { f -> f.name.endsWith(".apk", ignoreCase = true) } ?: emptyArray()
                }

                if (apksToInstall.isEmpty()) {
                    tempExtract.deleteRecursively()
                    withContext(Dispatchers.Main) {
                        onResult(false, "Nenhum arquivo APK encontrado dentro deste backup.")
                    }
                    return@launch
                }

                val hasPriv = ShizukuHelper.hasPermission() || ShizukuHelper.isRootAvailable()
                if (hasPriv) {
                    val (success, out) = installApkFiles(apksToInstall)
                    tempExtract.deleteRecursively()

                    withContext(Dispatchers.Main) {
                        if (success) {
                            onResult(true, "APK reinstalado com sucesso pelo PackageManager!")
                        } else {
                            val err = ApkInstallerManager.translateInstallError(out, "pm install")
                            onResult(false, "Falha na reinstalação:\n$err")
                        }
                    }
                } else {
                    // Sem privilégios, abre intent de instalação padrão com o primeiro APK
                    val baseApk = apksToInstall[0]
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
