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
    CUSTOM("Personalizado", "📝", 0xFF607D8B);

    fun getLocalizedName(context: android.content.Context): String {
        val resId = when (this) {
            PERFORMANCE -> R.string.category_performance
            GRAPHICS -> R.string.category_graphics
            MEMORY -> R.string.category_memory
            SYSTEM -> R.string.category_system
            NETWORK -> R.string.category_network
            CUSTOM -> R.string.category_custom
        }
        return try {
            context.getString(resId)
        } catch (_: Exception) {
            displayName
        }
    }
}

fun CustomScript.getLocalizedTitle(context: android.content.Context): String {
    val resId = when (id) {
        "preset_120hz" -> R.string.preset_120hz_title
        "preset_ram_clean" -> R.string.preset_ram_clean_title
        "preset_skia_vk" -> R.string.preset_skia_vk_title
        "preset_reduce_logger" -> R.string.preset_reduce_logger_title
        "preset_disable_spooler" -> R.string.preset_disable_spooler_title
        "preset_fixed_performance" -> R.string.preset_fixed_performance_title
        "preset_network_tweaks" -> R.string.preset_network_tweaks_title
        "preset_webview_gpu" -> R.string.preset_webview_gpu_title
        "preset_force_msaa" -> R.string.preset_force_msaa_title
        else -> null
    }
    return if (resId != null) {
        try { context.getString(resId) } catch (_: Exception) { title }
    } else {
        title
    }
}

fun CustomScript.getLocalizedDescription(context: android.content.Context): String {
    val resId = when (id) {
        "preset_120hz" -> R.string.preset_120hz_desc
        "preset_ram_clean" -> R.string.preset_ram_clean_desc
        "preset_skia_vk" -> R.string.preset_skia_vk_desc
        "preset_reduce_logger" -> R.string.preset_reduce_logger_desc
        "preset_disable_spooler" -> R.string.preset_disable_spooler_desc
        "preset_fixed_performance" -> R.string.preset_fixed_performance_desc
        "preset_network_tweaks" -> R.string.preset_network_tweaks_desc
        "preset_webview_gpu" -> R.string.preset_webview_gpu_desc
        "preset_force_msaa" -> R.string.preset_force_msaa_desc
        else -> null
    }
    return if (resId != null) {
        try { context.getString(resId) } catch (_: Exception) { description }
    } else {
        description
    }
}
