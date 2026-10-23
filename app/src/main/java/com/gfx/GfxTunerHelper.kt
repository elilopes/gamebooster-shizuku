package com.gfx

import android.content.Context

typealias GfxConfig = GfxTools.GfxConfig
typealias EngineType = GfxTools.EngineType

/**
 * [PT-BR] GfxTunerHelper: Fachada de compatibilidade que delega as operações para o GfxTools.
 */
object GfxTunerHelper {
    fun detectEngine(context: Context, packageName: String): GfxTools.EngineType =
        GfxTools.detectEngine(context, packageName)

    fun getGfxConfig(context: Context, packageName: String): GfxTools.GfxConfig =
        GfxTools.getGfxConfig(context, packageName)

    fun saveGfxConfig(context: Context, packageName: String, config: GfxTools.GfxConfig) =
        GfxTools.saveGfxConfig(context, packageName, config)

    fun resetGfxConfig(context: Context, packageName: String): String =
        GfxTools.resetGfxConfig(context, packageName)

    fun applyGfxOptimizations(context: Context, packageName: String, config: GfxTools.GfxConfig): String =
        GfxTools.applyGfxOptimizations(context, packageName, config)
}
