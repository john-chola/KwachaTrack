package zm.kwachatrack.feature.transactions

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import zm.kwachatrack.core.domain.model.UnparsedSms
import zm.kwachatrack.core.domain.model.UnparsedStatus
import zm.kwachatrack.core.domain.repository.UnparsedSmsRepository
import zm.kwachatrack.core.domain.usecase.ImportSmsUseCase
import zm.kwachatrack.core.ui.EmptyState
import javax.inject.Inject
import javax.inject.Singleton

// ---------- Review queue (UC4 / FR6) ----------

@HiltViewModel
class ReviewViewModel
    @Inject
    constructor(
        private val repo: UnparsedSmsRepository,
    ) : ViewModel() {
        val items = repo.observeNew().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        fun ignore(sms: UnparsedSms) = viewModelScope.launch { repo.setStatus(sms.id, UnparsedStatus.IGNORED) }

        fun reported(sms: UnparsedSms) = viewModelScope.launch { repo.setStatus(sms.id, UnparsedStatus.REPORTED) }
    }

@Composable
fun ReviewQueueScreen(viewModel: ReviewViewModel = hiltViewModel()) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val context = LocalContext.current
    if (items.isEmpty()) {
        EmptyState("Nothing to review", "Every mobile money message so far was understood.")
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(
                "These messages came from a provider but weren't understood. Reporting opens your share menu " +
                    "so you can send the format (remove names and numbers first) to the developer.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        items(items, key = { it.id }) { sms ->
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(sms.sender, style = MaterialTheme.typography.labelLarge)
                    Text(sms.body, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "Why: ${sms.reason}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { viewModel.ignore(sms) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Ignore") }
                        Button(
                            onClick = {
                                val send =
                                    Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "KwachaTrack: unrecognised SMS format")
                                        putExtra(Intent.EXTRA_TEXT, "Sender: ${sms.sender}\n\n${sms.body}")
                                    }
                                context.startActivity(Intent.createChooser(send, "Report format"))
                                viewModel.reported(sms)
                            },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text("Report") }
                    }
                }
            }
        }
    }
}

// ---------- Paste / share import (FR8) ----------

data class PasteState(
    val text: String = "",
    val busy: Boolean = false,
    val result: ImportSmsUseCase.PasteResult? = null,
)

/** Holds text shared into the app (ACTION_SEND) until the paste screen picks it up. */
@Singleton
class SharedTextInbox
    @Inject
    constructor() {
        private var pending: String? = null

        fun put(text: String) {
            pending = text
        }

        fun take(): String? = pending.also { pending = null }
    }

@HiltViewModel
class PasteImportViewModel
    @Inject
    constructor(
        sharedText: SharedTextInbox,
        private val importSms: ImportSmsUseCase,
    ) : ViewModel() {
        private val _state = MutableStateFlow(PasteState(text = sharedText.take().orEmpty()))
        val state = _state.asStateFlow()

        fun setText(text: String) = _state.update { it.copy(text = text, result = null) }

        fun import() {
            val text = _state.value.text
            if (text.isBlank()) return
            _state.update { it.copy(busy = true) }
            viewModelScope.launch {
                val result = importSms.importPasted(text, System.currentTimeMillis())
                _state.update { PasteState(text = result.failedMessages.joinToString("\n\n"), result = result) }
            }
        }
    }

@Composable
fun PasteImportScreen(viewModel: PasteImportViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Paste one or more mobile money messages. Leave a blank line between messages. " +
                "You can also share a message to KwachaTrack from your SMS app.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = state.text,
            onValueChange = viewModel::setText,
            modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp),
            label = { Text("Messages") },
        )
        Button(
            onClick = viewModel::import,
            enabled = !state.busy && state.text.isNotBlank(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) { Text("Import") }
        state.result?.let { r ->
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Imported ${r.summary.imported}", style = MaterialTheme.typography.titleMedium)
                    if (r.summary.duplicates > 0) Text("${r.summary.duplicates} already in the app")
                    if (r.failedMessages.isNotEmpty()) {
                        Text(
                            "${r.failedMessages.size} not recognised. They're left in the box above.",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}
