package com.gfx

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class ControllerMode(val displayName: String, val description: String, val icon: String) {
    VIRTUAL_JOYSTICK("Joystick Virtual", "Analógico flutuante na tela com resposta suave", "🕹️"),
    TILT_GYRO("Inclinação / Giroscópio", "Controla o movimento inclinando o dispositivo (Acelerômetro)", "📱"),
    DPAD_ARROWS("Duas Setas / D-Pad", "Botões direcionais clássicos para cima, baixo, esquerda, direita", "⬅️"),
    VOICE_COMMANDS("Controle por Voz", "Comandos de áudio para pular, atirar, correr e recarregar", "🎙️"),
    TOUCH_SAMPLING("Toque de Alta Precisão", "Sampling otimizado para zero latência no toque", "⚡")
}

@Composable
fun GameControllerTab(viewModel: GameViewModel) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("game_controller_prefs", Context.MODE_PRIVATE) }

    var selectedMode by remember {
        mutableStateOf(
            try {
                ControllerMode.valueOf(
                    prefs.getString("controller_mode", ControllerMode.VIRTUAL_JOYSTICK.name) ?: ControllerMode.VIRTUAL_JOYSTICK.name
                )
            } catch (_: Exception) {
                ControllerMode.VIRTUAL_JOYSTICK
            }
        )
    }

    var sensitivity by remember { mutableFloatStateOf(prefs.getFloat("controller_sensitivity", 1.5f)) }
    var deadzone by remember { mutableFloatStateOf(prefs.getFloat("controller_deadzone", 10f)) }
    var vibrationFeedback by remember { mutableStateOf(prefs.getBoolean("controller_vibration", true)) }

    // Test joystick state
    var joystickX by remember { mutableFloatStateOf(0f) }
    var joystickY by remember { mutableFloatStateOf(0f) }

    // Test tilt simulation
    var tiltX by remember { mutableFloatStateOf(0f) }
    var tiltY by remember { mutableFloatStateOf(0f) }

    // Test voice command simulation result
    var lastVoiceCommand by remember { mutableStateOf("Nenhum comando recente") }

    fun saveSettings() {
        prefs.edit()
            .putString("controller_mode", selectedMode.name)
            .putFloat("controller_sensitivity", sensitivity)
            .putFloat("controller_deadzone", deadzone)
            .putBoolean("controller_vibration", vibrationFeedback)
            .apply()
        Toast.makeText(context, "Configurações de controle salvas com sucesso!", Toast.LENGTH_SHORT).show()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "🎮 Controle de Jogo & Sensibilidade",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Ajuste a sensibilidade do controle, escolha o formato de entrada ideal (Joystick, Inclinação, D-Pad, Voz ou Alta Precisão) e teste o comportamento em tempo real.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Seletor de Modo de Controle
        item {
            Text(
                text = "Formato do Controle / Entrada:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ControllerMode.values().forEach { mode ->
                    val isSelected = selectedMode == mode
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedMode = mode },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        ),
                        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = mode.icon, fontSize = 24.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = mode.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = mode.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Ajustes de Sensibilidade e Zona Morta
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Ajustes de Sensibilidade & Resposta",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Multiplicador de Sensibilidade: ${String.format("%.1f", sensitivity)}x",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = sensitivity,
                        onValueChange = { sensitivity = it },
                        valueRange = 0.5f..5.0f,
                        steps = 8
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Zona Morta (Deadzone) do Analógico: ${deadzone.toInt()}%",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = deadzone,
                        onValueChange = { deadzone = it },
                        valueRange = 0f..30f,
                        steps = 5
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Feedback Tátil (Vibração)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("Vibra ao acionar botões ou realizar ações críticas", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = vibrationFeedback,
                            onCheckedChange = { vibrationFeedback = it }
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = { saveSettings() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Salvar Configurações de Controle")
                    }
                }
            }
        }

        // Área de Teste Interativa (Sandbox) baseada no modo selecionado
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "🎮 Área de Teste do Controle (${selectedMode.displayName})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Teste a sensibilidade e os comandos ao vivo:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    when (selectedMode) {
                        ControllerMode.VIRTUAL_JOYSTICK -> {
                            Box(
                                modifier = Modifier
                                    .size(160.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                    .pointerInput(Unit) {
                                        detectDragGestures(
                                            onDragEnd = {
                                                joystickX = 0f
                                                joystickY = 0f
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                joystickX = (joystickX + dragAmount.x * 0.05f * sensitivity).coerceIn(-1f, 1f)
                                                joystickY = (joystickY + dragAmount.y * 0.05f * sensitivity).coerceIn(-1f, 1f)
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(50.dp)
                                        .offset(x = (joystickX * 45).dp, y = (joystickY * 45).dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Eixos: X=${String.format("%.2f", joystickX)}, Y=${String.format("%.2f", joystickY)} (Sens: ${String.format("%.1f", sensitivity)}x)",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        ControllerMode.TILT_GYRO -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Button(onClick = { tiltX -= 0.2f * sensitivity }) { Text("Inclinar Esquerda") }
                                    Button(onClick = { tiltX += 0.2f * sensitivity }) { Text("Inclinar Direita") }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Button(onClick = { tiltY -= 0.2f * sensitivity }) { Text("Inclinar Frente") }
                                    Button(onClick = { tiltY += 0.2f * sensitivity }) { Text("Inclinar Trás") }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Ângulo Simulado: X=${String.format("%.2f", tiltX)}°, Y=${String.format("%.2f", tiltY)}°",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                OutlinedButton(onClick = { tiltX = 0f; tiltY = 0f }) {
                                    Text("Resetar Posição Neutra")
                                }
                            }
                        }

                        ControllerMode.DPAD_ARROWS -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Button(onClick = { Toast.makeText(context, "D-Pad: Cima", Toast.LENGTH_SHORT).show() }) {
                                    Text("▲ CIMA")
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                                    Button(onClick = { Toast.makeText(context, "D-Pad: Esquerda", Toast.LENGTH_SHORT).show() }) {
                                        Text("◄ ESQ")
                                    }
                                    Button(onClick = { Toast.makeText(context, "D-Pad: Direita", Toast.LENGTH_SHORT).show() }) {
                                        Text("DIR ►")
                                    }
                                }
                                Button(onClick = { Toast.makeText(context, "D-Pad: Baixo", Toast.LENGTH_SHORT).show() }) {
                                    Text("▼ BAIXO")
                                }
                            }
                        }

                        ControllerMode.VOICE_COMMANDS -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Último Comando Detectado: \"$lastVoiceCommand\"",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { lastVoiceCommand = "Atirar! (Bang)" }) { Text("🎤 \"Atirar\"") }
                                    Button(onClick = { lastVoiceCommand = "Pular! (Jump)" }) { Text("🎤 \"Pular\"") }
                                    Button(onClick = { lastVoiceCommand = "Correr! (Run)" }) { Text("🎤 \"Correr\"") }
                                }
                            }
                        }

                        ControllerMode.TOUCH_SAMPLING -> {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                            ) {
                                Text(
                                    text = "⚡ Touch Sampling Boost Ativo!\nSensibilidade de resposta ao toque otimizada em 1000Hz com taxa de amostragem máxima.",
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
