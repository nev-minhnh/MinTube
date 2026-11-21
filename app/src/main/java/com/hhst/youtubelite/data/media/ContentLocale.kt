package com.hhst.youtubelite.data.media

import java.util.Locale

/**
 * The `hl` (language) and `gl` (region) every InnerTube request carries. YouTube returns
 * titles in `hl`: a creator's translated title or YouTube's machine translation when the
 * video has one for that language, otherwise the original. `hl` also localizes server text
 * such as view counts and relative dates, and `gl` selects regional trending shelves.
 */
data class ContentLocale(val language: String, val region: String, val originalText: Boolean = false) {
    val key: String get() = "$language-$region"

    /** An owner key for cached rows: rows fetched for another locale are never painted. */
    fun scope(owner: String): String = "$owner@$key"

    companion object {
        const val ENGLISH = "en"
        const val VIETNAM = "VN"
        const val FALLBACK_REGION = "US"

        /**
         * [englishContent] keeps today's English requests; otherwise the app language
         * ([appLanguageTag], empty for "follow the system") or [system] picks `hl`, falling
         * back to English when YouTube has no such interface language, and the watch page
         * prefers the creator's own title and description. [vietnamRegion] pins
         * `gl=VN`; otherwise the system country decides, falling back to US.
         */
        fun resolve(englishContent: Boolean, vietnamRegion: Boolean, appLanguageTag: String, system: Locale): ContentLocale {
            val language = if (englishContent) ENGLISH
            else youtubeLanguage(appLanguageTag.takeIf(String::isNotBlank)?.let(Locale::forLanguageTag) ?: system)
            val region = if (vietnamRegion) VIETNAM else youtubeRegion(system)
            return ContentLocale(language, region, originalText = !englishContent)
        }

        /** Maps a locale to one of YouTube's interface languages, or English. */
        fun youtubeLanguage(locale: Locale): String {
            val language = when (locale.language) {
                "iw", "he" -> "iw"
                "in", "id" -> "id"
                "nb", "nn", "no" -> "no"
                "tl", "fil" -> "fil"
                else -> locale.language
            }
            val script = locale.script
            val country = locale.country
            return when (language) {
                "zh" -> when {
                    script == "Hant" || country in setOf("TW", "MO") -> "zh-TW"
                    country == "HK" -> "zh-HK"
                    else -> "zh-CN"
                }
                "pt" -> if (country == "PT") "pt-PT" else "pt"
                "sr" -> if (script == "Latn") "sr-Latn" else "sr"
                else -> language.takeIf { it in LANGUAGES } ?: ENGLISH
            }
        }

        fun youtubeRegion(locale: Locale): String =
            locale.country.takeIf { it.length == 2 && it.all { c -> c in 'A'..'Z' } } ?: FALLBACK_REGION

        /** YouTube interface languages whose code equals the locale's ISO 639 language. */
        private val LANGUAGES = setOf(
            "af", "am", "ar", "as", "az", "be", "bg", "bn", "bs", "ca", "cs", "da", "de", "el", "en", "es",
            "et", "eu", "fa", "fi", "fil", "fr", "gl", "gu", "hi", "hr", "hu", "hy", "id", "is", "it", "iw",
            "ja", "ka", "kk", "km", "kn", "ko", "ky", "lo", "lt", "lv", "mk", "ml", "mn", "mr", "ms", "my",
            "ne", "nl", "no", "or", "pa", "pl", "pt", "ro", "ru", "si", "sk", "sl", "sq", "sr", "sv", "sw",
            "ta", "te", "th", "tr", "uk", "ur", "uz", "vi", "zu",
        )
    }
}
