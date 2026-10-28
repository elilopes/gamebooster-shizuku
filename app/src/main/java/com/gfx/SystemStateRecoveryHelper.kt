package com.gfx

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * [PT-BR] Helper responsável pela gravação persistente das configurações alteradas do sistema
 * (Resolução wm size, Densidade wm density, Escalas de animação, DNS Privado, Modo DND e Não Perturbe).
 * Garante que se o jogo ou aplicativo for finalizado abruptamente ou o aparelho reiniciar,
 * o estado original possa ser 100% restaurado na próxima inicialização do app ou via Botão de Pânico.
 *
 * [EN] Helper responsible for persistent tracking of system alterations
 * (wm size, wm density, animation scales, private DNS, and DND mode).
 * Ensures full recovery on app launch or via the Panic Button.
 */
object SystemStateRecoveryHelper {

    private const val PREFS_NAME = "system_recovery_prefs"
    private const val KEY_IS_ALTERED = "is_system_altered"
    private const val KEY_ORIG_WM_SIZE = "orig_wm_size"
    private const val KEY_ORIG_WM_DENSITY = "orig_wm_density"
    private const val KEY_ORIG_WINDOW_SCALE = "orig_window_scale"
    private const val KEY_ORIG_TRANSITION_SCALE = "orig_transition_scale"
    private const val KEY_ORIG_ANIMATOR_SCALE = "orig_animator_scale"
    private const val KEY_ORIG_PRIVATE_DNS_MODE = "orig_private_dns_mode"
    private const val KEY_ORIG_PRIVATE_DNS_SPECIFIER = "orig_private_dns_specifier"
    private const val KEY_ORIG_ZEN_MODE = "orig_zen_mode"
    private const val KEY_ORIG_HEADS_UP = "orig_heads_up"
    private const val KEY_ACTIVE_GAME_PKG = "active_game_package"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * [PT-BR] Grava o estado original do sistema antes de aplicar qualquer alteração.
     */
    fun recordOriginalState(context: Context, gamePackage: String? = null) {
        val prefs = getPrefs(context)
        if (prefs.getBoolean(KEY_IS_ALTERED, false)) {
            // Se já foi alterado, preserva o estado original registrado na primeira alteração
            return
        }

        try {
            val editor = prefs.edit()
            editor.putBoolean(KEY_IS_ALTERED, true)
            if (!gamePackage.isNullOrBlank()) {
                editor.putString(KEY_ACTIVE_GAME_PKG, gamePackage)
            }

            // Lê valores atuais do sistema para backup seguro
            val curWinScale = ShellFunctions.executePrivileged("settings get global window_animation_scale").trim()
            val curTransScale = ShellFunctions.executePrivileged("settings get global transition_animation_scale").trim()
            val curAnimScale = ShellFunctions.executePrivileged("settings get global animator_duration_scale").trim()
            val curDnsMode = ShellFunctions.executePrivileged("settings get global private_dns_mode").trim()
            val curDnsSpec = ShellFunctions.executePrivileged("settings get global private_dns_specifier").trim()
            val curZenMode = ShellFunctions.executePrivileged("settings get global zen_mode").trim()
            val curHeadsUp = ShellFunctions.executePrivileged("settings get global heads_up_notifications_enabled").trim()

            if (curWinScale.isNotEmpty() && !curWinScale.startsWith("Error")) editor.putString(KEY_ORIG_WINDOW_SCALE, curWinScale)
            if (curTransScale.isNotEmpty() && !curTransScale.startsWith("Error")) editor.putString(KEY_ORIG_TRANSITION_SCALE, curTransScale)
            if (curAnimScale.isNotEmpty() && !curAnimScale.startsWith("Error")) editor.putString(KEY_ORIG_ANIMATOR_SCALE, curAnimScale)
            if (curDnsMode.isNotEmpty() && !curDnsMode.startsWith("Error")) editor.putString(KEY_ORIG_PRIVATE_DNS_MODE, curDnsMode)
            if (curDnsSpec.isNotEmpty() && !curDnsSpec.startsWith("Error")) editor.putString(KEY_ORIG_PRIVATE_DNS_SPECIFIER, curDnsSpec)
            if (curZenMode.isNotEmpty() && !curZenMode.startsWith("Error")) editor.putString(KEY_ORIG_ZEN_MODE, curZenMode)
            if (curHeadsUp.isNotEmpty() && !curHeadsUp.startsWith("Error")) editor.putString(KEY_ORIG_HEADS_UP, curHeadsUp)

            editor.apply()
            Log.d("SystemRecovery", "Estado original do sistema salvo para recuperação segura.")
        } catch (e: Exception) {
            Log.e("SystemRecovery", "Erro ao gravar estado original: ${e.message}")
        }
    }

    /**
     * [PT-BR] Retorna true se houver alterações de sistema pendentes de restauração.
     */
    fun isSystemAltered(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_IS_ALTERED, false)
    }

    /**
     * [PT-BR] Executa a restauração completa de todas as configurações do sistema para os valores padrão.
     * Chamado pelo Botão de Pânico na barra de notificação, no encerramento de jogos ou na inicialização do app.
     */
    fun restoreSystemState(context: Context): String {
        val prefs = getPrefs(context)
        val details = mutableListOf<String>()

        try {
            // 1. Encerra o jogo ativo se estiver rodando
            val activePkg = prefs.getString(KEY_ACTIVE_GAME_PKG, null) ?: GameForegroundService.activeGamePackage
            if (!activePkg.isNullOrBlank()) {
                ShellFunctions.executePrivileged("am force-stop $activePkg")
                details.add("Jogo encerrado: $activePkg")
            }

            // 2. Restaura resolução e densidade da tela (wm size / wm density)
            ShellFunctions.executePrivileged("wm size reset")
            ShellFunctions.executePrivileged("wm density reset")
            details.add("Resolução e Densidade restauradas (wm reset)")

            // 3. Restaura escalas de animação do Android
            val origWin = prefs.getString(KEY_ORIG_WINDOW_SCALE, "1.0") ?: "1.0"
            val origTrans = prefs.getString(KEY_ORIG_TRANSITION_SCALE, "1.0") ?: "1.0"
            val origAnim = prefs.getString(KEY_ORIG_ANIMATOR_SCALE, "1.0") ?: "1.0"
            ShellFunctions.executePrivileged("settings put global window_animation_scale $origWin")
            ShellFunctions.executePrivileged("settings put global transition_animation_scale $origTrans")
            ShellFunctions.executePrivileged("settings put global animator_duration_scale $origAnim")
            details.add("Escalas de animação restauradas para $origAnim")

            // 4. Restaura DNS Privado
            val origDnsMode = prefs.getString(KEY_ORIG_PRIVATE_DNS_MODE, "off") ?: "off"
            val origDnsSpec = prefs.getString(KEY_ORIG_PRIVATE_DNS_SPECIFIER, "") ?: ""
            ShellFunctions.executePrivileged("settings put global private_dns_mode $origDnsMode")
            if (origDnsSpec.isNotBlank()) {
                ShellFunctions.executePrivileged("settings put global private_dns_specifier $origDnsSpec")
            } else {
                ShellFunctions.executePrivileged("settings delete global private_dns_specifier")
            }
            details.add("DNS Privado restaurado")

            // 5. Restaura Não Perturbe (DND) e notificações flutuantes (Heads-up)
            val origZen = prefs.getString(KEY_ORIG_ZEN_MODE, "0") ?: "0"
            val origHeadsUp = prefs.getString(KEY_ORIG_HEADS_UP, "1") ?: "1"
            ShellFunctions.executePrivileged("settings put global zen_mode $origZen")
            ShellFunctions.executePrivileged("settings put global heads_up_notifications_enabled $origHeadsUp")
            ShellFunctions.executePrivileged("cmd notification set_interruption_filter 1")
            details.add("Notificações e DND restaurados")

            // 6. Garante que o modo Doze forçado (force-idle) esteja liberado para jogos e rede
            ShellFunctions.executePrivileged("cmd deviceidle unforce")
            details.add("Modo Doze liberado (unforce)")

            // 7. Limpa a flag de alteração do sistema
            prefs.edit().clear().apply()
            GameForegroundService.activeGamePackage = null

            Log.i("SystemRecovery", "Restauração de pânico do sistema concluída: ${details.joinToString(", ")}")
            return "Sistema restaurado com sucesso! (${details.size} itens revertidos)"
        } catch (e: Exception) {
            Log.e("SystemRecovery", "Falha durante restauração de pânico: ${e.message}", e)
            return "Erro ao restaurar: ${e.message}"
        }
    }
}
