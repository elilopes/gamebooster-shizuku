package com.gfx

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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
 * Representa um aplicativo analisado pelo Gerenciador de Bloatware
 */
data class BloatwareItem(
    val packageName: String,
    val appName: String,
    val category: String,
    val description: String,
    val isSystemApp: Boolean,
    val isInstalled: Boolean,
    val isEnabled: Boolean,
    val isDangerousToRemove: Boolean,
    val iconBitmap: Bitmap? = null
)

enum class BloatwareFilter(val label: String) {
    RECOMMENDED("Bloatwares Detectados"),
    UNINSTALLED("Desativados / Removidos"),
    ALL_SYSTEM("Todos os Apps do Sistema")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BloatwareManagerDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(BloatwareFilter.RECOMMENDED) }
    var isLoading by remember { mutableStateOf(true) }
    var bloatwareList by remember { mutableStateOf<List<BloatwareItem>>(emptyList()) }
    var selectedAppForRemoval by remember { mutableStateOf<BloatwareItem?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    fun loadBloatwareList() {
        isLoading = true
        scope.launch(Dispatchers.IO) {
            val pm = context.packageManager
            val installedPackages = try {
                pm.getInstalledPackages(PackageManager.GET_META_DATA)
            } catch (_: Exception) {
                emptyList()
            }

            // Pacotes desinstalados para usuário 0 ou desativados
            val uninstalledOutput = try {
                ShellFunctions.executePrivileged("pm list packages -u -d").trim()
            } catch (_: Exception) {
                ""
            }
            val disabledOrUninstalledPackages = uninstalledOutput.lines()
                .map { it.removePrefix("package:").trim() }
                .filter { it.isNotBlank() }
                .toSet()

            // Banco de dados de Bloatwares conhecidos do Android
            val knownBloatMap = mapOf(
                // Facebook & Meta
                "com.facebook.services" to Pair("Facebook / Telemetria", "Serviço em segundo plano da Meta para sincronização e análise."),
                "com.facebook.system" to Pair("Facebook / Telemetria", "Instalador do sistema Meta frequentemente pré-carregado em aparelhos."),
                "com.facebook.appmanager" to Pair("Facebook / Telemetria", "Gerenciador de atualizações em segundo plano do ecossistema Facebook."),
                "com.facebook.katana" to Pair("Facebook / Rede Social", "Aplicativo principal do Facebook pré-instalado em partição de sistema."),
                
                // Samsung Bloat
                "com.samsung.android.bixby.agent" to Pair("Samsung / Bixby", "Assistente de voz Bixby e serviços de segundo plano."),
                "com.samsung.android.bixby.service" to Pair("Samsung / Bixby", "Serviço principal de ativação do Bixby."),
                "com.samsung.android.app.spage" to Pair("Samsung / Notícias", "Samsung Free / Bixby Home com feed de notícias e anúncios."),
                "com.samsung.android.game.gamehome" to Pair("Samsung / Games", "Samsung Gaming Hub / Game Launcher."),
                "com.samsung.android.game.gametools" to Pair("Samsung / Games", "Serviços extras de sobreposição de jogos da Samsung."),
                "com.samsung.android.aremoji" to Pair("Samsung / Câmera", "Recurso AR Emoji de avatares 3D."),
                "com.samsung.android.app.tips" to Pair("Samsung / Dicas", "Notificações e tutoriais de dicas do sistema One UI."),
                "com.sec.android.app.samsungapps" to Pair("Samsung / Loja", "Galaxy Store da Samsung."),
                "com.samsung.android.allshare.service.mediashare" to Pair("Samsung / Mídia", "Serviço de compartilhamento de tela e streaming local."),

                // Xiaomi / MIUI / HyperOS
                "com.miui.analytics" to Pair("Xiaomi / Telemetria", "Serviço de coleta de dados analíticos e telemetria da Xiaomi."),
                "com.miui.msa.global" to Pair("Xiaomi / Anúncios", "MIUI System Ads (MSA), responsável pela exibição de anúncios no sistema."),
                "com.miui.bugreport" to Pair("Xiaomi / Telemetria", "Ferramenta de envio de relatórios e telemetria."),
                "com.miui.cleanmaster" to Pair("Xiaomi / Limpeza", "Módulo do CleanMaster integrado com publicidade."),
                "com.miui.hybrid" to Pair("Xiaomi / Quick Apps", "Plataforma Quick Apps com permissões extensivas de rastreamento."),
                "com.miui.yellowpage" to Pair("Xiaomi / Serviços", "Páginas Amarelas e identificação comercial da Xiaomi."),
                "com.mi.globalbrowser" to Pair("Xiaomi / Navegador", "Mi Browser pré-instalado com feed de publicidade."),
                "com.xiaomi.glgm" to Pair("Xiaomi / Games", "Central de jogos e promoções da Xiaomi."),
                "com.xiaomi.payment" to Pair("Xiaomi / Pagamentos", "Mi Pay integrado em sistemas MIUI/HyperOS."),

                // Google Telemetria & Pré-instalados
                "com.google.android.apps.tachyon" to Pair("Google / Meet", "Google Meet / Duo pré-instalado em partição de sistema."),
                "com.google.android.feedback" to Pair("Google / Telemetria", "Serviço de telemetria e envio de feedback do Android."),
                "com.google.android.apps.youtube.music" to Pair("Google / Mídia", "YouTube Music pré-carregado no sistema."),
                "com.google.android.videos" to Pair("Google / TV", "Google TV / Play Filmes."),
                "com.google.android.apps.magazines" to Pair("Google / Notícias", "Google Notícias e Revistas."),

                // Parceiros & Operadoras
                "com.netflix.partner.activation" to Pair("Parceiro / Streaming", "Serviço de pré-ativação de fábrica da Netflix."),
                "com.amazon.appmanager" to Pair("Amazon / Loja", "Gerenciador de aplicativos pré-instalados da Amazon."),
                "com.booking" to Pair("Parceiro / Viagens", "Aplicativo de reservas Booking pré-instalado."),
                "com.zhiliaoapp.musically" to Pair("Parceiro / Redes", "TikTok pré-instalado por fabricantes/operadoras."),
                "com.clarobrasil.minhaclaro" to Pair("Operadora / Claro", "Aplicativo de serviços da operadora Claro."),
                "br.com.vivo" to Pair("Operadora / Vivo", "Aplicativo de serviços da operadora Vivo."),
                "com.timbrasil.meutim" to Pair("Operadora / TIM", "Aplicativo de serviços da operadora TIM."),
                "com.vzw.hss.myverizon" to Pair("Operadora / Verizon", "Serviços da operadora Verizon.")
            )

            // Pacotes essenciais do Android que NÃO devem ser removidos
            val criticalPackages = setOf(
                "android",
                "com.android.systemui",
                "com.android.settings",
                "com.android.vending",
                "com.google.android.gms",
                "com.google.android.gsf",
                "com.android.webview",
                "com.google.android.webview",
                "com.android.packageinstaller",
                "com.google.android.packageinstaller"
            )

            val items = mutableListOf<BloatwareItem>()

            for (pkg in installedPackages) {
                val pName = pkg.packageName
                val isSystem = (pkg.applicationInfo != null && (pkg.applicationInfo!!.flags and ApplicationInfo.FLAG_SYSTEM) != 0)
                val isKnown = knownBloatMap.containsKey(pName)
                val isCritical = criticalPackages.contains(pName)

                val label = try {
                    pkg.applicationInfo?.loadLabel(pm)?.toString() ?: pName
                } catch (_: Exception) {
                    pName
                }

                val iconBitmap = try {
                    val drawable = pkg.applicationInfo?.loadIcon(pm)
                    drawable?.let { drawableToBitmap(it) }
                } catch (_: Exception) {
                    null
                }

                val (cat, desc) = knownBloatMap[pName] ?: if (isSystem) {
                    Pair("App de Sistema", if (isCritical) "Componente vital do Android." else "Aplicativo pré-instalado pelo fabricante.")
                } else {
                    Pair("Aplicativo de Usuário", "Aplicativo instalado pelo usuário.")
                }

                val isEnabled = pkg.applicationInfo?.enabled ?: true

                items.add(
                    BloatwareItem(
                        packageName = pName,
                        appName = label,
                        category = cat,
                        description = desc,
                        isSystemApp = isSystem,
                        isInstalled = true,
                        isEnabled = isEnabled,
                        isDangerousToRemove = isCritical,
                        iconBitmap = iconBitmap
                    )
                )
            }

            // Adicionar pacotes conhecidos que já foram desinstalados para usuário 0
            for ((pkgName, info) in knownBloatMap) {
                if (disabledOrUninstalledPackages.contains(pkgName) && items.none { it.packageName == pkgName }) {
                    items.add(
                        BloatwareItem(
                            packageName = pkgName,
                            appName = pkgName,
                            category = info.first,
                            description = "${info.second} (Atualmente desativado / removido)",
                            isSystemApp = true,
                            isInstalled = false,
                            isEnabled = false,
                            isDangerousToRemove = false,
                            iconBitmap = null
                        )
                    )
                }
            }

            withContext(Dispatchers.Main) {
                bloatwareList = items.sortedWith(
                    compareByDescending<BloatwareItem> { knownBloatMap.containsKey(it.packageName) }
                        .thenBy { it.appName.lowercase() }
                )
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadBloatwareList()
    }

    // Ações de remoção / desativação / restauração
    fun uninstallBloatware(item: BloatwareItem) {
        scope.launch(Dispatchers.IO) {
            val cmd = "pm uninstall -k --user 0 ${item.packageName}"
            val out = ShellFunctions.executePrivileged(cmd).trim()
            withContext(Dispatchers.Main) {
                if (out.contains("Success", ignoreCase = true)) {
                    statusMessage = "✅ ${item.appName} removido com sucesso para o Usuário 0!"
                    Toast.makeText(context, statusMessage, Toast.LENGTH_SHORT).show()
                    loadBloatwareList()
                } else {
                    statusMessage = "⚠️ Falha ao remover ${item.appName}: $out"
                    Toast.makeText(context, statusMessage, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun disableBloatware(item: BloatwareItem) {
        scope.launch(Dispatchers.IO) {
            val cmd = "pm disable-user --user 0 ${item.packageName}"
            val out = ShellFunctions.executePrivileged(cmd).trim()
            withContext(Dispatchers.Main) {
                if (out.contains("disabled", ignoreCase = true) || out.contains("new state", ignoreCase = true)) {
                    statusMessage = "✅ ${item.appName} desativado com êxito!"
                    Toast.makeText(context, statusMessage, Toast.LENGTH_SHORT).show()
                    loadBloatwareList()
                } else {
                    statusMessage = "⚠️ Falha ao desativar ${item.appName}: $out"
                    Toast.makeText(context, statusMessage, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun restoreBloatware(item: BloatwareItem) {
        scope.launch(Dispatchers.IO) {
            val cmdInstall = "pm install-existing ${item.packageName}"
            val cmdEnable = "pm enable ${item.packageName}"
            val out = ShellFunctions.executePrivileged("$cmdInstall ; $cmdEnable").trim()
            withContext(Dispatchers.Main) {
                if (out.contains("installed", ignoreCase = true) || out.contains("enabled", ignoreCase = true) || out.contains("Package", ignoreCase = true)) {
                    statusMessage = "✅ ${item.appName} restaurado com sucesso!"
                    Toast.makeText(context, statusMessage, Toast.LENGTH_SHORT).show()
                    loadBloatwareList()
                } else {
                    statusMessage = "⚠️ Resposta do sistema: $out"
                    Toast.makeText(context, statusMessage, Toast.LENGTH_LONG).show()
                    loadBloatwareList()
                }
            }
        }
    }

    val filteredList = remember(bloatwareList, searchQuery, selectedFilter) {
        bloatwareList.filter { item ->
            val matchesSearch = item.appName.contains(searchQuery, ignoreCase = true) ||
                    item.packageName.contains(searchQuery, ignoreCase = true) ||
                    item.category.contains(searchQuery, ignoreCase = true)

            val matchesFilter = when (selectedFilter) {
                BloatwareFilter.RECOMMENDED -> (item.category.contains("Telemetria") ||
                        item.category.contains("Facebook") ||
                        item.category.contains("Xiaomi") ||
                        item.category.contains("Samsung") ||
                        item.category.contains("Operadora") ||
                        item.category.contains("Parceiro") ||
                        item.category.contains("Anúncios")) && item.isInstalled

                BloatwareFilter.UNINSTALLED -> !item.isInstalled || !item.isEnabled
                BloatwareFilter.ALL_SYSTEM -> item.isSystemApp
            }

            matchesSearch && matchesFilter
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f),
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
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Gerenciador de Bloatware",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Remova telemetrias e aplicativos desnecessários sem risco de brick",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_bloatware_manager_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Campo de Busca
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_bloatware_input"),
                    placeholder = { Text("Pesquisar aplicativo ou pacote...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Limpar busca")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Filtros rápidos
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    BloatwareFilter.values().forEach { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            label = { Text(filter.label, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f).testTag("filter_${filter.name}")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Card explicativo sobre a segurança do modo usuário 0
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "A remoção via usuário 0 (pm uninstall --user 0) é 100% segura e reversível: não modifica a partição de sistema e pode ser restaurada com 1 clique.",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Lista de aplicativos
                if (isLoading) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("Escaneando pacotes do dispositivo...", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                } else if (filteredList.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Nenhum aplicativo encontrado neste filtro.", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                } else {
                    Text(
                        text = "Exibindo ${filteredList.size} aplicativo(s):",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredList, key = { it.packageName }) { item ->
                            BloatwareCard(
                                item = item,
                                onUninstall = { selectedAppForRemoval = item },
                                onDisable = { disableBloatware(item) },
                                onRestore = { restoreBloatware(item) }
                            )
                        }
                    }
                }

                // Botão de atualizar / fechar na base
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { loadBloatwareList() },
                        modifier = Modifier.weight(1f).testTag("refresh_bloatware_list_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Atualizar Lista")
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).testTag("close_bloatware_dialog_bottom_button")
                    ) {
                        Text("Concluir")
                    }
                }
            }
        }
    }

    // Modal de confirmação para remoção de aplicativo
    if (selectedAppForRemoval != null) {
        val app = selectedAppForRemoval!!
        AlertDialog(
            onDismissRequest = { selectedAppForRemoval = null },
            icon = {
                Icon(
                    imageVector = if (app.isDangerousToRemove) Icons.Default.Warning else Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = if (app.isDangerousToRemove) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(if (app.isDangerousToRemove) "Aviso: Componente do Sistema" else "Remover Bloatware")
            },
            text = {
                Column {
                    Text(
                        text = "Deseja desinstalar o pacote para o Usuário 0?",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Aplicativo: ${app.appName}\nPacote: ${app.packageName}\nCategoria: ${app.category}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                    if (app.isDangerousToRemove) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "⚠️ ATENÇÃO: Este pacote faz parte dos componentes centrais do sistema operacional. Remover este pacote pode causar instabilidade no Android.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val toRemove = selectedAppForRemoval
                        selectedAppForRemoval = null
                        if (toRemove != null) {
                            uninstallBloatware(toRemove)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (app.isDangerousToRemove) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Confirmar Remoção")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { selectedAppForRemoval = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun BloatwareCard(
    item: BloatwareItem,
    onUninstall: () -> Unit,
    onDisable: () -> Unit,
    onRestore: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("bloatware_card_${item.packageName}"),
        colors = CardDefaults.cardColors(
            containerColor = if (!item.isInstalled || !item.isEnabled) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
            }
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Ícone do App
                if (item.iconBitmap != null) {
                    Image(
                        bitmap = item.iconBitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(38.dp)
                            .padding(end = 8.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Android,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.appName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = item.packageName,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Tag de Categoria
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (item.category.contains("Telemetria") || item.category.contains("Anúncios")) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    }
                ) {
                    Text(
                        text = item.category.take(18),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (item.category.contains("Telemetria") || item.category.contains("Anúncios")) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = item.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Linha de Status e Botões de Ação
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val statusColor = when {
                        !item.isInstalled -> Color(0xFFE65100)
                        !item.isEnabled -> Color.Gray
                        else -> Color(0xFF2E7D32)
                    }
                    val statusText = when {
                        !item.isInstalled -> "Removido (User 0)"
                        !item.isEnabled -> "Desativado"
                        else -> "Ativo no Sistema"
                    }
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(statusColor, RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }

                // Botões de Ação
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (item.isInstalled && item.isEnabled) {
                        OutlinedButton(
                            onClick = onDisable,
                            modifier = Modifier.testTag("disable_btn_${item.packageName}"),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Desativar", fontSize = 11.sp)
                        }

                        Button(
                            onClick = onUninstall,
                            modifier = Modifier.testTag("uninstall_btn_${item.packageName}"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (item.isDangerousToRemove) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Remover", fontSize = 11.sp)
                        }
                    } else {
                        Button(
                            onClick = onRestore,
                            modifier = Modifier.testTag("restore_btn_${item.packageName}"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Restaurar App", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Converte um Drawable em Bitmap de alta qualidade para renderização no Compose
 */
private fun drawableToBitmap(drawable: Drawable): Bitmap {
    if (drawable is BitmapDrawable && drawable.bitmap != null) {
        return drawable.bitmap
    }
    val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 48
    val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 48
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}
