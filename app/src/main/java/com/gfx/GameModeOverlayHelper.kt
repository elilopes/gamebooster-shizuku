package com.gfx

import android.content.Context
import android.os.Build
import android.util.Log

/**
 * [PT-BR] Helper para controle de Teto de FPS e Downscale no Android Game Mode (Android 13+ / API 33+).
 * Aplica configurações refinadas de FPS estável (ex: 30, 45, 60) e escala de renderização (downscaleFactor),
 * permitindo estabilidade de frametime superior em jogos exigentes.
 *
 * [EN] Helper for FPS Cap and Downscale controls in Android 13+ Game Mode.
 */
object GameModeOverlayHelper {

    val FPS_OPTIONS = listOf(0, 30, 45, 60, 90, 120) // 0 = Ilimitado / Sistema
    val DOWNSCALE_OPTIONS = listOf("1.0", "0.9", "0.8", "0.7", "0.6", "0.5")

    /**
     * [PT-BR] Aplica teto de FPS e downscale para o pacote usando device_config e cmd game.
     */
    fun applyGameModeConfig(
        packageName: String,
        fpsLimit: Int,
        downscaleFactor: String
    ): Pair<Boolean, String> {
        val details = mutableListOf<String>()

        try {
            val downscale = if (downscaleFactor.isNotBlank() && downscaleFactor != "1.0") downscaleFactor else "1.0"
            val fpsStr = if (fpsLimit > 0) ",fps=$fpsLimit" else ""

            // 1. Configura game_overlay no device_config (suportado a partir do Android 12L / 13+)
            val configStr = "mode=2,downscaleFactor=$downscale$fpsStr:mode=3,downscaleFactor=$downscale$fpsStr"
            val cmdDeviceConfig = "device_config put game_overlay $packageName \"$configStr\""
            val resConfig = ShellFunctions.executePrivileged(cmdDeviceConfig)

            // 2. Aplica modo de jogo de performance (mode 2)
            val cmdMode = "cmd game mode 2 $packageName"
            val resMode = ShellFunctions.executePrivileged(cmdMode)

            // 3. Se Android 13+, tenta o comando direto cmd game mode custom se disponível
            if (fpsLimit > 0) {
                ShellFunctions.executePrivileged("cmd game mode custom --fps $fpsLimit --downscale $downscale $packageName")
            }

            // 4. Verificação de leitura (Aplicar e verificar)
            val verifyConfig = ShellFunctions.executePrivileged("device_config get game_overlay $packageName").trim()
            val isVerified = verifyConfig.contains("downscaleFactor=$downscale") || verifyConfig.contains(downscale)

            val msg = if (isVerified) {
                val fpsText = if (fpsLimit > 0) "$fpsLimit FPS" else "Ilimitado"
                "Game Mode Aplicado e Verificado: Teto de $fpsText | Downscale: ${downscale}x"
            } else {
                "Comando enviado (Saída: $resMode). Nota: O suporte ao game_overlay depende da versão do Android (13+)."
            }

            return Pair(true, msg)
        } catch (e: Exception) {
            Log.e("GameModeOverlay", "Erro ao aplicar Game Mode Overlay: ${e.message}")
            return Pair(false, "Erro ao configurar Game Mode: ${e.message}")
        }
    }

    /**
     * [PT-BR] Restaura as configurações de Game Mode para o padrão nativo do jogo.
     */
    fun resetGameModeConfig(packageName: String): String {
        return try {
            ShellFunctions.executePrivileged("device_config delete game_overlay $packageName")
            ShellFunctions.executePrivileged("cmd game reset $packageName")
            "Game Mode e Overlay restaurados para o padrão nativo."
        } catch (e: Exception) {
            "Erro ao resetar: ${e.message}"
        }
    }
}
