package zm.kwachatrack.core.domain.usecase

import zm.kwachatrack.core.domain.model.Category
import zm.kwachatrack.core.domain.model.LUSAKA
import zm.kwachatrack.core.domain.model.Transaction
import java.time.Instant
import java.time.format.DateTimeFormatter

/** FR10: CSV export. Amounts are written in kwacha with two decimals so spreadsheets read them as numbers. */
class CsvExporter {
    fun export(
        transactions: List<Transaction>,
        categories: List<Category>,
    ): String {
        val names = categories.associate { it.id to it.name }
        val sb = StringBuilder()
        sb.append("date,provider,type,amount_zmw,fee_zmw,counterparty,balance_zmw,category,transaction_id\n")
        transactions.sortedBy { it.timestamp }.forEach { t ->
            sb.append(
                listOf(
                    DATE.format(Instant.ofEpochMilli(t.timestamp).atZone(LUSAKA)),
                    t.provider.name,
                    t.type.name,
                    plain(t.amountNgwee),
                    plain(t.feeNgwee),
                    t.counterparty.orEmpty(),
                    t.balanceAfterNgwee?.let(::plain).orEmpty(),
                    names[t.categoryId].orEmpty(),
                    t.providerTxnId,
                ).joinToString(",") { escape(it) },
            )
            sb.append('\n')
        }
        return sb.toString()
    }

    private fun plain(ngwee: Long): String {
        val sign = if (ngwee < 0) "-" else ""
        val abs = kotlin.math.abs(ngwee)
        return "$sign${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
    }

    /** Quotes fields with commas/quotes/newlines and defuses spreadsheet formula injection. */
    private fun escape(value: String): String {
        var v = value
        if (v.isNotEmpty() && v[0] in "=+@") v = "'$v"
        return if (v.any { it == ',' || it == '"' || it == '\n' }) "\"${v.replace("\"", "\"\"")}\"" else v
    }

    private companion object {
        val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    }
}
