package zm.kwachatrack.core.parser

import zm.kwachatrack.core.domain.model.RawSms
import zm.kwachatrack.core.domain.model.TxnType
import java.io.File

/** One fixture: an SMS and what the parser must produce (or null when it must fail). */
data class Fixture(
    val file: String,
    val sms: RawSms,
    val expected: Expected?,
) {
    data class Expected(
        val type: TxnType,
        val amountNgwee: Long,
        val feeNgwee: Long,
        val counterparty: String?,
        val balanceNgwee: Long?,
        val txnId: String,
        val timestamp: Long,
    )

    override fun toString(): String = "$file: ${sms.body.take(60)}"
}

object Fixtures {
    /** Reads every fixtures/<provider>/<type>.txt file plus fixtures/negative.txt. */
    fun all(): List<Fixture> {
        val root = File(Fixtures::class.java.getResource("/fixtures")!!.toURI())
        return root
            .walkTopDown()
            .filter { it.isFile && it.extension == "txt" }
            .sortedBy { it.path }
            .flatMap { parseFile(it.relativeTo(root).path, it.readText()) }
            .toList()
    }

    fun parseFile(
        name: String,
        text: String,
    ): List<Fixture> =
        text
            .split(Regex("\\n\\s*\\n"))
            .map { block -> block.lines().filter { it.isNotBlank() && !it.startsWith("#") } }
            .filter { it.isNotEmpty() }
            .map { lines ->
                val fields = lines.associate { it.substringBefore(": ").trim() to it.substringAfter(": ") }
                val sms = RawSms(fields.getValue("sender"), fields.getValue("body"), fields.getValue("received").toLong())
                Fixture(name, sms, expectation(fields.getValue("expect")))
            }

    private fun expectation(text: String): Fixture.Expected? {
        if (text.trim() == "FAILURE") return null
        val kv = text.split("; ").associate { it.substringBefore("=") to it.substringAfter("=") }
        return Fixture.Expected(
            type = TxnType.valueOf(kv.getValue("type")),
            amountNgwee = kv.getValue("amount").toLong(),
            feeNgwee = kv.getValue("fee").toLong(),
            counterparty = kv.getValue("counterparty").ifEmpty { null },
            balanceNgwee = kv.getValue("balance").ifEmpty { null }?.toLong(),
            txnId = kv.getValue("txnId"),
            timestamp = kv.getValue("timestamp").toLong(),
        )
    }
}
