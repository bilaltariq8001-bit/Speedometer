package com.speedometer.app.core.data

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.runtime.mutableIntStateOf
import java.util.Locale

/**
 * The app's language, applied by wrapping the activity's context in the chosen locale. The choice is also kept in
 * SharedPreferences, because the context is wrapped before DataStore can be read.
 *
 * Changing it while the app runs ([applyNow]) updates the activity's resources in place and bumps [revision], which the
 * root composable turns into a new configuration: every string redraws in the new language at once, with no activity
 * recreate, no white flash and no lost screen state.
 */
object AppLanguage {
    private const val PREFS = "app_language"
    private const val KEY = "name"
    const val DEFAULT = "English"

    /** Language name as saved in settings, to its BCP 47 tag. Each tag has a matching values-XX resource folder. */
    val tags: Map<String, String> = linkedMapOf(
        "English" to "en",
        "Chinese (Simplified)" to "zh-CN",
        "Hindi" to "hi",
        "Spanish" to "es",
        "Arabic" to "ar",
        "French" to "fr",
        "Bengali" to "bn",
        "Portuguese" to "pt",
        "Russian" to "ru",
        "Urdu" to "ur",
        "Indonesian" to "id",
        "German" to "de",
        "Japanese" to "ja",
        "Marathi" to "mr",
        "Telugu" to "te",
        "Turkish" to "tr",
        "Tamil" to "ta",
        "Vietnamese" to "vi",
        "Korean" to "ko",
        "Italian" to "it",
        "Persian" to "fa",
        "Thai" to "th",
        "Gujarati" to "gu",
        "Punjabi" to "pa",
        "Polish" to "pl",
        "Ukrainian" to "uk",
        "Malay" to "ms",
        "Dutch" to "nl",
        "Swahili" to "sw",
        "Greek" to "el",
    )

    /** Each language written in itself. */
    val native: Map<String, String> = linkedMapOf(
        "English" to "English",
        "Chinese (Simplified)" to "简体中文",
        "Hindi" to "हिन्दी",
        "Spanish" to "Español",
        "Arabic" to "العربية",
        "French" to "Français",
        "Bengali" to "বাংলা",
        "Portuguese" to "Português",
        "Russian" to "Русский",
        "Urdu" to "اردو",
        "Indonesian" to "Bahasa Indonesia",
        "German" to "Deutsch",
        "Japanese" to "日本語",
        "Marathi" to "मराठी",
        "Telugu" to "తెలుగు",
        "Turkish" to "Türkçe",
        "Tamil" to "தமிழ்",
        "Vietnamese" to "Tiếng Việt",
        "Korean" to "한국어",
        "Italian" to "Italiano",
        "Persian" to "فارسی",
        "Thai" to "ไทย",
        "Gujarati" to "ગુજરાતી",
        "Punjabi" to "ਪੰਜਾਬੀ",
        "Polish" to "Polski",
        "Ukrainian" to "Українська",
        "Malay" to "Bahasa Melayu",
        "Dutch" to "Nederlands",
        "Swahili" to "Kiswahili",
        "Greek" to "Ελληνικά",
    )

    /** Bumped on every live language change; the root composable reads it to redraw in the new language. */
    val revision = mutableIntStateOf(0)

    /** Writes the choice now (not in the background), so a recreated activity already sees it. */
    fun save(context: Context, name: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, name).commit()
    }

    fun saved(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)?.takeIf { it in tags } ?: DEFAULT

    /** True once the user has picked a language (first run has not). */
    fun hasChoice(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(KEY)

    /** The supported language matching the phone's own language, used to preselect one on first run. */
    fun deviceDefault(): String? {
        val device = Resources.getSystem().configuration.locales[0] ?: return null
        val lang = normalize(device.language)
        return tags.entries.firstOrNull { normalize(Locale.forLanguageTag(it.value).language) == lang }?.key
    }

    /** Short code shown beside a language (ZH, PT, ...). */
    fun shortCode(name: String): String = (tags[name] ?: "en").substringBefore('-').uppercase()

    fun locale(name: String): Locale = Locale.forLanguageTag(tags[name] ?: "en")

    /** [base] with the saved language applied. */
    fun wrap(base: Context): Context {
        val locale = locale(saved(base))
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }

    /**
     * Saves [name] and switches the running [activity] to it in place. Returns false when the in-place switch could not
     * be made, in which case the caller recreates the activity instead.
     */
    @Suppress("DEPRECATION")
    fun applyNow(activity: Activity, name: String): Boolean {
        save(activity, name)
        val locale = locale(name)
        Locale.setDefault(locale)
        return runCatching {
            listOf(activity.resources, activity.applicationContext.resources).distinct().forEach { res ->
                val config = Configuration(res.configuration)
                config.setLocale(locale)
                config.setLayoutDirection(locale)
                res.updateConfiguration(config, res.displayMetrics)
            }
            revision.intValue++
        }.isSuccess
    }

    /** Indonesian is "in" on older Android and "id" on newer; Hebrew and Yiddish have the same split. */
    private fun normalize(lang: String) = when (lang) { "in" -> "id"; "iw" -> "he"; "ji" -> "yi"; else -> lang }
}
