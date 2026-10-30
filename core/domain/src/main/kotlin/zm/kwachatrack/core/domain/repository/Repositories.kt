package zm.kwachatrack.core.domain.repository

import kotlinx.coroutines.flow.Flow
import zm.kwachatrack.core.domain.model.Category
import zm.kwachatrack.core.domain.model.CategoryRule
import zm.kwachatrack.core.domain.model.ParseResult
import zm.kwachatrack.core.domain.model.Provider
import zm.kwachatrack.core.domain.model.RawSms
import zm.kwachatrack.core.domain.model.Transaction
import zm.kwachatrack.core.domain.model.UnparsedSms
import zm.kwachatrack.core.domain.model.UnparsedStatus

/** Implemented by the parser module; the domain does not know how parsing works. */
interface TransactionParser {
    /** True when the sender ID belongs to a known mobile money provider. */
    fun isProviderSender(sender: String): Boolean

    fun parse(sms: RawSms): ParseResult

    /** For pasted or shared text where the sender is unknown: tries every provider. */
    fun parseAnySender(
        body: String,
        receivedAt: Long,
    ): ParseResult
}

interface TransactionRepository {
    fun observeBetween(
        fromMillis: Long,
        toMillis: Long,
    ): Flow<List<Transaction>>

    fun observeRecent(limit: Int): Flow<List<Transaction>>

    fun observeById(id: Long): Flow<Transaction?>

    suspend fun getAll(): List<Transaction>

    /** Inserts unless a transaction with the same provider and providerTxnId exists. Returns true if inserted. */
    suspend fun insertIfNew(transaction: Transaction): Boolean

    suspend fun updateCategory(
        id: Long,
        categoryId: Long,
    )

    suspend fun updateCategoryForCounterparty(
        counterparty: String,
        categoryId: Long,
    )
}

interface CategoryRepository {
    fun observeCategories(): Flow<List<Category>>

    suspend fun getCategories(): List<Category>

    fun observeRules(): Flow<List<CategoryRule>>

    suspend fun getRules(): List<CategoryRule>

    /** Replaces any existing user rule with the same pattern and match type. */
    suspend fun upsertUserRule(rule: CategoryRule)

    suspend fun deleteRule(id: Long)
}

interface UnparsedSmsRepository {
    fun observeNew(): Flow<List<UnparsedSms>>

    /** Ignores exact duplicates (same sender, time and body). */
    suspend fun add(sms: UnparsedSms)

    suspend fun setStatus(
        id: Long,
        status: UnparsedStatus,
    )
}

interface SettingsRepository {
    fun observeEnabledProviders(): Flow<Set<Provider>>

    suspend fun enabledProviders(): Set<Provider>

    suspend fun setProviderEnabled(
        provider: Provider,
        enabled: Boolean,
    )

    fun observeOnboardingDone(): Flow<Boolean>

    suspend fun setOnboardingDone()
}

/** Reads the device SMS inbox. Implemented in the data layer. */
interface SmsInbox {
    suspend fun read(senderFilter: (String) -> Boolean): List<RawSms>
}

/** Runs a block inside one database transaction (makes bulk imports fast). */
interface DbTransactionRunner {
    suspend fun <T> run(block: suspend () -> T): T
}
