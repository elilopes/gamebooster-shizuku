package com.gfx

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // Handle notification permission result
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applySavedLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        LocaleHelper.applySavedLocale(this)
        super.onCreate(savedInstanceState)

        /*
        * 🛡️ OBRIGATÓRIO: Inicializa o ShizukuHelper passando o Contexto da aplicação
        * Deve ser chamado antes de qualquer operação ou observação de estado.
        * Inicia o monitoramento em tempo real. 
        */
        ShizukuHelper.initialize(applicationContext)

        preventAppKillInBackground()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Opcional: Atualiza o status do Shizuku toda vez que o usuário retorna ao app
        try {
            ShizukuHelper.updateStatus()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun preventAppKillInBackground() {
        try {
            GameForegroundService.keepAliveActive = true
            GameForegroundService.startService(this)
            android.util.Log.d("AppKeepAlive", "Serviço de permanência em segundo plano acionado com sucesso.")
        } catch (e: Exception) {
            android.util.Log.e("AppKeepAlive", "Erro ao configurar permanência em segundo plano: ${e.message}")
        }
    }
}

@Composable
fun SystemStatusBadge() {
    val isShizukuActive by ShizukuHelper.isShizukuGranted.collectAsState()
    val isBinderAlive by ShizukuHelper.isBinderAlive.collectAsState()
    val isRootActive = remember { mutableStateOf(ShizukuHelper.isRootAvailable()) }
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                ShizukuHelper.updateStatus()
                isRootActive.value = ShizukuHelper.isRootAvailable()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val isActive = isShizukuActive || isRootActive.value
    val statusText = when {
        isRootActive.value -> "Root: ON"
        isShizukuActive -> "Shizuku: ON"
        isBinderAlive -> "Shizuku: PENDENTE (Toque p/ autorizar)"
        else -> "Root/Shizuku: OFF"
    }

    val containerColor = when {
        isActive -> MaterialTheme.colorScheme.primaryContainer
        isBinderAlive -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.errorContainer
    }

    val contentColor = when {
        isActive -> MaterialTheme.colorScheme.onPrimaryContainer
        isBinderAlive -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onErrorContainer
    }

    var showDiagnosticModal by remember { mutableStateOf(false) }

    if (showDiagnosticModal) {
        PrivilegeDiagnosticDialog(onDismiss = { showDiagnosticModal = false })
    }

    Surface(
        color = containerColor,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .testTag("system_status_badge")
            .clickable {
                showDiagnosticModal = true
            }
    ) {
        Text(
            text = statusText,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            color = contentColor
        )
    }
}

@Composable
fun ExitBadge() {
    val context = LocalContext.current
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.clickable {
            exitApp(context)
        }
    ) {
        Text(
            text = stringResource(id = R.string.exit_label),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}

fun exitApp(context: Context) {
    try {
        GameForegroundService.stopService(context)
    } catch (e: Exception) {
        e.printStackTrace()
    }
    (context as? Activity)?.finishAffinity()
    kotlin.system.exitProcess(0)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: GameViewModel = viewModel()) {
    val context = LocalContext.current
    val thermalMonitorEnabled by viewModel.thermalMonitorEnabled.collectAsState()
    val fpsAnalyticsEnabled by viewModel.fpsAnalyticsEnabled.collectAsState()
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    var termsAccepted by remember { mutableStateOf(prefs.getBoolean("terms_accepted", false)) }
    var termsChecked by remember { mutableStateOf(false) }

    val baseTabs = listOf(
        stringResource(id = R.string.tab_games),
        stringResource(id = R.string.tab_background),
        stringResource(id = R.string.tab_boost_settings),
        stringResource(id = R.string.tab_backup_app),
        stringResource(id = R.string.tab_scripts),
        stringResource(id = R.string.tab_controller),
        stringResource(id = R.string.tab_setedit)
    )

    val thermalTitle = stringResource(id = R.string.tab_thermal_monitor)
    val fpsTitle = stringResource(id = R.string.tab_fps_metrics)

    val tabs = remember(thermalMonitorEnabled, fpsAnalyticsEnabled) {
        mutableListOf<String>().apply {
            if (thermalMonitorEnabled) add(thermalTitle)
            if (fpsAnalyticsEnabled) add(fpsTitle)
            addAll(baseTabs)
        }
    }

    val activeIndex = selectedTabIndex.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))

    var showMenu by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showHardwareDialog by remember { mutableStateOf(false) }
    var showLogsDialog by remember { mutableStateOf(false) }
    var hardwareReportText by remember { mutableStateOf("") }

    val shizukuAlert by ShizukuHelper.shizukuAlert.collectAsState()
    val shizukuSuccess by ShizukuHelper.shizukuSuccess.collectAsState()

    DisposableEffect(Unit) {
        val prevAlert = ShizukuHelper.onShizukuAlert
        val prevSuccess = ShizukuHelper.onShizukuSuccess
        ShizukuHelper.onShizukuAlert = { msg ->
            (context as? Activity)?.runOnUiThread {
                Toast.makeText(context, "⚠️ $msg", Toast.LENGTH_SHORT).show()
            }
        }
        ShizukuHelper.onShizukuSuccess = { msg ->
            (context as? Activity)?.runOnUiThread {
                Toast.makeText(context, "✅ $msg", Toast.LENGTH_SHORT).show()
            }
        }
        onDispose {
            ShizukuHelper.onShizukuAlert = prevAlert
            ShizukuHelper.onShizukuSuccess = prevSuccess
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_chip),
                        contentDescription = "Game Booster Chip",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(id = R.string.app_name))
                }
            },
            actions = {
                SystemStatusBadge()
                Spacer(modifier = Modifier.width(6.dp))
                ExitBadge()
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(id = R.string.menu_view_logs)) },
                        onClick = {
                            showLogsDialog = true
                            showMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(id = R.string.menu_language)) },
                        onClick = {
                            showLanguageDialog = true
                            showMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (thermalMonitorEnabled) {
                                    stringResource(id = R.string.menu_disable_thermal)
                                } else {
                                    stringResource(id = R.string.menu_enable_thermal)
                                }
                            )
                        },
                        onClick = {
                            viewModel.toggleThermalMonitor(!thermalMonitorEnabled)
                            showMenu = false
                            selectedTabIndex = 0
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (fpsAnalyticsEnabled) {
                                    stringResource(id = R.string.menu_disable_fps_analytics)
                                } else {
                                    stringResource(id = R.string.menu_enable_fps_analytics)
                                }
                            )
                        },
                        onClick = {
                            viewModel.toggleFpsAnalytics(!fpsAnalyticsEnabled)
                            showMenu = false
                            selectedTabIndex = 0
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(id = R.string.menu_export_hardware)) },
                        onClick = {
                            hardwareReportText = HardwareHelper.getHardwareInfo(context)
                            showHardwareDialog = true
                            showMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(id = R.string.menu_about)) },
                        onClick = {
                            showAbout = true
                            showMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(id = R.string.menu_exit)) },
                        onClick = {
                            exitApp(context)
                            showMenu = false
                        }
                    )
                }
            }
        )

        ScrollableTabRow(
            selectedTabIndex = activeIndex,
            edgePadding = 16.dp
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = activeIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { Text(title) }
                )
            }
        }

        shizukuAlert?.let { alertMsg ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = alertMsg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { ShizukuHelper.clearAlert() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(id = R.string.logs_close),
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        shizukuSuccess?.let { successMsg ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = successMsg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { ShizukuHelper.clearSuccess() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(id = R.string.logs_close),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        val currentTabTitle = tabs.getOrNull(activeIndex) ?: ""
        when {
            thermalMonitorEnabled && activeIndex == 0 -> ThermalMonitorTab(viewModel)
            currentTabTitle == stringResource(id = R.string.tab_games) -> GameListTab(viewModel)
            currentTabTitle == stringResource(id = R.string.tab_background) -> BackgroundAppsTab(viewModel)
            currentTabTitle == stringResource(id = R.string.tab_boost_settings) -> BoostSettingsTab(viewModel)
            currentTabTitle == stringResource(id = R.string.tab_backup_app) -> BackupAppTab(viewModel)
            currentTabTitle == stringResource(id = R.string.tab_scripts) -> ScriptsTab(viewModel)
            currentTabTitle == stringResource(id = R.string.tab_controller) -> GameControllerTab(viewModel)
            currentTabTitle == stringResource(id = R.string.tab_fps_metrics) -> FpsMetricsTab(viewModel)
            currentTabTitle == stringResource(id = R.string.tab_setedit) -> SetEditTab(viewModel)
            else -> GameListTab(viewModel)
        }
    }

    if (showAbout) {
        val uriHandler = LocalUriHandler.current
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_chip),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(id = R.string.about_title))
                }
            },
            text = {
                Column {
                    Text(stringResource(id = R.string.about_description))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(id = R.string.shizuku_github_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "https://github.com/RikkaApps/Shizuku-API",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier
                            .clickable {
                                uriHandler.openUri("https://github.com/RikkaApps/Shizuku-API")
                            }
                            .padding(vertical = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            uriHandler.openUri("https://github.com/RikkaApps/Shizuku-API")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(id = R.string.shizuku_download_btn))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) {
                    Text(stringResource(id = R.string.ok))
                }
            }
        )
    }

    if (showLanguageDialog) {
        val currentLang = remember { LocaleHelper.getSavedLanguage(context) }
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(id = R.string.language_dialog_title)) },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                    items(LocaleHelper.SUPPORTED_LANGUAGES) { langItem ->
                        val isSelected = currentLang.equals(langItem.code, ignoreCase = true)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    LocaleHelper.setLocale(context, langItem.code)
                                    showLanguageDialog = false
                                    (context as? Activity)?.recreate()
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = langItem.nativeName,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = langItem.displayName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(stringResource(id = R.string.cancel))
                }
            }
        )
    }

    if (showHardwareDialog) {
        AlertDialog(
            onDismissRequest = { showHardwareDialog = false },
            title = { Text(stringResource(id = R.string.menu_export_hardware)) },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                    item {
                        Text(
                            text = hardwareReportText,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.exportHardwareReport { file ->
                        val msg = if (file != null) context.getString(R.string.hardware_export_success) else context.getString(R.string.hardware_export_fail)
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                    showHardwareDialog = false
                }) {
                    Text(stringResource(id = R.string.hardware_export_btn))
                }
            },
            dismissButton = {
                TextButton(onClick = { showHardwareDialog = false }) {
                    Text(stringResource(id = R.string.cancel))
                }
            }
        )
    }

    if (!termsAccepted) {
        AlertDialog(
            onDismissRequest = { /* Não fecha ao clicar fora - aceitação obrigatória para prosseguir */ },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(id = R.string.terms_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(id = R.string.terms_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { termsChecked = !termsChecked }
                    ) {
                        Checkbox(
                            checked = termsChecked,
                            onCheckedChange = { termsChecked = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(id = R.string.terms_accept_checkbox),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (termsChecked) {
                            prefs.edit().putBoolean("terms_accepted", true).apply()
                            termsAccepted = true
                        }
                    },
                    enabled = termsChecked
                ) {
                    Text(stringResource(id = R.string.terms_start_btn))
                }
            }
        )
    }

    if (showLogsDialog) {
        LogsDialog(onDismiss = { showLogsDialog = false })
    }
}

@Composable
fun LogsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    val logs by ShizukuHelper.logEntries.collectAsState()
    val crashHistory by CrashReporterHelper.crashHistory.collectAsState()
    val lastCrashAlert by CrashReporterHelper.lastCrashAlert.collectAsState()

    var selectedTab by remember { mutableStateOf(0) }
    var systemLogcat by remember { mutableStateOf<String?>(null) }
    var isLoadingLogcat by remember { mutableStateOf(false) }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty() && selectedTab == 0) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab == 1 && systemLogcat == null) {
            isLoadingLogcat = true
            scope.launch(Dispatchers.IO) {
                val captured = CrashReporterHelper.captureLogcat(120)
                withContext(Dispatchers.Main) {
                    systemLogcat = captured
                    isLoadingLogcat = false
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(id = R.string.logs_dialog_title),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Alerta caso tenha ocorrido um crash/force close recente
                if (!lastCrashAlert.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(id = R.string.crash_detected_banner),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                // Abas de navegação (Console Shizuku, Logcat, Falhas)
                TabRow(
                    selectedTabIndex = selectedTab,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text(stringResource(id = R.string.logs_tab_console), fontSize = 11.sp) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text(stringResource(id = R.string.logs_tab_logcat), fontSize = 11.sp) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text(stringResource(id = R.string.logs_tab_crashes), fontSize = 11.sp) }
                    )
                }

                when (selectedTab) {
                    0 -> {
                        // ABA 0: CONSOLE SHIZUKU / APP LOGS
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilledTonalButton(
                                onClick = { ShizukuHelper.isShizukuAvailable() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text(text = stringResource(id = R.string.logs_check_available), fontSize = 10.sp)
                            }
                            FilledTonalButton(
                                onClick = {
                                    Toast.makeText(context, context.getString(R.string.checking_shizuku_installed), Toast.LENGTH_SHORT).show()
                                    ShizukuHelper.verifyShizukuInstallationAndState(context)
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text(text = stringResource(id = R.string.logs_check_installed), fontSize = 10.sp)
                            }
                            FilledTonalButton(
                                onClick = { ShizukuHelper.hasPermission(context, dispatchFeedback = true) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text(text = stringResource(id = R.string.logs_check_permission), fontSize = 10.sp)
                            }
                            FilledTonalButton(
                                onClick = { ShizukuHelper.isShizukuRunningProvider(context) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text(text = stringResource(id = R.string.logs_check_provider), fontSize = 10.sp)
                            }
                            FilledTonalButton(
                                onClick = { ShizukuHelper.isShizukuBinderAlive() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text(text = stringResource(id = R.string.logs_check_reflection), fontSize = 10.sp)
                            }
                            FilledTonalButton(
                                onClick = { ShizukuHelper.isShizukuProcessRunning() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text(text = stringResource(id = R.string.logs_check_process), fontSize = 10.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (logs.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(id = R.string.logs_empty),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            } else {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(8.dp)
                                ) {
                                    items(logs) { entry ->
                                        val textColor = when {
                                            entry.contains("[ERRO]") -> Color(0xFFFF6B68)
                                            entry.contains("concedida") || entry.contains("OK") || entry.contains("sucesso") -> Color(0xFF81C784)
                                            else -> Color(0xFFE0E0E0)
                                        }
                                        Text(
                                            text = entry,
                                            fontSize = 11.sp,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            color = textColor,
                                            modifier = Modifier.padding(vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        // ABA 1: LOGCAT DO SISTEMA
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Logcat (Últimas 120 linhas)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FilledTonalButton(
                                onClick = {
                                    isLoadingLogcat = true
                                    scope.launch(Dispatchers.IO) {
                                        val captured = CrashReporterHelper.captureLogcat(120)
                                        withContext(Dispatchers.Main) {
                                            systemLogcat = captured
                                            isLoadingLogcat = false
                                        }
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(id = R.string.logs_refresh_logcat), fontSize = 10.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (isLoadingLogcat) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            } else {
                                val textContent = systemLogcat ?: "Toque em Atualizar Logcat para inspecionar registros."
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                        .padding(8.dp)
                                ) {
                                    Text(
                                        text = textContent,
                                        fontSize = 10.sp,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        color = Color(0xFFD4D4D4)
                                    )
                                }
                            }
                        }
                    }

                    2 -> {
                        // ABA 2: HISTÓRICO DE FALHAS E CRASHES
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (crashHistory.isEmpty() && lastCrashAlert.isNullOrBlank()) {
                                Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = stringResource(id = R.string.no_crashes_found),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            } else {
                                val allCrashes = buildList {
                                    lastCrashAlert?.let { add("--- ÚLTIMO CRASH SALVO ---\n$it") }
                                    addAll(crashHistory)
                                }
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(8.dp)
                                ) {
                                    items(allCrashes) { item ->
                                        Text(
                                            text = item,
                                            fontSize = 10.sp,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            color = Color(0xFFFF8A80),
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        )
                                        HorizontalDivider(color = Color(0xFF333333))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Botão para gerar Bug Report completo
                FilledTonalButton(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            val report = CrashReporterHelper.generateFullBugReport(context)
                            withContext(Dispatchers.Main) {
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(report))
                                Toast.makeText(context, context.getString(R.string.bugreport_generated_toast), Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(stringResource(id = R.string.logs_generate_bugreport), fontSize = 11.sp)
                }

                // Botão para copiar conteúdo da aba atual
                TextButton(
                    onClick = {
                        val textToCopy = when (selectedTab) {
                            0 -> logs.joinToString("\n")
                            1 -> systemLogcat ?: ""
                            2 -> crashHistory.joinToString("\n")
                            else -> ""
                        }
                        if (textToCopy.isNotBlank()) {
                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(textToCopy))
                            Toast.makeText(context, context.getString(R.string.logs_copied_toast), Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text(stringResource(id = R.string.logs_copy))
                }

                // Botão para limpar conteúdo
                TextButton(
                    onClick = {
                        when (selectedTab) {
                            0 -> ShizukuHelper.clearLogs()
                            1 -> systemLogcat = null
                            2 -> CrashReporterHelper.clearCrashReports(context)
                        }
                    }
                ) {
                    Text(stringResource(id = R.string.logs_clear))
                }

                TextButton(onClick = onDismiss) {
                    Text(stringResource(id = R.string.logs_close))
                }
            }
        }
    )
}

@Composable
fun ThermalMonitorTab(viewModel: GameViewModel) {
    val temp by viewModel.temperature.collectAsState()
    val mem by viewModel.memoryUsage.collectAsState()

    Column(modifier = Modifier.padding(16.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(id = R.string.thermal_monitor_header),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Switch(
                        checked = viewModel.autoMemoryRelease.collectAsState().value,
                        onCheckedChange = { viewModel.autoMemoryRelease.value = it }
                    )
                }
                Text(
                    text = stringResource(id = R.string.auto_memory_release_desc),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                val tempText = if (temp > 0f) String.format(java.util.Locale.US, "Battery Temp: %.1f°C", temp) else "Battery Temp: --°C"
                val memText = if (mem > 0f) String.format(java.util.Locale.US, "Memory Usage: %.1f%%", mem) else "Memory Usage: --%"

                Text(
                    text = tempText,
                    style = MaterialTheme.typography.headlineSmall,
                    color = when {
                        temp >= 45f -> MaterialTheme.colorScheme.error
                        temp >= 38f -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = memText,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { (temp / 80f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(id = R.string.thermal_monitor_warning),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun AppIconImage(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap = remember(packageName) {
        try {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: 96
            val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: 96
            val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bmp)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bmp.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = modifier
        )
    } else {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            modifier = modifier,
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun GameListTab(viewModel: GameViewModel) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val games by viewModel.games.collectAsState()
    val updateResults by viewModel.updateCheckResults.collectAsState()
    val isCheckingAllUpdates by viewModel.isCheckingAllUpdates.collectAsState()
    var selectedGame by remember { mutableStateOf<GameApp?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    var showUpdatesDialog by remember { mutableStateOf(false) }
    var adScanResult by remember { mutableStateOf<AdBlockHelper.AdScanResult?>(null) }
    var integrityScanResult by remember { mutableStateOf<GameIntegrityHelper.IntegritySystemResult?>(null) }
    var pakBundleReport by remember { mutableStateOf<GameIntegrityHelper.PakBundleCompressionReport?>(null) }
    var isCompressingPakBundle by remember { mutableStateOf(false) }
    var compactResultMsg by remember { mutableStateOf<String?>(null) }
    var showGfxDialog by remember { mutableStateOf(false) }
    var selectedGfxGame by remember { mutableStateOf<GameApp?>(null) }
    var currentGfxConfig by remember { mutableStateOf(GfxTools.GfxConfig()) }
    var gfxApplyResultMsg by remember { mutableStateOf<String?>(null) }

    var showManualAddDialog by remember { mutableStateOf(false) }

    var customResolutionInput by remember { mutableStateOf("1280x720") }
    var customDensityInput by remember { mutableStateOf("320") }

    var showBackupDialog by remember { mutableStateOf(false) }
    var backupTargetGame by remember { mutableStateOf<GameApp?>(null) }
    var includeApk by remember { mutableStateOf(true) }
    var includeObb by remember { mutableStateOf(true) }
    var includeData by remember { mutableStateOf(true) }
    var compressZip by remember { mutableStateOf(true) }
    var backupResultMsg by remember { mutableStateOf<String?>(null) }

    var showActionsDialog by remember { mutableStateOf(false) }
    var actionsTargetGame by remember { mutableStateOf<GameApp?>(null) }

    LazyColumn(modifier = Modifier.padding(16.dp)) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
            ) {
                Text(
                    text = stringResource(id = R.string.hardware_warning_disclaimer),
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(id = R.string.game_list_guidance),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(id = R.string.game_native_resolution),
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text(
                                text = viewModel.nativeResolution,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val isShizukuGranted by ShizukuHelper.isShizukuGranted.collectAsState()
                            val isBinderAlive by ShizukuHelper.isBinderAlive.collectAsState()
                            Button(
                                onClick = {
                                    Toast.makeText(context, context.getString(R.string.checking_shizuku_installed), Toast.LENGTH_SHORT).show()
                                    val status = ShizukuHelper.verifyShizukuInstallationAndState(context) { msg, isErr ->
                                        Toast.makeText(context, msg, if (isErr) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
                                    }
                                    when (status) {
                                        ShizukuHelper.ShizukuDetailedStatus.NOT_INSTALLED -> {
                                            ShizukuHelper.openShizukuApp(context)
                                        }
                                        ShizukuHelper.ShizukuDetailedStatus.INSTALLED_BUT_NOT_RUNNING -> {
                                            ShizukuHelper.openShizukuApp(context)
                                        }
                                        ShizukuHelper.ShizukuDetailedStatus.RUNNING_NOT_AUTHORIZED -> {
                                            val requested = ShizukuHelper.requestPermission()
                                            if (!requested) {
                                                ShizukuHelper.openShizukuApp(context)
                                            }
                                        }
                                        ShizukuHelper.ShizukuDetailedStatus.RUNNING_AND_AUTHORIZED -> {
                                            // Shizuku já autorizado e ativo
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = when {
                                        isShizukuGranted -> MaterialTheme.colorScheme.primary
                                        isBinderAlive -> MaterialTheme.colorScheme.tertiary
                                        else -> MaterialTheme.colorScheme.secondary
                                    }
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = when {
                                        isShizukuGranted -> stringResource(id = R.string.shizuku_btn_active)
                                        isBinderAlive -> stringResource(id = R.string.shizuku_btn_authorize)
                                        else -> stringResource(id = R.string.shizuku_btn_verify)
                                    },
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = { showManualAddDialog = true },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                        ) {
                            Text(stringResource(id = R.string.game_add_manually), style = MaterialTheme.typography.labelSmall)
                        }
                        Button(
                            onClick = {
                                Toast.makeText(context, context.getString(R.string.auto_scan_starting), Toast.LENGTH_SHORT).show()
                                viewModel.autoScanAndAddGames { count ->
                                    val msg = context.getString(R.string.auto_scan_result, count)
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                        ) {
                            Text(stringResource(id = R.string.game_auto_scan), style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = { showUpdatesDialog = true },
                            modifier = Modifier.weight(1.2f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                        ) {
                            Text(stringResource(id = R.string.game_check_updates), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (games.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(44.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource(id = R.string.no_apps_found),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(id = R.string.game_list_guidance),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        items(games) { game ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable {
                        if (ShizukuHelper.hasPermission()) {
                            selectedGame = game
                            customResolutionInput = game.profile?.resolution ?: "1280x720"
                            customDensityInput = (game.profile?.density ?: viewModel.getNativeDensity()).toString()
                            showDialog = true
                        }
                    }
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = game.name,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.removeGame(game.packageName) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove App",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    // Badge da Engine / Modo Web / Origem
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(game.engine.badgeColorHex).copy(alpha = 0.16f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(game.engine.badgeColorHex).copy(alpha = 0.5f)),
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = game.engine.iconEmoji,
                                    fontSize = 10.sp
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = game.engine.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(game.engine.badgeColorHex)
                                )
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Text(
                                text = "v${game.versionName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                        val updateInfo = updateResults[game.packageName]
                        if (updateInfo?.status == UpdateBadgeStatus.UPDATE_AVAILABLE) {
                            Surface(
                                shape = MaterialTheme.shapes.extraSmall,
                                color = Color(0xFFFFF3E0),
                                modifier = Modifier
                                    .padding(end = 6.dp)
                                    .clickable { showUpdatesDialog = true }
                            ) {
                                Text(
                                    text = "🟠 v${updateInfo.latestVersion} na web",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFE65100),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        } else if (updateInfo?.status == UpdateBadgeStatus.UP_TO_DATE) {
                            Surface(
                                shape = MaterialTheme.shapes.extraSmall,
                                color = Color(0xFFE8F5E9),
                                modifier = Modifier.padding(end = 6.dp)
                            ) {
                                Text(
                                    text = "🟢 Atualizado",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF2E7D32),
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                        val res = game.profile?.resolution ?: viewModel.nativeResolution
                        Text(
                            text = "Res: $res",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                actionsTargetGame = game
                                showActionsDialog = true
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(id = R.string.game_actions_btn), style = MaterialTheme.typography.labelMedium)
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = { viewModel.launchGame(game.packageName) },
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text(stringResource(id = R.string.game_launch), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }

    if (showActionsDialog && actionsTargetGame != null) {
        val targetGame = actionsTargetGame!!
        AlertDialog(
            onDismissRequest = { showActionsDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${stringResource(id = R.string.game_actions_title)}: ${targetGame.name}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    IconButton(onClick = { showActionsDialog = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            showActionsDialog = false
                            selectedGame = targetGame
                            customResolutionInput = targetGame.profile?.resolution ?: "1280x720"
                            customDensityInput = (targetGame.profile?.density ?: viewModel.getNativeDensity()).toString()
                            showDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(id = R.string.game_resolution_btn))
                    }

                    OutlinedButton(
                        onClick = {
                            showActionsDialog = false
                            selectedGfxGame = targetGame
                            currentGfxConfig = viewModel.getGfxConfig(targetGame.packageName)
                            showGfxDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(id = R.string.gfx_tuner_btn))
                    }

                    OutlinedButton(
                        onClick = {
                            showActionsDialog = false
                            viewModel.cleanGameCache(targetGame.packageName) { _, msg -> compactResultMsg = msg }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(id = R.string.cleanup_btn))
                    }

                    OutlinedButton(
                        onClick = {
                            showActionsDialog = false
                            viewModel.moveObbToSd(targetGame.packageName) { _, msg -> compactResultMsg = msg }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(id = R.string.move_obb_btn))
                    }

                    OutlinedButton(
                        onClick = {
                            showActionsDialog = false
                            integrityScanResult = viewModel.checkGameIntegritySystem(targetGame.packageName)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(id = R.string.game_integrity_btn))
                    }

                    OutlinedButton(
                        onClick = {
                            showActionsDialog = false
                            backupTargetGame = targetGame
                            showBackupDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(id = R.string.backup_btn))
                    }

                    OutlinedButton(
                        onClick = {
                            showActionsDialog = false
                            adScanResult = viewModel.scanGameAds(targetGame.packageName)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(id = R.string.game_verify_ads))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showActionsDialog = false }) {
                    Text(stringResource(id = R.string.ok))
                }
            }
        )
    }

    if (showManualAddDialog) {
        var appSearchQuery by remember { mutableStateOf("") }
        val installedApps = remember(appSearchQuery, showManualAddDialog) {
            viewModel.getInstalledApps(appSearchQuery)
        }

        AlertDialog(
            onDismissRequest = { showManualAddDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(id = R.string.select_app_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    IconButton(onClick = { showManualAddDialog = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                    OutlinedTextField(
                        value = appSearchQuery,
                        onValueChange = { appSearchQuery = it },
                        placeholder = { Text(stringResource(id = R.string.search_app_placeholder)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                        trailingIcon = {
                            if (appSearchQuery.isNotEmpty()) {
                                IconButton(onClick = { appSearchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )

                    if (installedApps.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(id = R.string.no_apps_found),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
                            items(installedApps, key = { it.packageName }) { appItem ->
                                val isAlreadyAdded = games.any { it.packageName == appItem.packageName }
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable {
                                            if (!isAlreadyAdded) {
                                                viewModel.addManualGame(appItem.packageName, appItem.name)
                                                Toast.makeText(
                                                    context,
                                                    context.getString(R.string.app_added_success, appItem.name),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                showManualAddDialog = false
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    context.getString(R.string.app_already_added, appItem.name),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isAlreadyAdded)
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        else
                                            MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AppIconImage(
                                            packageName = appItem.packageName,
                                            modifier = Modifier.size(42.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = appItem.name,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    maxLines = 1
                                                )
                                                if (appItem.isGame) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Surface(
                                                        shape = MaterialTheme.shapes.extraSmall,
                                                        color = MaterialTheme.colorScheme.primaryContainer
                                                    ) {
                                                        Text(
                                                            text = "Game",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Surface(
                                                    shape = MaterialTheme.shapes.extraSmall,
                                                    color = Color(appItem.engine.badgeColorHex).copy(alpha = 0.16f)
                                                ) {
                                                    Text(
                                                        text = "${appItem.engine.iconEmoji} ${appItem.engine.shortName}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = Color(appItem.engine.badgeColorHex),
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                text = appItem.packageName,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1
                                            )
                                        }
                                        if (isAlreadyAdded) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Added",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        } else {
                                            Button(
                                                onClick = {
                                                    viewModel.addManualGame(appItem.packageName, appItem.name)
                                                    Toast.makeText(
                                                        context,
                                                        context.getString(R.string.app_added_success, appItem.name),
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                    showManualAddDialog = false
                                                },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                            ) {
                                                Text("+", style = MaterialTheme.typography.labelLarge)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showManualAddDialog = false }) {
                    Text(stringResource(id = R.string.cancel))
                }
            }
        )
    }

    if (compactResultMsg != null) {
        AlertDialog(
            onDismissRequest = { compactResultMsg = null },
            title = { Text(stringResource(id = R.string.game_img_compactor_title)) },
            text = { Text(compactResultMsg!!) },
            confirmButton = {
                TextButton(onClick = { compactResultMsg = null }) {
                    Text(stringResource(id = R.string.ok))
                }
            }
        )
    }

    if (showBackupDialog && backupTargetGame != null) {
        AlertDialog(
            onDismissRequest = { showBackupDialog = false },
            title = { Text(stringResource(id = R.string.backup_dialog_title)) },
            text = {
                Column {
                    Text(text = backupTargetGame!!.name, style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = includeApk, onCheckedChange = { includeApk = it })
                        Text(stringResource(id = R.string.backup_apk))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = includeObb, onCheckedChange = { includeObb = it })
                        Text(stringResource(id = R.string.backup_obb))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = includeData, onCheckedChange = { includeData = it })
                        Text(stringResource(id = R.string.backup_data))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = compressZip, onCheckedChange = { compressZip = it })
                        Text(stringResource(id = R.string.backup_zip_option), style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val pkg = backupTargetGame!!.packageName
                    showBackupDialog = false
                    viewModel.backupApp(pkg, includeApk, includeObb, includeData, compressZip) { success, msg ->
                        backupResultMsg = msg
                    }
                }) {
                    Text(stringResource(id = R.string.backup_start))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBackupDialog = false }) {
                    Text(stringResource(id = R.string.cancel))
                }
            }
        )
    }

    if (backupResultMsg != null) {
        AlertDialog(
            onDismissRequest = { backupResultMsg = null },
            title = { Text("Backup") },
            text = { Text(backupResultMsg!!) },
            confirmButton = {
                Button(onClick = { backupResultMsg = null }) {
                    Text(stringResource(id = R.string.ok))
                }
            }
        )
    }

    if (showDialog && selectedGame != null) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("${stringResource(id = R.string.game_res_profile_title)}: ${selectedGame?.name}") },
            text = {
                Column {
                    Text(stringResource(id = R.string.game_test_res_desc))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customResolutionInput,
                        onValueChange = { customResolutionInput = it },
                        label = { Text(stringResource(id = R.string.game_res_input_label)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Presets:", style = MaterialTheme.typography.labelSmall)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("1080x1920", "720x1280", "480x854", "360x640", "240x426").forEach { res ->
                            OutlinedButton(
                                onClick = { customResolutionInput = res },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(res, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customDensityInput,
                        onValueChange = { customDensityInput = it },
                        label = { Text("Density (DPI)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            val recommended = viewModel.calculatePurrDpi(customResolutionInput.trim())
                            customDensityInput = recommended.toString()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(id = R.string.purr_auto_dpi_btn))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.testResolutionLive(customResolutionInput.trim()) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(id = R.string.game_test_live))
                        }
                        OutlinedButton(
                            onClick = { viewModel.resetResolutionLive() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(id = R.string.game_reset))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val parts = customResolutionInput.trim().split("x")
                    val dpi = customDensityInput.trim().toIntOrNull() ?: 320
                    if (parts.size == 2) {
                        viewModel.saveProfile(selectedGame!!.packageName, customResolutionInput.trim(), dpi)
                    }
                    showDialog = false
                }) {
                    Text(stringResource(id = R.string.game_save_permanent))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(id = R.string.cancel))
                }
            }
        )
    }

    if (showGfxDialog && selectedGfxGame != null) {
        val game = selectedGfxGame!!
        val engine = GfxTools.detectEngine(context, game.packageName)
        val engineName = when (engine) {
            GfxTools.EngineType.UNITY -> "Unity (Zooba, etc.)"
            GfxTools.EngineType.GODOT -> "Godot Engine (override.cfg)"
            GfxTools.EngineType.ITSMAGIC -> "ITsMagic Engine (engine_settings.json)"
            GfxTools.EngineType.UNREAL -> "Unreal Engine"
            GfxTools.EngineType.WEBVIEW -> "Jogos Web / HTML5 (WebView, React, Canvas)"
            GfxTools.EngineType.ANDROID_STUDIO -> "Android Studio (Nativo Java/Kotlin)"
            GfxTools.EngineType.COCOS -> "Cocos2d-x"
            GfxTools.EngineType.GENERIC -> "Genérico (Android Game Mode)"
            GfxTools.EngineType.UNKNOWN -> "Origem Desconhecida"
        }

        AlertDialog(
            onDismissRequest = { showGfxDialog = false },
            title = {
                Text(stringResource(id = R.string.gfx_tuner_title))
            },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    item {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        ) {
                            Text(
                                text = game.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        ) {
                            Text(
                                text = stringResource(id = R.string.gfx_engine_note, engineName),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(8.dp)
                            )
                        }

                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        ) {
                            Text(
                                text = stringResource(id = R.string.gfx_tuner_warning),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(8.dp)
                            )
                        }

                        // Modo Ultra Batata
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = currentGfxConfig.extremePotatoMode,
                                onCheckedChange = { isChecked ->
                                    currentGfxConfig = if (isChecked) {
                                        currentGfxConfig.copy(
                                            extremePotatoMode = true,
                                            disableShadows = true,
                                            reducePolygons = true,
                                            disableWaterEffects = true,
                                            disableExplosions = true
                                        )
                                    } else {
                                        currentGfxConfig.copy(extremePotatoMode = false)
                                    }
                                }
                            )
                            Text(
                                text = stringResource(id = R.string.gfx_extreme_potato),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                        // Sombras e Iluminação
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        ) {
                            Checkbox(
                                checked = currentGfxConfig.disableShadows,
                                onCheckedChange = { currentGfxConfig = currentGfxConfig.copy(disableShadows = it) }
                            )
                            Text(stringResource(id = R.string.gfx_disable_shadows), style = MaterialTheme.typography.bodyMedium)
                        }

                        // Polígonos e Malha
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        ) {
                            Checkbox(
                                checked = currentGfxConfig.reducePolygons,
                                onCheckedChange = { currentGfxConfig = currentGfxConfig.copy(reducePolygons = it) }
                            )
                            Text(stringResource(id = R.string.gfx_reduce_polygons), style = MaterialTheme.typography.bodyMedium)
                        }

                        // Água e Reflexos
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        ) {
                            Checkbox(
                                checked = currentGfxConfig.disableWaterEffects,
                                onCheckedChange = { currentGfxConfig = currentGfxConfig.copy(disableWaterEffects = it) }
                            )
                            Text(stringResource(id = R.string.gfx_disable_water), style = MaterialTheme.typography.bodyMedium)
                        }

                        // Explosões e Partículas
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        ) {
                            Checkbox(
                                checked = currentGfxConfig.disableExplosions,
                                onCheckedChange = { currentGfxConfig = currentGfxConfig.copy(disableExplosions = it) }
                            )
                            Text(stringResource(id = R.string.gfx_disable_explosions), style = MaterialTheme.typography.bodyMedium)
                        }

                        // Silenciar Áudio
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        ) {
                            Checkbox(
                                checked = currentGfxConfig.muteAudio,
                                onCheckedChange = { currentGfxConfig = currentGfxConfig.copy(muteAudio = it) }
                            )
                            Text(stringResource(id = R.string.gfx_mute_audio), style = MaterialTheme.typography.bodyMedium)
                        }

                        // Compactação ASTC / ETC2 de Texturas
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        ) {
                            Checkbox(
                                checked = currentGfxConfig.astcTextureCompression,
                                onCheckedChange = { currentGfxConfig = currentGfxConfig.copy(astcTextureCompression = it) }
                            )
                            Text(stringResource(id = R.string.gfx_astc_texture_compression), style = MaterialTheme.typography.bodyMedium)
                        }

                        // Forçar Geração de Mipmaps
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        ) {
                            Checkbox(
                                checked = currentGfxConfig.forceMipmapping,
                                onCheckedChange = { currentGfxConfig = currentGfxConfig.copy(forceMipmapping = it) }
                            )
                            Text(stringResource(id = R.string.gfx_force_mipmapping), style = MaterialTheme.typography.bodyMedium)
                        }

                        // Aceleração WebGL / WebView
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        ) {
                            Checkbox(
                                checked = currentGfxConfig.webViewGpuBoost,
                                onCheckedChange = { currentGfxConfig = currentGfxConfig.copy(webViewGpuBoost = it) }
                            )
                            Text(stringResource(id = R.string.gfx_webview_gpu_boost), style = MaterialTheme.typography.bodyMedium)
                        }

                        // Limpeza de GPUCache do WebView
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        ) {
                            Checkbox(
                                checked = currentGfxConfig.cleanWebViewCache,
                                onCheckedChange = { currentGfxConfig = currentGfxConfig.copy(cleanWebViewCache = it) }
                            )
                            Text(stringResource(id = R.string.gfx_webview_clean_cache), style = MaterialTheme.typography.bodyMedium)
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Security,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(id = R.string.gfx_backup_option_title),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(id = R.string.gfx_backup_option_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = {
                                        backupTargetGame = game
                                        showBackupDialog = true
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(id = R.string.gfx_backup_btn))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = {
                                viewModel.saveGfxConfig(game.packageName, currentGfxConfig)
                                val res = viewModel.applyGfxProfile(game.packageName)
                                gfxApplyResultMsg = res
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(id = R.string.gfx_apply_now))
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.saveGfxConfig(game.packageName, currentGfxConfig)
                    showGfxDialog = false
                }) {
                    Text(stringResource(id = R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showGfxDialog = false }) {
                    Text(stringResource(id = R.string.cancel))
                }
            }
        )
    }

    if (gfxApplyResultMsg != null) {
        AlertDialog(
            onDismissRequest = { gfxApplyResultMsg = null },
            title = { Text(stringResource(id = R.string.gfx_tuner_title)) },
            text = { Text(gfxApplyResultMsg!!) },
            confirmButton = {
                TextButton(onClick = { gfxApplyResultMsg = null }) {
                    Text(stringResource(id = R.string.ok))
                }
            }
        )
    }

    if (adScanResult != null) {
        val result = adScanResult!!
        AlertDialog(
            onDismissRequest = { adScanResult = null },
            title = { Text(stringResource(id = R.string.ad_scan_title)) },
            text = {
                Column {
                    if (result.hasAds) {
                        Text(
                            text = stringResource(id = R.string.ad_scan_detected),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        result.detectedNetworks.forEach { network ->
                            Text(
                                text = "• $network",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else {
                        Text(
                            text = stringResource(id = R.string.ad_scan_clean),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { adScanResult = null }) {
                    Text(stringResource(id = R.string.ok))
                }
            }
        )
    }

    if (integrityScanResult != null) {
        val result = integrityScanResult!!
        AlertDialog(
            onDismissRequest = { integrityScanResult = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = if (result.hasIntegritySystem) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(id = R.string.game_integrity_dialog_title))
                }
            },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    item {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = if (result.hasIntegritySystem) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        ) {
                            Text(
                                text = if (result.hasIntegritySystem) stringResource(id = R.string.game_integrity_detected) else stringResource(id = R.string.game_integrity_not_detected),
                                style = MaterialTheme.typography.titleSmall,
                                color = if (result.hasIntegritySystem) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(10.dp)
                            )
                        }

                        Text(
                            text = result.summary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        if (result.detectedMechanisms.isNotEmpty()) {
                            Text(
                                text = stringResource(id = R.string.game_integrity_mechanisms_label),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            result.detectedMechanisms.forEach { mech ->
                                Text(
                                    text = "• $mech",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        if (result.detectedFiles.isNotEmpty()) {
                            Text(
                                text = stringResource(id = R.string.game_integrity_manifests_label),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            result.detectedFiles.forEach { fileInfo ->
                                Text(
                                    text = "• $fileInfo",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        if (!result.hasIntegritySystem) {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = stringResource(id = R.string.pak_bundle_safe_notice),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = {
                                            isCompressingPakBundle = true
                                            viewModel.compressPakAndBundle(result.packageName) { report ->
                                                isCompressingPakBundle = false
                                                pakBundleReport = report
                                            }
                                        },
                                        enabled = !isCompressingPakBundle,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        if (isCompressingPakBundle) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.onPrimary
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                        }
                                        Text(stringResource(id = R.string.compact_pak_bundle_btn))
                                    }
                                }
                            }
                        } else {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = stringResource(id = R.string.pak_bundle_blocked_notice),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { integrityScanResult = null }) {
                    Text(stringResource(id = R.string.ok))
                }
            }
        )
    }

    if (pakBundleReport != null) {
        val report = pakBundleReport!!
        AlertDialog(
            onDismissRequest = { pakBundleReport = null },
            title = { Text(stringResource(id = R.string.pak_bundle_compression_title)) },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp)) {
                    item {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "Arquivos processados: ${report.compressedFiles}/${report.totalFilesFound}",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "Tamanho original: ${String.format(java.util.Locale.US, "%.1f", report.originalSizeMb)} MB",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "Economia estimada: ${String.format(java.util.Locale.US, "%.1f", report.spaceSavedMb)} MB",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Text("Detalhes por arquivo:", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        report.details.forEach { detail ->
                            Text(
                                text = "• $detail",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pakBundleReport = null }) {
                    Text(stringResource(id = R.string.ok))
                }
            }
        )
    }

    if (showUpdatesDialog) {
        LaunchedEffect(Unit) {
            if (updateResults.isEmpty() && games.isNotEmpty()) {
                viewModel.checkAllGameUpdates()
            }
        }

        AlertDialog(
            onDismissRequest = { showUpdatesDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(id = R.string.game_updates_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    if (isCheckingAllUpdates) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(id = R.string.game_updates_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { viewModel.checkAllGameUpdates() },
                        enabled = !isCheckingAllUpdates && games.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        if (isCheckingAllUpdates) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(id = R.string.game_updates_checking))
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(id = R.string.game_updates_check_all))
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    if (games.isEmpty()) {
                        Text(
                            text = "Nenhum jogo adicionado na lista.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 380.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(games) { game ->
                                val info = updateResults[game.packageName]
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = when (info?.status) {
                                            UpdateBadgeStatus.UPDATE_AVAILABLE -> Color(0xFFFFF8E1)
                                            UpdateBadgeStatus.UP_TO_DATE -> Color(0xFFF1F8E9)
                                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        }
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = game.name,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = stringResource(R.string.game_updates_installed_ver, game.versionName),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            IconButton(
                                                onClick = { viewModel.checkUpdateForGame(game) },
                                                modifier = Modifier.size(28.dp),
                                                enabled = info?.status != UpdateBadgeStatus.CHECKING
                                            ) {
                                                Icon(
                                                    Icons.Default.Refresh,
                                                    contentDescription = "Recarregar",
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        when (info?.status) {
                                            UpdateBadgeStatus.CHECKING -> {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(vertical = 4.dp)
                                                ) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(14.dp),
                                                        strokeWidth = 2.dp,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = stringResource(R.string.game_updates_checking),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                            UpdateBadgeStatus.UP_TO_DATE -> {
                                                Column {
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = Color(0xFFE8F5E9)
                                                    ) {
                                                        Text(
                                                            text = stringResource(R.string.game_updates_status_uptodate),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = Color(0xFF2E7D32),
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                    if (!info.sourceName.isNullOrBlank()) {
                                                        Text(
                                                            text = stringResource(R.string.game_updates_source, info.sourceName),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            fontSize = 10.sp
                                                        )
                                                    }
                                                }
                                            }
                                            UpdateBadgeStatus.UPDATE_AVAILABLE -> {
                                                Column {
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = Color(0xFFFFF3E0)
                                                    ) {
                                                        Text(
                                                            text = stringResource(R.string.game_updates_status_available, info.latestVersion ?: ""),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = Color(0xFFE65100),
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                    if (!info.sourceName.isNullOrBlank()) {
                                                        Text(
                                                            text = stringResource(R.string.game_updates_source, info.sourceName),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            fontSize = 10.sp
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        if (!info.downloadUrl.isNullOrBlank()) {
                                                            Button(
                                                                onClick = { viewModel.openUpdateUrl(info.downloadUrl) },
                                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF6C00))
                                                            ) {
                                                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                                                                Spacer(modifier = Modifier.width(4.dp))
                                                                Text(stringResource(R.string.game_updates_download_direct), style = MaterialTheme.typography.labelSmall)
                                                            }
                                                        }
                                                        if (!info.webPageUrl.isNullOrBlank()) {
                                                            OutlinedButton(
                                                                onClick = { viewModel.openUpdateUrl(info.webPageUrl) },
                                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                                            ) {
                                                                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                                                                Spacer(modifier = Modifier.width(4.dp))
                                                                Text(stringResource(R.string.game_updates_view_web), style = MaterialTheme.typography.labelSmall)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            UpdateBadgeStatus.NOT_FOUND -> {
                                                Column {
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = MaterialTheme.colorScheme.surfaceVariant
                                                    ) {
                                                        Text(
                                                            text = stringResource(R.string.game_updates_status_notfound),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    OutlinedButton(
                                                        onClick = { viewModel.searchGameOnWeb(game.packageName) },
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                                    ) {
                                                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(14.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(stringResource(R.string.game_updates_search_web), style = MaterialTheme.typography.labelSmall)
                                                    }
                                                }
                                            }
                                            else -> {
                                                OutlinedButton(
                                                    onClick = { viewModel.checkUpdateForGame(game) },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                                ) {
                                                    Text(stringResource(R.string.game_updates_check_single), style = MaterialTheme.typography.labelSmall)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showUpdatesDialog = false }) {
                    Text(stringResource(id = R.string.ok))
                }
            }
        )
    }
}

@Composable
fun BackgroundAppsTab(viewModel: GameViewModel) {
    val context = LocalContext.current
    val backgroundApps by viewModel.backgroundApps.collectAsState()
    var alertMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.scanBackgroundApps()
    }

    LazyColumn(modifier = Modifier.padding(16.dp)) {
        item {
            // Cabeçalho da Seção com Título, Contador e Botão de Reload
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Apps Ativos (${backgroundApps.size})",
                    style = MaterialTheme.typography.titleMedium
                )
                
                // Botão de Reload / Atualizar Lista
                IconButton(onClick = { 
                    viewModel.scanBackgroundApps() 
                }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Atualizar lista de apps"
                    )
                }
            }

            // Botão de Parar Todos
            Button(
                onClick = {
                    viewModel.killAllApps { _, msg ->
                        alertMsg = msg
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Text(stringResource(id = R.string.btn_kill_all))
            }
        }

        items(backgroundApps) { app ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (app.isFrozen) {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    }
                )
            ) {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                    headlineContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = app.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (app.isFrozen) FontWeight.Bold else FontWeight.Normal
                            )
                            if (app.isFrozen) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = "FREEZE ON",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            if (app.isInternetBlocked) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.errorContainer
                                ) {
                                    Text(
                                        text = "NET BLOCKED",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    },
                    supportingContent = {
                        Text(
                            text = if (app.isFrozen) "Status: Congelado (Freeze ON)" else "Mem: ${app.memUsage}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Controle de Internet (Bloqueio / Liberação)
                            IconButton(onClick = {
                                viewModel.toggleInternet(app.packageName, !app.isInternetBlocked) { _, msg ->
                                    alertMsg = msg
                                }
                            }) {
                                Icon(
                                    imageVector = if (app.isInternetBlocked) Icons.Default.WifiOff else Icons.Default.Wifi,
                                    contentDescription = if (app.isInternetBlocked) "Internet Bloqueada - Clique para liberar" else "Internet Liberada - Clique para bloquear",
                                    tint = if (app.isInternetBlocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Botão de Force Kill
                            TextButton(
                                enabled = !app.isFrozen,
                                onClick = {
                                    viewModel.killApp(app.packageName) { _, msg ->
                                        alertMsg = msg
                                    }
                                }
                            ) {
                                Text(stringResource(id = R.string.btn_kill))
                            }

                            // Botão de Congelar / Descongelar
                            IconButton(onClick = {
                                viewModel.freezeApp(app.packageName) { _, msg ->
                                    alertMsg = msg
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.AcUnit,
                                    contentDescription = if (app.isFrozen) "Freeze ON - Clique para descongelar" else "Freeze OFF - Clique para congelar",
                                    tint = if (app.isFrozen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                            }
                        }
                    }
                )
            }
        }
    }

    if (alertMsg != null) {
        AlertDialog(
            onDismissRequest = { alertMsg = null },
            title = { Text(stringResource(id = R.string.app_control_title)) },
            text = { Text(alertMsg!!) },
            confirmButton = {
                TextButton(onClick = { alertMsg = null }) {
                    Text(stringResource(id = R.string.ok))
                }
            }
        )
    }
}

@Composable
fun BoostSettingsTab(viewModel: GameViewModel) {
    val thermalBoost by viewModel.thermalBoost.collectAsState()
    val disableOverlays by viewModel.disableOverlays.collectAsState()
    val autoClearCache by viewModel.autoClearCache.collectAsState()
    val oomImmortal by viewModel.oomImmortal.collectAsState()
    val adBlockerEnabled by viewModel.adBlockerEnabled.collectAsState()
    val adBlockerRealtime by viewModel.adBlockerRealtime.collectAsState()
    val disableAnimations by viewModel.disableAnimations.collectAsState()
    val highTouchSampling by viewModel.highTouchSampling.collectAsState()
    val limitBackgroundProcesses by viewModel.limitBackgroundProcesses.collectAsState()
    val blockAutoRestart by viewModel.blockAutoRestart.collectAsState()
    val forceDeepSleepCache by viewModel.forceDeepSleepCache.collectAsState()
    val forceMaxCpu by viewModel.forceMaxCpu.collectAsState()
    val forceMaxGpu by viewModel.forceMaxGpu.collectAsState()
    val nativeGameMode by viewModel.nativeGameMode.collectAsState()
    val blockNotificationsGamer by viewModel.blockNotificationsGamer.collectAsState()
    val reduceLoggerBuffer by viewModel.reduceLoggerBuffer.collectAsState()
    val disablePrintSpooler by viewModel.disablePrintSpooler.collectAsState()
    val forceSystemGraphicsDriver by viewModel.forceSystemGraphicsDriver.collectAsState()
    val webViewGameBoost by viewModel.webViewGameBoost.collectAsState()

    var showInfoDialog by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showVmDialog by remember { mutableStateOf(false) }
    var vmResultMsg by remember { mutableStateOf<String?>(null) }
    var deepSleepResultMsg by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
        ) {
            Text(
                text = stringResource(id = R.string.boost_warning_note),
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }

        SettingItem(
            title = stringResource(id = R.string.boost_native_game_mode_title),
            checked = nativeGameMode,
            onCheckedChange = { viewModel.toggleNativeGameMode(it) },
            titleRes = R.string.boost_native_game_mode_title,
            descRes = R.string.boost_native_game_mode_desc,
            onInfoClick = { showInfoDialog = it }
        )
        SettingItem(
            title = stringResource(id = R.string.boost_system_driver_title),
            checked = forceSystemGraphicsDriver,
            onCheckedChange = { viewModel.toggleForceSystemGraphicsDriver(it) },
            titleRes = R.string.boost_system_driver_title,
            descRes = R.string.boost_system_driver_desc,
            onInfoClick = { showInfoDialog = it }
        )
        SettingItem(
            title = stringResource(id = R.string.boost_webview_title),
            checked = webViewGameBoost,
            onCheckedChange = { viewModel.toggleWebViewGameBoost(it) },
            titleRes = R.string.boost_webview_title,
            descRes = R.string.boost_webview_desc,
            onInfoClick = { showInfoDialog = it }
        )
        SettingItem(
            title = stringResource(id = R.string.boost_block_notifications_title),
            checked = blockNotificationsGamer,
            onCheckedChange = { viewModel.toggleBlockNotificationsGamer(it) },
            titleRes = R.string.boost_block_notifications_title,
            descRes = R.string.boost_block_notifications_desc,
            onInfoClick = { showInfoDialog = it }
        )
        SettingItem(
            title = stringResource(id = R.string.boost_reduce_logger_title),
            checked = reduceLoggerBuffer,
            onCheckedChange = { viewModel.toggleReduceLoggerBuffer(it) },
            titleRes = R.string.boost_reduce_logger_title,
            descRes = R.string.boost_reduce_logger_desc,
            onInfoClick = { showInfoDialog = it }
        )
        SettingItem(
            title = stringResource(id = R.string.boost_disable_print_spooler_title),
            checked = disablePrintSpooler,
            onCheckedChange = { viewModel.toggleDisablePrintSpooler(it) },
            titleRes = R.string.boost_disable_print_spooler_title,
            descRes = R.string.boost_disable_print_spooler_desc,
            onInfoClick = { showInfoDialog = it }
        )

        SettingItem(
            title = stringResource(id = R.string.boost_max_cpu_title),
            checked = forceMaxCpu,
            onCheckedChange = { viewModel.toggleForceMaxCpu(it) },
            titleRes = R.string.boost_max_cpu_title,
            descRes = R.string.boost_max_cpu_desc,
            onInfoClick = { showInfoDialog = it }
        )
        SettingItem(
            title = stringResource(id = R.string.boost_max_gpu_title),
            checked = forceMaxGpu,
            onCheckedChange = { viewModel.toggleForceMaxGpu(it) },
            titleRes = R.string.boost_max_gpu_title,
            descRes = R.string.boost_max_gpu_desc,
            onInfoClick = { showInfoDialog = it }
        )

        SettingItem(
            title = stringResource(id = R.string.boost_animations_title),
            checked = disableAnimations,
            onCheckedChange = { viewModel.toggleDisableAnimations(it) },
            titleRes = R.string.boost_animations_title,
            descRes = R.string.boost_animations_desc,
            onInfoClick = { showInfoDialog = it }
        )
        SettingItem(
            title = stringResource(id = R.string.boost_touch_sampling_title),
            checked = highTouchSampling,
            onCheckedChange = { viewModel.toggleHighTouchSampling(it) },
            titleRes = R.string.boost_touch_sampling_title,
            descRes = R.string.boost_touch_sampling_desc,
            onInfoClick = { showInfoDialog = it }
        )
        SettingItem(
            title = stringResource(id = R.string.boost_limit_bg_title),
            checked = limitBackgroundProcesses,
            onCheckedChange = { viewModel.toggleLimitBackgroundProcesses(it) },
            titleRes = R.string.boost_limit_bg_title,
            descRes = R.string.boost_limit_bg_desc,
            onInfoClick = { showInfoDialog = it }
        )
        SettingItem(
            title = stringResource(id = R.string.boost_block_autorestart_title),
            checked = blockAutoRestart,
            onCheckedChange = { viewModel.toggleBlockAutoRestart(it) },
            titleRes = R.string.boost_block_autorestart_title,
            descRes = R.string.boost_block_autorestart_desc,
            onInfoClick = { showInfoDialog = it }
        )
        SettingItem(
            title = stringResource(id = R.string.boost_deep_sleep_title),
            checked = forceDeepSleepCache,
            onCheckedChange = { viewModel.toggleForceDeepSleepCache(it) },
            titleRes = R.string.boost_deep_sleep_title,
            descRes = R.string.boost_deep_sleep_desc,
            onInfoClick = { showInfoDialog = it }
        )
        OutlinedButton(
            onClick = {
                viewModel.executeDeepSleepNow { _, msg -> deepSleepResultMsg = msg }
            },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        ) {
            Text(stringResource(id = R.string.boost_deep_sleep_btn))
        }

        SettingItem(
            title = stringResource(id = R.string.boost_adblock_title),
            checked = adBlockerEnabled,
            onCheckedChange = { viewModel.toggleAdBlocker(it) },
            titleRes = R.string.boost_adblock_title,
            descRes = R.string.boost_adblock_desc,
            onInfoClick = { showInfoDialog = it }
        )
        SettingItem(
            title = stringResource(id = R.string.boost_adblock_realtime),
            checked = adBlockerRealtime,
            onCheckedChange = { viewModel.toggleAdBlockerRealtime(it) },
            titleRes = R.string.boost_adblock_title,
            descRes = R.string.boost_adblock_desc,
            onInfoClick = { showInfoDialog = it }
        )
        SettingItem(
            title = stringResource(id = R.string.boost_thermal_title),
            checked = thermalBoost,
            onCheckedChange = { viewModel.toggleThermalBoost(it) },
            titleRes = R.string.boost_thermal_title,
            descRes = R.string.boost_thermal_desc,
            onInfoClick = { showInfoDialog = it }
        )
        SettingItem(
            title = stringResource(id = R.string.boost_overlay_title),
            checked = disableOverlays,
            onCheckedChange = { viewModel.toggleDisableOverlays(it) },
            titleRes = R.string.boost_overlay_title,
            descRes = R.string.boost_overlay_desc,
            onInfoClick = { showInfoDialog = it }
        )
        SettingItem(
            title = stringResource(id = R.string.boost_cache_title),
            checked = autoClearCache,
            onCheckedChange = { viewModel.toggleAutoClearCache(it) },
            titleRes = R.string.boost_cache_title,
            descRes = R.string.boost_cache_desc,
            onInfoClick = { showInfoDialog = it }
        )
        SettingItem(
            title = stringResource(id = R.string.boost_oom_title),
            checked = oomImmortal,
            onCheckedChange = { viewModel.toggleOomImmortal(it) },
            titleRes = R.string.boost_oom_title,
            descRes = R.string.boost_oom_desc,
            onInfoClick = { showInfoDialog = it }
        )
        
        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = stringResource(id = R.string.virtual_mem_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Button(
                    onClick = { showVmDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(id = R.string.virtual_mem_btn))
                }
            }
        }
    }

    if (showVmDialog) {
        AlertDialog(
            onDismissRequest = { showVmDialog = false },
            title = { Text(stringResource(id = R.string.virtual_mem_btn)) },
            text = { Text("Are you sure you want to increase virtual memory by 1GB? This operation is persistent and should be used with caution.") },
            confirmButton = {
                Button(onClick = {
                    showVmDialog = false
                    viewModel.increaseVirtualMemory(1024) { _, msg -> vmResultMsg = msg }
                }) { Text("Confirm") }
            },
            dismissButton = { TextButton(onClick = { showVmDialog = false }) { Text("Cancel") } }
        )
    }

    if (vmResultMsg != null) {
        AlertDialog(
            onDismissRequest = { vmResultMsg = null },
            title = { Text("Virtual Memory") },
            text = { Text(vmResultMsg!!) },
            confirmButton = { TextButton(onClick = { vmResultMsg = null }) { Text("OK") } }
        )
    }

    if (deepSleepResultMsg != null) {
        AlertDialog(
            onDismissRequest = { deepSleepResultMsg = null },
            title = { Text(stringResource(id = R.string.boost_deep_sleep_title)) },
            text = { Text(deepSleepResultMsg!!) },
            confirmButton = { TextButton(onClick = { deepSleepResultMsg = null }) { Text(stringResource(id = R.string.ok)) } }
        )
    }

    if (showInfoDialog != null) {
        // ...
        AlertDialog(
            onDismissRequest = { showInfoDialog = null },
            title = { Text(showInfoDialog!!.first) },
            text = { Text(showInfoDialog!!.second) },
            confirmButton = { TextButton(onClick = { showInfoDialog = null }) { Text(stringResource(id = R.string.ok)) } }
        )
    }
}

@Composable
fun SettingItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    titleRes: Int,
    descRes: Int,
    onInfoClick: (Pair<String, String>) -> Unit
) {
    val context = LocalContext.current
    val t = stringResource(id = titleRes)
    val d = stringResource(id = descRes)
    ListItem(
        headlineContent = { Text(title) },
        leadingContent = {
            IconButton(onClick = { onInfoClick(t to d) }) {
                Icon(Icons.Default.Info, contentDescription = "Info")
            }
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = { newValue ->
                    onCheckedChange(newValue)
                    val isPrivileged = ShizukuHelper.hasPermission() || ShizukuHelper.isRootAvailable()
                    if (newValue && !isPrivileged) {
                        ShizukuHelper.requestPermission()
                        Toast.makeText(context, context.getString(R.string.setting_saved_notice), Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, context.getString(R.string.setting_saved_success), Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    )
}

