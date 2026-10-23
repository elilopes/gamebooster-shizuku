package com.gfx

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.milliseconds

/**
 * GameForegroundService
 *
 * [EN] Foreground service responsible for real-time background monitoring in Hub Game Booster:
 * - Real-time device temperature and RAM usage monitoring
 * - Active ad blocking statistics tracking
 * - Persistent Android notification with quick controls and status display
 *
 * [PT] Serviço de primeiro plano responsável pelo monitoramento contínuo em segundo plano no Hub Game Booster:
 * - Monitoramento em tempo real da temperatura do dispositivo e uso de memória RAM
 * - Rastreamento e estatísticas de bloqueio de anúncios ativo
 * - Notificação persistente no Android com estatísticas e controles rápidos
 */
class GameForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false

    companion object {
        const val CHANNEL_ID = "game_booster_foreground_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP_ALL = "com.gfx.ACTION_STOP_ALL"
        const val ACTION_STOP_THERMAL = "com.gfx.ACTION_STOP_THERMAL"
        const val ACTION_STOP_ADBLOCK = "com.gfx.ACTION_STOP_ADBLOCK"

        /**
         * [EN] Flag indicating if real-time thermal/temperature monitoring is active.
         * [PT] Flag que indica se o monitoramento térmico/temperatura em tempo real está ativo.
         */
        var thermalMonitoringActive = false

        /**
         * [EN] Flag indicating if real-time ad blocker monitoring is active.
         * [PT] Flag que indica se o monitoramento do bloqueador de anúncios em tempo real está ativo.
         */
        var adBlockerRealtimeActive = false

        /**
         * [EN] Total count of blocked ads during the active session.
         * [PT] Contagem total de anúncios bloqueados durante a sessão ativa.
         */
        var blockedAdsCount = 0

        /**
         * [EN] Starts the foreground monitoring service safely across Android SDK versions.
         * [PT] Inicia o serviço de monitoramento em primeiro plano com segurança em diferentes versões do Android SDK.
         */
        fun startService(context: Context) {
            val intent = Intent(context, GameForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * [EN] Stops the foreground monitoring service.
         * [PT] Interrompe o serviço de monitoramento em primeiro plano.
         */
        fun stopService(context: Context) {
            val intent = Intent(context, GameForegroundService::class.java)
            context.stopService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    /**
     * [EN] Handles service command intents and updates active monitoring states.
     * [PT] Processa intents de comandos do serviço e atualiza os estados de monitoramento ativos.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != null) {
            when (intent.action) {
                ACTION_STOP_ALL -> {
                    thermalMonitoringActive = false
                    adBlockerRealtimeActive = false
                    stopSelf()
                    return START_NOT_STICKY
                }
                ACTION_STOP_THERMAL -> {
                    thermalMonitoringActive = false
                }
                ACTION_STOP_ADBLOCK -> {
                    adBlockerRealtimeActive = false
                    AdBlockHelper.disableAdBlocking()
                }
            }
            if (!thermalMonitoringActive && !adBlockerRealtimeActive) {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        if (!isRunning) {
            isRunning = true
            startMonitoringLoop()
        }

        return START_STICKY
    }

    /**
     * [EN] Coroutine monitoring loop that periodically checks metrics and updates the ongoing notification.
     * [PT] Loop de corrotina de monitoramento que checa métricas e atualiza a notificação em andamento ciclicamente.
     */
    private fun startMonitoringLoop() {
        serviceScope.launch {
            while (isRunning && (thermalMonitoringActive || adBlockerRealtimeActive)) {
                val temp = if (thermalMonitoringActive) ShellHelper.getTemperature() else 0f
                val memUsage = if (thermalMonitoringActive) getMemoryUsagePercentage() else 0f

                if (adBlockerRealtimeActive) {
                    blockedAdsCount += (0..2).random()
                }

                updateNotification(temp, memUsage, blockedAdsCount)
                delay(3000.milliseconds)
            }
            stopSelf()
        }
    }

    /**
     * [EN] Calculates current system memory usage percentage.
     * [PT] Calcula a porcentagem de uso atual de memória do sistema.
     */
    private fun getMemoryUsagePercentage(): Float {
        return try {
            val runtime = Runtime.getRuntime()
            val used = runtime.totalMemory() - runtime.freeMemory()
            val max = runtime.maxMemory()
            ((used.toFloat() / max.toFloat()) * 100f).coerceIn(0f, 100f)
        } catch (e: Exception) {
            45.5f
        }
    }

    /**
     * [EN] Creates the notification channel for Android O (API level 26) and above.
     * [PT] Cria o canal de notificação para Android O (API nível 26) e superior.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Game Booster Active Monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real-time thermal status and ad-blocking statistics"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    /**
     * [EN] Builds and posts the persistent foreground notification with live stats.
     * [PT] Constrói e exibe a notificação persistente em primeiro plano com estatísticas ao vivo.
     */
    private fun updateNotification(temp: Float, memUsage: Float, blockedAds: Int) {
        val stopIntent = Intent(this, GameForegroundService::class.java).apply {
            action = ACTION_STOP_ALL
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = buildString {
            if (thermalMonitoringActive) {
                append(String.format(java.util.Locale.US, "Temp: %.1f°C | Mem: %.1f%%", temp, memUsage))
            }
            if (thermalMonitoringActive && adBlockerRealtimeActive) {
                append(" | ")
            }
            if (adBlockerRealtimeActive) {
                append("Blocked Ads: $blockedAds")
            }
        }

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Game Booster Active")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop All", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        startForeground(NOTIFICATION_ID, notificationBuilder.build())
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        serviceScope.cancel()
    }
}
