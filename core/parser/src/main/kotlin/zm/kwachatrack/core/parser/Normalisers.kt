package zm.kwachatrack.core.parser

import zm.kwachatrack.core.domain.model.LUSAKA
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoField
import java.util.Locale

/** Pure Fabrication: turns "K1,250.50", "ZMW 1250.5" or "1 250" into ngwee without using Double. */
object AmountNormaliser {
    private val CLEAN = Regex("^(?:ZMW|ZMK|K)?\\s*([0-9][0-9, ]*)(?:\\.([0-9]{1,2}))?$", RegexOption.IGNORE_CASE)

    fun toNgwee(text: String?): Long? {
        if (text == null) return null
        val trimmed = text.trim().trimEnd('.')
        val m = CLEAN.matchEntire(trimmed) ?: return null
        val whole = m.groupValues[1].replace(",", "").replace(" ", "")
        if (whole.isEmpty() || whole.length > 13) return null
        val fraction = m.groupValues[2].padEnd(2, '0')
        return whole.toLong() * 100 + (if (fraction.isEmpty()) 0 else fraction.toLong())
    }
}

/** Parses SMS dates as Zambian local time. Several patterns can be given, separated by "|". */
object DateNormaliser {
    private val cache = mutableMapOf<String, DateTimeFormatter>()

    fun toEpochMillis(
        text: String?,
        patterns: String?,
    ): Long? {
        if (text.isNullOrBlank() || patterns.isNullOrBlank()) return null
        val cleaned = text.trim().replace(Regex("\\s+"), " ")
        for (pattern in patterns.split('|').map { it.trim() }.filter { it.isNotEmpty() }) {
            val formatter = synchronized(cache) { cache.getOrPut(pattern) { build(pattern) } }
            try {
                val dateTime =
                    if (pattern.contains('H') || pattern.contains('h')) {
                        LocalDateTime.parse(cleaned, formatter)
                    } else {
                        LocalDate.parse(cleaned, formatter).atStartOfDay()
                    }
                return dateTime.atZone(LUSAKA).toInstant().toEpochMilli()
            } catch (_: DateTimeParseException) {
                // try the next pattern
            }
        }
        return null
    }

    private fun build(pattern: String): DateTimeFormatter =
        DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern(pattern)
            .parseDefaulting(ChronoField.ERA, 1)
            .toFormatter(Locale.ENGLISH)
}

object NameNormaliser {
    /** Trims, collapses spaces and strips trailing punctuation the SMS may add. */
    fun clean(text: String?): String? =
        text
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.trimEnd('.', ',', ';')
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
}
