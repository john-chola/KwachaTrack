package zm.kwachatrack.core.parser

import org.junit.jupiter.api.Test
import zm.kwachatrack.core.domain.model.ParseResult
import zm.kwachatrack.core.domain.model.Provider
import zm.kwachatrack.core.domain.model.RawSms
import zm.kwachatrack.core.domain.model.TxnType
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AmountNormaliserTest {
    @Test
    fun `parses common amount shapes into ngwee`() {
        assertEquals(125000, AmountNormaliser.toNgwee("1,250.00"))
        assertEquals(125000, AmountNormaliser.toNgwee("1250"))
        assertEquals(125050, AmountNormaliser.toNgwee("1250.5"))
        assertEquals(5, AmountNormaliser.toNgwee("0.05"))
        assertEquals(125000, AmountNormaliser.toNgwee("K1,250.00"))
        assertEquals(125000, AmountNormaliser.toNgwee("ZMW 1,250.00"))
        assertEquals(125000, AmountNormaliser.toNgwee("1,250.00."))
        assertEquals(0, AmountNormaliser.toNgwee("0.00"))
    }

    @Test
    fun `rejects garbage`() {
        assertNull(AmountNormaliser.toNgwee(null))
        assertNull(AmountNormaliser.toNgwee(""))
        assertNull(AmountNormaliser.toNgwee("abc"))
        assertNull(AmountNormaliser.toNgwee("1.234"))
        assertNull(AmountNormaliser.toNgwee("-50"))
        assertNull(AmountNormaliser.toNgwee("99999999999999999"))
    }
}

class DateNormaliserTest {
    @Test
    fun `reads dates as Lusaka time`() {
        // 29 Oct 2026 13:05 in Lusaka is 11:05 UTC
        assertEquals(1_793_271_900_000, DateNormaliser.toEpochMillis("29/10/2026 13:05", "dd/MM/yyyy HH:mm"))
    }

    @Test
    fun `tries alternative patterns and returns null when none fit`() {
        assertEquals(1_793_271_900_000, DateNormaliser.toEpochMillis("29-10-26 13:05", "dd/MM/yyyy HH:mm|dd-MM-yy HH:mm"))
        assertNull(DateNormaliser.toEpochMillis("yesterday", "dd/MM/yyyy HH:mm"))
        assertNull(DateNormaliser.toEpochMillis(null, "dd/MM/yyyy"))
    }
}

class TemplateSmsParserTest {
    private val template =
        SmsTemplate(
            id = "t_sent",
            type = TxnType.SENT,
            regex =
                Regex(
                    "sent K(?<amount>[\\d,.]+) to (?<counterparty>.+?)\\. Fee K(?<fee>[\\d,.]+)\\. ID (?<txnId>\\w+)",
                    RegexOption.IGNORE_CASE,
                ),
        )
    private val parser = TemplateSmsParser(Provider.MTN, setOf("MoMo"), listOf(template), parserVersion = 3)

    @Test
    fun `sender matching ignores case and spaces`() {
        assertTrue(parser.canHandle("MOMO"))
        assertTrue(parser.canHandle("Mo Mo"))
        assertTrue(!parser.canHandle("Bank"))
    }

    @Test
    fun `extracts fields and falls back to received time when the SMS has no date`() {
        val r = parser.parse(RawSms("MoMo", "You sent K1,000.00 to  Jane   Doe. Fee K2.50. ID ABC123", 42L))
        val t = assertIs<ParseResult.Success>(r).transaction
        assertEquals(100000, t.amountNgwee)
        assertEquals(250, t.feeNgwee)
        assertEquals("Jane Doe", t.counterparty)
        assertNull(t.balanceAfterNgwee)
        assertEquals(42L, t.timestamp)
        assertEquals(3, t.parserVersion)
        assertEquals("t_sent", t.templateId)
    }

    @Test
    fun `messages split across lines are joined`() {
        val r = parser.parse(RawSms("MoMo", "You sent K10.00 to Jane\nDoe. Fee K0.00.\nID X1", 0L))
        assertEquals("Jane Doe", assertIs<ParseResult.Success>(r).transaction.counterparty)
    }

    @Test
    fun `zero amount and unknown text fail`() {
        assertIs<ParseResult.Failure>(parser.parse(RawSms("MoMo", "You sent K0.00 to Jane. Fee K0. ID X1", 0L)))
        assertIs<ParseResult.Failure>(parser.parse(RawSms("MoMo", "Win big with MoMo!", 0L)))
    }
}

class ParserRegistryTest {
    private val registry = ParserRegistry.fromBundledTemplates()

    @Test
    fun `unknown sender fails`() {
        val r = registry.parse(RawSms("MyBank", "You have sent ZMW 10.00", 0))
        assertEquals("Unknown sender", assertIs<ParseResult.Failure>(r).reason)
        assertTrue(!registry.isProviderSender("MyBank"))
        assertTrue(registry.isProviderSender("MoMo"))
        assertTrue(registry.isProviderSender("airtel money"))
    }

    @Test
    fun `pasted text is matched against every provider`() {
        val body =
            "Dear Customer, you have received ZMW 50.00 from JOHN BANDA 0951234567. " +
                "Transaction ID ZK123. Your new balance is ZMW 75.00."
        val t = assertIs<ParseResult.Success>(registry.parseAnySender(body, 9L)).transaction
        assertEquals(Provider.ZAMTEL, t.provider)
        assertEquals(TxnType.RECEIVED, t.type)
        assertEquals(7500, t.balanceAfterNgwee)
        assertIs<ParseResult.Failure>(registry.parseAnySender("hello there", 9L))
    }
}

class TemplateLoaderTest {
    @Test
    fun `rejects templates without required groups`() {
        val json =
            """{"version":1,"providers":[{"provider":"MTN","senders":["MoMo"],
               "templates":[{"id":"x","type":"SENT","pattern":"sent (?<amount>\\d+)"}]}]}"""
        val e = assertFailsWith<IllegalArgumentException> { TemplateLoader.load(json) }
        assertTrue(e.message!!.contains("txnId"))
    }

    @Test
    fun `rejects duplicate template ids and date groups without a format`() {
        val dup =
            """{"version":1,"providers":[{"provider":"MTN","senders":["MoMo"],"templates":[
               {"id":"a","type":"SENT","pattern":"(?<amount>\\d+) (?<txnId>\\w+)"},
               {"id":"a","type":"SENT","pattern":"(?<amount>\\d+) (?<txnId>\\w+)"}]}]}"""
        assertFailsWith<IllegalArgumentException> { TemplateLoader.load(dup) }
        val noFormat =
            """{"version":1,"providers":[{"provider":"MTN","senders":["MoMo"],"templates":[
               {"id":"a","type":"SENT","pattern":"(?<amount>\\d+) (?<txnId>\\w+) (?<date>.+)"}]}]}"""
        assertFailsWith<IllegalArgumentException> { TemplateLoader.load(noFormat) }
    }

    @Test
    fun `bundled templates load`() {
        assertEquals(1, ParserRegistry.fromBundledTemplates().templateVersion)
    }
}

class MiniJsonTest {
    @Test
    fun `parses nested structures and escapes`() {
        val v = MiniJson.parse("""{"a":[1,2.5,true,null],"b":"x\"y\\zA","c":{}}""") as Map<*, *>
        assertEquals(listOf(1.0, 2.5, true, null), v["a"])
        assertEquals("x\"y\\zA", v["b"])
        assertEquals(emptyMap<String, Any?>(), v["c"])
    }

    @Test
    fun `rejects malformed json`() {
        assertFailsWith<IllegalArgumentException> { MiniJson.parse("{\"a\":}") }
        assertFailsWith<IllegalArgumentException> { MiniJson.parse("[1,2") }
        assertFailsWith<IllegalArgumentException> { MiniJson.parse("{} extra") }
    }
}
