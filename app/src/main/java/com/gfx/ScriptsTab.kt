package com.gfx

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * [PT-BR] Aba de Scripts e Funções (.sh).
 * Permite ao usuário adicionar novas funções ao aplicativo criando scripts shell personalizados,
 * além de utilizar rotinas pré-configuradas (120Hz, Skia Vulkan, Limpeza de RAM, Buffer de Log, etc.)
 * com execução via Shizuku / Root ou Shell padrão e console de saída em tempo real.
 *
 * [EN] Scripts and Functions tab (.sh).
 * Allows the user to add new functions to the application by creating custom shell scripts,
 * as well as using pre-configured routines with real-time output console.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptsTab(viewModel: GameViewModel) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    val scripts by viewModel.customScripts.collectAsState()
    val commandLog by ShellFunctions.commandLog.collectAsState()

    var selectedCategory by remember { mutableStateOf<ScriptCategory?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showAddEditDialog by remember { mutableStateOf(false) }
    var editingScript by remember { mutableStateOf<CustomScript?>(null) }
    var runningScriptId by remember { mutableStateOf<String?>(null) }

    // Quick Command State
    var quickCommandText by remember { mutableStateOf("") }
    var isQuickRunning by remember { mutableStateOf(false) }

    val terminalScrollState = rememberScrollState()

    LaunchedEffect(commandLog.size) {
        if (commandLog.isNotEmpty()) {
            terminalScrollState.animateScrollTo(terminalScrollState.maxValue)
        }
    }

    val filteredScripts = remember(scripts, selectedCategory, searchQuery, context) {
        scripts.filter { script ->
            val matchCategory = selectedCategory == null || script.category == selectedCategory
            val title = script.getLocalizedTitle(context)
            val desc = script.getLocalizedDescription(context)
            val matchQuery = searchQuery.isBlank() ||
                    title.contains(searchQuery, ignoreCase = true) ||
                    desc.contains(searchQuery, ignoreCase = true) ||
                    script.scriptContent.contains(searchQuery, ignoreCase = true)
            matchCategory && matchQuery
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        // Cabeçalho e Botão de Ação Primária
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(id = R.string.scripts_tab_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Adicione novas funções ao app através de scripts shell (.sh)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = {
                        editingScript = null
                        showAddEditDialog = true
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Adicionar Script",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Nova Função", fontSize = 12.sp)
                }

                IconButton(
                    onClick = {
                        viewModel.resetCustomScriptsToPresets()
                        Toast.makeText(context, "Presets padrão restaurados!", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Restaurar Padrões",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Barra de Pesquisa
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Buscar scripts e funções...", fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Buscar", modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Limpar", modifier = Modifier.size(16.dp))
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Filtro por Categorias (Chips)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null },
                    label = { Text("Todos (${scripts.size})", fontSize = 11.sp) }
                )
            }
            items(ScriptCategory.values()) { cat ->
                val count = scripts.count { it.category == cat }
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = {
                        selectedCategory = if (selectedCategory == cat) null else cat
                    },
                    label = { Text("${cat.iconEmoji} ${cat.getLocalizedName(context)} ($count)", fontSize = 11.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Lista de Scripts + Terminal Dividido
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filteredScripts, key = { it.id }) { script ->
                ScriptItemCard(
                    script = script,
                    isRunning = runningScriptId == script.id,
                    onExecute = {
                        runningScriptId = script.id
                        viewModel.executeCustomScript(script) { result ->
                            runningScriptId = null
                            Toast.makeText(context, "${context.getString(R.string.scripts_run_btn)}: ${script.getLocalizedTitle(context)}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onEdit = {
                        editingScript = script
                        showAddEditDialog = true
                    },
                    onDelete = {
                        viewModel.deleteCustomScript(script.id)
                        Toast.makeText(context, "Script removido", Toast.LENGTH_SHORT).show()
                    },
                    onCopy = {
                        clipboardManager.setText(AnnotatedString(script.scriptContent))
                        Toast.makeText(context, "Comandos copiados!", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))

                // Terminal / Console Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Barra Superior do Terminal
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF2D2D2D))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(Color(0xFF4CAF50), CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Terminal / Console de Saída",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(
                                    onClick = {
                                        if (commandLog.isNotEmpty()) {
                                            val fullLog = commandLog.joinToString("\n")
                                            clipboardManager.setText(AnnotatedString(fullLog))
                                            Toast.makeText(context, "Log copiado!", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("Copiar", color = Color(0xFF64B5F6), fontSize = 11.sp)
                                }

                                TextButton(
                                    onClick = { ShellFunctions.clearLog() },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("Limpar", color = Color(0xFFE57373), fontSize = 11.sp)
                                }
                            }
                        }

                        // Área de Log do Terminal
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .padding(8.dp)
                                .verticalScroll(terminalScrollState)
                        ) {
                            if (commandLog.isEmpty()) {
                                Text(
                                    text = "# Nenhum comando executado recentemente.\n# Execute um script ou digite um comando abaixo.",
                                    color = Color(0xFF888888),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                )
                            } else {
                                Column {
                                    commandLog.forEach { line ->
                                        val lineColor = when {
                                            line.startsWith("#") || line.startsWith("$") -> Color(0xFF64B5F6)
                                            line.startsWith("> Error") || line.contains("falhou") -> Color(0xFFEF5350)
                                            line.startsWith(">") -> Color(0xFF81C784)
                                            else -> Color(0xFFE0E0E0)
                                        }
                                        Text(
                                            text = line,
                                            color = lineColor,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Execução Rápida de Linha de Comando Avulsa
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF252525))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "# ",
                                color = Color(0xFF64B5F6),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            OutlinedTextField(
                                value = quickCommandText,
                                onValueChange = { quickCommandText = it },
                                placeholder = {
                                    Text("Comando rápido (ex: pm trim-caches 100M)", color = Color.Gray, fontSize = 11.sp)
                                },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall.copy(
                                    color = Color.White,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF64B5F6),
                                    unfocusedBorderColor = Color.Transparent
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                            )
                            IconButton(
                                onClick = {
                                    if (quickCommandText.isNotBlank()) {
                                        isQuickRunning = true
                                        viewModel.runCustomScript(quickCommandText) {
                                            isQuickRunning = false
                                            quickCommandText = ""
                                        }
                                    }
                                },
                                enabled = !isQuickRunning && quickCommandText.isNotBlank(),
                                modifier = Modifier.size(36.dp)
                            ) {
                                if (isQuickRunning) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Executar",
                                        tint = if (quickCommandText.isNotBlank()) Color(0xFF4CAF50) else Color.Gray,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Diálogo para Adicionar ou Editar Nova Função / Script
    if (showAddEditDialog) {
        AddEditScriptDialog(
            initialScript = editingScript,
            onDismiss = { showAddEditDialog = false },
            onSave = { savedScript ->
                viewModel.saveCustomScript(savedScript)
                showAddEditDialog = false
                Toast.makeText(context, "Função '${savedScript.title}' salva!", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

/**
 * [PT-BR] Card de item de script individual com metadados, badges e botões de ação rápida.
 */
@Composable
fun ScriptItemCard(
    script: CustomScript,
    isRunning: Boolean,
    onExecute: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit
) {
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Linha Principal - SEMPRE VISÍVEL: Nome do Script + Badges + Botão Expandir/Ocultar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Categoria Badge
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(script.category.colorHex).copy(alpha = 0.16f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(script.category.colorHex).copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "${script.category.iconEmoji} ${script.category.getLocalizedName(context)}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(script.category.colorHex),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        // Privilégio Badge
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (script.runAsPrivileged) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = if (script.runAsPrivileged) "🛡️ Shizuku" else "🐚 Shell",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }

                        // Auto-Start Badge
                        if (script.runOnAppStart) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFFFF3E0)
                            ) {
                                Text(
                                    text = "🚀 Auto",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFE65100),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Nome do Script (SEMPRE VISÍVEL)
                    Text(
                        text = script.getLocalizedTitle(context),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Botão Ocultar / Expandir (Ícone interativo)
                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Ocultar detalhes" else "Expandir detalhes",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Conteúdo recolhível / expansível (Descrição, Código e Ações de edição)
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    val localizedDesc = script.getLocalizedDescription(context)
                    if (localizedDesc.isNotBlank()) {
                        Text(
                            text = localizedDesc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Bloco do Código Shell com Ações (Copiar, Editar, Excluir)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Código Shell (.sh):",
                                    color = Color(0xFFAAAAAA),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(onClick = onCopy, modifier = Modifier.size(26.dp)) {
                                        Icon(Icons.Default.Share, contentDescription = "Copiar Código", tint = Color.LightGray, modifier = Modifier.size(15.dp))
                                    }
                                    IconButton(onClick = onEdit, modifier = Modifier.size(26.dp)) {
                                        Icon(Icons.Default.Edit, contentDescription = "Editar", tint = Color.LightGray, modifier = Modifier.size(15.dp))
                                    }
                                    IconButton(onClick = onDelete, modifier = Modifier.size(26.dp)) {
                                        Icon(Icons.Default.Delete, contentDescription = "Excluir", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(15.dp))
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = script.scriptContent,
                                color = Color(0xFFE0E0E0),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Botão Executar Agora (SEMPRE VISÍVEL)
            Button(
                onClick = onExecute,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isRunning,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(script.category.colorHex)
                )
            ) {
                if (isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Executando...", color = Color.White)
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Executar Agora", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

/**
 * [PT-BR] Diálogo modal para criar ou editar novas funções e scripts personalizados.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditScriptDialog(
    initialScript: CustomScript?,
    onDismiss: () -> Unit,
    onSave: (CustomScript) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(initialScript?.title ?: "") }
    var description by remember { mutableStateOf(initialScript?.description ?: "") }
    var scriptContent by remember { mutableStateOf(initialScript?.scriptContent ?: "# Digite seus comandos shell aqui\n") }
    var category by remember { mutableStateOf(initialScript?.category ?: ScriptCategory.PERFORMANCE) }
    var runAsPrivileged by remember { mutableStateOf(initialScript?.runAsPrivileged ?: true) }
    var runOnAppStart by remember { mutableStateOf(initialScript?.runOnAppStart ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialScript == null) "Adicionar Nova Função / Script" else "Editar Função",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Nome da Função (ex: Modo 120Hz)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descrição do que a função faz") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text("Categoria:", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(ScriptCategory.values()) { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text("${cat.iconEmoji} ${cat.getLocalizedName(context)}", fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Inserção Rápida de Snippets Úteis
                Text("Comandos Rápidos:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SuggestionChip(
                        onClick = { scriptContent += "settings put system peak_refresh_rate 120.0\n" },
                        label = { Text("+ 120Hz", fontSize = 10.sp) }
                    )
                    SuggestionChip(
                        onClick = { scriptContent += "setprop debug.hwui.renderer skiavk\n" },
                        label = { Text("+ Skia Vulkan", fontSize = 10.sp) }
                    )
                    SuggestionChip(
                        onClick = { scriptContent += "pm trim-caches 4096M\n" },
                        label = { Text("+ Limpar Cache", fontSize = 10.sp) }
                    )
                    SuggestionChip(
                        onClick = { scriptContent += "cmd power set-fixed-performance-mode-enabled true\n" },
                        label = { Text("+ Fixed Performance", fontSize = 10.sp) }
                    )
                    SuggestionChip(
                        onClick = { scriptContent += "setprop debug.webview.gpu_raster true\n" },
                        label = { Text("+ WebView GPU", fontSize = 10.sp) }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = scriptContent,
                    onValueChange = { scriptContent = it },
                    label = { Text("Comandos Shell (.sh)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Opção Privilégio
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Privilégios Shizuku / Root", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("Necessário para comandos de sistema como setprop e pm", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = runAsPrivileged,
                        onCheckedChange = { runAsPrivileged = it }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Opção Auto-Start
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Executar ao Iniciar o App", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("Roda este script automaticamente quando o Game Booster for aberto", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = runOnAppStart,
                        onCheckedChange = { runOnAppStart = it }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() && scriptContent.isNotBlank()) {
                        val newScript = CustomScript(
                            id = initialScript?.id ?: java.util.UUID.randomUUID().toString(),
                            title = title.trim(),
                            description = description.trim(),
                            scriptContent = scriptContent.trim(),
                            category = category,
                            runAsPrivileged = runAsPrivileged,
                            runOnAppStart = runOnAppStart,
                            isPreset = initialScript?.isPreset ?: false,
                            createdAt = initialScript?.createdAt ?: System.currentTimeMillis()
                        )
                        onSave(newScript)
                    }
                },
                enabled = title.isNotBlank() && scriptContent.isNotBlank()
            ) {
                Text("Salvar Função")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
