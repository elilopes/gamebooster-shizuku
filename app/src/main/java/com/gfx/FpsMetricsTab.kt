package com.gfx

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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

data class FpsSessionRecord(
    val id: String,
    val gameName: String,
    val averageFps: Int,
    val peakFps: Int,
    val lowestFps: Int,
    val timestamp: String,
    val engine: String
)

@Composable
fun FpsMetricsTab(viewModel: GameViewModel) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val sessions = remember {
        listOf(
            FpsSessionRecord("1", "PUBG Mobile", 59, 60, 48, "Hoje, 15:30", "Unreal Engine"),
            FpsSessionRecord("2", "Genshin Impact", 52, 60, 35, "Hoje, 14:10", "Unity"),
            FpsSessionRecord("3", "Free Fire", 88, 90, 75, "Ontem, 20:45", "Unity"),
            FpsSessionRecord("4", "Call of Duty: Mobile", 116, 120, 95, "Ontem, 18:20", "Android Studio"),
            FpsSessionRecord("5", "Minecraft", 142, 160, 110, "23 Out, 22:00", "Java / OpenGL")
        )
    }

    var selectedChartType by remember { mutableStateOf("line") } // "line" or "bar"

    val avgOverall = if (sessions.isNotEmpty()) sessions.map { it.averageFps }.average().toInt() else 0
    val maxPeak = if (sessions.isNotEmpty()) sessions.maxOf { it.peakFps } else 0
    val minLow = if (sessions.isNotEmpty()) sessions.minOf { it.lowestFps } else 0

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "📊 Métricas & Gráficos de FPS (Analytics)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Análise de desempenho médio de FPS nas sessões de jogos registradas (Componente de visualização equivalente ao Recharts Analytics para Android).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Resumo de Métricas (Cards)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("FPS Médio", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("$avgOverall FPS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Pico Máximo", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("$maxPeak FPS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                    }
                }
                Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Mínimo Crítico", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("$minLow FPS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFFD32F2F))
                    }
                }
            }
        }

        // Componente Gráfico (Canvas interativo estilo Recharts Line / Bar)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Evolução do FPS por Sessão",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilterChip(
                                selected = selectedChartType == "line",
                                onClick = { selectedChartType = "line" },
                                label = { Text("Linha", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = selectedChartType == "bar",
                                onClick = { selectedChartType = "bar" },
                                label = { Text("Colunas", fontSize = 11.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    val primaryColor = MaterialTheme.colorScheme.primary
                    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
                    val strokePx = with(density) { 1.dp.toPx() }
                    val circleRadius = with(density) { 5.dp.toPx() }
                    val strokeWidthPx = with(density) { 3.dp.toPx() }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val canvasWidth = size.width
                            val canvasHeight = size.height
                            val maxFps = 180f
                            val stepX = canvasWidth / (sessions.size.coerceAtLeast(1))

                            // Linhas de Grade de Fundo
                            for (i in 0..4) {
                                val y = canvasHeight * (i / 4f)
                                drawLine(
                                    color = surfaceVariantColor,
                                    start = Offset(0f, y),
                                    end = Offset(canvasWidth, y),
                                    strokeWidth = strokePx
                                )
                            }

                            if (selectedChartType == "line") {
                                val path = Path()
                                val fillPath = Path()
                                fillPath.moveTo(0f, canvasHeight)

                                sessions.forEachIndexed { index, session ->
                                    val x = index * stepX + stepX / 2f
                                    val y = canvasHeight - (session.averageFps / maxFps) * canvasHeight

                                    if (index == 0) {
                                        path.moveTo(x, y)
                                        fillPath.lineTo(x, y)
                                    } else {
                                        path.lineTo(x, y)
                                        fillPath.lineTo(x, y)
                                    }

                                    drawCircle(
                                        color = primaryColor,
                                        radius = circleRadius,
                                        center = Offset(x, y)
                                    )
                                }

                                fillPath.lineTo(canvasWidth, canvasHeight)
                                fillPath.close()

                                drawPath(
                                    path = fillPath,
                                    brush = Brush.verticalGradient(
                                        colors = listOf(primaryColor.copy(alpha = 0.4f), primaryColor.copy(alpha = 0.0f)),
                                        startY = 0f,
                                        endY = canvasHeight
                                    )
                                )

                                drawPath(
                                    path = path,
                                    color = primaryColor,
                                    style = Stroke(width = strokeWidthPx)
                                )
                            } else {
                                val barWidth = stepX * 0.6f
                                sessions.forEachIndexed { index, session ->
                                    val x = index * stepX + (stepX - barWidth) / 2f
                                    val barHeight = (session.averageFps / maxFps) * canvasHeight
                                    val y = canvasHeight - barHeight

                                    drawRect(
                                        color = primaryColor,
                                        topLeft = Offset(x, y),
                                        size = Size(barWidth, barHeight)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        sessions.forEach { session ->
                            Text(
                                text = session.gameName.take(6) + "..",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "Histórico de Sessões de Jogos",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        items(sessions, key = { it.id }) { session ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = session.gameName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Engine: ${session.engine} • ${session.timestamp}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "${session.averageFps} FPS",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
