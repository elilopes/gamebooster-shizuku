package com.gfx

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Modal de diagnóstico detalhado de privilégios do Shizuku e Root.
 * Exibe botões para cada método de verificação, executando e mostrando comandos e respostas detalhadas.
 */
@Composable
fun PrivilegeDiagnosticDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var activeResult by remember { mutableStateOf<ShizukuHelper.DiagnosticResult?>(null) }
    var isRunningTest by remember { mutableStateOf(false) }
    var currentTestName by remember { mutableStateOf<String?>(null) }
    var fullReport by remember { mutableStateOf<String?>(null) }

    val strAllTests = stringResource(id = R.string.privilege_diagnostic_run_all)
    val strSuccess = stringResource(id = R.string.privilege_diagnostic_success_status)
    val strFail = stringResource(id = R.string.privilege_diagnostic_fail_status)
    val strCopiedToast = stringResource(id = R.string.privilege_diagnostic_copied_toast)
    val strCopyLabel = stringResource(id = R.string.privilege_diagnostic_copy)

    fun runSingleTest(name: String, testAction: suspend () -> ShizukuHelper.DiagnosticResult) {
        if (isRunningTest) return
        isRunningTest = true
        currentTestName = name
        fullReport = null
        scope.launch {
            val res = withContext(Dispatchers.IO) {
                testAction()
            }
            activeResult = res
            isRunningTest = false
            currentTestName = null
        }
    }

    fun runAllTests() {
        if (isRunningTest) return
        isRunningTest = true
        currentTestName = strAllTests
        scope.launch {
            val results = withContext(Dispatchers.IO) {
                listOf(
                    ShizukuHelper.runTestPingBinder(),
                    ShizukuHelper.runTestGetUid(),
                    ShizukuHelper.runTestPermission(),
                    ShizukuHelper.runTestPgrepPidof(),
                    ShizukuHelper.runTestPsGrep(),
                    ShizukuHelper.runTestServiceList(),
                    ShizukuHelper.runTestContentProvider(context),
                    ShizukuHelper.runTestRootBinary(),
                    ShizukuHelper.runTestRootExecution(),
                    ShizukuHelper.runTestLibSuShell(),
                    ShizukuHelper.runTestRootManagers(context)
                )
            }
            val sb = StringBuilder()
            sb.append("=== RELATÓRIO COMPLETO DE PRIVILÉGIOS (SHIZUKU & ROOT) ===\n")
            sb.append("Data/Hora: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}\n\n")
            results.forEachIndexed { idx, res ->
                sb.append("[${idx + 1}/${results.size}] ${res.title}\n")
                sb.append("Comando/API: ${res.commandOrApi}\n")
                sb.append("Status: ${if (res.isSuccess) strSuccess else strFail}\n")
                sb.append("Detalhes:\n${res.output}\n")
                sb.append("------------------------------------------------------------\n\n")
            }
            fullReport = sb.toString()
            activeResult = null
            isRunningTest = false
            currentTestName = null
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .testTag("privilege_diagnostic_modal"),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Cabeçalho
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(id = R.string.privilege_diagnostic_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(id = R.string.privilege_diagnostic_close))
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(id = R.string.privilege_diagnostic_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Botão de Executar Todos
                Button(
                    onClick = { runAllTests() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isRunningTest,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(id = R.string.privilege_diagnostic_run_all), fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Área de Botões de Testes Individuais com Scroll
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Linha 1: Shizuku Service & API
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { runSingleTest("1. Binder Provider") { ShizukuHelper.runTestPingBinder() } },
                            modifier = Modifier.weight(1f),
                            enabled = !isRunningTest
                        ) {
                            Text(stringResource(id = R.string.shizuku_test_1), fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = { runSingleTest("2. Manager App") { ShizukuHelper.runTestGetUid() } },
                            modifier = Modifier.weight(1f),
                            enabled = !isRunningTest
                        ) {
                            Text(stringResource(id = R.string.shizuku_test_2), fontSize = 11.sp)
                        }
                    }

                    // Linha 2: Permissões e Versão
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { runSingleTest("3. Shizuku API") { ShizukuHelper.runTestPermission() } },
                            modifier = Modifier.weight(1f),
                            enabled = !isRunningTest
                        ) {
                            Text(stringResource(id = R.string.shizuku_test_3), fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = { runSingleTest("4. Shizuku.getVersion()") { ShizukuHelper.runTestPgrepPidof() } },
                            modifier = Modifier.weight(1f),
                            enabled = !isRunningTest
                        ) {
                            Text(stringResource(id = R.string.shizuku_test_4), fontSize = 11.sp)
                        }
                    }

                    // Linha 3: Uid e pgrep
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { runSingleTest("5. Shizuku.getUid()") { ShizukuHelper.runTestPsGrep() } },
                            modifier = Modifier.weight(1f),
                            enabled = !isRunningTest
                        ) {
                            Text(stringResource(id = R.string.shizuku_test_5), fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = { runSingleTest("6. pgrep/pidof") { ShizukuHelper.runTestServiceList() } },
                            modifier = Modifier.weight(1f),
                            enabled = !isRunningTest
                        ) {
                            Text(stringResource(id = R.string.shizuku_test_6), fontSize = 11.sp)
                        }
                    }

                    // Linha 4: Content Provider & ps grep
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { runSingleTest("7. ps grep") { ShizukuHelper.runTestContentProvider(context) } },
                            modifier = Modifier.weight(1f),
                            enabled = !isRunningTest
                        ) {
                            Text(stringResource(id = R.string.shizuku_test_7), fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = { runSingleTest("8. which su") { ShizukuHelper.runTestRootBinary() } },
                            modifier = Modifier.weight(1f),
                            enabled = !isRunningTest
                        ) {
                            Text(stringResource(id = R.string.shizuku_test_8), fontSize = 11.sp)
                        }
                    }

                    // Linha 5: su binary & su execution
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { runSingleTest("9. su -c id") { ShizukuHelper.runTestRootExecution() } },
                            modifier = Modifier.weight(1f),
                            enabled = !isRunningTest
                        ) {
                            Text(stringResource(id = R.string.shizuku_test_9), fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = { runSingleTest("libsu Shell") { ShizukuHelper.runTestLibSuShell() } },
                            modifier = Modifier.weight(1f),
                            enabled = !isRunningTest
                        ) {
                            Text(stringResource(id = R.string.shizuku_test_10), fontSize = 11.sp)
                        }
                    }

                    // Linha 6: Gerenciadores Root
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { runSingleTest("rootManagers") { ShizukuHelper.runTestRootManagers(context) } },
                            modifier = Modifier.weight(1f),
                            enabled = !isRunningTest
                        ) {
                            Text(stringResource(id = R.string.shizuku_test_11), fontSize = 11.sp)
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Terminal / Console com os resultados detalhados
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(id = R.string.privilege_diagnostic_console),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        val textToCopy = fullReport ?: activeResult?.let {
                            "TESTE: ${it.title}\nCOMANDO/API: ${it.commandOrApi}\nSTATUS: ${if (it.isSuccess) strSuccess else strFail}\n\n${it.output}"
                        }
                        if (textToCopy != null) {
                            TextButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Diagnóstico Privilégios", textToCopy))
                                    Toast.makeText(context, strCopiedToast, Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(strCopyLabel, fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 160.dp)
                            .background(Color(0xFF181825), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        if (isRunningTest) {
                            Row(
                                modifier = Modifier.align(Alignment.Center),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color(0xFF89B4FA))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = stringResource(id = R.string.privilege_diagnostic_running, currentTestName ?: "..."),
                                    color = Color(0xFFBAC2DE),
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        } else if (fullReport != null) {
                            Text(
                                text = fullReport!!,
                                color = Color(0xFFA6E3A1),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.horizontalScroll(rememberScrollState())
                            )
                        } else if (activeResult != null) {
                            val res = activeResult!!
                            Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (res.isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                                        contentDescription = null,
                                        tint = if (res.isSuccess) Color(0xFFA6E3A1) else Color(0xFFF38BA8),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${res.title} - ${if (res.isSuccess) strSuccess else strFail}",
                                        color = if (res.isSuccess) Color(0xFFA6E3A1) else Color(0xFFF38BA8),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = stringResource(id = R.string.privilege_diagnostic_command_api, res.commandOrApi),
                                    color = Color(0xFF89B4FA),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = res.output,
                                    color = Color(0xFFCDD6F4),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        } else {
                            Text(
                                text = stringResource(id = R.string.privilege_diagnostic_not_run),
                                color = Color(0xFF6C7086),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Botão Fechar
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(id = R.string.privilege_diagnostic_close))
                }
            }
        }
    }
}
