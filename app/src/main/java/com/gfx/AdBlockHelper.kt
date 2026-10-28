package com.gfx

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build

object AdBlockHelper {

    data class AdScanResult(
        val packageName: String,
        val hasAds: Boolean,
        val detectedNetworks: List<String>,
        val adComponentCount: Int
    )

    private val KNOWN_AD_NETWORKS = listOf(
        "Google AdMob" to listOf("com.google.android.gms.ads", "com.google.ads"),
        "Unity Ads" to listOf("com.unity3d.services.ads", "com.unity3d.ads"),
        "AppLovin" to listOf("com.applovin"),
        "IronSource" to listOf("com.ironsource"),
        "Vungle" to listOf("com.vungle"),
        "Mintegral" to listOf("com.mintegral"),
        "InMobi" to listOf("com.inmobi"),
        "Chartboost" to listOf("com.chartboost"),
        "Pangle" to listOf("com.bytedance.sdk.openadsdk", "com.pangle"),
        "Meta Ads" to listOf("com.facebook.ads")
    )

    private const val ADGUARD_DNS = "dns.adguard-dns.com"
    private var originalDnsMode: String? = null
    private var originalDnsSpecifier: String? = null

    fun scanGame(pm: PackageManager, packageName: String): AdScanResult {
        return try {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS
            }

            val packageInfo: PackageInfo = pm.getPackageInfo(packageName, flags)
            val componentNames = mutableListOf<String>()

            packageInfo.activities?.forEach { componentNames.add(it.name) }
            packageInfo.services?.forEach { componentNames.add(it.name) }
            packageInfo.receivers?.forEach { componentNames.add(it.name) }

            val detectedNetworks = mutableSetOf<String>()
            var adComponentsCount = 0

            for (component in componentNames) {
                for ((networkName, signatures) in KNOWN_AD_NETWORKS) {
                    if (signatures.any { component.contains(it, ignoreCase = true) }) {
                        detectedNetworks.add(networkName)
                        adComponentsCount++
                    }
                }
            }

            AdScanResult(
                packageName = packageName,
                hasAds = detectedNetworks.isNotEmpty(),
                detectedNetworks = detectedNetworks.toList(),
                adComponentCount = adComponentsCount
            )
        } catch (e: Exception) {
            AdScanResult(packageName, hasAds = false, detectedNetworks = emptyList(), adComponentCount = 0)
        }
    }

    fun enableAdBlocking(): Boolean {
        return try {
            if (originalDnsMode == null) {
                val currentMode = ShellHelper.executePrivileged("settings get global private_dns_mode").trim()
                originalDnsMode = if (currentMode.isNotEmpty() && currentMode != "null") currentMode else "opportunistic"
                val currentSpec = ShellHelper.executePrivileged("settings get global private_dns_specifier").trim()
                originalDnsSpecifier = if (currentSpec.isNotEmpty() && currentSpec != "null") currentSpec else ""
            }

            ShellHelper.executePrivileged("settings put global private_dns_mode hostname")
            ShellHelper.executePrivileged("settings put global private_dns_specifier $ADGUARD_DNS")
            true
        } catch (e: Exception) {
            false
        }
    }

    fun disableAdBlocking(): Boolean {
        return try {
            val restoreMode = originalDnsMode ?: "opportunistic"
            val restoreSpec = originalDnsSpecifier ?: ""

            ShellHelper.executePrivileged("settings put global private_dns_mode $restoreMode")
            if (restoreSpec.isNotEmpty()) {
                ShellHelper.executePrivileged("settings put global private_dns_specifier $restoreSpec")
            } else {
                ShellHelper.executePrivileged("settings delete global private_dns_specifier")
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun isAdBlockingActive(): Boolean {
        val currentSpec = ShellFunctions.executePrivileged("settings get global private_dns_specifier").trim()
        val currentMode = ShellFunctions.executePrivileged("settings get global private_dns_mode").trim()
        return currentMode == "hostname" && currentSpec.contains("adguard", ignoreCase = true)
    }

    /**
     * Desativa componentes nativos de anúncios (Activities, Services, Receivers) via pm disable
     */
    fun disableAdComponents(pm: PackageManager, packageName: String): Int {
        var disabledCount = 0
        try {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS
            }
            val packageInfo: PackageInfo = pm.getPackageInfo(packageName, flags)
            val allComponents = mutableListOf<String>()

            packageInfo.activities?.forEach { allComponents.add(it.name) }
            packageInfo.services?.forEach { allComponents.add(it.name) }
            packageInfo.receivers?.forEach { allComponents.add(it.name) }

            for (component in allComponents) {
                for ((_, signatures) in KNOWN_AD_NETWORKS) {
                    if (signatures.any { component.contains(it, ignoreCase = true) }) {
                        val cmd = "pm disable \"$packageName/$component\""
                        val out = ShellFunctions.executePrivileged(cmd)
                        if (out.contains("disabled", ignoreCase = true) || out.contains("new state", ignoreCase = true)) {
                            disabledCount++
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return disabledCount
    }

    /**
     * Bloqueia ou libera acesso à rede do aplicativo via iptables e policy
     */
    fun toggleGameInternet(pm: PackageManager, packageName: String, block: Boolean): Boolean {
        return try {
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val uid = appInfo.uid
            if (block) {
                ShellFunctions.executePrivileged("iptables -I OUTPUT -m owner --uid-owner $uid -j DROP")
                ShellFunctions.executePrivileged("cmd netpolicy add restrict-background-blacklist $uid")
            } else {
                ShellFunctions.executePrivileged("iptables -D OUTPUT -m owner --uid-owner $uid -j DROP")
                ShellFunctions.executePrivileged("cmd netpolicy remove restrict-background-blacklist $uid")
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
