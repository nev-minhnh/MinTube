package com.hhst.youtubelite.core.format

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import java.util.Locale

/**
 * View counts in one short form everywhere ("188K views" / "188 N lượt xem"), whether YouTube sent
 * "188,491 views", "188K views" or "188 N lượt xem". Text that is not a view count ("75K watching",
 * "LIVE") is left as it came.
 */
object ViewCounts {
    private val viewWords = Regex("""(?i)\b(views?|view)\b|lượt xem""")
    private val number = Regex("""(\d[\d.,\s ]*)\s*(k|m|b|n|tr|tỷ|nghìn|ngàn|triệu)?(?![\p{L}])""", RegexOption.IGNORE_CASE)
    private val noViews = Regex("""(?i)^\s*(no views|chưa có lượt xem)\s*$""")

    /** The count behind [raw], or null when [raw] is not a view count. */
    fun parse(raw: String?): Long? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        if (noViews.matches(text)) return 0
        val bare = text.all { it.isDigit() || it in ".,   " }
        if (!bare && !viewWords.containsMatchIn(text)) return null
        val match = number.find(text) ?: return null
        val digits = match.groupValues[1].trim()
        val multiplier = when (match.groupValues[2].lowercase(Locale.ROOT)) {
            "k", "n", "nghìn", "ngàn" -> 1_000L
            "m", "tr", "triệu" -> 1_000_000L
            "b", "tỷ" -> 1_000_000_000L
            else -> 1L
        }
        val value = if (multiplier == 1L) {
            digits.filter(Char::isDigit).toLongOrNull() ?: return null
        } else {
            // "1.2M" / "1,2 Tr": the separator is a decimal point when a suffix follows.
            val decimal = digits.replace(Regex("""[\s ]"""), "").replace(',', '.')
            ((decimal.toDoubleOrNull() ?: return null) * multiplier).toLong()
        }
        return value
    }

    /**
     * "188K" / "188 N" in [locale]; below 1,000 the plain number. English and Vietnamese are spelled out
     * here (as the watch page reads them, one decimal under 10: "1.2M", "1,2 Tr"); other languages use ICU.
     */
    fun compact(count: Long, locale: Locale): String {
        val vi = locale.language == "vi"
        if (!vi && locale.language != "en") runCatching {
            android.icu.text.CompactDecimalFormat.getInstance(locale, android.icu.text.CompactDecimalFormat.CompactStyle.SHORT)
                ?.format(count)
        }.getOrNull()?.let { return it }
        if (count < 1_000) return count.toString()
        val (unit, suffix) = when {
            count >= 1_000_000_000 -> 1_000_000_000L to if (vi) " Tỷ" else "B"
            count >= 1_000_000 -> 1_000_000L to if (vi) " Tr" else "M"
            else -> 1_000L to if (vi) " N" else "K"
        }
        val value = count.toDouble() / unit
        val text = if (value < 10) {
            val tenths = (value * 10).toLong() // truncate like YouTube: 1.29M reads 1.2M
            if (tenths % 10 == 0L) (tenths / 10).toString() else "${tenths / 10}${if (vi) ',' else '.'}${tenths % 10}"
        } else value.toLong().toString()
        return text + suffix
    }
}

/** The short, localized view count for [raw]; falls back to [raw] when it is not a view count. */
@Composable
fun viewsText(raw: String?): String {
    val count = remember(raw) { ViewCounts.parse(raw) } ?: return raw.orEmpty()
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val short = remember(count, locale) { ViewCounts.compact(count, locale) }
    return pluralStringResource(com.hhst.youtubelite.R.plurals.view_count_short, count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), short)
}
