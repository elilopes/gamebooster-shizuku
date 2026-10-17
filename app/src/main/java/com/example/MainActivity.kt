package com.example

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            // Permission granted
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ShizukuHelper.registerPermissionListener(permissionListener)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: GameViewModel = viewModel()) {
    val dashboardEnabled by viewModel.dashboardEnabled.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = mutableListOf("Games", "Background Apps", "Boost Settings")
    if (dashboardEnabled) tabs.add(0, "Dashboard")
    
    var showMenu by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Game Booster") },
            actions = {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(id = R.string.menu_language)) }, onClick = { showLanguageDialog = true; showMenu = false })
                    DropdownMenuItem(text = { Text(stringResource(id = R.string.menu_about)) }, onClick = { showAbout = true; showMenu = false })
                    DropdownMenuItem(text = { Text(if (dashboardEnabled) "Disable Dashboard" else "Enable Dashboard") }, onClick = { viewModel.dashboardEnabled.value = !dashboardEnabled; showMenu = false })
                }
            }
        )
        
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }

        when (tabs[selectedTab]) {
            "Dashboard" -> DashboardTab(viewModel)
            "Games" -> GameListTab(viewModel)
            "Background Apps" -> BackgroundAppsTab(viewModel)
            "Boost Settings" -> BoostSettingsTab(viewModel)
        }
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text(stringResource(id = R.string.about_title)) },
            text = { Text(stringResource(id = R.string.about_description)) },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) {
                    Text(stringResource(id = R.string.ok))
                }
            }
        )
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(id = R.string.language_dialog_title)) },
            text = {
                Column {
                    Text(stringResource(id = R.string.language_en), modifier = Modifier.fillMaxWidth().clickable { showLanguageDialog = false })
                    Text(stringResource(id = R.string.language_pt), modifier = Modifier.fillMaxWidth().clickable { showLanguageDialog = false })
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(stringResource(id = R.string.ok))
                }
            }
        )
    }
}

@Composable
fun DashboardTab(viewModel: GameViewModel) {
    val temp by viewModel.temperature.collectAsState()
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Device Temperature: ${String.format("%.1f", temp)}°C", style = MaterialTheme.typography.headlineMedium)
        // Simplified visual indicator
        LinearProgressIndicator(progress = { (temp / 100f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(20.dp))
    }
}

@Composable
fun GameListTab(viewModel: GameViewModel) {
    val games by viewModel.games.collectAsState()
    var selectedGame by remember { mutableStateOf<GameApp?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    LazyColumn(modifier = Modifier.padding(16.dp)) {
        item {
            Text(text = "Native Res: ${viewModel.nativeResolution}", style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = {
                if (ShizukuHelper.isShizukuAvailable() && !ShizukuHelper.hasPermission()) {
                    ShizukuHelper.requestPermission()
                }
            }) {
                Text("Initialize Shizuku")
            }
        }
        items(games) { game ->
            ListItem(
                modifier = Modifier.clickable {
                    if (ShizukuHelper.hasPermission()) {
                        selectedGame = game
                        showDialog = true
                    }
                },
                headlineContent = { Text(game.name) },
                supportingContent = { 
                    val res = game.profile?.resolution ?: viewModel.nativeResolution
                    Text("Res: $res") 
                },
                trailingContent = {
                    TextButton(onClick = { viewModel.launchGame(game.packageName) }) {
                        Text("Launch")
                    }
                }
            )
        }
    }

    if (showDialog && selectedGame != null) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Edit Profile: ${selectedGame?.name}") },
            text = { Text("Tap to simulate save (1080x1920, 480dpi)") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.saveProfile(selectedGame!!.packageName, "1080x1920", 480)
                    showDialog = false
                }) {
                    Text("Save")
                }
            }
        )
    }
}

@Composable
fun BackgroundAppsTab(viewModel: GameViewModel) {
    val backgroundApps by viewModel.backgroundApps.collectAsState()

    LazyColumn(modifier = Modifier.padding(16.dp)) {
        items(backgroundApps) { app ->
            ListItem(
                headlineContent = { Text(app.name) },
                supportingContent = { Text("Mem: ${app.memUsage}") },
                trailingContent = {
                    Row {
                        TextButton(onClick = { viewModel.killApp(app.packageName) }) { Text("Kill") }
                        TextButton(onClick = { viewModel.freezeApp(app.packageName) }) { Text("Freeze") }
                    }
                }
            )
        }
    }
}

@Composable
fun BoostSettingsTab(viewModel: GameViewModel) {
    val thermalBoost by viewModel.thermalBoost.collectAsState()
    val disableOverlays by viewModel.disableOverlays.collectAsState()
    val autoClearCache by viewModel.autoClearCache.collectAsState()
    val oomImmortal by viewModel.oomImmortal.collectAsState()

    var showInfoDialog by remember { mutableStateOf<Pair<String, String>?>(null) }

    Column(modifier = Modifier.padding(16.dp)) {
        SettingItem("Thermal Limit Disable", thermalBoost, { viewModel.thermalBoost.value = it }, R.string.boost_thermal_title, R.string.boost_thermal_desc, { showInfoDialog = it })
        SettingItem("Disable Hardware Overlays", disableOverlays, { viewModel.disableOverlays.value = it }, R.string.boost_overlay_title, R.string.boost_overlay_desc, { showInfoDialog = it })
        SettingItem("Auto Clear Cache", autoClearCache, { viewModel.autoClearCache.value = it }, R.string.boost_cache_title, R.string.boost_cache_desc, { showInfoDialog = it })
        SettingItem("OOM Killer Immortal", oomImmortal, { viewModel.oomImmortal.value = it }, R.string.boost_oom_title, R.string.boost_oom_desc, { showInfoDialog = it })
    }

    if (showInfoDialog != null) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = null },
            title = { Text(showInfoDialog!!.first) },
            text = { Text(showInfoDialog!!.second) },
            confirmButton = { TextButton(onClick = { showInfoDialog = null }) { Text("OK") } }
        )
    }
}

@Composable
fun SettingItem(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, titleRes: Int, descRes: Int, onInfoClick: (Pair<String, String>) -> Unit) {
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
