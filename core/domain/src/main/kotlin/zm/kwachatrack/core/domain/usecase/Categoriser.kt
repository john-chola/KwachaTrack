package zm.kwachatrack.core.domain.usecase

import zm.kwachatrack.core.domain.model.CategoryRule
import zm.kwachatrack.core.domain.model.MatchType
import zm.kwachatrack.core.domain.model.SystemCategories
import zm.kwachatrack.core.domain.model.TxnType

/**
 * Picks a category for a transaction.
 *
 * Order: user EXACT rules, user CONTAINS rules, seeded EXACT, seeded CONTAINS,
 * then a default based on the transaction type. Matching ignores case and extra spaces.
 */
class Categoriser {
    fun categorise(
        counterparty: String?,
        type: TxnType,
        rules: List<CategoryRule>,
    ): Long {
        val name = counterparty?.let(::normalise)
        if (!name.isNullOrEmpty()) {
            val ordered =
                rules.sortedWith(
                    compareByDescending<CategoryRule> { it.createdByUser }
                        .thenBy { if (it.matchType == MatchType.EXACT) 0 else 1 }
                        .thenByDescending { it.pattern.length },
                )
            ordered.firstOrNull { matches(it, name) }?.let { return it.categoryId }
        }
        return defaultFor(type)
    }

    fun defaultFor(type: TxnType): Long =
        when (type) {
            TxnType.RECEIVED, TxnType.CASH_IN -> SystemCategories.INCOME
            TxnType.AIRTIME -> SystemCategories.AIRTIME
            TxnType.BILL_PAYMENT -> SystemCategories.UTILITIES
            else -> SystemCategories.OTHER
        }

    private fun matches(
        rule: CategoryRule,
        name: String,
    ): Boolean {
        val pattern = normalise(rule.pattern)
        if (pattern.isEmpty()) return false
        return when (rule.matchType) {
            MatchType.EXACT -> name == pattern
            MatchType.CONTAINS -> containsWord(name, pattern)
        }
    }

    /** "BUS" should match "LUSAKA BUS STOP" but not "BUSINESS". */
    private fun containsWord(
        haystack: String,
        needle: String,
    ): Boolean = Regex("(^|[^A-Z0-9])${Regex.escape(needle)}($|[^A-Z0-9])").containsMatchIn(haystack)

    companion object {
        fun normalise(text: String): String = text.trim().uppercase().replace(Regex("\\s+"), " ")
    }
}
