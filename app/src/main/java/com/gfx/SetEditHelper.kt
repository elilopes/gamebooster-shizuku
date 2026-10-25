package com.gfx

import android.content.Context
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [PT-BR] Helper utilitário para edição e gerenciamento de parâmetros das tabelas do Settings Provider (estilo SetEdit):
 * Permite ler, alterar e excluir chaves nas tabelas System, Secure e Global usando Shizuku sem necessidade de root.
 *
 * [EN] Utility helper for Settings Provider database editing (SetEdit style):
 * Allows reading, updating and deleting keys in System, Secure and Global tables via Shizuku without root.
 */
object SetEditHelper {

    data class SettingItem(
        val namespace: String,
        val key: String,
        val value: String
    )

    const val TABLE_GLOBAL = "global"
    const val TABLE_SYSTEM = "system"
    const val TABLE_SECURE = "secure"

    val AVAILABLE_TABLES = listOf(TABLE_GLOBAL, TABLE_SYSTEM, TABLE_SECURE)

    /**
     * [PT-BR] Obtém o valor de um parâmetro específico no Settings Provider.
     * Tenta primeiro via ContentResolver nativo do Android e recorre ao Shell privilegiado se necessário.
     */
    fun getSetting(context: Context, namespace: String, key: String): String {
        return try {
            val resolver = context.contentResolver
            val nativeVal = when (namespace.lowercase()) {
                TABLE_GLOBAL -> Settings.Global.getString(resolver, key)
                TABLE_SECURE -> Settings.Secure.getString(resolver, key)
                TABLE_SYSTEM -> Settings.System.getString(resolver, key)
                else -> null
            }
            if (nativeVal != null) return nativeVal

            val shellOut = ShellFunctions.executePrivileged("settings get $namespace $key").trim()
            if (shellOut.isEmpty() || shellOut.equals("null", ignoreCase = true)) "" else shellOut
        } catch (e: Exception) {
            Log.e("SetEditHelper", "Erro ao obter parâmetro $namespace/$key: ${e.message}")
            ""
        }
    }

    /**
     * [PT-BR] Altera ou cria um parâmetro no Settings Provider usando comando privilegiado do Shizuku.
     *
     * @return Pair de sucesso e mensagem descritiva.
     */
    fun putSetting(namespace: String, key: String, value: String): Pair<Boolean, String> {
        val trimmedKey = key.trim()
        val trimmedVal = value.trim()
        if (trimmedKey.isEmpty()) {
            return Pair(false, "O nome da chave não pode ser vazio.")
        }

        val hasPriv = ShizukuHelper.hasPermission() || ShizukuHelper.isRootAvailable()
        val cmd = "settings put $namespace $trimmedKey $trimmedVal"
        val output = ShellFunctions.executePrivileged(cmd)

        return if (output.contains("Error", ignoreCase = true) || output.contains("denied", ignoreCase = true)) {
            val reason = if (!hasPriv) "Inicie e autorize o Shizuku para aplicar parâmetros no sistema." else output
            Pair(false, "Falha ao gravar $trimmedKey: $reason")
        } else {
            Pair(true, "Parâmetro $trimmedKey alterado para '$trimmedVal' na tabela $namespace com sucesso!")
        }
    }

    /**
     * [PT-BR] Exclui um parâmetro no Settings Provider.
     */
    fun deleteSetting(namespace: String, key: String): Pair<Boolean, String> {
        val trimmedKey = key.trim()
        if (trimmedKey.isEmpty()) return Pair(false, "Chave inválida.")

        val hasPriv = ShizukuHelper.hasPermission() || ShizukuHelper.isRootAvailable()
        val cmd = "settings delete $namespace $trimmedKey"
        val output = ShellFunctions.executePrivileged(cmd)

        return if (output.contains("Error", ignoreCase = true) || output.contains("denied", ignoreCase = true)) {
            val reason = if (!hasPriv) "Inicie e autorize o Shizuku para excluir parâmetros no sistema." else output
            Pair(false, "Falha ao excluir $trimmedKey: $reason")
        } else {
            Pair(true, "Parâmetro $trimmedKey removido da tabela $namespace.")
        }
    }

    /**
     * [PT-BR] Lista todos os parâmetros da tabela selecionada (Global, System ou Secure).
     */
    suspend fun listSettings(namespace: String): List<SettingItem> = withContext(Dispatchers.IO) {
        val output = ShellFunctions.executePrivileged("settings list $namespace")
        if (output.startsWith("Error", ignoreCase = true) || output.contains("Shizuku off", ignoreCase = true)) {
            return@withContext emptyList()
        }

        val items = mutableListOf<SettingItem>()
        output.lineSequence().forEach { line ->
            val eqIdx = line.indexOf('=')
            if (eqIdx != -1) {
                val k = line.substring(0, eqIdx).trim()
                val v = line.substring(eqIdx + 1).trim()
                if (k.isNotEmpty()) {
                    items.add(SettingItem(namespace, k, v))
                }
            }
        }
        items.sortedBy { it.key.lowercase() }
    }

    // =========================================================================
    // PRESETS DE OTIMIZAÇÃO (Desempenho, Bateria e Interface)
    // =========================================================================

    /**
     * [PT-BR] Ajusta o tempo limite de suspensão da tela (em milissegundos).
     */
    fun setScreenTimeout(timeoutMs: Long): Pair<Boolean, String> {
        return putSetting(TABLE_SYSTEM, "screen_off_timeout", timeoutMs.toString())
    }

    /**
     * [PT-BR] Ajusta a taxa máxima de atualização (FPS de pico) e taxa mínima.
     */
    fun setRefreshRate(fps: Float): Pair<Boolean, String> {
        val res1 = putSetting(TABLE_SYSTEM, "peak_refresh_rate", fps.toString())
        val res2 = putSetting(TABLE_SYSTEM, "min_refresh_rate", fps.toString())
        return if (res1.first) {
            Pair(true, "Taxa de atualização ajustada para ${fps.toInt()}Hz.")
        } else {
            res1
        }
    }

    /**
     * [PT-BR] Ajusta as 3 escalas de animação do sistema de uma só vez (janela, transição, duração).
     */
    fun setAnimationScales(scale: Float): Pair<Boolean, String> {
        val str = scale.toString()
        putSetting(TABLE_GLOBAL, "window_animation_scale", str)
        putSetting(TABLE_GLOBAL, "transition_animation_scale", str)
        val res3 = putSetting(TABLE_GLOBAL, "animator_duration_scale", str)
        return if (res3.first) {
            Pair(true, "Escala de animações definida para ${scale}x.")
        } else {
            res3
        }
    }

    /**
     * [PT-BR] Ajusta o limite padrão de processos em segundo plano (0 = sem processos, -1 = padrão).
     */
    fun setBackgroundProcessLimit(limit: Int): Pair<Boolean, String> {
        return putSetting(TABLE_GLOBAL, "standard_background_process_limit", limit.toString())
    }

    /**
     * [PT-BR] Mantém a tela ligada enquanto carrega (útil para jogos pesados durante o carregamento).
     * 0 = desligado, 7 = AC + USB + Wireless
     */
    fun setStayAwakeWhileCharging(enabled: Boolean): Pair<Boolean, String> {
        val value = if (enabled) "7" else "0"
        return putSetting(TABLE_GLOBAL, "stay_on_while_plugged_in", value)
    }

    /**
     * [PT-BR] Política de suspensão de Wi-Fi para economia de bateria.
     * 2 = Nunca suspender, 0 = Suspender quando a tela desligar
     */
    fun setWifiSleepPolicy(neverSleep: Boolean): Pair<Boolean, String> {
        val value = if (neverSleep) "2" else "0"
        return putSetting(TABLE_GLOBAL, "wifi_sleep_policy", value)
    }

    /**
     * [PT-BR] Ajusta a sensibilidade de toque para modo jogo.
     */
    fun setGamingTouchOptimization(enabled: Boolean): Pair<Boolean, String> {
        val v = if (enabled) "1" else "0"
        putSetting(TABLE_SYSTEM, "touch_response_rate", v)
        putSetting(TABLE_SYSTEM, "gaming_touch_mode", v)
        return putSetting(TABLE_SYSTEM, "touch_sensitivity", v)
    }
}
