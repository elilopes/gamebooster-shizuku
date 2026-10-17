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
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Games", "Background Apps")

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Game Booster") })
        
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }

        when (selectedTab) {
            0 -> GameListTab(viewModel)
            1 -> BackgroundAppsTab(viewModel)
        }
    }
}

@Composable
fun GameListTab(viewModel: GameViewModel) {
    val games by viewModel.games.collectAsState()
    var selectedGame by remember { mutableStateOf<GameApp?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    LazyColumn {
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

    LazyColumn {
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
