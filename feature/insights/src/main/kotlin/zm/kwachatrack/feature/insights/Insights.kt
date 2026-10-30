package zm.kwachatrack.feature.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import zm.kwachatrack.core.domain.model.LUSAKA
import zm.kwachatrack.core.domain.money.Kwacha
import zm.kwachatrack.core.domain.repository.CategoryRepository
import zm.kwachatrack.core.domain.repository.TransactionRepository
import zm.kwachatrack.core.domain.usecase.MonthTotals
import zm.kwachatrack.core.domain.usecase.MonthlySummary
import zm.kwachatrack.core.domain.usecase.MonthlySummaryUseCase
import zm.kwachatrack.core.domain.usecase.Months
import zm.kwachatrack.core.ui.BalanceLine
import zm.kwachatrack.core.ui.BarGroup
import zm.kwachatrack.core.ui.DonutChart
import zm.kwachatrack.core.ui.EmptyState
import zm.kwachatrack.core.ui.IncomeSpendingBars
import zm.kwachatrack.core.ui.Slice
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class InsightsState(
    val month: YearMonth,
    val summary: MonthlySummary? = null,
    val history: List<MonthTotals> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InsightsViewModel
    @Inject
    constructor(
        transactions: TransactionRepository,
        categories: CategoryRepository,
        summarise: MonthlySummaryUseCase,
    ) : ViewModel() {
        private val month = MutableStateFlow(YearMonth.now(LUSAKA))

        /** Loads six months ending at the selected one: enough for the bar chart and the month summary. */
        private val window =
            month.flatMapLatest { m -> transactions.observeBetween(Months.startMillis(m.minusMonths(5)), Months.endMillis(m)) }

        val state =
            combine(month, window, categories.observeCategories()) { m, txns, cats ->
                InsightsState(m, summarise(m, txns, cats), summarise.monthlyTotals(m, 6, txns))
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsState(YearMonth.now(LUSAKA)))

        fun previous() = month.update { it.minusMonths(1) }

        fun next() = month.update { if (it < YearMonth.now(LUSAKA)) it.plusMonths(1) else it }
    }

private val MONTH = DateTimeFormatter.ofPattern("MMMM yyyy")
private val SHORT = DateTimeFormatter.ofPattern("MMM")

@Composable
fun InsightsScreen(viewModel: InsightsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = viewModel::previous) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
            }
            Text(
                MONTH.format(state.month),
                Modifier.weight(1f).semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            IconButton(onClick = viewModel::next) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month") }
        }
        val s = state.summary
        if (s == null || s.transactionCount == 0) {
            EmptyState("No data for this month", "Pick another month, or wait for new transactions.")
        } else {
            InsightCard("Spending by category") {
                DonutChart(
                    slices = s.spendingByCategory.map { Slice(it.category.name, it.totalNgwee, Color(it.category.colour)) },
                    centreLabel = Kwacha.formatCompact(s.spendingNgwee),
                )
            }
            InsightCard("Fees paid") {
                Text(Kwacha.format(s.feesNgwee), style = MaterialTheme.typography.headlineMedium)
                val share = if (s.spendingNgwee > 0) s.feesNgwee * 1000 / s.spendingNgwee else 0
                Text("${share / 10}.${share % 10}% of what you sent or spent", style = MaterialTheme.typography.bodyMedium)
            }
            InsightCard("Balance") {
                if (s.balanceTrend.size < 2) {
                    Text("Not enough balance readings this month.")
                } else {
                    BalanceLine(s.balanceTrend.map { it.balanceNgwee })
                    Text("Latest: ${Kwacha.format(s.balanceTrend.last().balanceNgwee)}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        InsightCard("Last six months") {
            IncomeSpendingBars(state.history.map { BarGroup(SHORT.format(it.month), it.incomeNgwee, it.spendingNgwee) })
        }
    }
}

@Composable
private fun InsightCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            content()
        }
    }
}
