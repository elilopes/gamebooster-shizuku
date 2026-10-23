package com.gfx

import android.Manifest
import android.app.Activity
import android.content.Context
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
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

        // 🛡️ OBRIGATÓRIO: Inicializa o ShizukuHelper passando o Contexto da aplicação
        // Deve ser chamado antes de qualquer operação ou observação de estado.
        ShizukuHelper.initialize(applicationContext)

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

    Surface(
        color = containerColor,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.clickable {
            if (isBinderAlive && !isShizukuActive) {
                val sent = ShizukuHelper.requestPermission()
                if (!sent) {
                    val opened = ShizukuHelper.openShizukuApp(context)
                    if (opened) {
                        Toast.makeText(context, "Abra o Shizuku e conceda a permissão ao aplicativo.", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Inicie o serviço do Shizuku no dispositivo.", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(context, "Solicitando autorização ao Shizuku...", Toast.LENGTH_SHORT).show()
                }
            } else {
                ShizukuHelper.updateStatus()
                isRootActive.value = ShizukuHelper.isRootAvailable()
                Toast.makeText(context, "Status atualizado: $statusText", Toast.LENGTH_SHORT).show()
            }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: GameViewModel = viewModel()) {
    val context = LocalContext.current
    val thermalMonitorEnabled by viewModel.thermalMonitorEnabled.collectAsState()
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    var termsAccepted by remember { mutableStateOf(prefs.getBoolean("terms_accepted", false)) }
    var termsChecked by remember { mutableStateOf(false) }

    val baseTabs = listOf(
        stringResource(id = R.string.tab_games),
        stringResource(id = R.string.tab_background),
        stringResource(id = R.string.tab_boost_settings),
        stringResource(id = R.string.tab_scripts)
    )

    val tabs = if (thermalMonitorEnabled) {
        listOf(stringResource(id = R.string.tab_thermal_monitor)) + baseTabs
    } else {
        baseTabs
    }

    val activeIndex = selectedTabIndex.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))

    var showMenu by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showHardwareDialog by remember { mutableStateOf(false) }
    var hardwareReportText by remember { mutableStateOf("") }

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
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
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

        val currentTabTitle = tabs.getOrNull(activeIndex) ?: ""
        when {
            thermalMonitorEnabled && activeIndex == 0 -> ThermalMonitorTab(viewModel)
            currentTabTitle == stringResource(id = R.string.tab_games) -> GameListTab(viewModel)
            currentTabTitle == stringResource(id = R.string.tab_background) -> BackgroundAppsTab(viewModel)
            currentTabTitle == stringResource(id = R.string.tab_boost_settings) -> BoostSettingsTab(viewModel)
            currentTabTitle == stringResource(id = R.string.tab_scripts) -> ScriptsTab(viewModel)
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
                val tempText = if (temp > 0f) String.format("Battery Temp: %.1f°C", temp) else "Battery Temp: --°C"
                val memText = if (mem > 0f) String.format("Memory Usage: %.1f%%", mem) else "Memory Usage: --%"

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
                                    if (!isShizukuGranted) {
                                        val requested = ShizukuHelper.requestPermission()
                                        if (!requested) {
                                            val opened = ShizukuHelper.openShizukuApp(context)
                                            if (opened) {
                                                Toast.makeText(context, "Abra o Shizuku e aprove a permissão para este aplicativo.", Toast.LENGTH_LONG).show()
                                            } else {
                                                Toast.makeText(context, "Serviço Shizuku não detectado. Inicie o Shizuku primeiro.", Toast.LENGTH_LONG).show()
                                            }
                                        } else {
                                            Toast.makeText(context, "Solicitação enviada. Confirme na janela do Shizuku.", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        Toast.makeText(context, "Shizuku já está autorizado e ativo!", Toast.LENGTH_SHORT).show()
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
                                        isShizukuGranted -> "Shizuku Ativo"
                                        isBinderAlive -> "Ativar Shizuku"
                                        else -> "Ativar Shizuku"
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
                    Spacer(modifier = Modifier.height(2.dp))
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
                        val res = game.profile?.resolution ?: viewModel.nativeResolution
                        Text(
                            text = "Res: $res",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    val cardBtnStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp)
                    val cardBtnPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                selectedGame = game
                                customResolutionInput = game.profile?.resolution ?: "1280x720"
                                customDensityInput = (game.profile?.density ?: viewModel.getNativeDensity()).toString()
                                showDialog = true
                            },
                            contentPadding = cardBtnPadding
                        ) {
                            Text(stringResource(id = R.string.game_resolution_btn), style = cardBtnStyle)
                        }

                        OutlinedButton(
                            onClick = {
                                selectedGfxGame = game
                                currentGfxConfig = viewModel.getGfxConfig(game.packageName)
                                showGfxDialog = true
                            },
                            contentPadding = cardBtnPadding
                        ) {
                            Text(stringResource(id = R.string.gfx_tuner_btn), style = cardBtnStyle)
                        }

                        OutlinedButton(
                            onClick = { viewModel.cleanGameCache(game.packageName) { _, msg -> compactResultMsg = msg } },
                            contentPadding = cardBtnPadding
                        ) {
                            Text(stringResource(id = R.string.cleanup_btn), style = cardBtnStyle)
                        }

                        OutlinedButton(
                            onClick = { viewModel.moveObbToSd(game.packageName) { _, msg -> compactResultMsg = msg } },
                            contentPadding = cardBtnPadding
                        ) {
                            Text(stringResource(id = R.string.move_obb_btn), style = cardBtnStyle)
                        }


                        OutlinedButton(
                            onClick = {
                                integrityScanResult = viewModel.checkGameIntegritySystem(game.packageName)
                            },
                            contentPadding = cardBtnPadding
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(stringResource(id = R.string.game_integrity_btn), style = cardBtnStyle)
                        }

                        OutlinedButton(
                            onClick = {
                                backupTargetGame = game
                                showBackupDialog = true
                            },
                            contentPadding = cardBtnPadding
                        ) {
                            Text(stringResource(id = R.string.backup_btn), style = cardBtnStyle)
                        }

                        OutlinedButton(
                            onClick = {
                                adScanResult = viewModel.scanGameAds(game.packageName)
                            },
                            contentPadding = cardBtnPadding
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(stringResource(id = R.string.game_verify_ads), style = cardBtnStyle)
                        }

                        Button(
                            onClick = { viewModel.launchGame(game.packageName) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            Text(stringResource(id = R.string.game_launch), style = cardBtnStyle)
                        }
                    }
                }
            }
        }
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
            GfxTools.EngineType.GENERIC -> "Genérico (Android Game Mode)"
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
        AlertDialog(
            onDismissRequest = { showUpdatesDialog = false },
            title = { Text(stringResource(id = R.string.game_updates_title)) },
            text = {
                Column {
                    Text(
                        text = stringResource(id = R.string.game_updates_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp)) {
                        items(games) { game ->
                            ListItem(
                                headlineContent = { Text(game.name, style = MaterialTheme.typography.bodyMedium) },
                                supportingContent = { Text("v${game.versionName}", style = MaterialTheme.typography.bodySmall) },
                                trailingContent = {
                                    OutlinedButton(
                                        onClick = { viewModel.openAppInStore(game.packageName) },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(stringResource(id = R.string.game_open_store), style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            )
                            HorizontalDivider()
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
    var killAlertMsg by remember { mutableStateOf<String?>(null) }
    var blockedApps by remember { mutableStateOf(setOf<String>()) }

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
                        killAlertMsg = msg
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
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                    headlineContent = { Text(app.name, style = MaterialTheme.typography.bodyMedium) },
                    supportingContent = { Text("Mem: ${app.memUsage}", style = MaterialTheme.typography.bodySmall) },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                val isBlocked = blockedApps.contains(app.packageName)
                                viewModel.toggleInternet(app.packageName, !isBlocked)
                                blockedApps = if (isBlocked) blockedApps - app.packageName else blockedApps + app.packageName
                            }) {
                                Icon(
                                    imageVector = if (blockedApps.contains(app.packageName)) Icons.Default.LockOpen else Icons.Default.Lock,
                                    contentDescription = "Internet Control"
                                )
                            }
                            TextButton(onClick = {
                                viewModel.killApp(app.packageName) { _, msg ->
                                    killAlertMsg = msg
                                }
                            }) { Text(stringResource(id = R.string.btn_kill)) }
                            TextButton(onClick = { viewModel.freezeApp(app.packageName) }) {
                                Text(stringResource(id = R.string.btn_freeze))
                            }
                        }
                    }
                )
            }
        }
    }

    if (killAlertMsg != null) {
        AlertDialog(
            onDismissRequest = { killAlertMsg = null },
            title = { Text(stringResource(id = R.string.app_control_title)) },
            text = { Text(killAlertMsg!!) },
            confirmButton = {
                TextButton(onClick = { killAlertMsg = null }) {
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
                    if (newValue && !ShizukuHelper.hasPermission() && !ShizukuHelper.isRootAvailable()) {
                        ShizukuHelper.requestPermission()
                        Toast.makeText(context, context.getString(R.string.shizuku_optional_tip), Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    )
}

@Composable
fun ScriptsTab(viewModel: GameViewModel) {
    var scriptContent by remember { mutableStateOf("# Exemplo de Script / Game Tuner Shell\n# wm size 720x1280\n# settings put global animator_duration_scale 0.0") }
    var isRunning by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val commandLog by ShellFunctions.commandLog.collectAsState()
    val scrollState = androidx.compose.foundation.rememberScrollState()

    LaunchedEffect(commandLog.size) {
        if (commandLog.isNotEmpty()) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        Text(
            text = stringResource(id = R.string.scripts_tab_title),
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(id = R.string.scripts_tab_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = scriptContent,
            onValueChange = { scriptContent = it },
            label = { Text(stringResource(id = R.string.scripts_input_label)) },
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
            textStyle = MaterialTheme.typography.bodySmall
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                if (scriptContent.isNotBlank()) {
                    isRunning = true
                    viewModel.runCustomScript(scriptContent) { _ ->
                        isRunning = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isRunning
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                if (isRunning) stringResource(id = R.string.scripts_running)
                else stringResource(id = R.string.scripts_run_btn)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Card do Terminal com Barra de Ações (Copiar e Limpar)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = androidx.compose.ui.graphics.Color(0xFF1E1E1E)
            )
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Barra de cabeçalho do console
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(androidx.compose.ui.graphics.Color(0xFF2D2D2D))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(id = R.string.scripts_terminal_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = androidx.compose.ui.graphics.Color.LightGray
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Botão de Copiar Logs do Terminal
                        IconButton(
                            onClick = {
                                if (commandLog.isNotEmpty()) {
                                    val fullLog = commandLog.joinToString("\n")
                                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(fullLog))
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.scripts_copied_toast),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.scripts_empty_toast),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = stringResource(id = R.string.scripts_copy_btn),
                                tint = androidx.compose.ui.graphics.Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // Botão de Limpar Terminal
                        IconButton(
                            onClick = {
                                ShellFunctions.clearLog()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(id = R.string.scripts_clear_btn),
                                tint = androidx.compose.ui.graphics.Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Conteúdo do Terminal
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(androidx.compose.ui.graphics.Color.Black)
                        .padding(10.dp)
                        .verticalScroll(scrollState)
                ) {
                    if (commandLog.isEmpty()) {
                        Text(
                            text = "# " + stringResource(id = R.string.scripts_empty_toast),
                            color = androidx.compose.ui.graphics.Color.DarkGray,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        Text(
                            text = commandLog.joinToString("\n"),
                            color = androidx.compose.ui.graphics.Color(0xFF00FF66),
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}
