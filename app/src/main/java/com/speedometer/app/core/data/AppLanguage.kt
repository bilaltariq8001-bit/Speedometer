package com.speedometer.app.core.data

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * The app's language, applied by wrapping the activity's context in the chosen locale. The choice is also kept in
 * SharedPreferences, because the context is wrapped before DataStore can be read.
 */
object AppLanguage {
    private const val PREFS = "app_language"
    private const val KEY = "name"

    /** Language name as saved in settings, to its BCP 47 tag. */
    val tags: Map<String, String> = linkedMapOf(
        "English" to "en", "Urdu" to "ur", "Arabic" to "ar", "Hindi" to "hi", "Spanish" to "es", "French" to "fr",
    )

    /** Each language written in itself. */
    val native: Map<String, String> = linkedMapOf(
        "English" to "English", "Urdu" to "اردو", "Arabic" to "العربية",
        "Hindi" to "हिन्दी", "Spanish" to "Español", "French" to "Français",
    )

    /** Writes the choice now (not in the background), so a recreated activity already sees it. */
    fun save(context: Context, name: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, name).commit()
    }

    fun saved(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: "English"

    /** [base] with the saved language applied, or [base] unchanged for English. */
    fun wrap(base: Context): Context {
        val tag = tags[saved(base)] ?: return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }
}
