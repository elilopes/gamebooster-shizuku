package com.gfx

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetEditTab(viewModel: GameViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedTable by remember { mutableStateOf(SetEditHelper.TABLE_GLOBAL) }
    var searchQuery by remember { mutableStateOf("") }
    var settingsList by remember { mutableStateOf<List<SetEditHelper.SettingItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<SetEditHelper.SettingItem?>(null) }

    var newKey by remember { mutableStateOf("") }
    var newValue by remember { mutableStateOf("") }

    // Função para carregar itens da tabela
    val loadTable: (String) -> Unit = { table ->
        coroutineScope.launch {
            isLoading = true
            settingsList = SetEditHelper.listSettings(table)
            isLoading = false
        }
    }

    LaunchedEffect(selectedTable) {
        loadTable(selectedTable)
    }

    val filteredList = remember(settingsList, searchQuery) {
        if (searchQuery.isBlank()) {
            settingsList
        } else {
            val q = searchQuery.trim().lowercase()
            settingsList.filter { it.key.lowercase().contains(q) || it.value.lowercase().contains(q) }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("setedit_tab_column")
    ) {
        // Card de Apresentação / Explicação
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = stringResource(id = R.string.setedit_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(id = R.string.setedit_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Seção: Presets de Ajustes Rápidos (Desempenho e Bateria)
        item {
            Text(
                text = stringResource(id = R.string.setedit_presets_header),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // 1. Tempo Limite de Tela
                    Text(
                        text = stringResource(id = R.string.setedit_screen_timeout_title),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "15s" to 15000L,
                            "30s" to 30000L,
                            "1m" to 60000L,
                            "2m" to 120000L,
                            "5m" to 300000L,
                            "10m" to 600000L,
                            "30m" to 1800000L
                        ).forEach { (label, ms) ->
                            OutlinedButton(
                                onClick = {
                                    val res = SetEditHelper.setScreenTimeout(ms)
                                    Toast.makeText(context, res.second, Toast.LENGTH_SHORT).show()
                                    if (selectedTable == SetEditHelper.TABLE_SYSTEM) loadTable(selectedTable)
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text(label, fontSize = 11.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 2. Taxa de Atualização / FPS de Pico
                    Text(
                        text = stringResource(id = R.string.setedit_refresh_rate_title),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "60Hz" to 60.0f,
                            "90Hz" to 90.0f,
                            "120Hz" to 120.0f,
                            "144Hz" to 144.0f
                        ).forEach { (label, fps) ->
                            FilledTonalButton(
                                onClick = {
                                    val res = SetEditHelper.setRefreshRate(fps)
                                    Toast.makeText(context, res.second, Toast.LENGTH_SHORT).show()
                                    if (selectedTable == SetEditHelper.TABLE_SYSTEM) loadTable(selectedTable)
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text(label, fontSize = 11.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 3. Escala de Animações (0x, 0.5x, 1x)
                    Text(
                        text = stringResource(id = R.string.setedit_animations_title),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "0.0x (Instantâneo)" to 0.0f,
                            "0.5x (Rápido)" to 0.5f,
                            "1.0x (Padrão)" to 1.0f
                        ).forEach { (label, scale) ->
                            OutlinedButton(
                                onClick = {
                                    val res = SetEditHelper.setAnimationScales(scale)
                                    Toast.makeText(context, res.second, Toast.LENGTH_SHORT).show()
                                    if (selectedTable == SetEditHelper.TABLE_GLOBAL) loadTable(selectedTable)
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text(label, fontSize = 11.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 4. Limite de Processos em Segundo Plano
                    Text(
                        text = stringResource(id = R.string.setedit_process_limit_title),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "0 (Sem processos)" to 0,
                            "1 processo" to 1,
                            "2 processos" to 2,
                            "Padrão Android" to -1
                        ).forEach { (label, limit) ->
                            OutlinedButton(
                                onClick = {
                                    val res = SetEditHelper.setBackgroundProcessLimit(limit)
                                    Toast.makeText(context, res.second, Toast.LENGTH_SHORT).show()
                                    if (selectedTable == SetEditHelper.TABLE_GLOBAL) loadTable(selectedTable)
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text(label, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        // Seção: Seletor de Tabela e Ferramentas (Global, System, Secure)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(id = R.string.setedit_table_editor_header),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(
                        onClick = { loadTable(selectedTable) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(id = R.string.btn_refresh_list),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    FilledTonalButton(
                        onClick = {
                            newKey = ""
                            newValue = ""
                            showAddDialog = true
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(id = R.string.setedit_add_param_btn), fontSize = 11.sp)
                    }
                }
            }

            // Tabs para Global / System / Secure
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                SetEditHelper.AVAILABLE_TABLES.forEachIndexed { index, table ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = SetEditHelper.AVAILABLE_TABLES.size),
                        onClick = { selectedTable = table },
                        selected = selectedTable == table
                    ) {
                        Text(table.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Campo de busca
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                placeholder = { Text(stringResource(id = R.string.setedit_search_placeholder)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true
            )
        }

        // Lista de Parâmetros da Tabela
        if (isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
            }
        } else if (filteredList.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(id = R.string.setedit_empty_notice),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(filteredList, key = { "${it.namespace}_${it.key}" }) { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clickable { editingItem = item },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.key,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = item.value.ifEmpty { "(vazio)" },
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }

    // Modal para Adicionar Novo Parâmetro
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = {
                Text("${stringResource(id = R.string.setedit_add_title)} (${selectedTable.uppercase()})")
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = newKey,
                        onValueChange = { newKey = it },
                        label = { Text(stringResource(id = R.string.setedit_key_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newValue,
                        onValueChange = { newValue = it },
                        label = { Text(stringResource(id = R.string.setedit_value_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (newKey.isNotBlank()) {
                        val res = SetEditHelper.putSetting(selectedTable, newKey, newValue)
                        Toast.makeText(context, res.second, Toast.LENGTH_LONG).show()
                        if (res.first) {
                            showAddDialog = false
                            loadTable(selectedTable)
                        }
                    }
                }) {
                    Text(stringResource(id = R.string.setedit_save_btn))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text(stringResource(id = R.string.cancel))
                }
            }
        )
    }

    // Modal para Editar ou Excluir Parâmetro Existente
    editingItem?.let { item ->
        var editVal by remember(item) { mutableStateOf(item.value) }

        AlertDialog(
            onDismissRequest = { editingItem = null },
            title = {
                Text("${item.key} (${item.namespace.uppercase()})", style = MaterialTheme.typography.titleMedium)
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Altere o valor do parâmetro na tabela do sistema:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = editVal,
                        onValueChange = { editVal = it },
                        label = { Text("Valor") },
                        singleLine = false,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(
                        onClick = {
                            val res = SetEditHelper.deleteSetting(item.namespace, item.key)
                            Toast.makeText(context, res.second, Toast.LENGTH_LONG).show()
                            editingItem = null
                            loadTable(item.namespace)
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(id = R.string.delete_backup))
                    }

                    Button(onClick = {
                        val res = SetEditHelper.putSetting(item.namespace, item.key, editVal)
                        Toast.makeText(context, res.second, Toast.LENGTH_LONG).show()
                        if (res.first) {
                            editingItem = null
                            loadTable(item.namespace)
                        }
                    }) {
                        Text(stringResource(id = R.string.setedit_save_btn))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { editingItem = null }) {
                    Text(stringResource(id = R.string.cancel))
                }
            }
        )
    }
}
