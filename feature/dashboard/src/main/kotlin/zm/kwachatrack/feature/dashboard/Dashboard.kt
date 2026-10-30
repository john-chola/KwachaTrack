package zm.kwachatrack.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import zm.kwachatrack.core.domain.model.Category
import zm.kwachatrack.core.domain.model.Transaction
import zm.kwachatrack.core.domain.money.Kwacha
import zm.kwachatrack.core.domain.repository.CategoryRepository
import zm.kwachatrack.core.domain.repository.TransactionRepository
import zm.kwachatrack.core.domain.repository.UnparsedSmsRepository
import zm.kwachatrack.core.domain.usecase.MonthlySummary
import zm.kwachatrack.core.domain.usecase.MonthlySummaryUseCase
import zm.kwachatrack.core.domain.usecase.Months
import zm.kwachatrack.core.ui.CategoryBadge
import zm.kwachatrack.core.ui.EmptyState
import zm.kwachatrack.core.ui.MoneyColours
import zm.kwachatrack.core.ui.SectionHeader
import zm.kwachatrack.core.ui.TransactionRow
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class DashboardState(
    val loading: Boolean = true,
    val summary: MonthlySummary? = null,
    val recent: List<Transaction> = emptyList(),
    val categories: Map<Long, Category> = emptyMap(),
    val reviewCount: Int = 0,
)

@HiltViewModel
class DashboardViewModel
    @Inject
    constructor(
        transactions: TransactionRepository,
        categories: CategoryRepository,
        unparsed: UnparsedSmsRepository,
        summarise: MonthlySummaryUseCase,
    ) : ViewModel() {
        private val month = YearMonth.now(zm.kwachatrack.core.domain.model.LUSAKA)

        val state: StateFlow<DashboardState> =
            combine(
                transactions.observeBetween(Months.startMillis(month), Months.endMillis(month)),
                transactions.observeRecent(5),
                categories.observeCategories(),
                unparsed.observeNew(),
            ) { monthTxns, recent, cats, review ->
                DashboardState(
                    loading = false,
                    summary = summarise(month, monthTxns, cats),
                    recent = recent,
                    categories = cats.associateBy { it.id },
                    reviewCount = review.size,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardState())
    }

private val MONTH = DateTimeFormatter.ofPattern("MMMM yyyy")

@Composable
fun DashboardScreen(
    onTransactionClick: (Long) -> Unit,
    onSeeAll: () -> Unit,
    onReview: () -> Unit,
    onPasteImport: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val summary = state.summary
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Text(
                summary?.month?.let(MONTH::format) ?: "This month",
                modifier = Modifier.padding(16.dp).semantics { heading() },
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        if (state.loading) {
            item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp)) }
            return@LazyColumn
        }
        if (summary == null || (summary.transactionCount == 0 && state.recent.isEmpty())) {
            item {
                EmptyState(
                    "No transactions yet",
                    "When mobile money messages arrive they'll appear here. You can also paste a message in.",
                ) { TextButton(onClick = onPasteImport) { Text("Paste a message") } }
            }
            return@LazyColumn
        }
        item {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TotalCard("Money in", summary.incomeNgwee, MoneyColours.income, Modifier.weight(1f))
                TotalCard("Money out", summary.spendingNgwee, MoneyColours.spending, Modifier.weight(1f))
                TotalCard("Fees", summary.feesNgwee, MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
            }
        }
        if (state.reviewCount > 0) {
            item {
                TextButton(onClick = onReview, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text("${state.reviewCount} messages need review")
                }
            }
        }
        if (summary.spendingByCategory.isNotEmpty()) {
            item { SectionHeader("Top spending") }
            val max =
                summary.spendingByCategory
                    .first()
                    .totalNgwee
                    .coerceAtLeast(1)
            items(summary.spendingByCategory.take(5), key = { "c" + it.category.id }) { total ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    CategoryBadge(total.category)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Row {
                            Text(total.category.name, Modifier.weight(1f))
                            Text(Kwacha.format(total.totalNgwee), style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { total.totalNgwee.toFloat() / max },
                            modifier = Modifier.fillMaxWidth(),
                            color =
                                androidx.compose.ui.graphics
                                    .Color(total.category.colour),
                        )
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionHeader("Recent", Modifier.weight(1f))
                TextButton(onClick = onSeeAll, modifier = Modifier.padding(end = 8.dp)) { Text("See all") }
            }
        }
        items(state.recent, key = { it.id }) { t ->
            TransactionRow(t, state.categories[t.categoryId], onClick = { onTransactionClick(t.id) })
        }
    }
}

@Composable
private fun TotalCard(
    label: String,
    ngwee: Long,
    colour: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(Kwacha.formatCompact(ngwee), style = MaterialTheme.typography.titleLarge, color = colour)
            Text(Kwacha.format(ngwee), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
