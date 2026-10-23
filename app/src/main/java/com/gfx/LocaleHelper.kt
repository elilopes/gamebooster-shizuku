package com.gfx

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

object LocaleHelper {
    private const val PREFS_NAME = "game_booster_prefs"
    private const val KEY_LANGUAGE = "selected_language"

    data class LanguageItem(
        val code: String,
        val displayName: String,
        val nativeName: String
    )

    val SUPPORTED_LANGUAGES = listOf(
        LanguageItem("en", "English", "English"),
        LanguageItem("pt", "Portuguese (Brazil)", "Português (BR)"),
        LanguageItem("ru", "Russian", "Русский"),
        LanguageItem("zh", "Chinese", "中文"),
        LanguageItem("ko", "Korean", "한국어"),
        LanguageItem("hi", "Hindi", "हिन्दी"),
        LanguageItem("fr", "French", "Français")
    )

    fun getSavedLanguage(context: Context): String {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANGUAGE, null) ?: Locale.getDefault().language
    }

    fun setLocale(context: Context, languageCode: String): Context {
        saveLanguage(context, languageCode)
        return applyLanguage(context, languageCode)
    }

    private fun saveLanguage(context: Context, languageCode: String) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANGUAGE, languageCode).apply()
    }

    fun applySavedLocale(context: Context): Context {
        val lang = getSavedLanguage(context)
        return applyLanguage(context, lang)
    }

    private fun applyLanguage(context: Context, languageCode: String): Context {
        val targetLocale = when (languageCode.lowercase()) {
            "pt" -> Locale("pt", "BR")
            "zh" -> Locale.SIMPLIFIED_CHINESE
            "ko" -> Locale.KOREAN
            "ru" -> Locale("ru")
            "hi" -> Locale("hi", "IN")
            "fr" -> Locale.FRENCH
            else -> Locale.ENGLISH
        }
        Locale.setDefault(targetLocale)

        val resources = context.resources
        val configuration = Configuration(resources.configuration)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val localeList = LocaleList(targetLocale)
            LocaleList.setDefault(localeList)
            configuration.setLocales(localeList)
        } else {
            @Suppress("DEPRECATION")
            configuration.locale = targetLocale
        }

        @Suppress("DEPRECATION")
        resources.updateConfiguration(configuration, resources.displayMetrics)

        return context.createConfigurationContext(configuration)
    }
}
