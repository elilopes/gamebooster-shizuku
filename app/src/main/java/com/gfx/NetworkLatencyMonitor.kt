package com.gfx

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * [PT-BR] Monitor de latência e jitter de rede via conexão TCP (sem necessidade de privilégios ou root).
 * Permite ao jogador monitorar a estabilidade da rota de rede, ping e oscilação (jitter)
 * para identificar se travamentos e lag em jogos online vêm de problemas na conexão de internet.
 *
 * [EN] Network latency and jitter monitor using standard TCP socket probing (no privileges required).
 */
object NetworkLatencyMonitor {

    data class ServerTarget(
        val name: String,
        val host: String,
        val port: Int
    )

    val PRESET_SERVERS = listOf(
        ServerTarget("Cloudflare Edge (1.1.1.1)", "1.1.1.1", 53),
        ServerTarget("Google Public DNS (8.8.8.8)", "8.8.8.8", 53),
        ServerTarget("Valve Steam / CS2", "162.254.192.1", 27015),
        ServerTarget("AWS Cloud Gaming / US-East", "us-east-1.amazonaws.com", 443),
        ServerTarget("Riot / LoL Games Edge", "104.16.24.4", 443)
    )

    enum class NetworkQuality {
        EXCELENTE,  // < 35ms, jitter < 5ms
        BOM,        // < 75ms, jitter < 15ms
        INSTAVEL,   // 75-150ms ou jitter > 25ms
        CRITICO     // > 150ms ou perda de pacotes
    }

    data class LatencyReport(
        val pingMs: Int = 0,
        val jitterMs: Int = 0,
        val packetLossPercent: Float = 0f,
        val minPingMs: Int = 0,
        val maxPingMs: Int = 0,
        val targetServerName: String = "Cloudflare Edge",
        val quality: NetworkQuality = NetworkQuality.EXCELENTE,
        val isMonitoring: Boolean = false,
        val sampleCount: Int = 0
    )

    private val _latencyState = MutableStateFlow(LatencyReport())
    val latencyState = _latencyState.asStateFlow()

    private var monitorJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    var currentTarget: ServerTarget = PRESET_SERVERS[0]
        set(value) {
            field = value
            _latencyState.value = _latencyState.value.copy(targetServerName = value.name)
        }

    fun isRunning(): Boolean = monitorJob?.isActive == true

    /**
     * [PT-BR] Inicia o monitoramento contínuo em segundo plano da latência e jitter TCP.
     */
    fun startMonitoring(target: ServerTarget = currentTarget) {
        if (monitorJob?.isActive == true) return
        currentTarget = target

        val history = mutableListOf<Int>()
        var consecutiveTimeouts = 0
        var totalAttempts = 0
        var totalFailures = 0

        monitorJob = scope.launch {
            _latencyState.value = _latencyState.value.copy(
                isMonitoring = true,
                targetServerName = target.name
            )

            while (isActive) {
                totalAttempts++
                val start = System.currentTimeMillis()
                var success = false
                var rtt = 0

                try {
                    Socket().use { socket ->
                        socket.tcpNoDelay = true
                        val endpoint = InetSocketAddress(target.host, target.port)
                        socket.connect(endpoint, 1200) // 1.2s timeout
                        val elapsed = (System.currentTimeMillis() - start).toInt()
                        rtt = elapsed.coerceAtLeast(1)
                        success = true
                        consecutiveTimeouts = 0
                    }
                } catch (e: Exception) {
                    success = false
                    consecutiveTimeouts++
                    totalFailures++
                }

                if (success) {
                    history.add(rtt)
                    if (history.size > 20) history.removeAt(0)
                }

                // Cálculo do Jitter médio (RFC 1889 - variação consecutiva de tempo de chegada)
                val jitter = if (history.size >= 2) {
                    var diffSum = 0
                    for (i in 1 until history.size) {
                        diffSum += abs(history[i] - history[i - 1])
                    }
                    diffSum / (history.size - 1)
                } else 0

                val avgPing = if (history.isNotEmpty()) history.average().toInt() else 0
                val minP = if (history.isNotEmpty()) history.minOrNull() ?: 0 else 0
                val maxP = if (history.isNotEmpty()) history.maxOrNull() ?: 0 else 0
                val packetLoss = if (totalAttempts > 0) (totalFailures.toFloat() / totalAttempts.toFloat()) * 100f else 0f

                val quality = when {
                    packetLoss > 10f || avgPing > 150 || consecutiveTimeouts > 2 -> NetworkQuality.CRITICO
                    avgPing > 80 || jitter > 25 -> NetworkQuality.INSTAVEL
                    avgPing > 45 || jitter > 12 -> NetworkQuality.BOM
                    else -> NetworkQuality.EXCELENTE
                }

                _latencyState.value = LatencyReport(
                    pingMs = if (success) rtt else avgPing,
                    jitterMs = jitter,
                    packetLossPercent = packetLoss,
                    minPingMs = minP,
                    maxPingMs = maxP,
                    targetServerName = target.name,
                    quality = quality,
                    isMonitoring = true,
                    sampleCount = totalAttempts
                )

                delay(1200)
            }
        }
    }

    /**
     * [PT-BR] Para o monitoramento de latência e libera os recursos da corrotina.
     */
    fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
        _latencyState.value = _latencyState.value.copy(isMonitoring = false)
    }
}
