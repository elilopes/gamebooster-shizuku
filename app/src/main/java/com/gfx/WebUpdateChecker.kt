package com.gfx

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Status da verificação de versão de aplicativo/jogo na web.
 */
enum class UpdateBadgeStatus {
    IDLE,             // Aguardando verificação
    CHECKING,         // Consultando repositórios na web...
    UP_TO_DATE,       // 🟢 Atualizado
    UPDATE_AVAILABLE, // 🟠 Nova versão encontrada na web (vX.X)
    NOT_FOUND,        // ⚪ Não identificado automaticamente na web
    ERROR             // 🔴 Falha de rede / conexão
}

/**
 * Dados do resultado da consulta de atualização na web.
 */
data class WebUpdateInfo(
    val packageName: String,
    val installedVersion: String,
    val status: UpdateBadgeStatus = UpdateBadgeStatus.IDLE,
    val latestVersion: String? = null,
    val downloadUrl: String? = null,
    val webPageUrl: String? = null,
    val sourceName: String? = null,
    val details: String? = null
)

object WebUpdateChecker {

    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile; rv:120.0) Gecko/120.0 Firefox/120.0"
    private const val TIMEOUT_MS = 6000

    /**
     * Compara versões semânticas (ex: "1.130.0" vs "1.132.1", "v2.5" vs "2.6.1").
     * Retorna:
     *   < 0 se installedVersion < remoteVersion (Há versão mais recente na web)
     *   0 se são iguais
     *   > 0 se installedVersion > remoteVersion (Instalada é mais recente ou igual)
     */
    fun compareVersions(installedVersion: String, remoteVersion: String): Int {
        fun extractParts(v: String): List<Long> {
            val sanitized = v.trim().removePrefix("v").removePrefix("V")
            val numbers = Regex("[0-9]+").findAll(sanitized).mapNotNull { it.value.toLongOrNull() }.toList()
            return numbers
        }

        val partsInstalled = extractParts(installedVersion)
        val partsRemote = extractParts(remoteVersion)

        val maxLen = maxOf(partsInstalled.size, partsRemote.size)
        for (i in 0 until maxLen) {
            val p1 = partsInstalled.getOrElse(i) { 0L }
            val p2 = partsRemote.getOrElse(i) { 0L }
            if (p1 != p2) {
                return p1.compareTo(p2)
            }
        }
        return 0
    }

    /**
     * Consulta endpoints públicos de repositórios abertos e web scraping
     * para obter a versão mais recente e o link direto para download.
     */
    suspend fun checkUpdate(packageName: String, installedVersion: String): WebUpdateInfo = withContext(Dispatchers.IO) {
        // 1. Tentar endpoint direto do APKPure (obter redirecionamento com nome do arquivo e link direto de APK)
        try {
            val apkPureResult = checkApkPureDirect(packageName, installedVersion)
            if (apkPureResult != null) {
                return@withContext apkPureResult
            }
        } catch (e: Exception) {
            // Continua para o próximo repositório
        }

        // 2. Tentar endpoint da API pública do F-Droid (especialmente útil para apps e emuladores open source)
        try {
            val fDroidResult = checkFDroid(packageName, installedVersion)
            if (fDroidResult != null) {
                return@withContext fDroidResult
            }
        } catch (e: Exception) {
            // Continua para o próximo
        }

        // 3. Tentar web scraping da página pública da Google Play Store (extração regex da versão oficial)
        try {
            val playStoreResult = checkGooglePlayWeb(packageName, installedVersion)
            if (playStoreResult != null) {
                return@withContext playStoreResult
            }
        } catch (e: Exception) {
            // Continua para o fallback
        }

        // 4. Fallback: Fornecer link direto para busca no APKMirror e APKPure
        val apkMirrorSearch = "https://www.apkmirror.com/?post_type=app_release&searchtype=apk&s=${URLEncoder.encode(packageName, "UTF-8")}"
        return@withContext WebUpdateInfo(
            packageName = packageName,
            installedVersion = installedVersion,
            status = UpdateBadgeStatus.NOT_FOUND,
            webPageUrl = apkMirrorSearch,
            sourceName = "APKMirror (Busca Web)",
            details = "Não foi possível extrair a versão automaticamente. Use o link para pesquisar na web."
        )
    }

    /**
     * Consulta o endpoint de download direto do APKPure.
     * https://d.apkpure.net/b/APK/{packageName}?version=latest
     */
    private fun checkApkPureDirect(packageName: String, installedVersion: String): WebUpdateInfo? {
        val targetUrl = "https://d.apkpure.net/b/APK/$packageName?version=latest"
        var conn: HttpURLConnection? = null
        try {
            conn = URL(targetUrl).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "*/*")
            conn.requestMethod = "GET"
            conn.connect()

            val responseCode = conn.responseCode
            if (responseCode in 300..399) {
                val location = conn.getHeaderField("Location") ?: ""
                if (location.isNotBlank()) {
                    // Extrair versão do parâmetro filename (ex: filename=AppName_3.70.3_APKPure.apk)
                    val filenameRegex = Regex("""filename=[^&]*?([0-9]+(?:\.[0-9]+)+)""")
                    val directVersionRegex = Regex("""_([0-9]+(?:\.[0-9]+)+)_(?:APKPure|apkpure)""")

                    val match = filenameRegex.find(location) ?: directVersionRegex.find(location)
                    val remoteVersion = match?.groupValues?.get(1)

                    if (remoteVersion != null && remoteVersion.isNotBlank()) {
                        val cmp = compareVersions(installedVersion, remoteVersion)
                        val status = if (cmp < 0) UpdateBadgeStatus.UPDATE_AVAILABLE else UpdateBadgeStatus.UP_TO_DATE
                        return WebUpdateInfo(
                            packageName = packageName,
                            installedVersion = installedVersion,
                            status = status,
                            latestVersion = remoteVersion,
                            downloadUrl = location,
                            webPageUrl = "https://apkpure.com/p/$packageName",
                            sourceName = "APKPure Repositório",
                            details = if (cmp < 0) "Nova versão $remoteVersion pronta para download" else "Jogo atualizado"
                        )
                    }
                }
            }
        } finally {
            conn?.disconnect()
        }
        return null
    }

    /**
     * Consulta a API aberta do F-Droid.
     */
    private fun checkFDroid(packageName: String, installedVersion: String): WebUpdateInfo? {
        val targetUrl = "https://f-droid.org/api/v1/packages/$packageName"
        var conn: HttpURLConnection? = null
        try {
            conn = URL(targetUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "application/json")
            conn.connect()

            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val response = reader.readText()
                reader.close()

                val json = JSONObject(response)
                val packages = json.optJSONArray("packages")
                if (packages != null && packages.length() > 0) {
                    val latestPkg = packages.getJSONObject(0)
                    val remoteVersion = latestPkg.optString("versionName", "")
                    val apkName = latestPkg.optString("apkName", "")

                    if (remoteVersion.isNotBlank()) {
                        val cmp = compareVersions(installedVersion, remoteVersion)
                        val status = if (cmp < 0) UpdateBadgeStatus.UPDATE_AVAILABLE else UpdateBadgeStatus.UP_TO_DATE
                        val downloadUrl = if (apkName.isNotBlank()) "https://f-droid.org/repo/$apkName" else "https://f-droid.org/packages/$packageName/"
                        return WebUpdateInfo(
                            packageName = packageName,
                            installedVersion = installedVersion,
                            status = status,
                            latestVersion = remoteVersion,
                            downloadUrl = downloadUrl,
                            webPageUrl = "https://f-droid.org/packages/$packageName/",
                            sourceName = "F-Droid Repositório Aberto",
                            details = if (cmp < 0) "Nova versão $remoteVersion disponível no F-Droid" else "App atualizado"
                        )
                    }
                }
            }
        } finally {
            conn?.disconnect()
        }
        return null
    }

    /**
     * Realiza web scraping no endpoint público da Google Play Store para extrair a versão oficial.
     */
    private fun checkGooglePlayWeb(packageName: String, installedVersion: String): WebUpdateInfo? {
        val targetUrl = "https://play.google.com/store/apps/details?id=$packageName&hl=en"
        var conn: HttpURLConnection? = null
        try {
            conn = URL(targetUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            conn.connect()

            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val html = reader.readText()
                reader.close()

                // Regex para capturar o array interno de versão no HTML do Google Play Store: [[["1.2.3"]
                val versionRegex = Regex("""\[\[\["([0-9]+(?:\.[0-9]+)+[^"]*)"]""")
                val match = versionRegex.find(html)
                val remoteVersion = match?.groupValues?.get(1)?.trim()

                if (remoteVersion != null && remoteVersion.isNotBlank()) {
                    val cmp = compareVersions(installedVersion, remoteVersion)
                    val status = if (cmp < 0) UpdateBadgeStatus.UPDATE_AVAILABLE else UpdateBadgeStatus.UP_TO_DATE
                    val apkMirrorSearch = "https://www.apkmirror.com/?post_type=app_release&searchtype=apk&s=${URLEncoder.encode(packageName, "UTF-8")}"
                    return WebUpdateInfo(
                        packageName = packageName,
                        installedVersion = installedVersion,
                        status = status,
                        latestVersion = remoteVersion,
                        downloadUrl = apkMirrorSearch,
                        webPageUrl = "https://play.google.com/store/apps/details?id=$packageName",
                        sourceName = "Google Play Web / APKMirror",
                        details = if (cmp < 0) "Versão recente v$remoteVersion encontrada na web" else "Jogo atualizado"
                    )
                }
            }
        } finally {
            conn?.disconnect()
        }
        return null
    }

    /**
     * Abre a URL fornecida no navegador padrão ou gerenciador de downloads do sistema.
     */
    fun openUrl(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
