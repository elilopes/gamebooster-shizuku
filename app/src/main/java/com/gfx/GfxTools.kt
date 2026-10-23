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
        val muteAudio: Boolean = false
    )

    enum class EngineType {
        UNITY,
        UNREAL,
        GODOT,
        ITSMAGIC,
        GENERIC
    }

    /**
     * [PT-BR] Detecta automaticamente a engine gráfica utilizada pelo jogo inspecionando bibliotecas nativas e arquivos de configuração.
     */
    fun detectEngine(context: Context, packageName: String): EngineType {
        if (packageName.contains("battle.royale", ignoreCase = true) || packageName.contains("wildlife", ignoreCase = true)) {
            return EngineType.UNITY // Ex: Zooba Battle Royale
        }
        if (packageName.contains("itsmagic", ignoreCase = true)) {
            return EngineType.ITSMAGIC
        }
        return try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            val nativeDir = File(appInfo.nativeLibraryDir)
            if (nativeDir.exists() && nativeDir.isDirectory) {
                val libs = nativeDir.list() ?: emptyArray()
                if (libs.any { it.contains("itsmagic", ignoreCase = true) }) return EngineType.ITSMAGIC
                if (libs.any { it.contains("godot", ignoreCase = true) }) return EngineType.GODOT
                if (libs.any { it.contains("unity", ignoreCase = true) }) return EngineType.UNITY
                if (libs.any { it.contains("UE4", ignoreCase = true) || it.contains("Unreal", ignoreCase = true) }) return EngineType.UNREAL
            }
            // Verifica pacotes .pck típicos do Godot
            val dataDir = File("/sdcard/Android/data/$packageName/files")
            if (dataDir.exists() && dataDir.listFiles()?.any { it.extension.equals("pck", ignoreCase = true) } == true) {
                return EngineType.GODOT
            }
            // Verifica arquivos típicos da ITsMagic Engine
            if (dataDir.exists() && dataDir.walkTopDown().maxDepth(3).any {
                    it.name.contains("itsmagic", ignoreCase = true) || it.name.equals("engine_settings.json", ignoreCase = true)
                }) {
                return EngineType.ITSMAGIC
            }
            EngineType.GENERIC
        } catch (e: Exception) {
            EngineType.GENERIC
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
            muteAudio = prefs.getBoolean("${packageName}_mute", false)
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

        return if (results.isNotEmpty()) results.joinToString("\n") else "Nenhum ajuste gráfico adicional ativado."
    }
}
