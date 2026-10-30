package zm.kwachatrack.core.domain.usecase

import zm.kwachatrack.core.domain.model.CategoryRule
import zm.kwachatrack.core.domain.model.MatchType
import zm.kwachatrack.core.domain.model.Transaction
import zm.kwachatrack.core.domain.repository.CategoryRepository
import zm.kwachatrack.core.domain.repository.DbTransactionRunner
import zm.kwachatrack.core.domain.repository.TransactionRepository

/** UC3: change a category and, optionally, learn a rule for that counterparty. */
class RecategoriseUseCase(
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
    private val dbTransaction: DbTransactionRunner,
) {
    suspend operator fun invoke(
        transaction: Transaction,
        categoryId: Long,
        applyToCounterparty: Boolean,
    ) {
        dbTransaction.run {
            transactions.updateCategory(transaction.id, categoryId)
            val counterparty = transaction.counterparty?.trim()
            if (applyToCounterparty && !counterparty.isNullOrEmpty()) {
                categories.upsertUserRule(
                    CategoryRule(
                        matchType = MatchType.EXACT,
                        pattern = Categoriser.normalise(counterparty),
                        categoryId = categoryId,
                        createdByUser = true,
                    ),
                )
                transactions.updateCategoryForCounterparty(counterparty, categoryId)
            }
        }
    }
}
