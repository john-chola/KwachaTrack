package zm.kwachatrack.feature.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import zm.kwachatrack.core.domain.model.Category
import zm.kwachatrack.core.domain.model.CategoryRule
import zm.kwachatrack.core.domain.model.MatchType
import zm.kwachatrack.core.domain.model.Provider
import zm.kwachatrack.core.domain.repository.CategoryRepository
import zm.kwachatrack.core.domain.repository.SettingsRepository
import zm.kwachatrack.core.domain.repository.TransactionRepository
import zm.kwachatrack.core.domain.usecase.CsvExporter
import zm.kwachatrack.core.ui.SectionHeader
import javax.inject.Inject

data class SettingsState(
    val enabledProviders: Set<Provider> = Provider.entries.toSet(),
    val rules: List<CategoryRule> = emptyList(),
    val categories: Map<Long, Category> = emptyMap(),
)

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val settings: SettingsRepository,
        private val categories: CategoryRepository,
        private val transactions: TransactionRepository,
        private val exporter: CsvExporter,
    ) : ViewModel() {
        val state =
            combine(settings.observeEnabledProviders(), categories.observeRules(), categories.observeCategories()) { p, r, c ->
                SettingsState(p, r, c.associateBy { it.id })
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())

        private val _message = MutableStateFlow<String?>(null)
        val message = _message.asStateFlow()

        fun setProvider(
            provider: Provider,
            enabled: Boolean,
        ) = viewModelScope.launch { settings.setProviderEnabled(provider, enabled) }

        fun deleteRule(rule: CategoryRule) = viewModelScope.launch { categories.deleteRule(rule.id) }

        fun exportCsv(uri: Uri) =
            viewModelScope.launch {
                _message.value =
                    try {
                        val csv = exporter.export(transactions.getAll(), categories.getCategories())
                        withContext(Dispatchers.IO) {
                            context.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray()) }
                        }
                        "Exported to CSV"
                    } catch (e: Exception) {
                        "Export failed: ${e.message}"
                    }
            }
    }

@Composable
fun SettingsScreen(
    onReview: () -> Unit,
    onPasteImport: () -> Unit,
    onPrivacy: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val export =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            if (uri != null) viewModel.exportCsv(uri)
        }
    val userRules = state.rules.filter { it.createdByUser }

    LazyColumn(Modifier.fillMaxSize()) {
        item { SectionHeader("Import") }
        item { LinkRow("Review unrecognised messages", onReview) }
        item { LinkRow("Paste or share messages", onPasteImport) }

        item { SectionHeader("Providers") }
        items(Provider.entries) { p ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .toggleable(p in state.enabledProviders, role = Role.Switch) { viewModel.setProvider(p, it) }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(p.displayName, Modifier.weight(1f))
                Switch(checked = p in state.enabledProviders, onCheckedChange = null)
            }
        }

        item { SectionHeader("Your category rules") }
        if (userRules.isEmpty()) {
            item {
                Text(
                    "None yet. Change a transaction's category and choose \"apply to all\" to create one.",
                    Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(userRules, key = { it.id }) { r ->
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (r.matchType == MatchType.EXACT) r.pattern else "Contains \"${r.pattern}\"")
                    Text(state.categories[r.categoryId]?.name ?: "?", style = MaterialTheme.typography.bodySmall)
                }
                IconButton(
                    onClick = { viewModel.deleteRule(r) },
                ) { Icon(Icons.Filled.Delete, contentDescription = "Delete rule for ${r.pattern}") }
            }
        }

        item { SectionHeader("Data") }
        item { LinkRow("Export transactions to CSV") { export.launch("kwachatrack-transactions.csv") } }
        message?.let { m -> item { Text(m, Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.primary) } }

        item { SectionHeader("About") }
        item { LinkRow("Privacy policy", onPrivacy) }
        item {
            val context = LocalContext.current
            val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
            Text("KwachaTrack ${version.orEmpty()}", Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun LinkRow(
    label: String,
    onClick: () -> Unit,
) {
    Text(
        label,
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        style = MaterialTheme.typography.bodyLarge,
    )
}

@Composable
fun PrivacyPolicyScreen() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Privacy policy", style = MaterialTheme.typography.headlineSmall)
        Text(PRIVACY_TEXT, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

/** Keep in sync with docs/privacy-policy.md, which is published on GitHub Pages. */
const val PRIVACY_TEXT =
    "What the app reads: only SMS messages from mobile money senders (MTN MoMo, Airtel Money, Zamtel Kwacha). " +
        "Other messages are skipped and never stored.\n\n" +
        "Where it is stored: in an encrypted database on your phone. The encryption key is protected by Android's Keystore. " +
        "The data is excluded from cloud backups.\n\n" +
        "What is shared: nothing. The app has no internet permission, so it cannot send your data anywhere. " +
        "If you choose to report an unrecognised message or export a CSV, you decide where it goes using your phone's share menu.\n\n" +
        "Deleting your data: uninstalling the app, or clearing its storage in Android settings, deletes everything."
