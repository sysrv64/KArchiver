package com.kerneldroid.karchiver.data

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.core.content.edit
import java.util.Locale

enum class AppLanguage(val id: String, val tag: String?) {
    SYSTEM("system", null),
    ENGLISH("en", "en"),
    RUSSIAN("ru", "ru"),
    CHINESE("zh", "zh-CN");

    val locale: Locale? get() = tag?.let(Locale::forLanguageTag)

    companion object {
        fun fromId(id: String?): AppLanguage = entries.firstOrNull { it.id == id } ?: SYSTEM

        fun fromTag(tag: String?): AppLanguage {
            val language = tag?.let(Locale::forLanguageTag)?.language ?: return SYSTEM
            return entries.firstOrNull { it.locale?.language == language } ?: SYSTEM
        }
    }
}

object LocaleStore {

    private const val PREFS = "karchiver_locale"
    private const val KEY_LANGUAGE = "app_language"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun selected(context: Context): AppLanguage {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales
            val first = locales?.takeUnless { it.isEmpty }?.get(0)
            if (first != null) return AppLanguage.fromTag(first.toLanguageTag())
        }
        return AppLanguage.fromId(prefs(context).getString(KEY_LANGUAGE, null))
    }

    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val locale = AppLanguage.fromId(prefs(base).getString(KEY_LANGUAGE, null)).locale ?: return base
        val config = Configuration(base.resources.configuration).apply {
            setLocale(locale)
            setLocales(LocaleList(locale))
        }
        return base.createConfigurationContext(config)
    }

    fun migrateToSystem(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val manager = context.getSystemService(LocaleManager::class.java) ?: return
        if (!manager.applicationLocales.isEmpty) return
        val stored = AppLanguage.fromId(prefs(context).getString(KEY_LANGUAGE, null))
        if (stored.locale != null) manager.applicationLocales = LocaleList(stored.locale!!)
    }

    fun applyLanguage(activity: Activity, language: AppLanguage) {
        prefs(activity).edit { putString(KEY_LANGUAGE, language.id) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val manager = activity.getSystemService(LocaleManager::class.java)
            manager?.applicationLocales =
                language.locale?.let { LocaleList(it) } ?: LocaleList.getEmptyLocaleList()
        } else {
            activity.recreate()
        }
    }
}

fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
