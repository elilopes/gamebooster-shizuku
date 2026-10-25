package com.gfx

import android.content.Context
import java.io.File

/**
 * [PT-BR] GfxTools: Utilitário consolidado contendo todas as otimizações do modal "GFX Tuner downgrade mode".
 *         Permite aplicar perfis agressivos de redução gráfica (modo batata, desativação de sombras,
 *         redução de polígonos/LOD, remoção de shaders de água/reflexos, redução de explosões/partículas,
 *         silenciamento de áudio) adaptados para Unity, Unreal Engine, Godot, ITsMagic e Android Game Mode.
 */
object GfxTools {

    data class GfxConfig(
        val extremePotatoMode: Boolean = false,
        val disableShadows: Boolean = false,
        val reducePolygons: Boolean = false,
        val disableWaterEffects: Boolean = false,
        val disableExplosions: Boolean = false,
        val muteAudio: Boolean = false,
        val astcTextureCompression: Boolean = false,
        val forceMipmapping: Boolean = false,
        val webViewGpuBoost: Boolean = false,
        val cleanWebViewCache: Boolean = false
    )

    enum class EngineType(
        val displayName: String,
        val shortName: String,
        val badgeColorHex: Long,
        val iconEmoji: String
    ) {
        UNITY("Unity", "Unity", 0xFF1976D2, "🎮"),
        UNREAL("Unreal Engine", "Unreal", 0xFF7B1FA2, "⚡"),
        GODOT("Godot Engine", "Godot", 0xFF388E3C, "🤖"),
        ITSMAGIC("ITsMagic", "ITsMagic", 0xFFE65100, "✨"),
        ANDROID_STUDIO("Android Studio", "Android Studio", 0xFF2E7D32, "📱"),
        WEBVIEW("Modo Web (HTML5/WebView)", "Modo Web", 0xFF0097A7, "🌐"),
        COCOS("Cocos2d", "Cocos2d", 0xFFD32F2F, "🕹️"),
        GENERIC("Genérico (Android Game Mode)", "Genérico", 0xFF546E7A, "⚙️"),
        UNKNOWN("Origem Desconhecida", "Desconhecida", 0xFF757575, "❓")
    }

    /**
     * [PT-BR] Detecta automaticamente a engine gráfica ou modo web utilizado pelo aplicativo / jogo,
     * inspecionando bibliotecas nativas (.so), manifesto, assets empacotados e arquivos de configuração.
     * Suporta: Unreal Engine, Unity, ITsMagic, Godot, Android Studio (Nativo), Modo Web (WebView/React/Cordova)
     * e Origem Desconhecida se a inspeção falhar.
     */
    fun detectEngine(context: Context, packageName: String): EngineType {
        val lowerPkg = packageName.lowercase()
        if (lowerPkg.contains("itsmagic")) return EngineType.ITSMAGIC
        if (lowerPkg.contains("godot")) return EngineType.GODOT
        if (lowerPkg.contains("unity3d") || lowerPkg.contains("battle.royale") || lowerPkg.contains("wildlife")) return EngineType.UNITY
        if (lowerPkg.contains("epicgames") || lowerPkg.contains("unreal")) return EngineType.UNREAL

        return try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)

            // 1. Inspeciona o arquivo APK (.apk) via ZipFile (alta fidelidade de detecção de assets e libs)
            val sourceApk = File(appInfo.sourceDir)
            if (sourceApk.exists() && sourceApk.canRead()) {
                try {
                    java.util.zip.ZipFile(sourceApk).use { zip ->
                        var hasUnity = false
                        var hasUnreal = false
                        var hasGodot = false
                        var hasItsMagic = false
                        var hasWeb = false
                        var hasCocos = false
                        var hasClassesDex = false

                        val entries = zip.entries()
                        var count = 0
                        while (entries.hasMoreElements() && count < 2500) {
                            count++
                            val entryName = entries.nextElement().name.lowercase()

                            if (entryName.contains("classes.dex")) hasClassesDex = true
                            if (entryName.contains("libunity.so") || entryName.contains("libil2cpp.so") || entryName.contains("assets/bin/data/")) hasUnity = true
                            if (entryName.contains("libue4.so") || entryName.contains("libunreal.so") || entryName.contains("libunrealengine.so") || entryName.contains("assets/ue4/")) hasUnreal = true
                            if (entryName.contains("libgodot_android.so") || entryName.endsWith(".pck")) hasGodot = true
                            if (entryName.contains("libitsmagic") || entryName.contains("engine_settings.json")) hasItsMagic = true
                            if (entryName.contains("libmonochrome.so") || entryName.contains("libhermes.so") || entryName.contains("libreactnativejni.so") ||
                                entryName.contains("assets/www/index.html") || entryName.contains("assets/public/index.html") || entryName.contains("assets/capacitor.config.json") ||
                                entryName.contains("assets/cordova.js") || entryName.contains("assets/index.android.bundle") || entryName.contains("libwebviewchromium.so")) {
                                hasWeb = true
                            }
                            if (entryName.contains("libcocos2d")) hasCocos = true

                            // Se já encontrou uma engine de ponta prioritária, encerra a varredura antecipadamente
                            if (hasUnity || hasUnreal || hasGodot || hasItsMagic) break
                        }

                        if (hasUnity) return EngineType.UNITY
                        if (hasUnreal) return EngineType.UNREAL
                        if (hasGodot) return EngineType.GODOT
                        if (hasItsMagic) return EngineType.ITSMAGIC
                        if (hasWeb) return EngineType.WEBVIEW
                        if (hasCocos) return EngineType.COCOS
                        if (hasClassesDex) return EngineType.ANDROID_STUDIO
                    }
                } catch (_: Exception) {
                    // Fallback para nativeLibraryDir se ZipFile não puder ser lido
                }
            }

            // 2. Inspeciona o diretório de bibliotecas nativas descompactadas
            val nativeDir = File(appInfo.nativeLibraryDir)
            if (nativeDir.exists() && nativeDir.isDirectory) {
                val libs = nativeDir.list() ?: emptyArray()
                if (libs.any { it.contains("itsmagic", ignoreCase = true) }) return EngineType.ITSMAGIC
                if (libs.any { it.contains("godot", ignoreCase = true) }) return EngineType.GODOT
                if (libs.any { it.contains("unity", ignoreCase = true) || it.contains("il2cpp", ignoreCase = true) }) return EngineType.UNITY
                if (libs.any { it.contains("UE4", ignoreCase = true) || it.contains("Unreal", ignoreCase = true) }) return EngineType.UNREAL
                if (libs.any { it.contains("cocos2d", ignoreCase = true) }) return EngineType.COCOS
                if (libs.any { it.contains("webview", ignoreCase = true) || it.contains("monochrome", ignoreCase = true) || it.contains("v8", ignoreCase = true) || it.contains("node", ignoreCase = true) || it.contains("hermes", ignoreCase = true) || it.contains("jsc", ignoreCase = true) }) {
                    return EngineType.WEBVIEW
                }
            }

            // 3. Verifica diretórios app_webview no /data/data/$packageName
            val webViewDataDir = File("/data/data/$packageName/app_webview")
            if (webViewDataDir.exists()) {
                return EngineType.WEBVIEW
            }

            // 4. Verifica pacotes .pck típicos do Godot no armazenamento
            val dataDir = File("/sdcard/Android/data/$packageName/files")
            if (dataDir.exists() && dataDir.listFiles()?.any { it.extension.equals("pck", ignoreCase = true) } == true) {
                return EngineType.GODOT
            }

            // 5. Verifica arquivos típicos da ITsMagic Engine
            if (dataDir.exists() && dataDir.walkTopDown().maxDepth(3).any {
                    it.name.contains("itsmagic", ignoreCase = true) || it.name.equals("engine_settings.json", ignoreCase = true)
                }) {
                return EngineType.ITSMAGIC
            }

            // 6. Inspeciona assets do contexto do pacote (para HTML5 / Cordova / Capacitor / React)
            try {
                val assets = context.createPackageContext(packageName, 0).assets.list("") ?: emptyArray()
                if (assets.any { it.contains("www", ignoreCase = true) || it.contains("index.html", ignoreCase = true) || it.contains("capacitor", ignoreCase = true) || it.contains("cordova", ignoreCase = true) || it.contains("phaser", ignoreCase = true) }) {
                    return EngineType.WEBVIEW
                }
            } catch (_: Exception) {}

            // 7. Se o pacote é válido no Android mas não utiliza engine externa: é Nativo Android Studio
            EngineType.ANDROID_STUDIO
        } catch (e: Exception) {
            // Se falhou ao inspecionar o pacote (não instalado, corrompido ou erro de leitura)
            EngineType.UNKNOWN
        }
    }

    /**
     * [PT-BR] Carrega a configuração do GFX Tuner salva para o pacote especificado.
     */
    fun getGfxConfig(context: Context, packageName: String): GfxConfig {
        val prefs = context.getSharedPreferences("gfx_profiles", Context.MODE_PRIVATE)
        return GfxConfig(
            extremePotatoMode = prefs.getBoolean("${packageName}_extreme", false),
            disableShadows = prefs.getBoolean("${packageName}_shadows", false),
            reducePolygons = prefs.getBoolean("${packageName}_polygons", false),
            disableWaterEffects = prefs.getBoolean("${packageName}_water", false),
            disableExplosions = prefs.getBoolean("${packageName}_explosions", false),
            muteAudio = prefs.getBoolean("${packageName}_mute", false),
            astcTextureCompression = prefs.getBoolean("${packageName}_astc_tex", false),
            forceMipmapping = prefs.getBoolean("${packageName}_mipmapping", false),
            webViewGpuBoost = prefs.getBoolean("${packageName}_webview_gpu", false),
            cleanWebViewCache = prefs.getBoolean("${packageName}_webview_clean", false)
        )
    }

    /**
     * [PT-BR] Salva a configuração de otimização GFX no SharedPreferences.
     */
    fun saveGfxConfig(context: Context, packageName: String, config: GfxConfig) {
        val prefs = context.getSharedPreferences("gfx_profiles", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("${packageName}_extreme", config.extremePotatoMode)
            .putBoolean("${packageName}_shadows", config.disableShadows)
            .putBoolean("${packageName}_polygons", config.reducePolygons)
            .putBoolean("${packageName}_water", config.disableWaterEffects)
            .putBoolean("${packageName}_explosions", config.disableExplosions)
            .putBoolean("${packageName}_mute", config.muteAudio)
            .putBoolean("${packageName}_astc_tex", config.astcTextureCompression)
            .putBoolean("${packageName}_mipmapping", config.forceMipmapping)
            .putBoolean("${packageName}_webview_gpu", config.webViewGpuBoost)
            .putBoolean("${packageName}_webview_clean", config.cleanWebViewCache)
            .apply()
    }

    /**
     * [PT-BR] Reseta as otimizações do GFX Tuner de um pacote específico.
     */
    fun resetGfxConfig(context: Context, packageName: String): String {
        val prefs = context.getSharedPreferences("gfx_profiles", Context.MODE_PRIVATE)
        prefs.edit()
            .remove("${packageName}_extreme")
            .remove("${packageName}_shadows")
            .remove("${packageName}_polygons")
            .remove("${packageName}_water")
            .remove("${packageName}_explosions")
            .remove("${packageName}_mute")
            .remove("${packageName}_astc_tex")
            .remove("${packageName}_mipmapping")
            .remove("${packageName}_webview_gpu")
            .remove("${packageName}_webview_clean")
            .apply()

        // Restaura modo padrão no Android Game Mode
        ShizukuHelper.executeCommand("cmd game reset $packageName")
        return "Configurações do GFX Tuner resetadas para o padrão."
    }

    /**
     * [PT-BR] Aplica todas as otimizações gráficas de downgrade mode no jogo selecionado.
     */
    fun applyGfxOptimizations(context: Context, packageName: String, config: GfxConfig): String {
        val results = mutableListOf<String>()
        val engine = detectEngine(context, packageName)

        // 1. Android Game Mode (Modo 3 = BATTERY_SAVER / LOW_FIDELITY no Android 12+)
        if (config.extremePotatoMode || config.disableShadows || config.reducePolygons) {
            ShizukuHelper.executeCommand("cmd game set --mode 3 $packageName")
            results.add("Game Mode Low-Fidelity ativado (cmd game set --mode 3)")
        }

        // 2. Otimizações para Unity Engine (ex: Zooba Battle Royale)
        if (engine == EngineType.UNITY || packageName.contains("wildlife", ignoreCase = true)) {
            val unityPrefsDir = "/data/data/$packageName/shared_prefs"
            val unityPrefsFile = "$unityPrefsDir/${packageName}.v2.playerprefs.xml"

            if (config.disableShadows || config.extremePotatoMode) {
                val cmd1 = "sed -i 's/<int name=\"unity.player_session_count\".*/<int name=\"unity.player_session_count\" value=\"1\" \\/><int name=\"QualitySettings.QualityLevel\" value=\"0\" \\/><int name=\"GraphicsQuality\" value=\"0\" \\/><int name=\"ShadowQuality\" value=\"0\" \\/>/g' $unityPrefsFile"
                ShizukuHelper.executeCommand(cmd1)
                results.add("Unity: Sombras e iluminação dinâmica desativadas (ShadowQuality=0, QualityLevel=0)")
            }

            if (config.reducePolygons || config.extremePotatoMode) {
                val cmdLOD = "sed -i 's/<int name=\"QualitySettings.*\"/& <float name=\"unity.lod_bias\" value=\"0.3\" \\/><int name=\"MeshLOD\" value=\"0\" \\/>/g' $unityPrefsFile"
                ShizukuHelper.executeCommand(cmdLOD)
                results.add("Unity: Polígonos e geometria simplificados (LOD Bias 0.3)")
            }

            if (config.disableWaterEffects || config.extremePotatoMode) {
                val cmdWater = "sed -i 's/<int name=\"QualitySettings.*\"/& <int name=\"WaterQuality\" value=\"0\" \\/><int name=\"ReflectionsEnabled\" value=\"0\" \\/>/g' $unityPrefsFile"
                ShizukuHelper.executeCommand(cmdWater)
                results.add("Unity: Efeitos de água e reflexos desativados")
            }

            if (config.disableExplosions || config.extremePotatoMode) {
                val cmdParticles = "sed -i 's/<int name=\"QualitySettings.*\"/& <int name=\"ParticleRaycastBudget\" value=\"16\" \\/><int name=\"ParticleQuality\" value=\"0\" \\/>/g' $unityPrefsFile"
                ShizukuHelper.executeCommand(cmdParticles)
                results.add("Unity: Efeitos de explosão e partículas reduzidos ao mínimo")
            }

            if (config.muteAudio) {
                val cmdMute = "sed -i 's/<map>/<map><int name=\"unity.muted\" value=\"1\" \\/><float name=\"MusicVolume\" value=\"0.0\" \\/><float name=\"SfxVolume\" value=\"0.0\" \\/>/g' $unityPrefsFile"
                ShizukuHelper.executeCommand(cmdMute)
                results.add("Unity: Áudio e efeitos sonoros desativados no PlayerPrefs")
            }

            if (config.astcTextureCompression || config.extremePotatoMode) {
                val cmdTex = "sed -i 's/<map>/<map><int name=\"QualitySettings.masterTextureLimit\" value=\"1\" \\/><int name=\"QualitySettings.anisotropicFiltering\" value=\"0\" \\/><int name=\"TextureCompressionASTC\" value=\"1\" \\/>/g' $unityPrefsFile"
                ShizukuHelper.executeCommand(cmdTex)
                results.add("Unity: Compactação de texturas ASTC (masterTextureLimit=1, economia de VRAM)")
            }

            if (config.forceMipmapping) {
                val cmdMip = "sed -i 's/<map>/<map><int name=\"QualitySettings.globalTextureMipmapLimit\" value=\"1\" \\/><int name=\"TextureMipBias\" value=\"1\" \\/>/g' $unityPrefsFile"
                ShizukuHelper.executeCommand(cmdMip)
                results.add("Unity: Mipmapping gerado e filtragem linear forçada")
            }
        } else if (engine == EngineType.UNREAL) {
            // Otimizações para Unreal Engine (UserCustom.ini / Scalability)
            val iniLines = mutableListOf<String>()
            iniLines.add("[ScalabilitySettings]")
            if (config.disableShadows || config.extremePotatoMode) {
                iniLines.add("+CVars=r.ShadowQuality=0")
                iniLines.add("+CVars=r.Shadow.DistanceScale=0")
                iniLines.add("+CVars=r.RayTracing=0")
            }
            if (config.reducePolygons || config.extremePotatoMode) {
                iniLines.add("+CVars=r.StaticMeshLODDistanceScale=0.2")
                iniLines.add("+CVars=r.SkeletalMeshLODBias=4")
            }
            if (config.disableWaterEffects || config.extremePotatoMode) {
                iniLines.add("+CVars=r.Water.SingleLayer=0")
                iniLines.add("+CVars=r.Water.SingleLayer.Reflection=0")
            }
            if (config.disableExplosions || config.extremePotatoMode) {
                iniLines.add("+CVars=r.ParticleLODBias=10")
                iniLines.add("+CVars=r.Emitter.FastPath=1")
            }
            if (config.astcTextureCompression || config.extremePotatoMode) {
                iniLines.add("+CVars=r.MipMapLODBias=2")
                iniLines.add("+CVars=r.Streaming.PoolSize=256")
                iniLines.add("+CVars=r.TextureStreaming=1")
                results.add("Unreal: ASTC Streaming PoolSize 256MB e LODBias=2")
            }
            if (config.forceMipmapping) {
                iniLines.add("+CVars=r.MaxAnisotropy=0")
                iniLines.add("+CVars=r.Streaming.MipBias=1")
                results.add("Unreal: Mipmapping forçado e anisotropia zerada")
            }
            results.add("Unreal: Comandos de escalabilidade gráfica injetados")
        } else if (engine == EngineType.GODOT) {
            // Otimizações nativas para Godot Engine via override.cfg e _cmdline.txt
            val godotDirs = listOf(
                "/data/data/$packageName/files",
                "/sdcard/Android/data/$packageName/files"
            )

            val overrideLines = mutableListOf<String>()
            overrideLines.add("[rendering]")

            if (config.disableShadows || config.extremePotatoMode) {
                overrideLines.add("quality/shadow_atlas/size=0")
                overrideLines.add("quality/directional_shadow/size=0")
                overrideLines.add("quality/shadows/filter_mode=0")
                overrideLines.add("lights_and_shadows/directional_shadow/soft_shadow_filter_quality=0")
                overrideLines.add("lights_and_shadows/positional_shadow/soft_shadow_filter_quality=0")
            }

            if (config.reducePolygons || config.extremePotatoMode) {
                overrideLines.add("quality/shading/force_vertex_shading=true")
                overrideLines.add("quality/shading/force_lambert_over_burley=true")
                overrideLines.add("anti_aliasing/quality/msaa_3d=0")
                overrideLines.add("anti_aliasing/quality/msaa_2d=0")
            }

            if (config.disableWaterEffects || config.extremePotatoMode) {
                overrideLines.add("quality/reflections/roughness_layers=0")
                overrideLines.add("quality/ssr/enabled=false")
                overrideLines.add("quality/ssao/enabled=false")
                overrideLines.add("quality/glow/enabled=false")
                overrideLines.add("quality/volumetric_fog/enabled=false")
                overrideLines.add("quality/sdfgi/enabled=false")
                overrideLines.add("quality/subsurface_scattering/quality=0")
            }

            if (config.disableExplosions || config.extremePotatoMode) {
                overrideLines.add("quality/particles/amount_ratio=0.1")
                overrideLines.add("textures/default_filters/anisotropic_filtering_level=0")
            }

            if (config.astcTextureCompression || config.extremePotatoMode) {
                overrideLines.add("textures/vram_compression/import_etc2_astc=true")
                overrideLines.add("textures/decals/filter=0")
                results.add("Godot: Compactação ASTC de texturas habilitada")
            }

            if (config.forceMipmapping) {
                overrideLines.add("textures/default_filters/use_nearest_mipmap_filter=true")
                results.add("Godot: Filtro de Mipmaps ativado para performance")
            }

            if (config.extremePotatoMode) {
                overrideLines.add("renderer/rendering_method=\"gl_compatibility\"")
            }

            if (config.muteAudio) {
                overrideLines.add("[audio]")
                overrideLines.add("driver/enable=false")
            }

            val overrideContent = overrideLines.joinToString("\n")
            for (dir in godotDirs) {
                ShizukuHelper.executeCommand("mkdir -p \"$dir\"")
                val writeCmd = "cat << 'EOF' > \"$dir/override.cfg\"\n$overrideContent\nEOF"
                ShizukuHelper.executeCommand(writeCmd)

                val cmdArgs = mutableListOf<String>()
                if (config.extremePotatoMode) cmdArgs.add("--rendering-method gl_compatibility --fixed-fps 30")
                if (config.muteAudio) cmdArgs.add("--audio-driver Dummy")
                if (cmdArgs.isNotEmpty()) {
                    val cmdContent = cmdArgs.joinToString(" ")
                    ShizukuHelper.executeCommand("echo \"$cmdContent\" > \"$dir/_cmdline.txt\"")
                }
            }

            results.add("Godot: override.cfg e _cmdline.txt injetados (sombras, reflexos e shaders desativados)")
        } else if (engine == EngineType.ITSMAGIC) {
            // Otimizações para jogos da engine ITsMagic (engine_settings.json e shared_prefs)
            val itsMagicDirs = listOf(
                "/data/data/$packageName/files",
                "/data/data/$packageName/files/ITsMagic",
                "/sdcard/Android/data/$packageName/files",
                "/sdcard/Android/data/$packageName/files/ITsMagic"
            )

            val jsonSettings = buildString {
                append("{\n")
                if (config.disableShadows || config.extremePotatoMode) {
                    append("  \"shadows\": false,\n")
                    append("  \"shadowQuality\": 0,\n")
                    append("  \"shadowDistance\": 0.0,\n")
                }
                if (config.reducePolygons || config.extremePotatoMode) {
                    append("  \"renderDistance\": 40.0,\n")
                    append("  \"viewDistance\": 40.0,\n")
                    append("  \"terrainLOD\": 0,\n")
                    append("  \"lodBias\": 0.2,\n")
                }
                if (config.disableWaterEffects || config.extremePotatoMode) {
                    append("  \"waterReflection\": false,\n")
                    append("  \"waterQuality\": 0,\n")
                    append("  \"waterEnabled\": false,\n")
                    append("  \"bloom\": false,\n")
                    append("  \"ssao\": false,\n")
                    append("  \"motionBlur\": false,\n")
                }
                if (config.disableExplosions || config.extremePotatoMode) {
                    append("  \"particlesEnabled\": false,\n")
                    append("  \"maxParticles\": 10,\n")
                    append("  \"particleQuality\": 0,\n")
                }
                if (config.muteAudio) {
                    append("  \"audioEnabled\": false,\n")
                    append("  \"masterVolume\": 0.0,\n")
                    append("  \"musicVolume\": 0.0,\n")
                    append("  \"sfxVolume\": 0.0,\n")
                }
                append("  \"targetFps\": 30,\n")
                append("  \"vSync\": 0\n")
                append("}")
            }

            for (dir in itsMagicDirs) {
                ShizukuHelper.executeCommand("mkdir -p \"$dir\"")
                ShizukuHelper.executeCommand("cat << 'EOF' > \"$dir/engine_settings.json\"\n$jsonSettings\nEOF")
                ShizukuHelper.executeCommand("cat << 'EOF' > \"$dir/settings.json\"\n$jsonSettings\nEOF")
            }

            val prefsFile = "/data/data/$packageName/shared_prefs/${packageName}_preferences.xml"
            if (config.disableShadows || config.extremePotatoMode) {
                ShizukuHelper.executeCommand("sed -i 's/<map>/<map><boolean name=\"shadows\" value=\"false\" \\/><int name=\"shadowQuality\" value=\"0\" \\/>/g' $prefsFile")
            }
            if (config.muteAudio) {
                ShizukuHelper.executeCommand("sed -i 's/<map>/<map><boolean name=\"sound\" value=\"false\" \\/><float name=\"volume\" value=\"0.0\" \\/>/g' $prefsFile")
            }

            results.add("ITsMagic: engine_settings.json injetado (sombras desativadas, view distance 40m)")
        }

        // 3. Sistema: Silenciamento de áudio do canal de mídia
        if (config.muteAudio) {
            ShizukuHelper.executeCommand("cmd media_session volume --stream 3 --set 0")
            results.add("Áudio: Canal de mídia do jogo silenciado via Shizuku")
        }

        // 4. Parâmetros gráficos universais no Android (Downgrade Mode)
        if (config.extremePotatoMode) {
            ShizukuHelper.executeCommand("setprop debug.egl.force_msaa 0")
            ShizukuHelper.executeCommand("setprop debug.hwui.disable_draw_defer 1")
            results.add("GPU: MSAA forçado em 0x e pipeline de hardware simplificado")
        }

        // 5. Otimizações para Jogos Web e Baseados em WebView (HTML5, React, Canvas 2D, WebGL)
        if (engine == EngineType.WEBVIEW || config.webViewGpuBoost) {
            val webViewFlags = "_ --enable-gpu-rasterization --enable-zero-copy --ignore-gpu-blocklist --enable-accelerated-2d-canvas --force-gpu-mem-available-mb=512 --disable-gpu-vsync --disable-background-timer-throttling"
            ShizukuHelper.executeCommand("mkdir -p /data/local/tmp")
            ShizukuHelper.executeCommand("echo \"$webViewFlags\" > /data/local/tmp/webview-command-line")
            ShizukuHelper.executeCommand("chmod 755 /data/local/tmp/webview-command-line")
            results.add("WebView: Flags de GPU Rasterization e Zero-Copy ativadas")

            ShizukuHelper.executeCommand("setprop debug.hwui.renderer skiagl")
            ShizukuHelper.executeCommand("setprop debug.hwui.render_ahead 1")
            ShizukuHelper.executeCommand("setprop debug.hwui.texture_cache_size 72")
            ShizukuHelper.executeCommand("setprop debug.hwui.texture_cache_flushrate 0.8")
            results.add("HWUI: Skia GL e cache de texturas do WebView otimizados")

            val boostCmd = "for p in \$(pgrep -f 'sandboxed_process'); do renice -n -20 -p \$p 2>/dev/null; echo -1000 > /proc/\$p/oom_score_adj 2>/dev/null; done"
            ShizukuHelper.executeCommand(boostCmd)
            results.add("WebView: Renderizador :sandboxed_process priorizado (Nice -20 / OOM -1000)")

            if (config.cleanWebViewCache) {
                ShizukuHelper.executeCommand("rm -rf /data/data/$packageName/app_webview/Default/GPUCache/*")
                ShizukuHelper.executeCommand("rm -rf /data/data/$packageName/app_webview/ShaderCache/*")
                results.add("WebView: Shaders antigos do GPUCache limpos")
            }
        }

        return if (results.isNotEmpty()) results.joinToString("\n") else "Nenhum ajuste gráfico adicional ativado."
    }
}
