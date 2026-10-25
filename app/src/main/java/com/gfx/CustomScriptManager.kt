package com.gfx

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * [PT-BR] Gerenciador de Scripts Shell (.sh).
 * Permite aos usuários cadastrar, salvar, editar, restaurar e executar novas funções personalizadas
 * usando comandos de terminal com privilégios do Shizuku / Root ou Shell padrão.
 *
 * [EN] Shell Scripts (.sh) Manager.
 * Allows users to register, save, edit, restore, and execute new custom functions
 * using terminal commands with Shizuku / Root privileges or standard shell.
 */
object CustomScriptManager {

    private const val SCRIPTS_PREF_NAME = "custom_sh_scripts_prefs"
    private const val SCRIPTS_KEY = "saved_scripts_json"

    val defaultPresets = listOf(
        CustomScript(
            id = "preset_120hz",
            title = "Taxa de Atualização 120Hz & Max FPS",
            description = "Fixa a tela na taxa máxima de 120Hz e desativa limitadores dinâmicos de FPS do sistema.",
            scriptContent = """
                # [PT-BR] Força a taxa de atualização máxima para jogos
                settings put system peak_refresh_rate 120.0
                settings put system min_refresh_rate 120.0
                settings put system user_refresh_rate 120.0
                settings put global animator_duration_scale 0.0
                settings put global transition_animation_scale 0.0
                echo "120Hz e acelerador de animações aplicados com sucesso!"
            """.trimIndent(),
            category = ScriptCategory.GRAPHICS,
            runAsPrivileged = true,
            isPreset = true
        ),
        CustomScript(
            id = "preset_ram_clean",
            title = "Limpeza Agressiva de RAM & Caches",
            description = "Sincroniza escritas pendentes, libera caches de página da memória e reduz swap inativo.",
            scriptContent = """
                # [PT-BR] Limpeza forçada de caches do kernel e de pacotes
                sync
                echo 3 > /proc/sys/vm/drop_caches 2>/dev/null || true
                pm trim-caches 4096M
                am kill-all 2>/dev/null || true
                echo "Memória RAM e caches do sistema limpos!"
            """.trimIndent(),
            category = ScriptCategory.MEMORY,
            runAsPrivileged = true,
            isPreset = true
        ),
        CustomScript(
            id = "preset_skia_vk",
            title = "Forçar Renderizador Skia Vulkan",
            description = "Altera o pipeline gráfico do HWUI para Vulkan (SkiaVK) com menos sobrecarga de draw calls.",
            scriptContent = """
                # [PT-BR] Pipeline Skia Vulkan no Android
                setprop debug.hwui.renderer skiavk
                setprop renderthread.skia.reduceopstasksplitting true
                setprop debug.hwui.fps_divisor 1
                echo "Renderizador Skia Vulkan configurado!"
            """.trimIndent(),
            category = ScriptCategory.PERFORMANCE,
            runAsPrivileged = true,
            isPreset = true
        ),
        CustomScript(
            id = "preset_reduce_logger",
            title = "Reduzir Overhead de Logs do Sistema",
            description = "Diminui o tamanho do buffer de log para 64KB e desativa logs persistentes pesados.",
            scriptContent = """
                # [PT-BR] Reduz a sobrecarga de escrita contínua de logs no kernel
                setprop logd.logpersistd.stop 1
                setprop persist.logd.size 64K
                setprop persist.log.tag S
                logcat -c
                echo "Buffer de logs reduzido para 64KB!"
            """.trimIndent(),
            category = ScriptCategory.SYSTEM,
            runAsPrivileged = true,
            isPreset = true
        ),
        CustomScript(
            id = "preset_disable_spooler",
            title = "Desativar Spooler de Impressão",
            description = "Desativa o serviço de impressão que fica buscando impressoras em segundo plano consumindo bateria e CPU.",
            scriptContent = """
                # [PT-BR] Desativa o serviço PrintSpooler do Android
                pm disable-user --user 0 com.android.printspooler
                echo "Serviço de impressão desativado com sucesso!"
            """.trimIndent(),
            category = ScriptCategory.SYSTEM,
            runAsPrivileged = true,
            isPreset = true
        ),
        CustomScript(
            id = "preset_fixed_performance",
            title = "Modo Desempenho Fixo (Android OS Core)",
            description = "Ativa a flag nativa do Android power service para modo de performance fixo em CPUs/GPUs.",
            scriptContent = """
                # [PT-BR] Trava frequências no modo de desempenho estável do Android
                cmd power set-fixed-performance-mode-enabled true
                echo "Modo de desempenho nativo do kernel ativado!"
            """.trimIndent(),
            category = ScriptCategory.PERFORMANCE,
            runAsPrivileged = true,
            isPreset = true
        ),
        CustomScript(
            id = "preset_network_tweaks",
            title = "Otimizar Latência de Rede (Game TCP)",
            description = "Aumenta buffers de rede TCP para Wi-Fi e desativa ECN para menor variação de ping.",
            scriptContent = """
                # [PT-BR] Ajustes de pilha TCP para estabilidade de ping em jogos online
                setprop net.tcp.buffersize.wifi 524288,1048576,2097152,262144,524288,1048576
                setprop net.ipv4.tcp_ecn 0
                setprop net.ipv4.tcp_sack 1
                echo "Buffers TCP de alta velocidade e baixa latência aplicados!"
            """.trimIndent(),
            category = ScriptCategory.NETWORK,
            runAsPrivileged = true,
            isPreset = true
        ),
        CustomScript(
            id = "preset_webview_gpu",
            title = "Aceleração GPU para Jogos Web / WebView",
            description = "Força rasterização GPU e desativa rasterizador por software para jogos em Canvas/HTML5/React.",
            scriptContent = """
                # [PT-BR] Aceleração gráfica direta para jogos rodando via WebView / Web
                setprop debug.webview.gpu_raster true
                setprop webview.disable_software_rasterizer 1
                setprop debug.egl.swapinterval 0
                echo "Rasterização GPU para WebViews ativada!"
            """.trimIndent(),
            category = ScriptCategory.GRAPHICS,
            runAsPrivileged = true,
            isPreset = true
        ),
        CustomScript(
            id = "preset_force_msaa",
            title = "Forçar 4x MSAA & Composição por GPU",
            description = "Força aceleração total 2D por GPU e antialiasing 4x em aplicações OpenGL ES.",
            scriptContent = """
                # [PT-BR] Ativa 4x MSAA e composição por GPU nas opções do sistema
                setprop debug.egl.force_msaa 1
                setprop debug.composition.type gpu
                echo "Composição por GPU e 4x MSAA ativados!"
            """.trimIndent(),
            category = ScriptCategory.GRAPHICS,
            runAsPrivileged = true,
            isPreset = true
        )
    )

    fun loadScripts(context: Context): List<CustomScript> {
        val prefs = context.getSharedPreferences(SCRIPTS_PREF_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(SCRIPTS_KEY, null)
        if (jsonStr.isNullOrBlank()) {
            val initial = defaultPresets
            saveAllScripts(context, initial)
            return initial
        }
        return try {
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<CustomScript>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val catName = obj.optString("category", ScriptCategory.PERFORMANCE.name)
                val category = try { ScriptCategory.valueOf(catName) } catch (_: Exception) { ScriptCategory.CUSTOM }

                list.add(
                    CustomScript(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        title = obj.optString("title", "Script Sem Título"),
                        description = obj.optString("description", ""),
                        scriptContent = obj.optString("scriptContent", ""),
                        category = category,
                        runAsPrivileged = obj.optBoolean("runAsPrivileged", true),
                        runOnAppStart = obj.optBoolean("runOnAppStart", false),
                        isPreset = obj.optBoolean("isPreset", false),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
            if (list.isEmpty()) defaultPresets else list
        } catch (e: Exception) {
            defaultPresets
        }
    }

    fun saveScript(context: Context, script: CustomScript): List<CustomScript> {
        val current = loadScripts(context).toMutableList()
        val existingIndex = current.indexOfFirst { it.id == script.id }
        if (existingIndex >= 0) {
            current[existingIndex] = script
        } else {
            current.add(0, script)
        }
        saveAllScripts(context, current)
        return current
    }

    fun deleteScript(context: Context, scriptId: String): List<CustomScript> {
        val current = loadScripts(context).filterNot { it.id == scriptId }
        saveAllScripts(context, current)
        return current
    }

    fun resetToDefaults(context: Context): List<CustomScript> {
        saveAllScripts(context, defaultPresets)
        return defaultPresets
    }

    private fun saveAllScripts(context: Context, scripts: List<CustomScript>) {
        val prefs = context.getSharedPreferences(SCRIPTS_PREF_NAME, Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        for (s in scripts) {
            val obj = JSONObject()
            obj.put("id", s.id)
            obj.put("title", s.title)
            obj.put("description", s.description)
            obj.put("scriptContent", s.scriptContent)
            obj.put("category", s.category.name)
            obj.put("runAsPrivileged", s.runAsPrivileged)
            obj.put("runOnAppStart", s.runOnAppStart)
            obj.put("isPreset", s.isPreset)
            obj.put("createdAt", s.createdAt)
            jsonArray.put(obj)
        }
        prefs.edit().putString(SCRIPTS_KEY, jsonArray.toString()).apply()
    }

    suspend fun executeScript(context: Context, script: CustomScript): String {
        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            ShellFunctions.log("[Script] Iniciando '${script.title}'...")

            val lines = script.scriptContent.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }

            if (lines.isEmpty()) {
                val msg = "O script '${script.title}' está vazio ou contém apenas comentários."
                ShellFunctions.log("> $msg")
                return@withContext msg
            }

            val sb = StringBuilder()
            var successCount = 0

            // Executa em lote criando um script temporário legível
            try {
                val tempScript = File(context.cacheDir, "exec_${System.currentTimeMillis()}.sh")
                tempScript.writeText(script.scriptContent)
                tempScript.setReadable(true, false)
                tempScript.setExecutable(true, false)

                val fullCmd = "sh ${tempScript.absolutePath}"
                val output = if (script.runAsPrivileged) {
                    ShellFunctions.executePrivileged(fullCmd)
                } else {
                    ShellFunctions.execute(fullCmd)
                }
                sb.append(output)
                tempScript.delete()
            } catch (e: Exception) {
                // Fallback para execução linha por linha caso criação de arquivo falhe
                for (cmd in lines) {
                    val out = if (script.runAsPrivileged) {
                        ShellFunctions.executePrivileged(cmd)
                    } else {
                        ShellFunctions.execute(cmd)
                    }
                    if (out.isNotEmpty()) {
                        sb.append(out).append("\n")
                    }
                    successCount++
                }
            }

            val elapsed = System.currentTimeMillis() - startTime
            val finalOutput = sb.toString().trim()
            val summary = "Executado em ${elapsed}ms: ${if (finalOutput.isEmpty()) "Comandos executados com sucesso (sem retorno impresso)." else finalOutput}"
            ShellFunctions.log("> Concluído: $summary")
            summary
        }
    }
}
