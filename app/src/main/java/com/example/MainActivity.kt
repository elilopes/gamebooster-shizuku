package com.example

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            // Permission granted
        }
    }

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
        ShizukuHelper.registerPermissionListener(permissionListener)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ShizukuHelper.unregisterPermissionListener(permissionListener)
    }
}

@Composable
fun ShizukuStatusBadge() {
    val isActive = remember { mutableStateOf(ShizukuHelper.hasPermission()) }
    LaunchedEffect(Unit) {
        isActive.value = ShizukuHelper.hasPermission()
    }

    Surface(
        color = if (isActive.value) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = if (isActive.value) "Shizuku: ON" else "Shizuku: OFF",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            color = if (isActive.value) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: GameViewModel = viewModel()) {
    val context = LocalContext.current
    val thermalMonitorEnabled by viewModel.thermalMonitorEnabled.collectAsState()
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    val baseTabs = listOf(
        stringResource(id = R.string.tab_games),
        stringResource(id = R.string.tab_background),
        stringResource(id = R.string.tab_boost_settings),
        stringResource(id = R.string.tab_image_optimizer)
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
                ShizukuStatusBadge()
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
            currentTabTitle == stringResource(id = R.string.tab_image_optimizer) -> ImageOptimizerTab(viewModel)
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
                Text(
                    text = stringResource(id = R.string.thermal_monitor_header),
                    style = MaterialTheme.typography.titleMedium
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
fun ImageOptimizerTab(viewModel: GameViewModel) {
    val inspectedImage by viewModel.inspectedImage.collectAsState()
    val compressedFile by viewModel.compressedImageFile.collectAsState()
    var makePermanent by remember { mutableStateOf(false) }
    var successMsg by remember { mutableStateOf(false) }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.inspectSelectedImage(uri)
            successMsg = false
        }
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = stringResource(id = R.string.img_opt_title),
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { pickImageLauncher.launch("image/*") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(id = R.string.img_opt_pick))
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (inspectedImage != null) {
            val img = inspectedImage!!
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Resolution: ${img.width} x ${img.height}", style = MaterialTheme.typography.bodyLarge)
                    Text("Quality Category: ${img.category}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Text("File Size: ${img.sizeBytes / 1024} KB", style = MaterialTheme.typography.bodySmall)

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = makePermanent,
                            onCheckedChange = { makePermanent = it }
                        )
                        Text(stringResource(id = R.string.img_opt_permanent))
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            viewModel.compressImageToSd(img.uri, makePermanent)
                            successMsg = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(id = R.string.img_opt_compress))
                    }
                }
            }
        }

        if (successMsg && compressedFile != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Text(
                    text = stringResource(id = R.string.img_opt_success),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
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
    var compactResultMsg by remember { mutableStateOf<String?>(null) }

    var showManualAddDialog by remember { mutableStateOf(false) }
    var manualPkg by remember { mutableStateOf("") }
    var manualName by remember { mutableStateOf("") }

    var customResolutionInput by remember { mutableStateOf("1280x720") }

    LazyColumn(modifier = Modifier.padding(16.dp)) {
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
                            OutlinedButton(
                                onClick = {
                                    uriHandler.openUri("https://github.com/RikkaApps/Shizuku-API")
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = stringResource(id = R.string.shizuku_download_btn),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            Button(
                                onClick = {
                                    if (ShizukuHelper.isShizukuAvailable() && !ShizukuHelper.hasPermission()) {
                                        ShizukuHelper.requestPermission()
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (ShizukuHelper.hasPermission()) stringResource(id = R.string.game_shizuku_active) else stringResource(id = R.string.game_shizuku_authorize),
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
                            onClick = { viewModel.autoScanAndAddGames() },
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
                            showDialog = true
                        }
                    }
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = game.name,
                        style = MaterialTheme.typography.titleMedium
                    )
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                val count = viewModel.compactGameImages(game.packageName)
                                compactResultMsg = if (count > 0) {
                                    context.getString(R.string.img_compact_success, count)
                                } else {
                                    context.getString(R.string.img_compact_none)
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(stringResource(id = R.string.img_compact_btn))
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        OutlinedButton(
                            onClick = {
                                adScanResult = viewModel.scanGameAds(game.packageName)
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(stringResource(id = R.string.game_verify_ads))
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        Button(
                            onClick = { viewModel.launchGame(game.packageName) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(stringResource(id = R.string.game_launch))
                        }
                    }
                }
            }
        }
    }

    if (showManualAddDialog) {
        AlertDialog(
            onDismissRequest = { showManualAddDialog = false },
            title = { Text(stringResource(id = R.string.game_add_manual_title)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = manualName,
                        onValueChange = { manualName = it },
                        label = { Text(stringResource(id = R.string.game_app_name_label)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = manualPkg,
                        onValueChange = { manualPkg = it },
                        label = { Text(stringResource(id = R.string.game_pkg_name_label)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (manualPkg.isNotBlank() && manualName.isNotBlank()) {
                        viewModel.addManualGame(manualPkg.trim(), manualName.trim())
                        manualPkg = ""
                        manualName = ""
                        showManualAddDialog = false
                    }
                }) {
                    Text(stringResource(id = R.string.ok))
                }
            },
            dismissButton = {
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
                    if (parts.size == 2) {
                        viewModel.saveProfile(selectedGame!!.packageName, customResolutionInput.trim(), 320)
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

    LaunchedEffect(Unit) {
        viewModel.scanBackgroundApps()
    }

    LazyColumn(modifier = Modifier.padding(16.dp)) {
        items(backgroundApps) { app ->
            ListItem(
                headlineContent = { Text(app.name) },
                supportingContent = { Text("Mem: ${app.memUsage}") },
                trailingContent = {
                    Row {
                        TextButton(onClick = {
                            val success = viewModel.killApp(app.packageName)
                            killAlertMsg = if (success) {
                                context.getString(R.string.kill_success)
                            } else {
                                context.getString(R.string.kill_fail)
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

    var showInfoDialog by remember { mutableStateOf<Pair<String, String>?>(null) }

    Column(modifier = Modifier.padding(16.dp)) {
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
            title = stringResource(id = R.string.boost_adblock_title),
            checked = adBlockerEnabled,
            onCheckedChange = { viewModel.toggleAdBlocker(it) },
            titleRes = R.string.boost_adblock_title,
            descRes = R.string.boost_adblock_desc,
            onInfoClick = { showInfoDialog = it }
        )
        SettingItem(
            title = "Real-time Background Ad Monitoring",
            checked = adBlockerRealtime,
            onCheckedChange = { viewModel.toggleAdBlockerRealtime(it) },
            titleRes = R.string.boost_adblock_title,
            descRes = R.string.boost_adblock_desc,
            onInfoClick = { showInfoDialog = it }
        )
        SettingItem(
            title = stringResource(id = R.string.boost_thermal_title),
            checked = thermalBoost,
            onCheckedChange = { viewModel.thermalBoost.value = it },
            titleRes = R.string.boost_thermal_title,
            descRes = R.string.boost_thermal_desc,
            onInfoClick = { showInfoDialog = it }
        )
        SettingItem(
            title = stringResource(id = R.string.boost_overlay_title),
            checked = disableOverlays,
            onCheckedChange = { viewModel.disableOverlays.value = it },
            titleRes = R.string.boost_overlay_title,
            descRes = R.string.boost_overlay_desc,
            onInfoClick = { showInfoDialog = it }
        )
        SettingItem(
            title = stringResource(id = R.string.boost_cache_title),
            checked = autoClearCache,
            onCheckedChange = { viewModel.autoClearCache.value = it },
            titleRes = R.string.boost_cache_title,
            descRes = R.string.boost_cache_desc,
            onInfoClick = { showInfoDialog = it }
        )
        SettingItem(
            title = stringResource(id = R.string.boost_oom_title),
            checked = oomImmortal,
            onCheckedChange = { viewModel.oomImmortal.value = it },
            titleRes = R.string.boost_oom_title,
            descRes = R.string.boost_oom_desc,
            onInfoClick = { showInfoDialog = it }
        )
    }

    if (showInfoDialog != null) {
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
                    if (newValue && !ShizukuHelper.hasPermission()) {
                        ShizukuHelper.requestPermission()
                    } else {
                        onCheckedChange(newValue)
                    }
                }
            )
        }
    )
}
