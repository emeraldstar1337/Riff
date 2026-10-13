package dev.lad.i18n

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object AppLanguage {
    private val mutable = MutableStateFlow("system")
    val selected = mutable.asStateFlow()

    fun current(context: Context): String =
        if (Build.VERSION.SDK_INT >= 33) {
            context
                .getSystemService(LocaleManager::class.java)
                .applicationLocales
                .toLanguageTags()
                .substringBefore(',')
                .substringBefore('-')
                .takeIf { it == "ru" || it == "en" } ?: "system"
        } else
            context
                .getSharedPreferences("language", Context.MODE_PRIVATE)
                .getString("selected", "system") ?: "system"

    fun sync(context: Context) {
        mutable.value = current(context)
    }

    fun set(context: Context, language: String) {
        require(language in setOf("system", "ru", "en"))
        context
            .getSharedPreferences("language", Context.MODE_PRIVATE)
            .edit()
            .putString("selected", language)
            .apply()
        mutable.value = language
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales =
                if (language == "system") LocaleList.getEmptyLocaleList()
                else LocaleList.forLanguageTags(language)
        }
    }

    fun context(context: Context, language: String = current(context)): Context {
        val configuration = Configuration(context.resources.configuration)
        if (language == "system")
            configuration.setLocales(Resources.getSystem().configuration.locales)
        else configuration.setLocale(Locale.forLanguageTag(language))
        return context.createConfigurationContext(configuration)
    }
}
