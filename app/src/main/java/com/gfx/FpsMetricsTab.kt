package com.gfx

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FpsMetricsTab(viewModel: GameViewModel) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val games by viewModel.games.collectAsState()
    val isRecording by viewModel.isBenchmarkRecording.collectAsState()
    val recordingPkg by viewModel.benchmarkRecordingPackage.collectAsState()
    val recordingProfile by viewModel.benchmarkRecordingProfile.collectAsState()
    val benchmarks by viewModel.benchmarkHistory.collectAsState()

    val networkEnabled by viewModel.networkMonitorEnabled.collectAsState()
    val latencyReport by viewModel.networkLatencyState.collectAsState()

    var selectedGamePackage by remember { mutableStateOf(games.firstOrNull()?.packageName ?: "") }
    var profileLabelInput by remember { mutableStateOf("Perfil Otimizado") }

    var compareProfileA by remember { mutableStateOf<BenchmarkManager.ProfileBenchmarkResult?>(null) }
    var compareProfileB by remember { mutableStateOf<BenchmarkManager.ProfileBenchmarkResult?>(null) }
    var abComparisonResult by remember { mutableStateOf<BenchmarkManager.ABComparison?>(null) }

    var selectedChartType by remember { mutableStateOf("line") }

    val prefs = remember { context.getSharedPreferences("gfx_benchmark_prefs", Context.MODE_PRIVATE) }
    var isRealMeasurementEnabled by remember {
        mutableStateOf(prefs.getBoolean("real_measurement_enabled", false))
    }

    LaunchedEffect(games) {
        if (selectedGamePackage.isEmpty() && games.isNotEmpty()) {
            selectedGamePackage = games.first().packageName
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Cabeçalho Principal com Switch de Medição Real Opcional
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isRealMeasurementEnabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Analytics,
                                contentDescription = null,
                                tint = if (isRealMeasurementEnabled) MaterialTheme.colorScheme.primary else Color.Gray,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "📊 Medição Real (Framestats)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isRealMeasurementEnabled) "Ativo: Medindo FPS real, 1% Low e Jank%" else "Opcional (Desativado)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isRealMeasurementEnabled) MaterialTheme.colorScheme.primary else Color.Gray
                                )
                            }
                        }
                        Switch(
                            checked = isRealMeasurementEnabled,
                            onCheckedChange = {
                                isRealMeasurementEnabled = it
                                prefs.edit().putBoolean("real_measurement_enabled", it).apply()
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Extrai métricas reais diretamente do subsistema do Android via 'dumpsys gfxinfo <pkg> framestats':\n• FPS Médio real\n• 1% Low FPS (percentil 99 dos tempos de quadro)\n• Jank % (porcentagem de quadros com engasgo)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 2. Card de Monitor de Latência & Jitter (Ping TCP sem privilégios)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Monitor de Rede (Ping TCP)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text("Mede latência e oscilação de rota até o servidor", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Switch(
                            checked = networkEnabled,
                            onCheckedChange = { viewModel.toggleNetworkMonitor(it) }
                        )
                    }

                    if (networkEnabled) {
                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Servidor: ${latencyReport.targetServerName}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                Text("Qualidade da rota: ${latencyReport.quality.name}", style = MaterialTheme.typography.labelSmall, color = when(latencyReport.quality) {
                                    NetworkLatencyMonitor.NetworkQuality.EXCELENTE -> Color(0xFF2E7D32)
                                    NetworkLatencyMonitor.NetworkQuality.BOM -> Color(0xFF1976D2)
                                    NetworkLatencyMonitor.NetworkQuality.INSTAVEL -> Color(0xFFF57C00)
                                    NetworkLatencyMonitor.NetworkQuality.CRITICO -> Color(0xFFD32F2F)
                                })
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Ping", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${latencyReport.pingMs} ms", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Jitter", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("±${latencyReport.jitterMs} ms", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (latencyReport.jitterMs > 15) Color(0xFFD32F2F) else Color(0xFF2E7D32))
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Seção de Gravação de Benchmark A/B em Tempo Real
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "🎮 Gravar Nova Medição de Perfil",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (!isRecording) {
                        // Seleção do Jogo
                        if (games.isNotEmpty()) {
                            var expanded by remember { mutableStateOf(false) }
                            val selectedGameName = games.find { it.packageName == selectedGamePackage }?.name ?: selectedGamePackage

                            OutlinedCard(
                                onClick = { expanded = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Jogo: $selectedGameName", style = MaterialTheme.typography.bodyMedium)
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            }

                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                games.forEach { game ->
                                    DropdownMenuItem(
                                        text = { Text(game.name) },
                                        onClick = {
                                            selectedGamePackage = game.packageName
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = profileLabelInput,
                            onValueChange = { profileLabelInput = it },
                            label = { Text("Nome do Perfil") },
                            placeholder = { Text("ex: Perfil A (Stock) ou Perfil B (MSAA off + 45FPS)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
                                if (selectedGamePackage.isNotBlank()) {
                                    viewModel.startBenchmark(selectedGamePackage, profileLabelInput) { started ->
                                        if (started) {
                                            Toast.makeText(context, "Benchmark iniciado! Jogue por alguns instantes.", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Falha ao iniciar medição no Shizuku.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Iniciar Gravação de Framestats")
                        }
                    } else {
                        // Painel ativo de gravação
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.error)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Gravando dados de quadros em tempo real...", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Jogo: $recordingPkg | Perfil: $recordingProfile", style = MaterialTheme.typography.bodySmall)
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        viewModel.stopBenchmark { result ->
                                            if (result != null) {
                                                Toast.makeText(context, "Medição concluída! Média: ${result.averageFps} FPS | 1% Low: ${result.onePercentLowFps} FPS", Toast.LENGTH_LONG).show()
                                            } else {
                                                Toast.makeText(context, "Erro ao coletar relatório.", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Finalizar e Salvar Perfil")
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Comparador A/B de Perfis
        if (benchmarks.size >= 2) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "⚖️ Comparação A/B de Perfis",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Seletor Perfil A
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Perfil A (Referência)", style = MaterialTheme.typography.labelSmall)
                                var menuA by remember { mutableStateOf(false) }
                                OutlinedButton(onClick = { menuA = true }, modifier = Modifier.fillMaxWidth()) {
                                    Text(compareProfileA?.profileName ?: "Selecionar A", maxLines = 1, fontSize = 11.sp)
                                }
                                DropdownMenu(expanded = menuA, onDismissRequest = { menuA = false }) {
                                    benchmarks.forEach { b ->
                                        DropdownMenuItem(
                                            text = { Text("${b.profileName} (${b.averageFps} FPS)") },
                                            onClick = {
                                                compareProfileA = b
                                                menuA = false
                                            }
                                        )
                                    }
                                }
                            }

                            // Seletor Perfil B
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Perfil B (Otimizado)", style = MaterialTheme.typography.labelSmall)
                                var menuB by remember { mutableStateOf(false) }
                                OutlinedButton(onClick = { menuB = true }, modifier = Modifier.fillMaxWidth()) {
                                    Text(compareProfileB?.profileName ?: "Selecionar B", maxLines = 1, fontSize = 11.sp)
                                }
                                DropdownMenu(expanded = menuB, onDismissRequest = { menuB = false }) {
                                    benchmarks.forEach { b ->
                                        DropdownMenuItem(
                                            text = { Text("${b.profileName} (${b.averageFps} FPS)") },
                                            onClick = {
                                                compareProfileB = b
                                                menuB = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        if (compareProfileA != null && compareProfileB != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    abComparisonResult = viewModel.compareBenchmarks(compareProfileA!!, compareProfileB!!)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Comparar Eficácia A/B")
                            }

                            abComparisonResult?.let { comp ->
                                Spacer(modifier = Modifier.height(12.dp))
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(comp.verdict, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Column {
                                                Text("FPS Médio:", style = MaterialTheme.typography.bodySmall)
                                                Text("${comp.profileA.averageFps} → ${comp.profileB.averageFps} FPS (${if (comp.avgFpsGainPercent >= 0) "+" else ""}${String.format(java.util.Locale.US, "%.1f", comp.avgFpsGainPercent)}%)", fontWeight = FontWeight.Bold, color = if (comp.avgFpsGainPercent >= 0) Color(0xFF2E7D32) else Color(0xFFD32F2F))
                                            }
                                            Column {
                                                Text("1% Low (Estabilidade):", style = MaterialTheme.typography.bodySmall)
                                                Text("${comp.profileA.onePercentLowFps} → ${comp.profileB.onePercentLowFps} FPS (${if (comp.onePercentLowGainPercent >= 0) "+" else ""}${String.format(java.util.Locale.US, "%.1f", comp.onePercentLowGainPercent)}%)", fontWeight = FontWeight.Bold, color = if (comp.onePercentLowGainPercent >= 0) Color(0xFF2E7D32) else Color(0xFFD32F2F))
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Jank: ${comp.profileA.jankPercentage}% → ${comp.profileB.jankPercentage}%", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Histórico de Medições Reais Salvas
        item {
            Text(
                text = "Histórico de Benchmarks Gravados (${benchmarks.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        if (benchmarks.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("Nenhum benchmark gravado ainda. Inicie uma gravação acima.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            items(benchmarks, key = { it.id }) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.profileName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text("Jogo: ${item.packageName} • ${item.durationSeconds}s de sessão", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "${item.averageFps} FPS",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("1% Low: ${item.onePercentLowFps} FPS", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = Color(0xFF1976D2))
                            Text("Jank: ${item.jankPercentage}%", style = MaterialTheme.typography.bodySmall, color = if (item.jankPercentage > 5) Color(0xFFD32F2F) else Color(0xFF2E7D32))
                            Text("Quadros: ${item.totalFrames}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
