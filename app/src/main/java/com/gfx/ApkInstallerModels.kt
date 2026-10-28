package com.gfx

import android.graphics.Bitmap
import java.io.File

/**
 * [PT-BR] Modelos de dados para o Instalador de APKs Detalhado.
 * [EN] Data models for the Detailed APK Installer.
 */

enum class PackageSourceType(val displayName: String, val extension: String) {
    APK("APK Simples", ".apk"),
    APKS("Split APKs Bundle", ".apks"),
    XAPK("XAPK (APK + Splits + OBB)", ".xapk"),
    APKM("APKMirror Bundle", ".apkm"),
    APP_BACKUP_ZIP("Backup Game Booster", ".zip"),
    UNKNOWN("Pacote Desconhecido", "")
}

enum class InstallType(val labelPt: String, val labelEn: String) {
    NEW_INSTALL("Nova Instalação", "New Installation"),
    UPGRADE("Atualização (Nova Versão)", "Upgrade"),
    REINSTALL("Mesma Versão", "Reinstallation"),
    DOWNGRADE("Versão Inferior (Downgrade)", "Version Downgrade")
}

data class PermissionDetail(
    val name: String,
    val simpleName: String,
    val isDangerous: Boolean,
    val description: String? = null
)

data class InstalledAppComparison(
    val isInstalled: Boolean,
    val currentVersionName: String? = null,
    val currentVersionCode: Long = 0,
    val installType: InstallType = InstallType.NEW_INSTALL,
    val installedSignerSha256: String? = null,
    val isSignatureMatching: Boolean = true
)

data class InstallFlags(
    var allowDowngrade: Boolean = false, // pm install-create -d
    var allowTest: Boolean = false,      // pm install-create -t
    var grantAllPermissions: Boolean = false // pm install-create -g
)

enum class InstallStep {
    IDLE,
    COPYING_SOURCE,
    EXTRACTING,
    ANALYZING,
    READY_TO_INSTALL,
    CREATING_SESSION,
    WRITING_APKS,
    COMMITTING,
    COPYING_OBB,
    CLEANING_UP,
    SUCCESS,
    ERROR
}

data class ApkMetadata(
    val sourceFileName: String,
    val sourceFileSizeBytes: Long,
    val sourceFileSha256: String,
    val sourceType: PackageSourceType,
    val stagingDir: File,
    val packageName: String,
    val label: String,
    val versionName: String,
    val versionCode: Long,
    val minSdkVersion: Int,
    val targetSdkVersion: Int,
    val supportedAbis: List<String>,
    val requestedPermissions: List<PermissionDetail>,
    val dangerousPermissionsCount: Int,
    val signerSha256: String,
    val signerSubject: String,
    val icon: Bitmap?,
    val extractedApks: List<File>,
    val extractedObbFiles: List<File>,
    val antiTamperLibsDetected: List<String>,
    val isCompatibleMinSdk: Boolean,
    val isCompatibleAbi: Boolean,
    val hasFreeStorage: Boolean,
    val freeStorageBytes: Long,
    val requiredStorageBytes: Long,
    val installedComparison: InstalledAppComparison
)
