package zm.kwachatrack.core.domain.usecase

import zm.kwachatrack.core.domain.model.Category
import zm.kwachatrack.core.domain.model.Provider
import zm.kwachatrack.core.domain.model.Transaction
import java.time.LocalDate

/** FR11: search and filter. Empty filters mean "everything". */
data class TransactionFilter(
    val query: String = "",
    val providers: Set<Provider> = emptySet(),
    val categoryId: Long? = null,
) {
    fun apply(
        transactions: List<Transaction>,
        categories: Map<Long, Category>,
    ): List<Transaction> {
        val q = query.trim().lowercase()
        return transactions.filter { t ->
            (providers.isEmpty() || t.provider in providers) &&
                (categoryId == null || t.categoryId == categoryId) &&
                (
                    q.isEmpty() ||
                        t.counterparty?.lowercase()?.contains(q) == true ||
                        t.providerTxnId.lowercase().contains(q) ||
                        t.type.label
                            .lowercase()
                            .contains(q) ||
                        categories[t.categoryId]?.name?.lowercase()?.contains(q) == true
                )
        }
    }

    companion object {
        /** Newest day first, newest transaction first within a day. */
        fun groupByDay(transactions: List<Transaction>): List<Pair<LocalDate, List<Transaction>>> =
            transactions
                .sortedByDescending { it.timestamp }
                .groupBy { Months.dayOf(it.timestamp) }
                .toList()
    }
}
