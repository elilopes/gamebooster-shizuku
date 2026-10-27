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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class ControllerMode(val titleRes: Int, val descRes: Int, val icon: String) {
    VIRTUAL_JOYSTICK(R.string.controller_mode_joystick, R.string.controller_mode_joystick_desc, "🕹️"),
    TILT_GYRO(R.string.controller_mode_tilt, R.string.controller_mode_tilt_desc, "📱"),
    DPAD_ARROWS(R.string.controller_mode_dpad, R.string.controller_mode_dpad_desc, "⬅️"),
    VOICE_COMMANDS(R.string.controller_mode_voice, R.string.controller_mode_voice_desc, "🎙️"),
    TOUCH_SAMPLING(R.string.controller_mode_touch, R.string.controller_mode_touch_desc, "⚡")
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
    val strNoneRecent = stringResource(id = R.string.controller_none_recent)
    var lastVoiceCommand by remember { mutableStateOf(strNoneRecent) }

    val strSavedToast = stringResource(id = R.string.controller_saved_toast)
    fun saveSettings() {
        prefs.edit()
            .putString("controller_mode", selectedMode.name)
            .putFloat("controller_sensitivity", sensitivity)
            .putFloat("controller_deadzone", deadzone)
            .putBoolean("controller_vibration", vibrationFeedback)
            .apply()
        Toast.makeText(context, strSavedToast, Toast.LENGTH_SHORT).show()
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
                        text = stringResource(id = R.string.controller_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(id = R.string.controller_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Seletor de Modo de Controle
        item {
            Text(
                text = stringResource(id = R.string.controller_mode_label),
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
                                    text = stringResource(id = mode.titleRes),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = stringResource(id = mode.descRes),
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
                        text = stringResource(id = R.string.controller_sensitivity_header),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = stringResource(id = R.string.controller_sensitivity_label, String.format(java.util.Locale.getDefault(), "%.1f", sensitivity)),
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
                        text = stringResource(id = R.string.controller_deadzone_label, deadzone.toInt()),
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
                            Text(stringResource(id = R.string.controller_vibration_title), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(id = R.string.controller_vibration_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        Text(stringResource(id = R.string.controller_save_btn))
                    }
                }
            }
        }

        // Área de Teste Interativa (Sandbox) baseada no modo selecionado
        item {
            val modeTitle = stringResource(id = selectedMode.titleRes)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(id = R.string.controller_test_title, modeTitle),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(id = R.string.controller_test_desc),
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
                                text = stringResource(
                                    id = R.string.controller_joystick_axes,
                                    String.format(java.util.Locale.getDefault(), "%.2f", joystickX),
                                    String.format(java.util.Locale.getDefault(), "%.2f", joystickY),
                                    String.format(java.util.Locale.getDefault(), "%.1f", sensitivity)
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        ControllerMode.TILT_GYRO -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Button(onClick = { tiltX -= 0.2f * sensitivity }) { Text(stringResource(id = R.string.controller_tilt_left)) }
                                    Button(onClick = { tiltX += 0.2f * sensitivity }) { Text(stringResource(id = R.string.controller_tilt_right)) }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Button(onClick = { tiltY -= 0.2f * sensitivity }) { Text(stringResource(id = R.string.controller_tilt_front)) }
                                    Button(onClick = { tiltY += 0.2f * sensitivity }) { Text(stringResource(id = R.string.controller_tilt_back)) }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = stringResource(
                                        id = R.string.controller_sim_angle,
                                        String.format(java.util.Locale.getDefault(), "%.2f", tiltX),
                                        String.format(java.util.Locale.getDefault(), "%.2f", tiltY)
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                OutlinedButton(onClick = { tiltX = 0f; tiltY = 0f }) {
                                    Text(stringResource(id = R.string.controller_reset_neutral))
                                }
                            }
                        }

                        ControllerMode.DPAD_ARROWS -> {
                            val toastUp = stringResource(id = R.string.controller_toast_dpad_up)
                            val toastLeft = stringResource(id = R.string.controller_toast_dpad_left)
                            val toastRight = stringResource(id = R.string.controller_toast_dpad_right)
                            val toastDown = stringResource(id = R.string.controller_toast_dpad_down)

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Button(onClick = { Toast.makeText(context, toastUp, Toast.LENGTH_SHORT).show() }) {
                                    Text(stringResource(id = R.string.controller_dpad_up))
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                                    Button(onClick = { Toast.makeText(context, toastLeft, Toast.LENGTH_SHORT).show() }) {
                                        Text(stringResource(id = R.string.controller_dpad_left))
                                    }
                                    Button(onClick = { Toast.makeText(context, toastRight, Toast.LENGTH_SHORT).show() }) {
                                        Text(stringResource(id = R.string.controller_dpad_right))
                                    }
                                }
                                Button(onClick = { Toast.makeText(context, toastDown, Toast.LENGTH_SHORT).show() }) {
                                    Text(stringResource(id = R.string.controller_dpad_down))
                                }
                            }
                        }

                        ControllerMode.VOICE_COMMANDS -> {
                            val voiceShoot = stringResource(id = R.string.controller_voice_shoot)
                            val voiceJump = stringResource(id = R.string.controller_voice_jump)
                            val voiceRun = stringResource(id = R.string.controller_voice_run)

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = stringResource(id = R.string.controller_last_voice, lastVoiceCommand),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { lastVoiceCommand = voiceShoot }) { Text(voiceShoot) }
                                    Button(onClick = { lastVoiceCommand = voiceJump }) { Text(voiceJump) }
                                    Button(onClick = { lastVoiceCommand = voiceRun }) { Text(voiceRun) }
                                }
                            }
                        }

                        ControllerMode.TOUCH_SAMPLING -> {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                            ) {
                                Text(
                                    text = stringResource(id = R.string.controller_touch_boost_active),
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
