package com.gfx

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BackupAppTab(viewModel: GameViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backupManager = remember { BackupApp(context, scope) }
    var backups by remember { mutableStateOf<List<File>>(emptyList()) }
    var refreshing by remember { mutableStateOf(false) }

    var showInstallerDialog by remember { mutableStateOf(false) }
    var selectedInstallerUri by remember { mutableStateOf<Uri?>(null) }
    var selectedInstallerFile by remember { mutableStateOf<File?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedInstallerUri = uri
            selectedInstallerFile = null
            showInstallerDialog = true
        }
    }

    var pendingExportBackupFile by remember { mutableStateOf<File?>(null) }
    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { destUri ->
        if (destUri != null && pendingExportBackupFile != null) {
            val target = pendingExportBackupFile!!
            scope.launch(Dispatchers.IO) {
                try {
                    val success = context.contentResolver.openOutputStream(destUri)?.use { out ->
                        backupManager.exportBackupToStream(target, out)
                    } ?: false

                    withContext(Dispatchers.Main) {
                        if (success) {
                            Toast.makeText(context, context.getString(R.string.backup_export_saf_success, target.name), Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "Falha ao gravar arquivo de backup no destino SAF.", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Erro na exportação SAF: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                } finally {
                    pendingExportBackupFile = null
                }
            }
        }
    }

    var pendingExportApkFile by remember { mutableStateOf<File?>(null) }
    val exportApkLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")
    ) { destUri ->
        if (destUri != null && pendingExportApkFile != null) {
            val target = pendingExportApkFile!!
            scope.launch(Dispatchers.IO) {
                try {
                    val success = context.contentResolver.openOutputStream(destUri)?.use { out ->
                        backupManager.exportApkToStream(target, out)
                    } ?: false

                    withContext(Dispatchers.Main) {
                        if (success) {
                            Toast.makeText(context, "APK exportado com sucesso para a pasta externa via SAF!", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "Nenhum arquivo APK pôde ser extraído para o destino selecionado.", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Erro ao exportar APK via SAF: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                } finally {
                    pendingExportApkFile = null
                }
            }
        }
    }

    fun loadBackups() {
        refreshing = true
        scope.launch(Dispatchers.IO) {
            val list = backupManager.listSavedBackups()
            withContext(Dispatchers.Main) {
                backups = list
                refreshing = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadBackups()
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(id = R.string.backup_app_title),
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(id = R.string.backup_app_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = { loadBackups() },
                modifier = Modifier.testTag("refresh_backups_button")
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Atualizar")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Card do Instalador Detalhado de APKs
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Instalador de APKs e Pacotes Detalhado",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Suporte a .apk, .apks, .xapk, .apkm e backups .zip. Extração monitorada, ficha técnica, verificação anti-tamper, alerta UPDATE_INCOMPATIBLE e logcat ao vivo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        filePickerLauncher.launch(arrayOf("*/*"))
                    },
                    modifier = Modifier.fillMaxWidth().testTag("select_apk_file_btn")
                ) {
                    Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Selecionar Arquivo para Instalação")
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (backups.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(id = R.string.no_backups_found),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(backups) { file ->
                    val name = file.name
                    val packageName = name.substringBefore("_")
                    BackupItemCard(
                        file = file,
                        packageName = packageName,
                        backupManager = backupManager,
                        onRestored = { success, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            loadBackups()
                        },
                        onDeleted = {
                            Toast.makeText(context, "Backup excluído com sucesso", Toast.LENGTH_SHORT).show()
                            loadBackups()
                        },
                        onExportBackupSaf = { toExport ->
                            pendingExportBackupFile = toExport
                            exportBackupLauncher.launch(toExport.name)
                        },
                        onExportApkSaf = { toExport ->
                            pendingExportApkFile = toExport
                            val suggestedApkName = if (packageName.isNotBlank()) "${packageName}.apk" else "app.apk"
                            exportApkLauncher.launch(suggestedApkName)
                        },
                        onReinstallApk = { toReinstall ->
                            backupManager.reinstallApkFromBackup(toReinstall, packageName) { success, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                loadBackups()
                            }
                        },
                        onDetailedInstall = { targetFile ->
                            selectedInstallerFile = targetFile
                            selectedInstallerUri = null
                            showInstallerDialog = true
                        }
                    )
                }
            }
        }
    }

    if (showInstallerDialog && (selectedInstallerUri != null || selectedInstallerFile != null)) {
        DetailedApkInstallerDialog(
            sourceUri = selectedInstallerUri,
            sourceFile = selectedInstallerFile,
            onDismiss = {
                showInstallerDialog = false
                selectedInstallerUri = null
                selectedInstallerFile = null
                loadBackups()
            },
            onBackupRequested = { pkg ->
                Toast.makeText(context, "Iniciando backup prévio do app $pkg...", Toast.LENGTH_SHORT).show()
                backupManager.backupApp(
                    packageName = pkg,
                    includeApk = true,
                    includeObb = true,
                    includeData = true,
                    compressZip = true
                ) { success, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    loadBackups()
                }
            }
        )
    }
}

@Composable
fun BackupItemCard(
    file: File,
    packageName: String,
    backupManager: BackupApp,
    onRestored: (Boolean, String) -> Unit,
    onDeleted: () -> Unit,
    onExportBackupSaf: (File) -> Unit,
    onExportApkSaf: (File) -> Unit,
    onReinstallApk: (File) -> Unit,
    onDetailedInstall: (File) -> Unit
) {
    val scope = rememberCoroutineScope()
    val formattedDate = remember(file) {
        try {
            SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(file.lastModified()))
        } catch (_: Exception) {
            ""
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Pacote: $packageName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (formattedDate.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(id = R.string.backup_date_time_label, formattedDate),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                IconButton(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            backupManager.deleteBackup(file)
                            withContext(Dispatchers.Main) {
                                onDeleted()
                            }
                        }
                    },
                    modifier = Modifier.testTag("delete_backup_${file.name}")
                ) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(id = R.string.delete_backup), tint = MaterialTheme.colorScheme.error)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Botão Reinstalar APK e Botão de Instalação Detalhada
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onReinstallApk(file) },
                    modifier = Modifier.weight(1f).testTag("reinstall_apk_${file.name}"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reinstalar APK", style = MaterialTheme.typography.labelMedium)
                }

                FilledTonalButton(
                    onClick = { onDetailedInstall(file) },
                    modifier = Modifier.weight(1.2f).testTag("detailed_install_${file.name}")
                ) {
                    Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Instalar Detalhado", style = MaterialTheme.typography.labelMedium)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Botões de Exportação SAF (Exportar APK e Exportar Backup)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onExportApkSaf(file) },
                    modifier = Modifier.weight(1f).testTag("export_apk_saf_${file.name}")
                ) {
                    Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Exportar APK (SAF)", style = MaterialTheme.typography.labelSmall)
                }

                OutlinedButton(
                    onClick = { onExportBackupSaf(file) },
                    modifier = Modifier.weight(1f).testTag("export_backup_saf_${file.name}")
                ) {
                    Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Exportar Backup (SAF)", style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        backupManager.restoreSpecific(file, packageName, restoreApk = true, restoreObb = false, restoreData = false, onResult = onRestored)
                    },
                    modifier = Modifier.weight(1f).testTag("restore_apk_${file.name}")
                ) {
                    Text(stringResource(id = R.string.restore_apk), style = MaterialTheme.typography.labelSmall)
                }
                Button(
                    onClick = {
                        backupManager.restoreSpecific(file, packageName, restoreApk = false, restoreObb = true, restoreData = false, onResult = onRestored)
                    },
                    modifier = Modifier.weight(1f).testTag("restore_obb_${file.name}")
                ) {
                    Text(stringResource(id = R.string.restore_obb), style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        backupManager.restoreSpecific(file, packageName, restoreApk = false, restoreObb = false, restoreData = true, onResult = onRestored)
                    },
                    modifier = Modifier.weight(1f).testTag("restore_data_${file.name}")
                ) {
                    Text(stringResource(id = R.string.restore_data), style = MaterialTheme.typography.labelSmall)
                }
                Button(
                    onClick = {
                        backupManager.restoreSpecific(file, packageName, restoreApk = true, restoreObb = true, restoreData = true, onResult = onRestored)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f).testTag("restore_all_${file.name}")
                ) {
                    Text(stringResource(id = R.string.restore_all), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
