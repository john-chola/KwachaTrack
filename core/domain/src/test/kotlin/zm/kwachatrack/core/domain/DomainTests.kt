package zm.kwachatrack.core.domain

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import zm.kwachatrack.core.domain.model.Category
import zm.kwachatrack.core.domain.model.CategoryRule
import zm.kwachatrack.core.domain.model.MatchType
import zm.kwachatrack.core.domain.model.Provider
import zm.kwachatrack.core.domain.model.RawSms
import zm.kwachatrack.core.domain.model.SystemCategories
import zm.kwachatrack.core.domain.model.Transaction
import zm.kwachatrack.core.domain.model.TxnType
import zm.kwachatrack.core.domain.money.Kwacha
import zm.kwachatrack.core.domain.usecase.Categoriser
import zm.kwachatrack.core.domain.usecase.CsvExporter
import zm.kwachatrack.core.domain.usecase.ImportOutcome
import zm.kwachatrack.core.domain.usecase.ImportSmsUseCase
import zm.kwachatrack.core.domain.usecase.MonthlySummaryUseCase
import zm.kwachatrack.core.domain.usecase.Months
import zm.kwachatrack.core.domain.usecase.RecategoriseUseCase
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KwachaTest {
    @Test
    fun `formats ngwee as kwacha`() {
        assertEquals("K1,250.00", Kwacha.format(125000))
        assertEquals("K0.05", Kwacha.format(5))
        assertEquals("K0.00", Kwacha.format(0))
        assertEquals("K1,000,000.10", Kwacha.format(100000010))
        assertEquals("-K12.50", Kwacha.format(-1250))
    }

    @Test
    fun `compact format for chart labels`() {
        assertEquals("K950", Kwacha.formatCompact(95000))
        assertEquals("K1.2k", Kwacha.formatCompact(125000))
        assertEquals("K3k", Kwacha.formatCompact(300000))
        assertEquals("K2.5m", Kwacha.formatCompact(250_000_000))
    }
}

class CategoriserTest {
    private val c = Categoriser()

    @Test
    fun `user rules beat seeded rules and exact beats contains`() {
        val rules =
            SystemCategories.seededRules +
                CategoryRule(
                    matchType = MatchType.EXACT,
                    pattern = "SHOPRITE MANDA HILL",
                    categoryId = SystemCategories.BUSINESS,
                    createdByUser = true,
                )
        assertEquals(SystemCategories.BUSINESS, c.categorise("Shoprite  Manda Hill", TxnType.MERCHANT_PAYMENT, rules))
        assertEquals(SystemCategories.FOOD, c.categorise("SHOPRITE CAIRO RD", TxnType.MERCHANT_PAYMENT, rules))
    }

    @Test
    fun `contains matches whole words only`() {
        val rules = SystemCategories.seededRules
        assertEquals(SystemCategories.TRANSPORT, c.categorise("LUSAKA BUS STOP", TxnType.SENT, rules))
        assertEquals(SystemCategories.OTHER, c.categorise("BUSINESS CENTRE", TxnType.SENT, rules))
    }

    @Test
    fun `falls back to type defaults`() {
        assertEquals(SystemCategories.INCOME, c.categorise("JOHN", TxnType.RECEIVED, emptyList()))
        assertEquals(SystemCategories.AIRTIME, c.categorise(null, TxnType.AIRTIME, emptyList()))
        assertEquals(SystemCategories.UTILITIES, c.categorise("X", TxnType.BILL_PAYMENT, emptyList()))
        assertEquals(SystemCategories.OTHER, c.categorise("X", TxnType.SENT, emptyList()))
    }
}

class ImportSmsUseCaseTest {
    private val txns = FakeTransactions()
    private val categories = FakeCategories()
    private val unparsed = FakeUnparsed()
    private val settings = FakeSettings()
    private val useCase = ImportSmsUseCase(FakeParser(), txns, categories, unparsed, settings, DirectRunner)

    @Test
    fun `imports, deduplicates and queues failures`() =
        runBlocking {
            assertEquals(ImportOutcome.IMPORTED, useCase.importOne(RawSms("MOMO", "SENT|1000|ZESCO PREPAID|T1", 1)))
            assertEquals(ImportOutcome.DUPLICATE, useCase.importOne(RawSms("MOMO", "SENT|1000|ZESCO PREPAID|T1", 2)))
            assertEquals(ImportOutcome.UNRECOGNISED, useCase.importOne(RawSms("MOMO", "promo text", 3)))
            assertEquals(ImportOutcome.NOT_PROVIDER, useCase.importOne(RawSms("MUM", "SENT|1|X|T9", 4)))

            assertEquals(1, txns.items.value.size)
            assertEquals(
                SystemCategories.UTILITIES,
                txns.items.value
                    .single()
                    .categoryId,
            )
            assertEquals(1, unparsed.items.value.size)
        }

    @Test
    fun `inbox import reports a summary and progress`() =
        runBlocking {
            val inbox =
                FakeInbox(
                    (1..600).map { RawSms("MOMO", "SENT|$it|JOHN|T$it", it.toLong()) } +
                        RawSms("MOMO", "SENT|5|JOHN|T5", 9999) +
                        RawSms("MOMO", "garbage", 1) +
                        RawSms("BANK", "SENT|1|X|B1", 1),
                )
            val progress = mutableListOf<Pair<Int, Int>>()
            val s = useCase.importInbox(inbox) { d, t -> progress += d to t }
            assertEquals(602, s.scanned)
            assertEquals(600, s.imported)
            assertEquals(1, s.duplicates)
            assertEquals(1, s.unrecognised)
            assertEquals(602 to 602, progress.last())
        }

    @Test
    fun `disabled providers are skipped`() =
        runBlocking {
            settings.setProviderEnabled(Provider.MTN, false)
            assertEquals(ImportOutcome.PROVIDER_DISABLED, useCase.importOne(RawSms("MOMO", "SENT|1000|X|T1", 1)))
            assertTrue(txns.items.value.isEmpty())
        }

    @Test
    fun `pasted text is split on blank lines and failures returned not queued`() =
        runBlocking {
            val r = useCase.importPasted("SENT|100|A|P1\n\nnot a message\r\n\r\nRECEIVED|200|B|P2", now = 5)
            assertEquals(2, r.summary.imported)
            assertEquals(listOf("not a message"), r.failedMessages)
            assertTrue(unparsed.items.value.isEmpty())
        }
}

class RecategoriseUseCaseTest {
    @Test
    fun `apply to counterparty updates history and adds a user rule`() =
        runBlocking {
            val txns = FakeTransactions()
            val categories = FakeCategories()
            txns.insertIfNew(tx("A", "John Banda"))
            txns.insertIfNew(tx("B", "JOHN  BANDA"))
            txns.insertIfNew(tx("C", "Mary"))
            val first = txns.items.value.first()

            RecategoriseUseCase(txns, categories, DirectRunner)(first, SystemCategories.FAMILY, applyToCounterparty = true)

            val byId = txns.items.value.associate { it.providerTxnId to it.categoryId }
            assertEquals(SystemCategories.FAMILY, byId["A"])
            assertEquals(SystemCategories.FAMILY, byId["B"])
            assertEquals(SystemCategories.OTHER, byId["C"])
            val rule = categories.rules.value.single { it.createdByUser }
            assertEquals("JOHN BANDA", rule.pattern)
            assertEquals(SystemCategories.FAMILY, Categoriser().categorise("john banda", TxnType.SENT, categories.rules.value))
        }

    @Test
    fun `single change does not add a rule`() =
        runBlocking {
            val txns = FakeTransactions()
            val categories = FakeCategories()
            txns.insertIfNew(tx("A", "John"))
            RecategoriseUseCase(txns, categories, DirectRunner)(txns.items.value.first(), SystemCategories.RENT, false)
            assertEquals(
                SystemCategories.RENT,
                txns.items.value
                    .first()
                    .categoryId,
            )
            assertTrue(categories.rules.value.none { it.createdByUser })
        }
}

class MonthlySummaryTest {
    private val oct = YearMonth.of(2026, 10)
    private val start = Months.startMillis(oct)
    private val day = 86_400_000L

    @Test
    fun `month boundaries are in Lusaka time`() {
        // 1 Oct 2026 00:00 in Lusaka = 30 Sep 22:00 UTC
        assertEquals(1_790_805_600_000, start)
        assertEquals(oct, Months.of(start))
        assertEquals(YearMonth.of(2026, 9), Months.of(start - 1))
    }

    @Test
    fun `totals, fees and categories`() {
        val list =
            listOf(
                tx("1", "Boss", TxnType.RECEIVED, 500000, ts = start + day, cat = SystemCategories.INCOME, bal = 600000),
                tx(
                    "2",
                    "ZESCO",
                    TxnType.BILL_PAYMENT,
                    20000,
                    fee = 250,
                    ts = start + 2 * day,
                    cat = SystemCategories.UTILITIES,
                    bal = 579750,
                ),
                tx(
                    "3",
                    "Shoprite",
                    TxnType.MERCHANT_PAYMENT,
                    30000,
                    ts = start + 2 * day + 1000,
                    cat = SystemCategories.FOOD,
                    bal = 549750,
                ),
                tx("4", "Bus", TxnType.SENT, 5000, fee = 100, ts = start + 3 * day, cat = null),
                tx("5", "Old", TxnType.SENT, 99999, ts = start - 1),
            )
        val s = MonthlySummaryUseCase()(oct, list, SystemCategories.all)
        assertEquals(500000, s.incomeNgwee)
        assertEquals(55000, s.spendingNgwee)
        assertEquals(350, s.feesNgwee)
        assertEquals(500000 - 55000 - 350L, s.netNgwee)
        assertEquals(4, s.transactionCount)
        assertEquals(listOf("Food", "Utilities", "Other"), s.spendingByCategory.map { it.category.name })
        assertEquals(listOf(600000L, 549750L), s.balanceTrend.map { it.balanceNgwee })
    }

    @Test
    fun `balance trend adds providers together`() {
        val list =
            listOf(
                tx("1", "x", ts = start, bal = 1000),
                tx("2", "x", ts = start + 10, bal = 500).copy(provider = Provider.AIRTEL),
                tx("3", "x", ts = start + day, bal = 800),
            )
        assertEquals(listOf(1500L, 1300L), MonthlySummaryUseCase().balanceTrend(list).map { it.balanceNgwee })
    }

    @Test
    fun `six month totals include empty months`() {
        val totals =
            MonthlySummaryUseCase().monthlyTotals(oct, 6, listOf(tx("1", "x", TxnType.RECEIVED, 100, ts = start)))
        assertEquals(6, totals.size)
        assertEquals(YearMonth.of(2026, 5), totals.first().month)
        assertEquals(100, totals.last().incomeNgwee)
        assertEquals(0, totals.first().incomeNgwee)
    }
}

class CsvExporterTest {
    @Test
    fun `escapes fields and writes kwacha amounts`() {
        val csv =
            CsvExporter().export(
                listOf(
                    tx("T1", "Banda, John", amount = 125050, fee = 250, ts = 1_790_805_600_000, cat = SystemCategories.FOOD),
                    tx("T2", "=HYPERLINK(\"x\")", ts = 1_790_805_600_001),
                ),
                listOf(Category(SystemCategories.FOOD, "Food", "food", 0, false, true)),
            )
        val lines = csv.trim().lines()
        assertEquals(3, lines.size)
        assertEquals("2026-10-01 00:00,MTN,SENT,1250.50,2.50,\"Banda, John\",,Food,T1", lines[1])
        assertTrue(lines[2].contains("\"'=HYPERLINK(\"\"x\"\")\""))
    }
}

internal fun tx(
    id: String,
    counterparty: String?,
    type: TxnType = TxnType.SENT,
    amount: Long = 1000,
    fee: Long = 0,
    ts: Long = 0,
    cat: Long? = SystemCategories.OTHER,
    bal: Long? = null,
) = Transaction(
    providerTxnId = id,
    provider = Provider.MTN,
    type = type,
    amountNgwee = amount,
    feeNgwee = fee,
    counterparty = counterparty,
    balanceAfterNgwee = bal,
    timestamp = ts,
    categoryId = cat,
    rawSms = "",
    parserVersion = 1,
)
