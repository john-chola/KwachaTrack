package zm.kwachatrack.core.domain.usecase

import zm.kwachatrack.core.domain.model.Category
import zm.kwachatrack.core.domain.model.Direction
import zm.kwachatrack.core.domain.model.LUSAKA
import zm.kwachatrack.core.domain.model.Provider
import zm.kwachatrack.core.domain.model.SystemCategories
import zm.kwachatrack.core.domain.model.Transaction
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

data class CategoryTotal(
    val category: Category,
    val totalNgwee: Long,
    val count: Int,
)

data class BalancePoint(
    val timestamp: Long,
    val balanceNgwee: Long,
)

data class MonthlySummary(
    val month: YearMonth,
    val incomeNgwee: Long,
    val spendingNgwee: Long,
    val feesNgwee: Long,
    val spendingByCategory: List<CategoryTotal>,
    val balanceTrend: List<BalancePoint>,
    val transactionCount: Int,
) {
    val netNgwee: Long get() = incomeNgwee - spendingNgwee - feesNgwee
}

data class MonthTotals(
    val month: YearMonth,
    val incomeNgwee: Long,
    val spendingNgwee: Long,
)

object Months {
    fun startMillis(month: YearMonth): Long =
        month
            .atDay(1)
            .atStartOfDay(LUSAKA)
            .toInstant()
            .toEpochMilli()

    /** Exclusive end. */
    fun endMillis(month: YearMonth): Long = startMillis(month.plusMonths(1))

    fun of(millis: Long): YearMonth = YearMonth.from(Instant.ofEpochMilli(millis).atZone(LUSAKA))

    fun dayOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(LUSAKA).toLocalDate()
}

/**
 * FR7/UC5. Pure calculation over transactions already loaded for the month.
 *
 * Income = money in (received, cash in). Spending = money out, excluding fees,
 * which are reported separately so "how much have I paid in fees?" has a direct answer.
 */
class MonthlySummaryUseCase {
    operator fun invoke(
        month: YearMonth,
        transactions: List<Transaction>,
        categories: List<Category>,
    ): MonthlySummary {
        val inMonth = transactions.filter { Months.of(it.timestamp) == month }
        val byId = categories.associateBy { it.id }
        val other = byId[SystemCategories.OTHER] ?: SystemCategories.all.last()

        val income = inMonth.filter { it.type.direction == Direction.IN }.sumOf { it.amountNgwee }
        val outflows = inMonth.filter { it.type.direction == Direction.OUT }
        val spending = outflows.sumOf { it.amountNgwee }
        val fees = inMonth.sumOf { it.feeNgwee }

        val byCategory =
            outflows
                .groupBy { byId[it.categoryId] ?: other }
                .map { (category, txns) -> CategoryTotal(category, txns.sumOf { it.amountNgwee }, txns.size) }
                .sortedByDescending { it.totalNgwee }

        return MonthlySummary(
            month = month,
            incomeNgwee = income,
            spendingNgwee = spending,
            feesNgwee = fees,
            spendingByCategory = byCategory,
            balanceTrend = balanceTrend(inMonth),
            transactionCount = inMonth.size,
        )
    }

    /**
     * Last known balance per day. If the user has several providers, their balances are
     * added together using each provider's latest balance up to that day.
     */
    fun balanceTrend(transactions: List<Transaction>): List<BalancePoint> {
        val latest = mutableMapOf<Provider, Long>()
        return transactions
            .filter { it.balanceAfterNgwee != null }
            .sortedBy { it.timestamp }
            .groupBy { Months.dayOf(it.timestamp) }
            .map { (_, dayTxns) ->
                dayTxns.forEach { latest[it.provider] = it.balanceAfterNgwee!! }
                BalancePoint(dayTxns.last().timestamp, latest.values.sum())
            }
    }

    /** Income vs spending for the [count] months ending at [endMonth], oldest first. */
    fun monthlyTotals(
        endMonth: YearMonth,
        count: Int,
        transactions: List<Transaction>,
    ): List<MonthTotals> {
        val grouped = transactions.groupBy { Months.of(it.timestamp) }
        return (count - 1 downTo 0).map { back ->
            val month = endMonth.minusMonths(back.toLong())
            val txns = grouped[month].orEmpty()
            MonthTotals(
                month = month,
                incomeNgwee = txns.filter { it.type.direction == Direction.IN }.sumOf { it.amountNgwee },
                spendingNgwee = txns.filter { it.type.direction == Direction.OUT }.sumOf { it.amountNgwee },
            )
        }
    }
}
