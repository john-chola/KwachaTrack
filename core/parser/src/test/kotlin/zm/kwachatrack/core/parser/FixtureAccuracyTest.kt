package zm.kwachatrack.core.parser

import org.junit.jupiter.api.Test
import zm.kwachatrack.core.domain.model.ParseResult
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The parser gate: every fixture against the bundled templates.
 * Target from the design doc: at least 95% of known message types parsed correctly.
 */
class FixtureAccuracyTest {
    private val registry = ParserRegistry.fromBundledTemplates()
    private val fixtures = Fixtures.all()

    @Test
    fun `fixtures exist for every provider`() {
        val providers = fixtures.map { it.file.substringBefore('/') }.toSet()
        assertTrue(providers.containsAll(setOf("mtn", "airtel", "zamtel")), "Found: $providers")
        assertTrue(fixtures.size > 100, "Expected a meaningful fixture set, got ${fixtures.size}")
    }

    @Test
    fun `positive fixtures parse with at least 95 percent accuracy`() {
        val positives = fixtures.filter { it.expected != null }
        val failures = positives.mapNotNull { f -> check(f)?.let { "$f\n    -> $it" } }
        val accuracy = (positives.size - failures.size) * 100.0 / positives.size
        println("Parser accuracy: ${"%.1f".format(accuracy)}% (${positives.size - failures.size}/${positives.size})")
        failures.take(20).forEach(::println)
        assertTrue(accuracy >= 95.0, "Accuracy $accuracy% is below the 95% gate")
    }

    @Test
    fun `every template is exercised by at least one fixture`() {
        val used =
            fixtures.mapNotNull { (registry.parse(it.sms) as? ParseResult.Success)?.transaction?.templateId }.toSet()
        val all = templateIds()
        assertEquals(all, used, "Templates without fixtures: ${all - used}")
    }

    @Test
    fun `negative fixtures never become transactions`() {
        val negatives = fixtures.filter { it.expected == null }
        assertTrue(negatives.isNotEmpty())
        negatives.forEach { f ->
            val r = registry.parse(f.sms)
            if (r is ParseResult.Success) fail("Should not parse: $f -> ${r.transaction}")
        }
    }

    private fun check(f: Fixture): String? {
        val e = f.expected!!
        val result = registry.parse(f.sms)
        val t = (result as? ParseResult.Success)?.transaction ?: return "failed: ${(result as ParseResult.Failure).reason}"
        val problems =
            buildList {
                if (t.type != e.type) add("type ${t.type} != ${e.type}")
                if (t.amountNgwee != e.amountNgwee) add("amount ${t.amountNgwee} != ${e.amountNgwee}")
                if (t.feeNgwee != e.feeNgwee) add("fee ${t.feeNgwee} != ${e.feeNgwee}")
                if (t.counterparty != e.counterparty) add("counterparty '${t.counterparty}' != '${e.counterparty}'")
                if (t.balanceAfterNgwee != e.balanceNgwee) add("balance ${t.balanceAfterNgwee} != ${e.balanceNgwee}")
                if (t.providerTxnId != e.txnId) add("txnId ${t.providerTxnId} != ${e.txnId}")
                if (t.timestamp != e.timestamp) add("timestamp ${t.timestamp} != ${e.timestamp}")
            }
        return problems.takeIf { it.isNotEmpty() }?.joinToString("; ")
    }

    private fun templateIds(): Set<String> {
        val json = javaClass.getResource(ParserRegistry.RESOURCE)!!.readText()
        return Regex("\"id\": \"([a-z0-9_]+)\"").findAll(json).map { it.groupValues[1] }.toSet()
    }
}
