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
    val games by viewModel.games.collectAsState()
    var selectedGame by remember { mutableStateOf<GameApp?>(null) }
    var showDialog by remember { mutableStateOf(false) }
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
                }
            }
        )
        
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Native Res: ${viewModel.nativeResolution}", style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(onClick = {
                if (ShizukuHelper.isShizukuAvailable() && !ShizukuHelper.hasPermission()) {
                    ShizukuHelper.requestPermission()
                }
            }) {
                Text("Initialize Shizuku")
            }
            
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
                    Text(stringResource(id = R.string.language_ru), modifier = Modifier.fillMaxWidth().clickable { showLanguageDialog = false })
                    Text(stringResource(id = R.string.language_zh), modifier = Modifier.fillMaxWidth().clickable { showLanguageDialog = false })
                    Text(stringResource(id = R.string.language_ko), modifier = Modifier.fillMaxWidth().clickable { showLanguageDialog = false })
                    Text(stringResource(id = R.string.language_hi), modifier = Modifier.fillMaxWidth().clickable { showLanguageDialog = false })
                    Text(stringResource(id = R.string.language_fr), modifier = Modifier.fillMaxWidth().clickable { showLanguageDialog = false })
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(stringResource(id = R.string.ok))
                }
            }
        )
    }

    if (showDialog && selectedGame != null) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Edit Profile: ${selectedGame?.name}") },
            text = {
                Text("Tap to simulate save (1080x1920, 480dpi)")
            },
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
