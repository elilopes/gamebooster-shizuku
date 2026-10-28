package com.gfx

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailedApkInstallerDialog(
    sourceUri: Uri?,
    sourceFile: File? = null,
    onDismiss: () -> Unit,
    onBackupRequested: (packageName: String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    var currentStep by remember { mutableStateOf(InstallStep.IDLE) }
    var stepMessage by remember { mutableStateOf("Iniciando leitor de pacotes...") }
    var stepProgress by remember { mutableStateOf(0f) }

    var metadata by remember { mutableStateOf<ApkMetadata?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val flags = remember { mutableStateOf(InstallFlags()) }
    var showPermissionsDialog by remember { mutableStateOf(false) }
    var showLogcatPanel by remember { mutableStateOf(false) }

    val isShizukuReady = remember { ShizukuHelper.hasPermission() || ShizukuHelper.isRootAvailable() }
    val logcatLogs by ApkInstallerManager.logcatStream.collectAsState()
    val logListState = rememberLazyListState()

    LaunchedEffect(logcatLogs.size) {
        if (logcatLogs.isNotEmpty()) {
            logListState.animateScrollToItem(logcatLogs.size - 1)
        }
    }

    // Iniciar processo de análise
    LaunchedEffect(sourceUri, sourceFile) {
        val targetUri = sourceUri ?: sourceFile?.let { Uri.fromFile(it) }
        if (targetUri == null) {
            errorMessage = "Nenhum arquivo ou pacote selecionado."
            currentStep = InstallStep.ERROR
            return@LaunchedEffect
        }

        currentStep = InstallStep.COPYING_SOURCE
        scope.launch(Dispatchers.IO) {
            try {
                val (name, size) = ApkInstallerManager.queryFileInfo(context, targetUri)
                val type = ApkInstallerManager.detectPackageSourceType(name)

                val (stagingDir, sha256) = ApkInstallerManager.extractAndStage(
                    context = context,
                    sourceUri = targetUri,
                    fileName = name,
                    sourceType = type,
                    totalSize = size
                ) { progress, status ->
                    scope.launch(Dispatchers.Main) {
                        stepProgress = progress
                        stepMessage = status
                    }
                }

                withContext(Dispatchers.Main) {
                    currentStep = InstallStep.ANALYZING
                    stepMessage = "Lendo metadados, permissões e assinaturas do APK..."
                }

                val meta = ApkInstallerManager.analyzeStagedPackage(
                    context = context,
                    stagingDir = stagingDir,
                    sourceFileName = name,
                    sourceFileSize = size,
                    sourceSha256 = sha256,
                    sourceType = type
                )

                withContext(Dispatchers.Main) {
                    metadata = meta
                    // Se for downgrade, marca a flag por padrão para conveniência
                    if (meta.installedComparison.installType == InstallType.DOWNGRADE) {
                        flags.value.allowDowngrade = true
                    }
                    currentStep = InstallStep.READY_TO_INSTALL
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorMessage = e.message ?: "Erro desconhecido ao processar pacote."
                    currentStep = InstallStep.ERROR
                }
            }
        }
    }

    Dialog(
        onDismissRequest = {
            ApkInstallerManager.stopLogcatMonitoring()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_chip),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Instalador de APKs Detalhado",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = {
                        ApkInstallerManager.stopLogcatMonitoring()
                        onDismiss()
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                when (currentStep) {
                    InstallStep.IDLE, InstallStep.COPYING_SOURCE, InstallStep.EXTRACTING, InstallStep.ANALYZING -> {
                        // Fase 1: Análise e Extração
                        Box(
                            modifier = Modifier.fillMaxSize().weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                CircularProgressIndicator(
                                    progress = { stepProgress.coerceIn(0f, 1f) },
                                    modifier = Modifier.size(72.dp),
                                    strokeWidth = 6.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                Text(
                                    text = stepMessage,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "${(stepProgress * 100).toInt()}%",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    InstallStep.ERROR -> {
                        // Fase de Erro com Detalhes Completos do PackageManager
                        val fullErrorText = errorMessage ?: stepMessage
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Top
                        ) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(60.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Falha na Instalação pelo PackageManager",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = "Motivo da Rejeição / Diagnóstico:",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = fullErrorText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Painel de Logs do Instalador
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Logs Brutos do Processamento (${logcatLogs.size}):",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        TextButton(onClick = {
                                            val logText = logcatLogs.joinToString("\n") + "\n\n" + fullErrorText
                                            clipboardManager.setText(AnnotatedString(logText))
                                            Toast.makeText(context, "Log de erro copiado para a área de transferência!", Toast.LENGTH_SHORT).show()
                                        }) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Copiar Log", color = Color.LightGray, fontSize = 11.sp)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    LazyColumn(
                                        modifier = Modifier.fillMaxWidth().height(160.dp)
                                    ) {
                                        items(logcatLogs) { line ->
                                            Text(
                                                text = line,
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = if (line.contains("[ERRO]") || line.contains("E/") || line.contains("Failure")) Color(0xFFFF8A80) else Color(0xFFE0E0E0)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        currentStep = InstallStep.READY_TO_INSTALL
                                        errorMessage = null
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Voltar às Opções")
                                }
                                Button(
                                    onClick = {
                                        ApkInstallerManager.stopLogcatMonitoring()
                                        onDismiss()
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Fechar")
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    InstallStep.READY_TO_INSTALL, InstallStep.CREATING_SESSION, InstallStep.WRITING_APKS,
                    InstallStep.COMMITTING, InstallStep.COPYING_OBB, InstallStep.CLEANING_UP, InstallStep.SUCCESS -> {
                        metadata?.let { meta ->
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // 1. Card Cabeçalho do Aplicativo
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (meta.icon != null) {
                                                Image(
                                                    bitmap = meta.icon.asImageBitmap(),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(54.dp)
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Default.Android,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(54.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = meta.label,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = meta.packageName,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = MaterialTheme.colorScheme.secondaryContainer
                                                    ) {
                                                        Text(
                                                            text = "v${meta.versionName} (${meta.versionCode})",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }

                                                    // Badge de Tipo de Pacote
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = MaterialTheme.colorScheme.tertiaryContainer
                                                    ) {
                                                        Text(
                                                            text = meta.sourceType.displayName,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // 2. Badge e Alertas do Tipo de Instalação (Upgrade, Downgrade, Assinatura)
                                item {
                                    val comp = meta.installedComparison
                                    val (badgeBg, badgeFg, labelText) = when (comp.installType) {
                                        InstallType.NEW_INSTALL -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), "✨ Nova Instalação (App não instalado)")
                                        InstallType.UPGRADE -> Triple(Color(0xFFE3F2FD), Color(0xFF1565C0), "⬆️ Atualização: v${comp.currentVersionName} ➔ v${meta.versionName}")
                                        InstallType.REINSTALL -> Triple(Color(0xFFFFF8E1), Color(0xFFF57F17), "🔄 Mesma Versão: v${meta.versionName}")
                                        InstallType.DOWNGRADE -> Triple(Color(0xFFFFEBEE), Color(0xFFC62828), "⬇️ Downgrade: v${comp.currentVersionName} ➔ v${meta.versionName}")
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = badgeBg,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = labelText,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = badgeFg
                                            )
                                        }
                                    }
                                }

                                // ⚠️ ALERTA CRÍTICO DE ASSINATURA INCOMPATÍVEL (UPDATE_INCOMPATIBLE)
                                if (meta.installedComparison.isInstalled && !meta.installedComparison.isSignatureMatching) {
                                    item {
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = Icons.Default.Security,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = "ALERTA CRÍTICO: Assinatura Incompatível!",
                                                        style = MaterialTheme.typography.titleSmall,
                                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    text = "O certificado deste APK não confere com o app atualmente instalado (UPDATE_INCOMPATIBLE). O Android rejeitará a atualização direta. Se desinstalar o app atual para instalar este, VOCÊ PERDERÁ TODOS OS DADOS E PROGRESSO DO APP.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onErrorContainer
                                                )
                                                Spacer(modifier = Modifier.height(10.dp))
                                                Button(
                                                    onClick = { onBackupRequested(meta.packageName) },
                                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                                ) {
                                                    Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Fazer Backup Prévio dos Dados Agora")
                                                }
                                            }
                                        }
                                    }
                                }

                                // 3. Card de Diagnóstico e Verificações
                                item {
                                    Card(modifier = Modifier.fillMaxWidth()) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = "Diagnóstico do Pacote",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))

                                            // Arquivo e Tamanho
                                            Text("Origem: ${meta.sourceFileName}", style = MaterialTheme.typography.bodySmall)
                                            Text("Tamanho: %.1f MB".format(meta.sourceFileSizeBytes / (1024f * 1024f)), style = MaterialTheme.typography.bodySmall)
                                            Text("SHA-256 Origem: ${meta.sourceFileSha256.take(24)}…", style = MaterialTheme.typography.bodySmall)

                                            Spacer(modifier = Modifier.height(6.dp))
                                            HorizontalDivider()
                                            Spacer(modifier = Modifier.height(6.dp))

                                            // Arquiteturas / ABIs
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                val abiOk = meta.isCompatibleAbi
                                                Icon(
                                                    imageVector = if (abiOk) Icons.Default.CheckCircle else Icons.Default.Warning,
                                                    contentDescription = null,
                                                    tint = if (abiOk) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "ABIs Suportadas: ${meta.supportedAbis.joinToString(", ").ifEmpty { "Universais" }}",
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }

                                            // Versão Mínima do Android
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                val sdkOk = meta.isCompatibleMinSdk
                                                Icon(
                                                    imageVector = if (sdkOk) Icons.Default.CheckCircle else Icons.Default.Warning,
                                                    contentDescription = null,
                                                    tint = if (sdkOk) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Android Mínimo: API ${meta.minSdkVersion} (Target: API ${meta.targetSdkVersion})",
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }

                                            // Espaço em Disco
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                val storageOk = meta.hasFreeStorage
                                                Icon(
                                                    imageVector = if (storageOk) Icons.Default.CheckCircle else Icons.Default.Warning,
                                                    contentDescription = null,
                                                    tint = if (storageOk) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                val freeMb = meta.freeStorageBytes / (1024 * 1024)
                                                val reqMb = meta.requiredStorageBytes / (1024 * 1024)
                                                Text(
                                                    text = "Armazenamento: ${freeMb} MB livres (Necessário: ~${reqMb} MB)",
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }

                                            // Splits e OBB
                                            Text(
                                                text = "APKs: ${meta.extractedApks.size} (Base + Splits)" + if (meta.extractedObbFiles.isNotEmpty()) " | OBBs inclusos: ${meta.extractedObbFiles.size}" else "",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }

                                // 4. Assinatura do APK e Permissões
                                item {
                                    Card(modifier = Modifier.fillMaxWidth()) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Assinatura do APK",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                TextButton(
                                                    onClick = {
                                                        clipboardManager.setText(AnnotatedString(meta.signerSha256))
                                                        Toast.makeText(context, "SHA-256 da assinatura copiado!", Toast.LENGTH_SHORT).show()
                                                    },
                                                    contentPadding = PaddingValues(0.dp)
                                                ) {
                                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Copiar SHA-256", fontSize = 11.sp)
                                                }
                                            }
                                            Text(
                                                text = meta.signerSha256,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.primary
                                            )

                                            Spacer(modifier = Modifier.height(10.dp))
                                            HorizontalDivider()
                                            Spacer(modifier = Modifier.height(8.dp))

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column {
                                                    Text(
                                                        text = "Permissões (${meta.requestedPermissions.size} total)",
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = "${meta.dangerousPermissionsCount} permissão(ões) perigosas solicitadas",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = if (meta.dangerousPermissionsCount > 0) MaterialTheme.colorScheme.error else Color.Gray
                                                    )
                                                }
                                                OutlinedButton(
                                                    onClick = { showPermissionsDialog = true },
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                                ) {
                                                    Text("Ver Lista", fontSize = 11.sp)
                                                }
                                            }
                                        }
                                    }
                                }

                                // 5. Proteções Anti-Tamper / DRM se detectadas
                                if (meta.antiTamperLibsDetected.isNotEmpty()) {
                                    item {
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "Proteções Anti-Tamper / DRM Detectadas",
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(6.dp))
                                                meta.antiTamperLibsDetected.forEach { lib ->
                                                    Text("• $lib", style = MaterialTheme.typography.bodySmall)
                                                }
                                            }
                                        }
                                    }
                                }

                                // 6. Opções de Instalação (Flags pm)
                                item {
                                    Card(modifier = Modifier.fillMaxWidth()) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = "Opções do Instalador (Flags pm)",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text("[-d] Permitir Downgrade", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                                    Text("Autoriza instalar versão inferior à atualmente instalada", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                                }
                                                Switch(
                                                    checked = flags.value.allowDowngrade,
                                                    onCheckedChange = { flags.value = flags.value.copy(allowDowngrade = it) }
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(6.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text("[-t] Permitir Pacotes de Teste", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                                    Text("Autoriza APKs marcados como test-only", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                                }
                                                Switch(
                                                    checked = flags.value.allowTest,
                                                    onCheckedChange = { flags.value = flags.value.copy(allowTest = it) }
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(6.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text("[-g] Conceder Todas as Permissões", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                                    Text("Concede permissões de tempo de execução durante a instalação", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                                }
                                                Switch(
                                                    checked = flags.value.grantAllPermissions,
                                                    onCheckedChange = { flags.value = flags.value.copy(grantAllPermissions = it) }
                                                )
                                            }
                                        }
                                    }
                                }

                                // 7. Status do Progresso da Instalação se estiver rodando
                                if (currentStep != InstallStep.READY_TO_INSTALL) {
                                    item {
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (currentStep == InstallStep.SUCCESS) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                            )
                                        ) {
                                            Column(modifier = Modifier.padding(14.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    if (currentStep == InstallStep.SUCCESS) {
                                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32))
                                                    } else {
                                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                                    }
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = stepMessage,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (currentStep == InstallStep.SUCCESS) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                                if (currentStep != InstallStep.SUCCESS) {
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    LinearProgressIndicator(
                                                        progress = { stepProgress.coerceIn(0f, 1f) },
                                                        modifier = Modifier.fillMaxWidth()
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // 8. Painel Retrátil de Logcat ao Vivo do Sistema
                                item {
                                    Card(modifier = Modifier.fillMaxWidth()) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { showLogcatPanel = !showLogcatPanel },
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(18.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "Logcat ao Vivo (PackageManager / PackageInstaller)",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                Icon(
                                                    imageVector = if (showLogcatPanel) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                    contentDescription = null
                                                )
                                            }

                                            AnimatedVisibility(visible = showLogcatPanel) {
                                                Column(modifier = Modifier.padding(top = 8.dp)) {
                                                    Surface(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(180.dp),
                                                        color = Color(0xFF1E1E1E),
                                                        shape = RoundedCornerShape(8.dp)
                                                    ) {
                                                        if (logcatLogs.isEmpty()) {
                                                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                                Text("Nenhum registro capturado ainda.", color = Color.Gray, fontSize = 11.sp)
                                                            }
                                                        } else {
                                                            LazyColumn(
                                                                state = logListState,
                                                                modifier = Modifier.fillMaxSize().padding(8.dp)
                                                            ) {
                                                                items(logcatLogs) { line ->
                                                                    val color = when {
                                                                        line.contains("[ERRO]") || line.contains("E/") || line.contains("Failure") -> Color(0xFFFF8A80)
                                                                        line.contains("[SUCESSO]") || line.contains("Success") -> Color(0xFFB9F6CA)
                                                                        else -> Color(0xFFE0E0E0)
                                                                    }
                                                                    Text(
                                                                        text = line,
                                                                        fontSize = 10.sp,
                                                                        fontFamily = FontFamily.Monospace,
                                                                        color = color
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Botões de Ação na base
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (currentStep == InstallStep.SUCCESS) {
                                    Button(
                                        onClick = {
                                            ApkInstallerManager.stopLogcatMonitoring()
                                            onDismiss()
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Concluir e Fechar")
                                    }
                                } else {
                                    val isInstalling = currentStep in listOf(
                                        InstallStep.CREATING_SESSION,
                                        InstallStep.WRITING_APKS,
                                        InstallStep.COMMITTING,
                                        InstallStep.COPYING_OBB,
                                        InstallStep.CLEANING_UP
                                    )

                                    OutlinedButton(
                                        onClick = {
                                            ApkInstallerManager.stopLogcatMonitoring()
                                            onDismiss()
                                        },
                                        enabled = !isInstalling,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Cancelar")
                                    }

                                    if (isShizukuReady) {
                                        Button(
                                            onClick = {
                                                showLogcatPanel = true
                                                scope.launch {
                                                    ApkInstallerManager.installPackageViaShizuku(
                                                        context = context,
                                                        metadata = meta,
                                                        flags = flags.value,
                                                        onStep = { s, msg, prog ->
                                                            currentStep = s
                                                            stepMessage = msg
                                                            stepProgress = prog
                                                            if (s == InstallStep.ERROR) {
                                                                errorMessage = msg
                                                            }
                                                        },
                                                        onResult = { success, msg ->
                                                            if (!success) {
                                                                errorMessage = msg
                                                            }
                                                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                                        }
                                                    )
                                                }
                                            },
                                            enabled = !isInstalling,
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                            modifier = Modifier.weight(1.5f)
                                        ) {
                                            Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Instalar (Shizuku)")
                                        }
                                    } else {
                                        Button(
                                            onClick = {
                                                ApkInstallerManager.installViaSystem(context, meta) { _, msg ->
                                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                                }
                                            },
                                            enabled = !isInstalling,
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                            modifier = Modifier.weight(1.5f)
                                        ) {
                                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Instalar (Sistema)")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal de Permissões Solicitadas
    if (showPermissionsDialog && metadata != null) {
        AlertDialog(
            onDismissRequest = { showPermissionsDialog = false },
            title = {
                Text("Permissões Solicitadas (${metadata!!.requestedPermissions.size})")
            },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
                    items(metadata!!.requestedPermissions) { perm ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (perm.isDangerous) Icons.Default.Warning else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (perm.isDangerous) MaterialTheme.colorScheme.error else Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = perm.simpleName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (perm.isDangerous) FontWeight.Bold else FontWeight.Normal,
                                    color = if (perm.isDangerous) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = perm.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray
                                )
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPermissionsDialog = false }) {
                    Text("Fechar")
                }
            }
        )
    }
}
