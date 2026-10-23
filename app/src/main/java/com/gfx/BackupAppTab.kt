package com.gfx

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
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

@Composable
fun BackupAppTab(viewModel: GameViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backupManager = remember { BackupApp(context, scope) }
    var backups by remember { mutableStateOf<List<File>>(emptyList()) }
    var refreshing by remember { mutableStateOf(false) }

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

        Spacer(modifier = Modifier.height(16.dp))

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
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun BackupItemCard(
    file: File,
    packageName: String,
    backupManager: BackupApp,
    onRestored: (Boolean, String) -> Unit,
    onDeleted: () -> Unit
) {
    val scope = rememberCoroutineScope()

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

            Spacer(modifier = Modifier.height(10.dp))

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
