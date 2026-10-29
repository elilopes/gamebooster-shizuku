package com.gfx

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/**
 * [PT-BR] Gerenciador e motor de análise e instalação detalhada de APKs, APKS, XAPK, APKM e Backups.
 * [EN] Manager and engine for detailed analysis and installation of APKs, APKS, XAPK, APKM, and Backups.
 */
object ApkInstallerManager {

    private val _logcatStream = MutableStateFlow<List<String>>(emptyList())
    val logcatStream: StateFlow<List<String>> = _logcatStream.asStateFlow()

    private var logcatProcess: Process? = null

    fun addInstallerLog(log: String) {
        val current = _logcatStream.value.toMutableList()
        current.add(log)
        if (current.size > 200) {
            current.removeAt(0)
        }
        _logcatStream.value = current
    }

    fun clearInstallerLogs() {
        _logcatStream.value = emptyList()
    }

    /**
     * Inicia streaming em tempo real do logcat do Android filtrado por PackageManager, PackageInstaller, installd, dex2oat e nome do pacote.
     */
    fun startLogcatMonitoring(targetPackageName: String) {
        stopLogcatMonitoring()
        clearInstallerLogs()
        addInstallerLog("[LOGCAT] Iniciando monitoramento ao vivo de eventos do sistema (PackageManager / PackageInstaller / installd / dex2oat)...")
        Thread {
            try {
                // Limpa buffer anterior para capturar eventos frescos da instalação
                Runtime.getRuntime().exec(arrayOf("sh", "-c", "logcat -c")).waitFor()
                val cmd = arrayOf(
                    "sh",
                    "-c",
                    "logcat -v time -s PackageManager:V PackageInstaller:V installd:V dex2oat:V | grep -E 'PackageManager|PackageInstaller|installd|dex2oat|${targetPackageName}'"
                )
                logcatProcess = Runtime.getRuntime().exec(cmd)
                val reader = logcatProcess?.inputStream?.bufferedReader()
                while (reader != null) {
                    val line = reader.readLine() ?: break
                    addInstallerLog(line)
                }
            } catch (_: Exception) {
            }
        }.start()
    }

    fun stopLogcatMonitoring() {
        try {
            logcatProcess?.destroy()
            logcatProcess = null
        } catch (_: Exception) {
        }
    }

    /**
     * Calcula hash SHA-256 de um InputStream com buffer
     */
    fun calculateSha256(inputStream: InputStream): String {
        addInstallerLog("[LOGCAT] Iniciando o SHA-256, verificando se o arquivo foi adulterado/corrompido...")
        
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(64 * 1024)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            "Erro ao calcular SHA-256: ${e.message}"
        } finally {
            try { inputStream.close() } catch (_: Exception) {}
        }
    }

    fun calculateFileSha256(file: File): String {
        return try {
            FileInputStream(file).use { calculateSha256(it) }
        } catch (e: Exception) {
            "SHA-256 Indisponível"
        }
    }

    /**
     * Detecta o tipo de pacote com base no nome do arquivo
     */
    fun detectPackageSourceType(fileName: String): PackageSourceType {
        val lower = fileName.lowercase()
        return when {
            lower.endsWith(".xapk") -> PackageSourceType.XAPK
            lower.endsWith(".apks") -> PackageSourceType.APKS
            lower.endsWith(".apkm") -> PackageSourceType.APKM
            lower.endsWith(".zip") -> PackageSourceType.APP_BACKUP_ZIP
            lower.endsWith(".apk") -> PackageSourceType.APK
            else -> PackageSourceType.UNKNOWN
        }
    }

    /**
     * Obtém nome e tamanho do arquivo via ContentResolver
     */
    fun queryFileInfo(context: Context, uri: Uri): Pair<String, Long> {
        var name = "arquivo_instalador"
        var size: Long = 0
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) name = cursor.getString(nameIndex)
                    if (sizeIndex != -1) size = cursor.getLong(sizeIndex)
                }
            }
        } catch (_: Exception) {}

        if (name == "arquivo_instalador") {
            uri.lastPathSegment?.let { name = it.substringAfterLast('/') }
        }
        return Pair(name, size)
    }

    /**
     * Converte Drawable para Bitmap
     */
    fun drawableToBitmap(drawable: Drawable?): Bitmap? {
        if (drawable == null) return null
        if (drawable is BitmapDrawable) {
            return drawable.bitmap
        }
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    /**
     * Descompacta e prepara a área de trabalho (Staging)
     */
    suspend fun extractAndStage(
        context: Context,
        sourceUri: Uri,
        fileName: String,
        sourceType: PackageSourceType,
        totalSize: Long,
        onProgress: (Float, String) -> Unit
    ): Pair<File, String> = withContext(Dispatchers.IO) {
        // Limpar sessões antigas com segurança
        val baseStaging = File(context.cacheDir, "apk_installer_staging")
        if (baseStaging.exists()) {
            baseStaging.deleteRecursively()
        }
        baseStaging.mkdirs()

        val stagingDir = File(baseStaging, "${System.currentTimeMillis()}")
        stagingDir.mkdirs()
        stagingDir.setReadable(true, false)
        stagingDir.setExecutable(true, false)

        val obbStaging = File(stagingDir, "obb")
        obbStaging.mkdirs()
        obbStaging.setReadable(true, false)
        obbStaging.setExecutable(true, false)

        var sha256 = ""

        if (sourceType == PackageSourceType.APK) {
            onProgress(0.1f, "Copiando APK para área de trabalho...")
            val targetFile = File(stagingDir, "base.apk")
            val digest = MessageDigest.getInstance("SHA-256")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesCopied: Long = 0
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                        bytesCopied += read
                        val pct = if (totalSize > 0) (bytesCopied.toFloat() / totalSize.toFloat()).coerceIn(0.1f, 0.95f) else 0.5f
                        onProgress(pct, "Copiando APK: ${bytesCopied / 1024} KB...")
                    }
                }
            }
            targetFile.setReadable(true, false)
            sha256 = digest.digest().joinToString("") { "%02x".format(it) }
            onProgress(1.0f, "APK copiado com sucesso!")
        } else {
            // Arquivo ZIP/XAPK/APKS/APKM
            onProgress(0.05f, "Calculando SHA-256 e abrindo pacote...")
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(64 * 1024)
            var totalBytesRead: Long = 0

            context.contentResolver.openInputStream(sourceUri)?.use { rawIn ->
                // Lê calculando hash e gravando temporariamente para descompactação confiável
                val tempZip = File(stagingDir, "package_source.tmp")
                FileOutputStream(tempZip).use { tmpOut ->
                    var r: Int
                    while (rawIn.read(buffer).also { r = it } != -1) {
                        digest.update(buffer, 0, r)
                        tmpOut.write(buffer, 0, r)
                        totalBytesRead += r
                        val p = if (totalSize > 0) (totalBytesRead.toFloat() / totalSize.toFloat() * 0.4f).coerceIn(0.05f, 0.4f) else 0.2f
                        onProgress(p, "Lendo origem: ${totalBytesRead / (1024 * 1024)} MB...")
                    }
                }
                sha256 = digest.digest().joinToString("") { "%02x".format(it) }

                onProgress(0.45f, "Descompactando entradas do pacote...")
                ZipFile(tempZip).use { zip ->
                    val entries = zip.entries().toList()
                    val totalEntries = entries.size.coerceAtLeast(1)
                    var processedEntries = 0

                    for (entry in entries) {
                        processedEntries++
                        val entryName = entry.name
                        val lowerName = entryName.lowercase()

                        val isApk = lowerName.endsWith(".apk")
                        val isObb = lowerName.endsWith(".obb") || lowerName.contains("android/obb/")
                        val isData = lowerName.contains("android/data/") || lowerName.contains("/data/data/")

                        if (isApk) {
                            val cleanName = entryName.substringAfterLast('/')
                            val outApk = File(stagingDir, cleanName)
                            zip.getInputStream(entry).use { inStream ->
                                FileOutputStream(outApk).use { outStream ->
                                    inStream.copyTo(outStream)
                                }
                            }
                            outApk.setReadable(true, false)
                        } else if (isObb) {
                            val cleanName = entryName.substringAfterLast('/')
                            val outObb = File(obbStaging, cleanName)
                            zip.getInputStream(entry).use { inStream ->
                                FileOutputStream(outObb).use { outStream ->
                                    inStream.copyTo(outStream)
                                }
                            }
                            outObb.setReadable(true, false)
                        }

                        val pct = 0.45f + (processedEntries.toFloat() / totalEntries.toFloat() * 0.5f)
                        onProgress(pct, "Extraindo: ${entryName.substringAfterLast('/')} ($processedEntries/$totalEntries)")
                    }
                }
                tempZip.delete()
            }
            onProgress(1.0f, "Extração concluída!")
        }

        Pair(stagingDir, sha256)
    }

    /**
     * Analisa minuciosamente os metadados do APK através do PackageManager
     */
    suspend fun analyzeStagedPackage(
        context: Context,
        stagingDir: File,
        sourceFileName: String,
        sourceFileSize: Long,
        sourceSha256: String,
        sourceType: PackageSourceType
    ): ApkMetadata = withContext(Dispatchers.IO) {
        val pm = context.packageManager

        // Localizar todos os APKs extraídos
        val apks = stagingDir.listFiles { f -> f.isFile && f.name.endsWith(".apk", ignoreCase = true) }?.toList() ?: emptyList()
        if (apks.isEmpty()) {
            throw IllegalStateException("Nenhum arquivo APK válido foi encontrado no pacote selecionado.")
        }

        // Determinar o APK base (geralmente nomeado base.apk ou o maior arquivo)
        val baseApk = apks.find { it.name.equals("base.apk", ignoreCase = true) }
            ?: apks.maxByOrNull { it.length() }
            ?: apks.first()

        // Obter PackageInfo completo
        val archiveFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_PERMISSIONS or PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_PERMISSIONS or PackageManager.GET_SIGNATURES or PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES
        }

        val packageInfo = pm.getPackageArchiveInfo(baseApk.absolutePath, archiveFlags)
            ?: throw IllegalStateException("Falha ao ler metadados do arquivo APK base (${baseApk.name}).")

        val appInfo = packageInfo.applicationInfo ?: ApplicationInfo()
        appInfo.sourceDir = baseApk.absolutePath
        appInfo.publicSourceDir = baseApk.absolutePath

        val packageName = packageInfo.packageName
        val label = try {
            pm.getApplicationLabel(appInfo).toString().ifBlank { packageName }
        } catch (_: Exception) {
            packageName
        }

        val versionName = packageInfo.versionName ?: "1.0"
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }

        val iconBitmap = try {
            drawableToBitmap(appInfo.loadIcon(pm))
        } catch (_: Exception) {
            null
        }

        val minSdkVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            appInfo.minSdkVersion
        } else {
            24
        }
        val targetSdkVersion = appInfo.targetSdkVersion

        // 1. Inspecionar ABIs suportadas varrendo lib/ dos APKs
        val supportedAbis = mutableSetOf<String>()
        for (apk in apks) {
            try {
                ZipFile(apk).use { zip ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val e = entries.nextElement().name
                        if (e.startsWith("lib/")) {
                            val abi = e.removePrefix("lib/").substringBefore('/')
                            if (abi.isNotBlank()) supportedAbis.add(abi)
                        }
                    }
                }
            } catch (_: Exception) {}
            // Também inspecionar pelo nome de split ex: config.arm64_v8a.apk
            val name = apk.name.lowercase()
            when {
                name.contains("arm64") -> supportedAbis.add("arm64-v8a")
                name.contains("armeabi") || name.contains("armv7") -> supportedAbis.add("armeabi-v7a")
                name.contains("x86_64") -> supportedAbis.add("x86_64")
                name.contains("x86") -> supportedAbis.add("x86")
            }
        }

        // 2. Permissões solicitadas e contagem de perigosas
        val dangerousList = setOf(
            "android.permission.CAMERA",
            "android.permission.RECORD_AUDIO",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.READ_CONTACTS",
            "android.permission.WRITE_CONTACTS",
            "android.permission.READ_CALENDAR",
            "android.permission.WRITE_CALENDAR",
            "android.permission.READ_CALL_LOG",
            "android.permission.WRITE_CALL_LOG",
            "android.permission.READ_PHONE_STATE",
            "android.permission.CALL_PHONE",
            "android.permission.READ_SMS",
            "android.permission.RECEIVE_SMS",
            "android.permission.SEND_SMS",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.READ_MEDIA_IMAGES",
            "android.permission.READ_MEDIA_VIDEO",
            "android.permission.READ_MEDIA_AUDIO",
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.BODY_SENSORS",
            "android.permission.NEARBY_WIFI_DEVICES",
            "android.permission.MANAGE_EXTERNAL_STORAGE"
        )

        val permissionDetails = mutableListOf<PermissionDetail>()
        packageInfo.requestedPermissions?.forEach { perm ->
            val simple = perm.substringAfterLast('.')
            val isDangerous = dangerousList.contains(perm)
            permissionDetails.add(PermissionDetail(name = perm, simpleName = simple, isDangerous = isDangerous))
        }

        val dangerousCount = permissionDetails.count { it.isDangerous }

        // 3. Extrair Certificado do Assinante e SHA-256
        var signerSha256 = "Desconhecido"
        var signerSubject = "Certificado de Assinatura"

        try {
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }

            if (!signatures.isNullOrEmpty()) {
                val certBytes = signatures[0].toByteArray()
                val certFactory = CertificateFactory.getInstance("X.509")
                val cert = certFactory.generateCertificate(certBytes.inputStream()) as? X509Certificate
                if (cert != null) {
                    val md = MessageDigest.getInstance("SHA-256")
                    val fingerprint = md.digest(cert.encoded)
                    signerSha256 = fingerprint.joinToString(":") { "%02X".format(it) }
                    signerSubject = cert.subjectDN.name
                }
            }
        } catch (e: Exception) {
            signerSha256 = "Falha ao extrair assinatura: ${e.message}"
        }

        // 4. Inspecionar bibliotecas anti-tamper / DRM
        val antiTamperDetected = mutableListOf<String>()
        val knownAntiTamper = listOf(
            "libMHoYoProtect.so" to "HoYoverse Anti-Tamper & Asset Protection",
            "libcrashsight.so" to "CrashSight / Anti-Cheat monitor",
            "libanogs.so" to "Tencent Anti-Cheat Expert / ACE",
            "libtersafe.so" to "Tencent TGuard Security Core",
            "libtprt.so" to "Tencent Integrity Runtime Protection",
            "libnesec.so" to "NetEase Game Security Module",
            "libmsao.so" to "NetEase Security Anti-Tamper Core",
            "libdenuvo.so" to "Denuvo Mobile Anti-Tamper",
            "libirdeto.so" to "Irdeto Cloakware Protection",
            "libarxan.so" to "Digital.ai / Arxan Application Protection",
            "libeasyanticheat.so" to "Easy Anti-Cheat Mobile",
            "libbattleye.so" to "BattlEye Anti-Cheat Core",
            "libplaycore.so" to "Google Play Core / Play Integrity API"
        )

        for (apk in apks) {
            try {
                ZipFile(apk).use { zip ->
                    for ((libFile, desc) in knownAntiTamper) {
                        if (zip.getEntry("lib/arm64-v8a/$libFile") != null ||
                            zip.getEntry("lib/armeabi-v7a/$libFile") != null ||
                            zip.getEntry(libFile) != null) {
                            if (!antiTamperDetected.contains(desc)) {
                                antiTamperDetected.add(desc)
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // 5. Comparação inteligente com o app instalado no sistema
        var installedComparison = InstalledAppComparison(isInstalled = false)
        try {
            val installedInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            }

            val installedVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                installedInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                installedInfo.versionCode.toLong()
            }
            val installedVersionName = installedInfo.versionName ?: "Desconhecido"

            // Extrair certificado instalado
            var installedSignerSha256 = ""
            val installedSigs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                installedInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                installedInfo.signatures
            }

            if (!installedSigs.isNullOrEmpty()) {
                val certBytes = installedSigs[0].toByteArray()
                val certFactory = CertificateFactory.getInstance("X.509")
                val cert = certFactory.generateCertificate(certBytes.inputStream()) as? X509Certificate
                if (cert != null) {
                    val md = MessageDigest.getInstance("SHA-256")
                    installedSignerSha256 = md.digest(cert.encoded).joinToString(":") { "%02X".format(it) }
                }
            }

            val isSigMatch = if (installedSignerSha256.isNotBlank() && signerSha256.isNotBlank()) {
                installedSignerSha256.equals(signerSha256, ignoreCase = true)
            } else {
                true
            }

            val installType = when {
                versionCode > installedVersionCode -> InstallType.UPGRADE
                versionCode == installedVersionCode -> InstallType.REINSTALL
                else -> InstallType.DOWNGRADE
            }

            installedComparison = InstalledAppComparison(
                isInstalled = true,
                currentVersionName = installedVersionName,
                currentVersionCode = installedVersionCode,
                installType = installType,
                installedSignerSha256 = installedSignerSha256,
                isSignatureMatching = isSigMatch
            )
        } catch (_: PackageManager.NameNotFoundException) {
            // App não está instalado -> Nova instalação
            installedComparison = InstalledAppComparison(isInstalled = false, installType = InstallType.NEW_INSTALL)
        }

        // 6. Verificações de Hardware e Armazenamento
        val isCompatibleMinSdk = Build.VERSION.SDK_INT >= minSdkVersion
        val isCompatibleAbi = supportedAbis.isEmpty() || Build.SUPPORTED_ABIS.any { it in supportedAbis }

        val stat = StatFs(context.filesDir.path)
        val freeStorageBytes = stat.availableBytes
        val totalApkSize = apks.sumOf { it.length() }
        val requiredStorageBytes = (totalApkSize * 2.2).toLong()
        val hasFreeStorage = freeStorageBytes > requiredStorageBytes

        val obbFiles = File(stagingDir, "obb").listFiles { f -> f.isFile && f.name.endsWith(".obb", ignoreCase = true) }?.toList() ?: emptyList()

        ApkMetadata(
            sourceFileName = sourceFileName,
            sourceFileSizeBytes = sourceFileSize,
            sourceFileSha256 = sourceSha256,
            sourceType = sourceType,
            stagingDir = stagingDir,
            packageName = packageName,
            label = label,
            versionName = versionName,
            versionCode = versionCode,
            minSdkVersion = minSdkVersion,
            targetSdkVersion = targetSdkVersion,
            supportedAbis = supportedAbis.toList(),
            requestedPermissions = permissionDetails,
            dangerousPermissionsCount = dangerousCount,
            signerSha256 = signerSha256,
            signerSubject = signerSubject,
            icon = iconBitmap,
            extractedApks = apks,
            extractedObbFiles = obbFiles,
            antiTamperLibsDetected = antiTamperDetected,
            isCompatibleMinSdk = isCompatibleMinSdk,
            isCompatibleAbi = isCompatibleAbi,
            hasFreeStorage = hasFreeStorage,
            freeStorageBytes = freeStorageBytes,
            requiredStorageBytes = requiredStorageBytes,
            installedComparison = installedComparison
        )
    }

    /**
     * Traduz erros clássicos do PackageInstaller do Android para mensagens claras em português
     */
    fun translateInstallError(rawError: String, executedCommand: String = ""): String {
        val clean = rawError.trim().replace(Regex("(?i)\\bSuccess\\b"), "").trim()
        val explanation = when {
            clean.contains("UPDATE_INCOMPATIBLE", ignoreCase = true) || clean.contains("INSTALL_FAILED_UPDATE_INCOMPATIBLE", ignoreCase = true) ->
                "⚠️ Incompatibilidade Crítica de Assinatura (UPDATE_INCOMPATIBLE):\nO certificado digital do novo APK é diferente do app atualmente instalado no seu aparelho. O Android rejeita a atualização para proteger os dados. Ao desinstalar o app existente você perderá os dados; realize um backup do app antes de desinstalar e tentar a nova instalação."

            clean.contains("VERSION_DOWNGRADE", ignoreCase = true) || clean.contains("INSTALL_FAILED_VERSION_DOWNGRADE", ignoreCase = true) ->
                "⚠️ Tentativa de Downgrade Bloqueada (VERSION_DOWNGRADE):\nA versão que você está tentando instalar é anterior à versão já instalada no aparelho. Ative a opção '[-d] Permitir Downgrade' nas opções avançadas."

            clean.contains("INSUFFICIENT_STORAGE", ignoreCase = true) || clean.contains("INSTALL_FAILED_INSUFFICIENT_STORAGE", ignoreCase = true) ->
                "⚠️ Espaço Livre Insuficiente (INSUFFICIENT_STORAGE):\nNão há armazenamento livre suficiente na memória interna do aparelho para concluir a instalação."

            clean.contains("NO_MATCHING_ABIS", ignoreCase = true) || clean.contains("INSTALL_FAILED_NO_MATCHING_ABIS", ignoreCase = true) ->
                "⚠️ Incompatibilidade de Processador (NO_MATCHING_ABIS):\nO APK contém bibliotecas nativas incompatíveis com a arquitetura de processador (ABI) do seu celular."

            clean.contains("OLDER_SDK", ignoreCase = true) || clean.contains("INSTALL_FAILED_OLDER_SDK", ignoreCase = true) ->
                "⚠️ Versão do Android Incompatível (OLDER_SDK):\nEste aplicativo requer uma versão mais recente do Android do que a versão do sistema instalada no seu aparelho."

            clean.contains("TEST_ONLY", ignoreCase = true) || clean.contains("INSTALL_FAILED_TEST_ONLY", ignoreCase = true) ->
                "⚠️ Pacote de Teste (TEST_ONLY):\nEste APK foi compilado como pacote de testes (test-only). Ative a opção '[-t] Permitir Pacote de Teste' nas opções avançadas."

            clean.contains("MISSING_SPLIT", ignoreCase = true) || clean.contains("INSTALL_FAILED_MISSING_SPLIT", ignoreCase = true) ->
                "⚠️ Pacote Dividido Incompleto (MISSING_SPLIT):\nFaltam partes essenciais dos Split APKs (como base.apk ou o split de densidade/arquitetura do seu aparelho)."

            clean.contains("INSTALL_FAILED_ALREADY_EXISTS") ->
                "O pacote já está instalado com os mesmos dados."

            clean.contains("INSTALL_PARSE_FAILED", ignoreCase = true) ->
                "Falha na validação da estrutura do arquivo APK (INSTALL_PARSE_FAILED). O arquivo pode estar corrompido, incompleto ou truncado durante a gravação."

            clean.contains("INSTALL_FAILED_INVALID_INSTALLER_PACKAGE") ->
                "O PackageManager rejeitou o nome do pacote instalador solicitado pelo sistema."

            clean.contains("INSTALL_FAILED_USER_RESTRICTED") ->
                "Instalação bloqueada pelas configurações de segurança ou controle parental do sistema."

            executedCommand.contains("install-create") ->
                "ℹ️ A sessão de instalação falhou ao ser gerada pelo PackageManager. O comando não retornou um ID de sessão válido."

            executedCommand.contains("install-write") ->
                "ℹ️ A etapa de gravação do pacote/split falhou. O PackageManager não conseguiu registrar o bloco de dados enviado via stdin."

            executedCommand.contains("install-commit") ->
                "ℹ️ A etapa final de confirmação e commit da instalação foi rejeitada pelo sistema Android."

            clean.isNotBlank() ->
                "O PackageManager rejeitou a execução do comando de instalação: $clean"

            else ->
                "Ocorreu um erro interno desconhecido ao processar a instalação."
        }

        return buildString {
            append(explanation)
            if (executedCommand.isNotBlank()) {
                append("\n\n⚙️ Comando Executado:\n$executedCommand")
            }
            if (clean.isNotBlank()) {
                append("\n\n📄 Resposta do Sistema:\n$clean")
            }
        }
    }

    /**
     * [PT-BR] Executa instalação avançada utilizando Shizuku ou Root através de sessão pm install-create / write / commit.
     * [EN] Performs advanced package installation using Shizuku or Root privileges via pm install-create / write / commit session.
     */
    suspend fun installPackageViaShizuku(
        context: Context,
        metadata: ApkMetadata,
        flags: InstallFlags,
        onStep: (InstallStep, String, Float) -> Unit,
        onResult: (Boolean, String) -> Unit
    ) = withContext(Dispatchers.IO) {
        startLogcatMonitoring(metadata.packageName)
        try {
            // =========================================================================
            // ETAPA 1 / STEP 1: Criação da Sessão do PackageManager
            // [PT-BR] Inicializa a sessão de instalação enviando as flags (-r, -d, -t, -g) ao shell.
            // [EN] Initializes the installation session by passing execution flags (-r, -d, -t, -g) to the shell.
            // =========================================================================
            onStep(InstallStep.CREATING_SESSION, "Criando sessão no PackageManager...", 0.1f)
            addInstallerLog("[INSTALL] Iniciando instalação com privilégios Shizuku/Shell...")
            addInstallerLog("[ETAPA] Verificando se o APK não foi corrompido ou modificado maliciosamente (SHA-256: ${metadata.sourceFileSha256}).")
            addInstallerLog("[ETAPA] Verificando o certificado digital do aplicativo (Assinante: ${metadata.signerSha256}).")
            addInstallerLog("[ETAPA] Lendo o AndroidManifest.xml...")
            addInstallerLog("[ETAPA] Registrando o nome do pacote (${metadata.packageName}), a versão do aplicativo (${metadata.versionName} / Code: ${metadata.versionCode}), as permissões solicitadas (${metadata.requestedPermissions.size} permissões)...")

            val flagsBuilder = StringBuilder("-r")
            if (flags.allowDowngrade) flagsBuilder.append(" -d")
            if (flags.allowTest) flagsBuilder.append(" -t")
            if (flags.grantAllPermissions) flagsBuilder.append(" -g")

            // [PT-BR] Se for apenas 1 APK (APK monolítico simples), preparamos o fallback direto caso o install-create falhe.
            // [EN] If it is a single APK (monolithic APK), prepare direct fallback in case install-create fails.
            val isSingleApk = metadata.extractedApks.size == 1

            val createCmd = "pm install-create $flagsBuilder"
            val createOutput = ShellFunctions.executePrivileged(createCmd).trim()
            addInstallerLog("[INSTALL] pm install-create -> $createOutput")

            // [PT-BR] Regex para capturar o ID da sessão retornado pelo sistema: "Success: created install session [12345]"
            // [EN] Regex to capture the session ID returned by the system: "Success: created install session [12345]"
            val sessionIdRegex = Regex("(?i)session\\s*\\[?(\\d+)\\]?|Success:\\s*\\[?(\\d+)\\]?|\\b(\\d{5,})\\b")
            val match = sessionIdRegex.find(createOutput)
            var sessionId = match?.groupValues?.drop(1)?.firstOrNull { it.isNotBlank() }
            if (sessionId == null) {
                val allNums = Regex("\\d+").findAll(createOutput).map { it.value }.toList()
                if (allNums.isNotEmpty() && (createOutput.contains("Success", true) || createOutput.contains("session", true))) {
                    sessionId = allNums.last()
                }
            }

            if (sessionId == null) {
                // [PT-BR] Fallback de segurança para APK único via streaming de stdin caso a criação de sessão falhe.
                // [EN] Safety streaming fallback for single APK via stdin if session creation fails.
                if (isSingleApk && metadata.extractedApks.isNotEmpty()) {
                    val singleApk = metadata.extractedApks[0]
                    val expected = singleApk.length()
                    addInstallerLog("[ETAPA] Copiando o APK ${singleApk.name} para o diretório temporário seguro (geralmente em /data/local/tmp)...")
                    addInstallerLog("[ETAPA] Criando os diretórios dedicados para o aplicativo em /data/app e em /data/data/${metadata.packageName} (Package Name)...")
                    addInstallerLog("[INSTALL] Tentando instalação direta via streaming (fallback para APK único): pm install $flagsBuilder ${singleApk.name}")
                    onStep(InstallStep.COMMITTING, "Instalando diretamente com PackageManager via streaming...", 0.5f)

                    val directCmd = "pm install $flagsBuilder -S $expected -"
                    val directOut = if (ShizukuHelper.hasPermission()) {
                        try {
                            val p = ShizukuHelper.newProcess("sh", "-c", directCmd)
                            p.outputStream.use { out -> singleApk.inputStream().use { it.copyTo(out, 64 * 1024) } }
                            val out = p.inputStream.bufferedReader().readText()
                            val err = p.errorStream.bufferedReader().readText()
                            p.waitFor()
                            (out + err).trim()
                        } catch (e: Exception) {
                            "Error: ${e.message}"
                        }
                    } else {
                        ShellFunctions.executePrivileged("cat \"${singleApk.absolutePath}\" | $directCmd").trim()
                    }
                    addInstallerLog("[INSTALL] pm install -> $directOut")

                    if (directOut.contains("Success", ignoreCase = true)) {
                        addInstallerLog("[ETAPA] Gerando os arquivos .odex ou .oat...")
                        addInstallerLog("[ETAPA] Adicionando informações do aplicativo no packages.xml...")
                        if (metadata.extractedObbFiles.isNotEmpty()) {
                            onStep(InstallStep.COPYING_OBB, "Copiando arquivos OBB para o jogo...", 0.92f)
                            val obbTarget = "/sdcard/Android/obb/${metadata.packageName}"
                            ShellFunctions.executePrivileged("mkdir -p \"$obbTarget\"")
                            for (obb in metadata.extractedObbFiles) {
                                ShellFunctions.executePrivileged("cp -f \"${obb.absolutePath}\" \"$obbTarget/${obb.name}\"")
                            }
                        }
                        addInstallerLog("[ETAPA] Apagando arquivos temporários usados na instalação...")
                        metadata.stagingDir.deleteRecursively()
                        addInstallerLog("[Sucesso] ${metadata.label} ${metadata.packageName} instalado com êxito!")
                        withContext(Dispatchers.Main) {
                            onStep(InstallStep.SUCCESS, "Instalação concluída com sucesso!", 1.0f)
                            onResult(true, "Aplicativo ${metadata.label} instalado com sucesso!")
                        }
                        return@withContext
                    } else {
                        val directErr = translateInstallError(directOut.ifBlank { createOutput }, directCmd)
                        addInstallerLog("[ERRO] Falha na instalação direta: $directErr")
                        withContext(Dispatchers.Main) {
                            onStep(InstallStep.ERROR, directErr, 0f)
                            onResult(false, directErr)
                        }
                        return@withContext
                    }
                } else {
                    val translatedErr = translateInstallError(createOutput, createCmd)
                    addInstallerLog("[ERRO] Falha ao criar sessão do PackageManager: $translatedErr")
                    withContext(Dispatchers.Main) {
                        onStep(InstallStep.ERROR, translatedErr, 0f)
                        onResult(false, translatedErr)
                    }
                    return@withContext
                }
            }

            addInstallerLog("[INSTALL] Sessão criada com ID: $sessionId")
            addInstallerLog("[ETAPA] Criando os diretórios dedicados para o aplicativo em /data/app e em /data/data/${metadata.packageName} (Package Name)...")

            // =========================================================================
            // ETAPA 2 / STEP 2: Gravação dos Splits / APKs na Sessão (pm install-write)
            // [PT-BR] Transmite os bytes de cada APK para a sessão do PackageInstaller via stdin com verificação de integridade.
            // [EN] Streams bytes of each APK to the PackageInstaller session via stdin with byte integrity verification.
            // =========================================================================
            val totalApks = metadata.extractedApks.size
            metadata.extractedApks.forEachIndexed { index, apkFile ->
                val stepProgress = 0.2f + ((index.toFloat() / totalApks.toFloat()) * 0.6f)
                onStep(InstallStep.WRITING_APKS, "Gravando ${apkFile.name} (${index + 1}/$totalApks)...", stepProgress)
                
                val splitName = apkFile.name
                val expected = apkFile.length()
                
                addInstallerLog("[ETAPA] Copiando o APK $splitName para o diretório temporário seguro (geralmente em /data/local/tmp ou /data/app/vmdl$sessionId.tmp)...")
                addInstallerLog("[INSTALL] Gravando split $splitName ($expected bytes)...")

                val writeOutput = if (ShizukuHelper.hasPermission()) {
                    try {
                        val p = ShizukuHelper.newProcess(
                            "sh", "-c", "pm install-write -S $expected $sessionId \"$splitName\" -"
                        )
                        p.outputStream.use { out -> apkFile.inputStream().use { it.copyTo(out, 64 * 1024) } }
                        val out = p.inputStream.bufferedReader().readText()
                        val err = p.errorStream.bufferedReader().readText()
                        p.waitFor()
                        (out + err).trim()
                    } catch (e: Exception) {
                        "Error: ${e.message}"
                    }
                } else {
                    ShellFunctions.executePrivileged(
                        "cat \"${apkFile.absolutePath}\" | pm install-write -S $expected $sessionId \"$splitName\" -"
                    ).trim()
                }

                addInstallerLog("[INSTALL] pm install-write $splitName -> $writeOutput")

                // Confere se o sistema recebeu todos os bytes, não só a palavra "Success"
                val streamed = Regex("streamed\\s+(\\d+)").find(writeOutput)?.groupValues?.get(1)?.toLongOrNull()
                val incomplete = streamed != null && streamed != expected
                if (!writeOutput.contains("Success", ignoreCase = true) || incomplete) {
                    ShellFunctions.executePrivileged("pm install-abandon $sessionId")
                    val err = if (incomplete) "Gravação incompleta de $splitName: $streamed de $expected bytes"
                    else translateInstallError(writeOutput, "pm install-write")
                    addInstallerLog("[ERRO] Falha na gravação do split $splitName: $err")
                    withContext(Dispatchers.Main) {
                        onStep(InstallStep.ERROR, err, 0f)
                        onResult(false, err)
                    }
                    return@withContext
                }
            }

            // =========================================================================
            // ETAPA 3 / STEP 3: Confirmação e Commit da Instalação (pm install-commit)
            // [PT-BR] Solicita a validação, montagem do diretório /data/app e instalação final dos pacotes gravados.
            // [EN] Triggers validation, /data/app directory assembly, and final installation of written packages.
            // =========================================================================
            onStep(InstallStep.COMMITTING, "Finalizando e confirmando instalação (pm install-commit)...", 0.85f)
            addInstallerLog("[INSTALL] Confirmando instalação com pm install-commit $sessionId...")

            val commitCmd = "pm install-commit $sessionId"
            val commitOutput = ShellFunctions.executePrivileged(commitCmd).trim()
            addInstallerLog("[INSTALL] pm install-commit -> $commitOutput")

            if (!commitOutput.contains("Success", ignoreCase = true)) {
                val err = translateInstallError(commitOutput, commitCmd)
                addInstallerLog("[ERRO] Instalação rejeitada pelo sistema: $err")
                withContext(Dispatchers.Main) {
                    onStep(InstallStep.ERROR, err, 0f)
                    onResult(false, err)
                }
                return@withContext
            }

            addInstallerLog("[ETAPA] Gerando os arquivos .odex ou .oat...")
            addInstallerLog("[ETAPA] Adicionando informações do aplicativo no packages.xml...")

            // =========================================================================
            // ETAPA 4 / STEP 4: Cópia dos Arquivos de Dados OBB (Se Existirem)
            // [PT-BR] Garante que dados de expansão/jogos (XAPK) sejam movidos para /sdcard/Android/obb/<packageName>/.
            // [EN] Ensures game/expansion assets (XAPK) are copied to /sdcard/Android/obb/<packageName>/.
            // =========================================================================
            if (metadata.extractedObbFiles.isNotEmpty()) {
                onStep(InstallStep.COPYING_OBB, "Copiando arquivos OBB para o jogo...", 0.92f)
                addInstallerLog("[INSTALL] Copiando ${metadata.extractedObbFiles.size} arquivo(s) OBB...")
                val obbTarget = "/sdcard/Android/obb/${metadata.packageName}"
                ShellFunctions.executePrivileged("mkdir -p \"$obbTarget\"")
                for (obb in metadata.extractedObbFiles) {
                    addInstallerLog("[INSTALL] Copiando OBB: ${obb.name}")
                    ShellFunctions.executePrivileged("cp -f \"${obb.absolutePath}\" \"$obbTarget/${obb.name}\"")
                }
                addInstallerLog("[INSTALL] Arquivos OBB copiados com sucesso para $obbTarget")
            }

            // =========================================================================
            // ETAPA 5 / STEP 5: Limpeza do Diretório de Staging e Notificação Final
            // [PT-BR] Remove os arquivos temporários descompactados do cache e notifica o término da operação.
            // [EN] Removes temporary extracted files from cache and notifies operation completion.
            // =========================================================================
            onStep(InstallStep.CLEANING_UP, "Limpando arquivos temporários...", 0.98f)
            addInstallerLog("[ETAPA] Apagando arquivos temporários usados na instalação...")
            metadata.stagingDir.deleteRecursively()
            addInstallerLog("[INSTALL] Área de trabalho temporária limpa.")

            addInstallerLog("[Sucesso] ${metadata.label} ${metadata.packageName} instalado com êxito!")
            withContext(Dispatchers.Main) {
                onStep(InstallStep.SUCCESS, "Instalação concluída com sucesso!", 1.0f)
                onResult(true, "Aplicativo ${metadata.label} instalado com sucesso!")
            }
        } catch (e: Exception) {
            val err = "Exceção inesperada na instalação: ${e.message}"
            addInstallerLog("[ERRO] $err")
            withContext(Dispatchers.Main) {
                onStep(InstallStep.ERROR, err, 0f)
                onResult(false, err)
            }
        }
    }

    /**
     * Instalação nativa via PackageInstaller padrão do sistema Android (Modo alternativo sem Shizuku)
     */
    fun installViaSystem(
        context: Context,
        metadata: ApkMetadata,
        onResult: (Boolean, String) -> Unit
    ) {
        try {
            startLogcatMonitoring(metadata.packageName)
            addInstallerLog("[SISTEMA] Iniciando instalador nativo do Android...")

            if (metadata.extractedApks.size == 1) {
                val apkFile = metadata.extractedApks.first()
                val apkUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    apkFile
                )
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                context.startActivity(intent)
                addInstallerLog("[SISTEMA] Intent de instalação disparada para o usuário.")
                onResult(true, "Instalador do Android iniciado na tela. Confirme para prosseguir.")
            } else {
                // Instalação de splits via PackageInstaller Session oficial
                val packageInstaller = context.packageManager.packageInstaller
                val params = android.content.pm.PackageInstaller.SessionParams(
                    android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL
                )
                val sessionId = packageInstaller.createSession(params)
                val session = packageInstaller.openSession(sessionId)

                metadata.extractedApks.forEach { apk ->
                    session.openWrite(apk.name, 0, apk.length()).use { out ->
                        FileInputStream(apk).use { inStream ->
                            inStream.copyTo(out)
                        }
                    }
                }

                val intent = Intent(context, MainActivity::class.java)
                val pendingIntent = android.app.PendingIntent.getActivity(
                    context,
                    sessionId,
                    intent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )

                session.commit(pendingIntent.intentSender)
                session.close()

                addInstallerLog("[SISTEMA] Sessão PackageInstaller enviada para confirmação do usuário.")
                onResult(true, "Sessão de splits aberta. Confirme a instalação no diálogo do sistema.")
            }
        } catch (e: Exception) {
            val err = "Falha ao iniciar instalador do sistema: ${e.message}"
            addInstallerLog("[ERRO] $err")
            onResult(false, err)
        }
    }
}
