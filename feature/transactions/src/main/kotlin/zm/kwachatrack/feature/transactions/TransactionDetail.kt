package zm.kwachatrack.feature.transactions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import zm.kwachatrack.core.domain.model.Category
import zm.kwachatrack.core.domain.model.LUSAKA
import zm.kwachatrack.core.domain.model.Transaction
import zm.kwachatrack.core.domain.money.Kwacha
import zm.kwachatrack.core.domain.repository.CategoryRepository
import zm.kwachatrack.core.domain.repository.TransactionRepository
import zm.kwachatrack.core.domain.usecase.RecategoriseUseCase
import zm.kwachatrack.core.ui.CategoryBadge
import zm.kwachatrack.core.ui.MoneyText
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class DetailState(
    val transaction: Transaction? = null,
    val categories: List<Category> = emptyList(),
)

@HiltViewModel
class TransactionDetailViewModel
    @Inject
    constructor(
        savedState: SavedStateHandle,
        transactions: TransactionRepository,
        categories: CategoryRepository,
        private val recategorise: RecategoriseUseCase,
    ) : ViewModel() {
        private val id: Long = checkNotNull(savedState.get<Long>(ARG_ID))

        val state =
            combine(transactions.observeById(id), categories.observeCategories()) { t, c -> DetailState(t, c) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailState())

        fun setCategory(
            transaction: Transaction,
            categoryId: Long,
            applyToCounterparty: Boolean,
        ) {
            viewModelScope.launch { recategorise(transaction, categoryId, applyToCounterparty) }
        }

        companion object {
            const val ARG_ID = "id"
        }
    }

private val WHEN = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy, HH:mm")

@Composable
fun TransactionDetailScreen(viewModel: TransactionDetailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val t = state.transaction ?: return
    val category = state.categories.firstOrNull { it.id == t.categoryId }
    var picking by remember { mutableStateOf(false) }
    var applyToAll by rememberSaveable { mutableStateOf(true) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(t.counterparty ?: t.type.label, style = MaterialTheme.typography.headlineSmall)
        MoneyText(t.amountNgwee, t.type.direction, style = MaterialTheme.typography.displaySmall)
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Type", t.type.label)
                Field("Provider", t.provider.displayName)
                Field("Date", WHEN.format(Instant.ofEpochMilli(t.timestamp).atZone(LUSAKA)))
                Field("Fee", Kwacha.format(t.feeNgwee))
                t.balanceAfterNgwee?.let { Field("Balance after", Kwacha.format(it)) }
                Field("Transaction ID", t.providerTxnId)
            }
        }
        Text("Category", style = MaterialTheme.typography.titleMedium)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClickLabel = "Change category") { picking = true },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryBadge(category)
            Spacer(Modifier.width(12.dp))
            Text(category?.name ?: "Uncategorised", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            TextButton(onClick = { picking = true }) { Text("Change") }
        }
        if (t.counterparty != null) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(applyToAll, role = Role.Switch) { applyToAll = it },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Apply to all from ${t.counterparty}", Modifier.weight(1f))
                Switch(checked = applyToAll, onCheckedChange = null)
            }
        }
        HorizontalDivider()
        Text("Original message", style = MaterialTheme.typography.titleMedium)
        Text(t.rawSms, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
    }

    if (picking) {
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text("Choose a category") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    state.categories.forEach { c ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable {
                                viewModel.setCategory(t, c.id, applyToAll)
                                picking = false
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CategoryBadge(c)
                            Spacer(Modifier.width(12.dp))
                            Text(c.name)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { OutlinedButton(onClick = { picking = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
) {
    Row {
        Text(label, Modifier.weight(0.4f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(0.6f))
    }
}
