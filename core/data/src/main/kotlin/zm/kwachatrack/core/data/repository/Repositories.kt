package zm.kwachatrack.core.data.repository

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import zm.kwachatrack.core.data.db.CategoryDao
import zm.kwachatrack.core.data.db.CategoryEntity
import zm.kwachatrack.core.data.db.CategoryRuleEntity
import zm.kwachatrack.core.data.db.KwachaDatabase
import zm.kwachatrack.core.data.db.TransactionDao
import zm.kwachatrack.core.data.db.TransactionEntity
import zm.kwachatrack.core.data.db.UnparsedSmsDao
import zm.kwachatrack.core.data.db.UnparsedSmsEntity
import zm.kwachatrack.core.domain.model.Category
import zm.kwachatrack.core.domain.model.CategoryRule
import zm.kwachatrack.core.domain.model.Provider
import zm.kwachatrack.core.domain.model.Transaction
import zm.kwachatrack.core.domain.model.UnparsedSms
import zm.kwachatrack.core.domain.model.UnparsedStatus
import zm.kwachatrack.core.domain.repository.CategoryRepository
import zm.kwachatrack.core.domain.repository.DbTransactionRunner
import zm.kwachatrack.core.domain.repository.SettingsRepository
import zm.kwachatrack.core.domain.repository.TransactionRepository
import zm.kwachatrack.core.domain.repository.UnparsedSmsRepository
import javax.inject.Inject
import javax.inject.Singleton

class RoomTransactionRepository
    @Inject
    constructor(
        private val dao: TransactionDao,
    ) : TransactionRepository {
        override fun observeBetween(
            fromMillis: Long,
            toMillis: Long,
        ): Flow<List<Transaction>> = dao.observeBetween(fromMillis, toMillis).map { it.map(TransactionEntity::toDomain) }

        override fun observeRecent(limit: Int) = dao.observeRecent(limit).map { it.map(TransactionEntity::toDomain) }

        override fun observeById(id: Long) = dao.observeById(id).map { it?.toDomain() }

        override suspend fun getAll() = dao.getAll().map(TransactionEntity::toDomain)

        override suspend fun insertIfNew(transaction: Transaction): Boolean = dao.insert(transaction.toEntity()) != -1L

        override suspend fun updateCategory(
            id: Long,
            categoryId: Long,
        ) = dao.updateCategory(id, categoryId)

        override suspend fun updateCategoryForCounterparty(
            counterparty: String,
            categoryId: Long,
        ) = dao.updateCategoryForCounterparty(counterparty, categoryId)
    }

class RoomCategoryRepository
    @Inject
    constructor(
        private val dao: CategoryDao,
    ) : CategoryRepository {
        override fun observeCategories() = dao.observeAll().map { it.map(CategoryEntity::toDomain) }

        override suspend fun getCategories() = dao.getAll().map(CategoryEntity::toDomain)

        override fun observeRules() = dao.observeRules().map { it.map(CategoryRuleEntity::toDomain) }

        override suspend fun getRules() = dao.getRules().map(CategoryRuleEntity::toDomain)

        override suspend fun upsertUserRule(rule: CategoryRule) {
            val existing = dao.findUserRule(rule.matchType, rule.pattern)
            dao.upsertRule(rule.toEntity().copy(id = existing?.id ?: 0, createdByUser = true))
        }

        override suspend fun deleteRule(id: Long) = dao.deleteRule(id)
    }

class RoomUnparsedSmsRepository
    @Inject
    constructor(
        private val dao: UnparsedSmsDao,
    ) : UnparsedSmsRepository {
        override fun observeNew() = dao.observeNew().map { list -> list.map { it.toDomain() } }

        override suspend fun add(sms: UnparsedSms) {
            dao.insert(UnparsedSmsEntity(0, sms.sender, sms.body, sms.receivedAt, sms.reason, sms.status))
        }

        override suspend fun setStatus(
            id: Long,
            status: UnparsedStatus,
        ) = dao.setStatus(id, status)
    }

class RoomTransactionRunner
    @Inject
    constructor(
        private val db: KwachaDatabase,
    ) : DbTransactionRunner {
        override suspend fun <T> run(block: suspend () -> T): T = db.withTransaction { block() }
    }

/** Small preferences store. SharedPreferences keeps the dependency list short; swap for DataStore if it grows. */
@Singleton
class PrefsSettingsRepository
    @Inject
    constructor(
        context: Context,
    ) : SettingsRepository {
        private val prefs = context.getSharedPreferences("kwachatrack_settings", Context.MODE_PRIVATE)
        private val providers = MutableStateFlow(readProviders())
        private val onboarding = MutableStateFlow(prefs.getBoolean(KEY_ONBOARDED, false))

        override fun observeEnabledProviders(): Flow<Set<Provider>> = providers.asStateFlow()

        override suspend fun enabledProviders(): Set<Provider> = providers.value

        override suspend fun setProviderEnabled(
            provider: Provider,
            enabled: Boolean,
        ) {
            val next = if (enabled) providers.value + provider else providers.value - provider
            prefs.edit().putStringSet(KEY_PROVIDERS, next.map { it.name }.toSet()).apply()
            providers.value = next
        }

        override fun observeOnboardingDone(): Flow<Boolean> = onboarding.asStateFlow()

        override suspend fun setOnboardingDone() {
            prefs.edit().putBoolean(KEY_ONBOARDED, true).apply()
            onboarding.value = true
        }

        private fun readProviders(): Set<Provider> =
            prefs
                .getStringSet(KEY_PROVIDERS, null)
                ?.mapNotNull { name -> Provider.entries.firstOrNull { it.name == name } }
                ?.toSet()
                ?: Provider.entries.toSet()

        private companion object {
            const val KEY_PROVIDERS = "enabled_providers"
            const val KEY_ONBOARDED = "onboarding_done"
        }
    }

internal fun TransactionEntity.toDomain() =
    Transaction(
        id,
        providerTxnId,
        provider,
        type,
        amountNgwee,
        feeNgwee,
        counterparty,
        balanceAfterNgwee,
        timestamp,
        categoryId,
        rawSms,
        parserVersion,
    )

internal fun Transaction.toEntity() =
    TransactionEntity(
        id,
        providerTxnId,
        provider,
        type,
        amountNgwee,
        feeNgwee,
        counterparty,
        balanceAfterNgwee,
        timestamp,
        categoryId,
        rawSms,
        parserVersion,
    )

internal fun CategoryEntity.toDomain() = Category(id, name, iconKey, colour, isIncome, isSystem)

internal fun CategoryRuleEntity.toDomain() = CategoryRule(id, matchType, pattern, categoryId, createdByUser)

internal fun CategoryRule.toEntity() = CategoryRuleEntity(id, matchType, pattern, categoryId, createdByUser)

internal fun UnparsedSmsEntity.toDomain() = UnparsedSms(id, sender, body, receivedAt, reason, status)
