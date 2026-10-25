package com.gfx

import java.util.UUID

/**
 * [PT-BR] Modelo para representar funções e scripts personalizados criados ou importados pelo usuário na aba .sh Scripts.
 * Permite estender o aplicativo adicionando novas automações, rotinas de otimização e ajustes avançados do Android via shell.
 *
 * [EN] Model representing custom functions and scripts created or imported by the user in the .sh Scripts tab.
 */
data class CustomScript(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String,
    val scriptContent: String,
    val category: ScriptCategory = ScriptCategory.PERFORMANCE,
    val runAsPrivileged: Boolean = true,
    val runOnAppStart: Boolean = false,
    val isPreset: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

enum class ScriptCategory(
    val displayName: String,
    val iconEmoji: String,
    val colorHex: Long
) {
    PERFORMANCE("Desempenho", "⚡", 0xFFFF9800),
    GRAPHICS("Gráficos & FPS", "🎮", 0xFF2196F3),
    MEMORY("Memória & RAM", "🧹", 0xFF4CAF50),
    SYSTEM("Sistema & Kernel", "⚙️", 0xFF9C27B0),
    NETWORK("Rede & Ping", "📶", 0xFF00BCD4),
    CUSTOM("Personalizado", "📝", 0xFF607D8B)
}
