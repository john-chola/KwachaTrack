package zm.kwachatrack.core.data

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import zm.kwachatrack.core.data.db.KwachaDatabase
import zm.kwachatrack.core.data.repository.RoomCategoryRepository
import zm.kwachatrack.core.data.repository.RoomTransactionRepository
import zm.kwachatrack.core.data.repository.RoomUnparsedSmsRepository
import zm.kwachatrack.core.domain.model.CategoryRule
import zm.kwachatrack.core.domain.model.MatchType
import zm.kwachatrack.core.domain.model.Provider
import zm.kwachatrack.core.domain.model.SystemCategories
import zm.kwachatrack.core.domain.model.Transaction
import zm.kwachatrack.core.domain.model.TxnType
import zm.kwachatrack.core.domain.model.UnparsedSms

/** Room integration tests on the JVM (Robolectric, unencrypted in-memory database). */
@RunWith(RobolectricTestRunner::class)
class DaoTest {
    private val db = KwachaDatabase.inMemory(ApplicationProvider.getApplicationContext())
    private val transactions = RoomTransactionRepository(db.transactionDao())
    private val categories = RoomCategoryRepository(db.categoryDao())
    private val unparsed = RoomUnparsedSmsRepository(db.unparsedSmsDao())

    @After fun close() = db.close()

    private fun tx(
        id: String,
        cp: String = "John Banda",
        provider: Provider = Provider.MTN,
    ) = Transaction(
        providerTxnId = id,
        provider = provider,
        type = TxnType.SENT,
        amountNgwee = 1000,
        counterparty = cp,
        balanceAfterNgwee = null,
        timestamp = 1000,
        categoryId = SystemCategories.OTHER,
        rawSms = "x",
        parserVersion = 1,
    )

    @Test
    fun seedsCategoriesAndRules() =
        runBlocking {
            assertEquals(SystemCategories.all.size, categories.getCategories().size)
            assertEquals(SystemCategories.seededRules.size, categories.getRules().size)
        }

    @Test
    fun deduplicatesByProviderAndTxnId() =
        runBlocking {
            assertTrue(transactions.insertIfNew(tx("A")))
            assertFalse(transactions.insertIfNew(tx("A")))
            assertTrue(transactions.insertIfNew(tx("A", provider = Provider.AIRTEL)))
            assertEquals(2, transactions.getAll().size)
        }

    @Test
    fun recategorisesByCounterpartyIgnoringCase() =
        runBlocking {
            transactions.insertIfNew(tx("A", "John Banda"))
            transactions.insertIfNew(tx("B", " JOHN BANDA "))
            transactions.insertIfNew(tx("C", "Mary"))
            transactions.updateCategoryForCounterparty("john banda", SystemCategories.FAMILY)
            val cats = transactions.getAll().associate { it.providerTxnId to it.categoryId }
            assertEquals(SystemCategories.FAMILY, cats["A"])
            assertEquals(SystemCategories.FAMILY, cats["B"])
            assertEquals(SystemCategories.OTHER, cats["C"])
        }

    @Test
    fun userRuleUpsertReplacesSamePattern() =
        runBlocking {
            categories.upsertUserRule(CategoryRule(matchType = MatchType.EXACT, pattern = "JOHN", categoryId = 6, createdByUser = true))
            categories.upsertUserRule(CategoryRule(matchType = MatchType.EXACT, pattern = "JOHN", categoryId = 7, createdByUser = true))
            val user = categories.getRules().filter { it.createdByUser }
            assertEquals(1, user.size)
            assertEquals(7L, user.single().categoryId)
        }

    @Test
    fun unparsedIgnoresExactDuplicates() =
        runBlocking {
            val sms = UnparsedSms(sender = "MoMo", body = "?", receivedAt = 1, reason = "r")
            unparsed.add(sms)
            unparsed.add(sms)
            assertEquals(1, unparsed.observeNew().first().size)
        }
}
