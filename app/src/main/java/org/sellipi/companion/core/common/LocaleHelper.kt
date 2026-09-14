package org.sellipi.companion.core.common

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

enum class AppLanguage(val code: String, val displayName: String) {
    SINHALA("si", "සිංහල"),
    TAMIL("ta", "தமிழ்"),
    ENGLISH("en", "English")
}

object LocaleHelper {
    fun updateLocale(context: Context, language: AppLanguage): Context {
        val locale = Locale(language.code)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}
