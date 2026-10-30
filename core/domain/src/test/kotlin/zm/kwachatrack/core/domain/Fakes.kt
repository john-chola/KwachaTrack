package zm.kwachatrack.core.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import zm.kwachatrack.core.domain.model.Category
import zm.kwachatrack.core.domain.model.CategoryRule
import zm.kwachatrack.core.domain.model.ParseResult
import zm.kwachatrack.core.domain.model.ParsedTransaction
import zm.kwachatrack.core.domain.model.Provider
import zm.kwachatrack.core.domain.model.RawSms
import zm.kwachatrack.core.domain.model.SystemCategories
import zm.kwachatrack.core.domain.model.Transaction
import zm.kwachatrack.core.domain.model.TxnType
import zm.kwachatrack.core.domain.model.UnparsedSms
import zm.kwachatrack.core.domain.model.UnparsedStatus
import zm.kwachatrack.core.domain.repository.CategoryRepository
import zm.kwachatrack.core.domain.repository.DbTransactionRunner
import zm.kwachatrack.core.domain.repository.SettingsRepository
import zm.kwachatrack.core.domain.repository.SmsInbox
import zm.kwachatrack.core.domain.repository.TransactionParser
import zm.kwachatrack.core.domain.repository.TransactionRepository
import zm.kwachatrack.core.domain.repository.UnparsedSmsRepository
import zm.kwachatrack.core.domain.usecase.Categoriser

/** Parses "SENT|<amount ngwee>|<counterparty>|<txnId>" from sender "MOMO"; anything else fails. */
class FakeParser : TransactionParser {
    override fun isProviderSender(sender: String) = sender == "MOMO"

    override fun parse(sms: RawSms): ParseResult = parseAnySender(sms.body, sms.receivedAt)

    override fun parseAnySender(
        body: String,
        receivedAt: Long,
    ): ParseResult {
        val p = body.split("|")
        if (p.size != 4) return ParseResult.Failure("No match")
        return ParseResult.Success(
            ParsedTransaction(
                providerTxnId = p[3],
                provider = Provider.MTN,
                type = TxnType.valueOf(p[0]),
                amountNgwee = p[1].toLong(),
                feeNgwee = 0,
                counterparty = p[2],
                balanceAfterNgwee = null,
                timestamp = receivedAt,
                rawSms = body,
                parserVersion = 1,
                templateId = "fake",
            ),
        )
    }
}

class FakeTransactions : TransactionRepository {
    val items = MutableStateFlow<List<Transaction>>(emptyList())
    private var nextId = 1L

    override fun observeBetween(
        fromMillis: Long,
        toMillis: Long,
    ): Flow<List<Transaction>> = items.map { list -> list.filter { it.timestamp in fromMillis until toMillis } }

    override fun observeRecent(limit: Int) = items.map { it.sortedByDescending { t -> t.timestamp }.take(limit) }

    override fun observeById(id: Long) = items.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun getAll() = items.value

    override suspend fun insertIfNew(transaction: Transaction): Boolean {
        if (items.value.any { it.provider == transaction.provider && it.providerTxnId == transaction.providerTxnId }) return false
        items.value += transaction.copy(id = nextId++)
        return true
    }

    override suspend fun updateCategory(
        id: Long,
        categoryId: Long,
    ) {
        items.value = items.value.map { if (it.id == id) it.copy(categoryId = categoryId) else it }
    }

    override suspend fun updateCategoryForCounterparty(
        counterparty: String,
        categoryId: Long,
    ) {
        val key = Categoriser.normalise(counterparty)
        items.value =
            items.value.map {
                val cp = it.counterparty
                if (cp != null && Categoriser.normalise(cp) == key) it.copy(categoryId = categoryId) else it
            }
    }
}

class FakeCategories : CategoryRepository {
    val rules = MutableStateFlow(SystemCategories.seededRules)

    override fun observeCategories() = MutableStateFlow(SystemCategories.all)

    override suspend fun getCategories(): List<Category> = SystemCategories.all

    override fun observeRules() = rules

    override suspend fun getRules() = rules.value

    override suspend fun upsertUserRule(rule: CategoryRule) {
        rules.value = rules.value.filterNot { it.createdByUser && it.pattern == rule.pattern && it.matchType == rule.matchType } + rule
    }

    override suspend fun deleteRule(id: Long) {
        rules.value = rules.value.filterNot { it.id == id }
    }
}

class FakeUnparsed : UnparsedSmsRepository {
    val items = MutableStateFlow<List<UnparsedSms>>(emptyList())

    override fun observeNew() = items.map { list -> list.filter { it.status == UnparsedStatus.NEW } }

    override suspend fun add(sms: UnparsedSms) {
        if (items.value.none { it.sender == sms.sender && it.body == sms.body && it.receivedAt == sms.receivedAt }) {
            items.value += sms.copy(id = items.value.size + 1L)
        }
    }

    override suspend fun setStatus(
        id: Long,
        status: UnparsedStatus,
    ) {
        items.value = items.value.map { if (it.id == id) it.copy(status = status) else it }
    }
}

class FakeSettings(
    enabled: Set<Provider> = Provider.entries.toSet(),
) : SettingsRepository {
    private val providers = MutableStateFlow(enabled)
    private val onboarding = MutableStateFlow(false)

    override fun observeEnabledProviders() = providers

    override suspend fun enabledProviders() = providers.value

    override suspend fun setProviderEnabled(
        provider: Provider,
        enabled: Boolean,
    ) {
        providers.value = if (enabled) providers.value + provider else providers.value - provider
    }

    override fun observeOnboardingDone() = onboarding

    override suspend fun setOnboardingDone() {
        onboarding.value = true
    }
}

class FakeInbox(
    private val messages: List<RawSms>,
) : SmsInbox {
    override suspend fun read(senderFilter: (String) -> Boolean) = messages.filter { senderFilter(it.sender) }
}

object DirectRunner : DbTransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T = block()
}
