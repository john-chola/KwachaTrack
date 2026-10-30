package zm.kwachatrack.feature.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import zm.kwachatrack.core.domain.model.Category
import zm.kwachatrack.core.domain.model.LUSAKA
import zm.kwachatrack.core.domain.model.Provider
import zm.kwachatrack.core.domain.model.Transaction
import zm.kwachatrack.core.domain.money.Kwacha
import zm.kwachatrack.core.domain.repository.CategoryRepository
import zm.kwachatrack.core.domain.repository.TransactionRepository
import zm.kwachatrack.core.domain.usecase.Months
import zm.kwachatrack.core.domain.usecase.TransactionFilter
import zm.kwachatrack.core.ui.EmptyState
import zm.kwachatrack.core.ui.SectionHeader
import zm.kwachatrack.core.ui.TransactionRow
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class TransactionListState(
    val month: YearMonth,
    val filter: TransactionFilter = TransactionFilter(),
    val days: List<Pair<LocalDate, List<Transaction>>> = emptyList(),
    val categories: List<Category> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TransactionListViewModel
    @Inject
    constructor(
        private val transactions: TransactionRepository,
        categories: CategoryRepository,
    ) : ViewModel() {
        private val month = MutableStateFlow(YearMonth.now(LUSAKA))
        private val filter = MutableStateFlow(TransactionFilter())

        private val monthTxns = month.flatMapLatest { transactions.observeBetween(Months.startMillis(it), Months.endMillis(it)) }

        val state: StateFlow<TransactionListState> =
            combine(month, filter, monthTxns, categories.observeCategories()) { m, f, txns, cats ->
                TransactionListState(m, f, TransactionFilter.groupByDay(f.apply(txns, cats.associateBy { it.id })), cats)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionListState(YearMonth.now(LUSAKA)))

        fun previousMonth() = month.update { it.minusMonths(1) }

        fun nextMonth() = month.update { if (it < YearMonth.now(LUSAKA)) it.plusMonths(1) else it }

        fun setQuery(q: String) = filter.update { it.copy(query = q) }

        fun toggleProvider(p: Provider) =
            filter.update { it.copy(providers = if (p in it.providers) it.providers - p else it.providers + p) }

        fun setCategory(id: Long?) = filter.update { it.copy(categoryId = if (it.categoryId == id) null else id) }
    }

private val MONTH = DateTimeFormatter.ofPattern("MMMM yyyy")
private val DAY = DateTimeFormatter.ofPattern("EEEE d MMMM")

@Composable
fun TransactionListScreen(
    onTransactionClick: (Long) -> Unit,
    viewModel: TransactionListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val categories = state.categories.associateBy { it.id }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = viewModel::previousMonth) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
                }
                Text(
                    MONTH.format(state.month),
                    Modifier.weight(1f).semantics { heading() },
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                )
                IconButton(onClick = viewModel::nextMonth) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
                }
            }
        }
        item {
            OutlinedTextField(
                value = state.filter.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text("Search name, category or ID") },
                singleLine = true,
            )
        }
        item {
            LazyRow(
                contentPadding =
                    androidx.compose.foundation.layout
                        .PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(Provider.entries) { p ->
                    FilterChip(
                        selected = p in state.filter.providers,
                        onClick = { viewModel.toggleProvider(p) },
                        label = { Text(p.displayName) },
                    )
                }
                items(state.categories, key = { it.id }) { c ->
                    FilterChip(
                        selected = state.filter.categoryId == c.id,
                        onClick = { viewModel.setCategory(c.id) },
                        label = { Text(c.name) },
                    )
                }
            }
        }
        if (state.days.isEmpty()) {
            item { EmptyState("No transactions", "Nothing matches for ${MONTH.format(state.month)}.") }
        }
        state.days.forEach { (day, txns) ->
            item(key = "d$day") {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionHeader(DAY.format(day), Modifier.weight(1f))
                        val out =
                            txns
                                .filter {
                                    it.type.direction == zm.kwachatrack.core.domain.model.Direction.OUT
                                }.sumOf { it.amountNgwee }
                        if (out > 0) {
                            Text(
                                "Out ${Kwacha.format(out)}",
                                Modifier.padding(end = 16.dp, top = 12.dp),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
            items(txns, key = { it.id }) { t -> TransactionRow(t, categories[t.categoryId], onClick = { onTransactionClick(t.id) }) }
        }
    }
}
