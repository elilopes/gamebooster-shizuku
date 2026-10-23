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
     * Executa o backup de um aplicativo selecionado
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
                if (!hasPriv) {
                    withContext(Dispatchers.Main) {
                        onResult(false, "Permissão Shizuku ou Root necessária para realizar o backup.")
                    }
                    return@launch
                }

                val backupDir = getBackupDirectory()
                val timestamp = System.currentTimeMillis()
                val targetFolder = File(backupDir, "${packageName}_$timestamp")
                targetFolder.mkdirs()

                val commands = mutableListOf<String>()

                if (includeApk) {
                    val apkPathCmd = "pm path $packageName"
                    val apkPathOut = ShellFunctions.executePrivileged(apkPathCmd).trim()
                    val paths = apkPathOut.lines().map { it.removePrefix("package:").trim() }.filter { it.isNotEmpty() }
                    for ((index, path) in paths.withIndex()) {
                        val destName = if (paths.size == 1) "base.apk" else "base_$index.apk"
                        commands.add("cp \"$path\" \"${targetFolder.absolutePath}/$destName\"")
                    }
                }

                if (includeObb) {
                    val obbDir = File("/sdcard/Android/obb/$packageName")
                    if (obbDir.exists()) {
                        commands.add("cp -r \"${obbDir.absolutePath}\" \"${targetFolder.absolutePath}/obb\"")
                    }
                }

                if (includeData) {
                    val sdDataDir = File("/sdcard/Android/data/$packageName")
                    if (sdDataDir.exists()) {
                        commands.add("cp -r \"${sdDataDir.absolutePath}\" \"${targetFolder.absolutePath}/data\"")
                    }
                    val internalDataDir = File("/data/data/$packageName")
                    if (internalDataDir.exists()) {
                        commands.add("cp -r \"${internalDataDir.absolutePath}\" \"${targetFolder.absolutePath}/internal_data\"")
                    }
                }

                for (cmd in commands) {
                    ShellFunctions.executePrivileged(cmd)
                }

                var finalPath = targetFolder.absolutePath
                if (compressZip) {
                    val zipFile = File(backupDir, "${packageName}_$timestamp.zip")
                    val zipCmd = "cd \"${targetFolder.absolutePath}\" && zip -r \"${zipFile.absolutePath}\" ."
                    ShellFunctions.executePrivileged(zipCmd)
                    targetFolder.deleteRecursively()
                    finalPath = zipFile.absolutePath
                }

                withContext(Dispatchers.Main) {
                    onResult(true, "Backup salvo com sucesso em: $finalPath")
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
}
