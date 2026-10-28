package com.gfx

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * [PT-BR] Gerenciador de Benchmarks e Comparação A/B de Perfis com Medição Real.
 * Utiliza 'dumpsys gfxinfo <pkg> framestats' via Shizuku/sistema para extrair:
 * - FPS Médio real
 * - 1% Low FPS (percentil 99 dos tempos de quadro)
 * - Porcentagem de quadros com engasgo (Jank %)
 * - Comparação A/B direta entre Perfil A (Padrão) e Perfil B (Otimizado)
 *
 * [EN] Benchmark and A/B Profile Testing Manager using 'dumpsys gfxinfo <pkg> framestats'.
 */
object BenchmarkManager {

    private const val PREFS_NAME = "gfx_benchmark_prefs"
    private const val KEY_BENCHMARK_HISTORY = "benchmark_history"

    data class ProfileBenchmarkResult(
        val id: String = System.currentTimeMillis().toString(),
        val packageName: String,
        val profileName: String, // ex: "Perfil A (Padrão)" ou "Perfil B (GFX Tuner + 45 FPS)"
        val averageFps: Float,
        val onePercentLowFps: Float,
        val jankPercentage: Float,
        val totalFrames: Int,
        val durationSeconds: Int,
        val timestamp: Long = System.currentTimeMillis(),
        val notes: String = ""
    )

    data class ABComparison(
        val profileA: ProfileBenchmarkResult,
        val profileB: ProfileBenchmarkResult,
        val avgFpsGainPercent: Float,
        val onePercentLowGainPercent: Float,
        val jankReductionPercent: Float,
        val verdict: String
    )

    private val _isRecording = MutableStateFlow(false)
    val isRecording = _isRecording.asStateFlow()

    private val _recordingPackage = MutableStateFlow<String?>(null)
    val recordingPackage = _recordingPackage.asStateFlow()

    private val _recordingProfile = MutableStateFlow("Perfil A")
    val recordingProfile = _recordingProfile.asStateFlow()

    private var sessionStartTime = 0L

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * [PT-BR] Inicia a gravação de benchmark de um jogo resetando os contadores do gfxinfo.
     */
    suspend fun startBenchmark(packageName: String, profileLabel: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Reseta framestats acumulados para iniciar medição limpa da sessão atual
                ShellFunctions.executePrivileged("dumpsys gfxinfo $packageName reset")
                sessionStartTime = System.currentTimeMillis()
                _recordingPackage.value = packageName
                _recordingProfile.value = profileLabel
                _isRecording.value = true
                Log.d("Benchmark", "Benchmark iniciado para $packageName com perfil $profileLabel")
                true
            } catch (e: Exception) {
                Log.e("Benchmark", "Erro ao iniciar benchmark: ${e.message}")
                false
            }
        }
    }

    /**
     * [PT-BR] Finaliza a gravação, coleta o relatório via gfxinfo, calcula FPS médio, 1% Low e Jank, e salva o resultado.
     */
    suspend fun stopBenchmark(context: Context): ProfileBenchmarkResult? {
        return withContext(Dispatchers.IO) {
            val pkg = _recordingPackage.value ?: return@withContext null
            val profile = _recordingProfile.value
            val durationSec = ((System.currentTimeMillis() - sessionStartTime) / 1000).toInt().coerceAtLeast(1)

            try {
                val output = ShellFunctions.executePrivileged("dumpsys gfxinfo $pkg")
                val result = parseGfxInfoOutput(pkg, profile, output, durationSec)

                saveBenchmarkResult(context, result)

                _isRecording.value = false
                _recordingPackage.value = null
                result
            } catch (e: Exception) {
                Log.e("Benchmark", "Erro ao parar benchmark: ${e.message}")
                _isRecording.value = false
                _recordingPackage.value = null
                null
            }
        }
    }

    /**
     * [PT-BR] Analisa o output do dumpsys gfxinfo para extrair métricas oficiais do Android Choreographer e RenderThread.
     */
    private fun parseGfxInfoOutput(packageName: String, profileName: String, output: String, durationSec: Int): ProfileBenchmarkResult {
        var totalFrames = 0
        var jankFrames = 0
        var p50 = 16.6f
        var p90 = 16.6f
        var p95 = 16.6f
        var p99 = 25.0f

        val lines = output.lines()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("Total frames rendered:", ignoreCase = true)) {
                totalFrames = trimmed.substringAfter(":").trim().toIntOrNull() ?: 0
            } else if (trimmed.startsWith("Janky frames:", ignoreCase = true)) {
                // Formato: Janky frames: 15 (2.50%)
                val parts = trimmed.substringAfter(":").trim().split(" ")
                jankFrames = parts.firstOrNull()?.toIntOrNull() ?: 0
            } else if (trimmed.startsWith("50th percentile:", ignoreCase = true)) {
                p50 = trimmed.substringAfter(":").replace("ms", "").trim().toFloatOrNull() ?: 16.6f
            } else if (trimmed.startsWith("90th percentile:", ignoreCase = true)) {
                p90 = trimmed.substringAfter(":").replace("ms", "").trim().toFloatOrNull() ?: 16.6f
            } else if (trimmed.startsWith("95th percentile:", ignoreCase = true)) {
                p95 = trimmed.substringAfter(":").replace("ms", "").trim().toFloatOrNull() ?: 16.6f
            } else if (trimmed.startsWith("99th percentile:", ignoreCase = true)) {
                p99 = trimmed.substringAfter(":").replace("ms", "").trim().toFloatOrNull() ?: 25.0f
            }
        }

        // Se total de frames for razoável, calculamos o FPS Médio
        val avgFps = if (durationSec > 0 && totalFrames > 0) {
            (totalFrames.toFloat() / durationSec.toFloat()).coerceIn(1f, 165f)
        } else {
            (1000f / p50.coerceAtLeast(5f)).coerceIn(1f, 165f)
        }

        // 1% Low FPS corresponde ao percentil 99 dos tempos de quadro (pior 1% de frames renderizados)
        val onePercentLow = (1000f / p99.coerceAtLeast(6f)).coerceIn(1f, avgFps)

        val jankPercent = if (totalFrames > 0) {
            ((jankFrames.toFloat() / totalFrames.toFloat()) * 100f).coerceIn(0f, 100f)
        } else {
            0f
        }

        return ProfileBenchmarkResult(
            packageName = packageName,
            profileName = profileName,
            averageFps = String.format(java.util.Locale.US, "%.1f", avgFps).toFloat(),
            onePercentLowFps = String.format(java.util.Locale.US, "%.1f", onePercentLow).toFloat(),
            jankPercentage = String.format(java.util.Locale.US, "%.1f", jankPercent).toFloat(),
            totalFrames = totalFrames,
            durationSeconds = durationSec
        )
    }

    /**
     * [PT-BR] Salva o resultado do benchmark no histórico local.
     */
    fun saveBenchmarkResult(context: Context, result: ProfileBenchmarkResult) {
        val prefs = getPrefs(context)
        val list = loadBenchmarkHistory(context).toMutableList()
        list.add(0, result)
        if (list.size > 30) list.removeAt(list.size - 1)

        val array = JSONArray()
        list.forEach { item ->
            val obj = JSONObject().apply {
                put("id", item.id)
                put("packageName", item.packageName)
                put("profileName", item.profileName)
                put("averageFps", item.averageFps.toDouble())
                put("onePercentLowFps", item.onePercentLowFps.toDouble())
                put("jankPercentage", item.jankPercentage.toDouble())
                put("totalFrames", item.totalFrames)
                put("durationSeconds", item.durationSeconds)
                put("timestamp", item.timestamp)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_BENCHMARK_HISTORY, array.toString()).apply()
    }

    /**
     * [PT-BR] Carrega todos os benchmarks salvos do histórico.
     */
    fun loadBenchmarkHistory(context: Context): List<ProfileBenchmarkResult> {
        val jsonStr = getPrefs(context).getString(KEY_BENCHMARK_HISTORY, null) ?: return emptyList()
        val list = mutableListOf<ProfileBenchmarkResult>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    ProfileBenchmarkResult(
                        id = obj.optString("id", System.currentTimeMillis().toString()),
                        packageName = obj.optString("packageName", ""),
                        profileName = obj.optString("profileName", "Perfil"),
                        averageFps = obj.optDouble("averageFps", 60.0).toFloat(),
                        onePercentLowFps = obj.optDouble("onePercentLowFps", 45.0).toFloat(),
                        jankPercentage = obj.optDouble("jankPercentage", 2.0).toFloat(),
                        totalFrames = obj.optInt("totalFrames", 0),
                        durationSeconds = obj.optInt("durationSeconds", 0),
                        timestamp = obj.optLong("timestamp", 0L)
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("Benchmark", "Erro ao carregar histórico de benchmarks: ${e.message}")
        }
        return list
    }

    /**
     * [PT-BR] Compara o Perfil A com o Perfil B gerando análise de eficácia objetiva.
     */
    fun compareProfiles(a: ProfileBenchmarkResult, b: ProfileBenchmarkResult): ABComparison {
        val avgGain = if (a.averageFps > 0) ((b.averageFps - a.averageFps) / a.averageFps) * 100f else 0f
        val lowGain = if (a.onePercentLowFps > 0) ((b.onePercentLowFps - a.onePercentLowFps) / a.onePercentLowFps) * 100f else 0f
        val jankReduction = a.jankPercentage - b.jankPercentage

        val verdict = when {
            lowGain > 15f && jankReduction > 1f -> "🚀 Otimização Excelente: o Perfil B estabilizou os micropausas e reduziu os engasgos significativamente (+${String.format(java.util.Locale.US, "%.1f", lowGain)}% no 1% Low)."
            avgGain > 10f || lowGain > 5f -> "✅ Ganho Comprovado: o Perfil B rendeu mais fluidez e melhor média de quadros (+${String.format(java.util.Locale.US, "%.1f", avgGain)}% FPS)."
            avgGain < -5f && lowGain < -5f -> "⚠️ Sem Otimização: o Perfil B gerou perda de performance (-${String.format(java.util.Locale.US, "%.1f", -avgGain)}% FPS). Recomenda-se manter o Perfil A."
            else -> "⚖️ Desempenho Equivalente: não houve variação expressiva entre os dois perfis neste jogo."
        }

        return ABComparison(
            profileA = a,
            profileB = b,
            avgFpsGainPercent = avgGain,
            onePercentLowGainPercent = lowGain,
            jankReductionPercent = jankReduction,
            verdict = verdict
        )
    }
}
