package zm.kwachatrack.core.domain.money

/** Formats ngwee as Zambian kwacha, e.g. 125000 -> "K1,250.00". Never uses floating point. */
object Kwacha {
    fun format(ngwee: Long): String {
        val negative = ngwee < 0
        val abs = if (ngwee == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(ngwee)
        val kwacha = abs / 100
        val cents = abs % 100
        val grouped =
            kwacha
                .toString()
                .reversed()
                .chunked(3)
                .joinToString(",")
                .reversed()
        val text = "K$grouped.${cents.toString().padStart(2, '0')}"
        return if (negative) "-$text" else text
    }

    /** Short form for chart labels: K950, K1.2k, K3.4m. */
    fun formatCompact(ngwee: Long): String {
        val kwacha = ngwee / 100
        val abs = kotlin.math.abs(kwacha)
        val sign = if (kwacha < 0) "-" else ""
        return when {
            abs >= 1_000_000 -> "${sign}K${oneDecimal(abs, 1_000_000)}m"
            abs >= 1_000 -> "${sign}K${oneDecimal(abs, 1_000)}k"
            else -> "${sign}K$abs"
        }
    }

    private fun oneDecimal(
        value: Long,
        unit: Long,
    ): String {
        val tenths = value * 10 / unit
        val whole = tenths / 10
        val fraction = tenths % 10
        return if (fraction == 0L) "$whole" else "$whole.$fraction"
    }
}
