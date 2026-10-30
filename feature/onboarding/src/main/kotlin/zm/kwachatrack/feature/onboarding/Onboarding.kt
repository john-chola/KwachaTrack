package zm.kwachatrack.feature.onboarding

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import zm.kwachatrack.core.domain.repository.SettingsRepository
import zm.kwachatrack.core.domain.repository.SmsInbox
import zm.kwachatrack.core.domain.usecase.ImportSmsUseCase
import zm.kwachatrack.core.domain.usecase.ImportSummary
import javax.inject.Inject

sealed interface OnboardingState {
    data object Intro : OnboardingState

    data object PermissionDenied : OnboardingState

    data class Importing(
        val done: Int,
        val total: Int,
    ) : OnboardingState

    data class Finished(
        val summary: ImportSummary,
    ) : OnboardingState

    data class Error(
        val message: String,
    ) : OnboardingState
}

@HiltViewModel
class OnboardingViewModel
    @Inject
    constructor(
        private val importSms: ImportSmsUseCase,
        private val inbox: SmsInbox,
        private val settings: SettingsRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow<OnboardingState>(OnboardingState.Intro)
        val state = _state.asStateFlow()

        fun onPermissionResult(granted: Boolean) {
            if (!granted) {
                _state.value = OnboardingState.PermissionDenied
                return
            }
            _state.value = OnboardingState.Importing(0, 0)
            viewModelScope.launch {
                _state.value =
                    try {
                        val summary = importSms.importInbox(inbox) { done, total -> _state.value = OnboardingState.Importing(done, total) }
                        settings.setOnboardingDone()
                        OnboardingState.Finished(summary)
                    } catch (e: SecurityException) {
                        OnboardingState.PermissionDenied
                    } catch (e: Exception) {
                        OnboardingState.Error(e.message ?: "Import failed")
                    }
            }
        }

        fun skipToManual(onDone: () -> Unit) {
            viewModelScope.launch {
                settings.setOnboardingDone()
                onDone()
            }
        }
    }

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    onManualImport: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            viewModel.onPermissionResult(result[Manifest.permission.READ_SMS] == true)
        }
    val ask = { launcher.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text("KwachaTrack", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
        Text(
            "See where your mobile money goes. KwachaTrack reads your MTN MoMo, Airtel Money and Zamtel Kwacha messages " +
                "and turns them into a spending summary. No typing.",
            style = MaterialTheme.typography.bodyLarge,
        )
        PrivacyCard()

        when (val s = state) {
            OnboardingState.Intro -> PermissionButtons(ask, { viewModel.skipToManual(onManualImport) })
            OnboardingState.PermissionDenied -> {
                Text(
                    "Without SMS access the app can't read your messages automatically. " +
                        "You can still paste or share messages into it.",
                    color = MaterialTheme.colorScheme.error,
                )
                PermissionButtons(ask, { viewModel.skipToManual(onManualImport) })
            }
            is OnboardingState.Importing -> ImportProgress(s.done, s.total)
            is OnboardingState.Finished -> Finished(s.summary, onFinished)
            is OnboardingState.Error -> {
                Text("Something went wrong: ${s.message}", color = MaterialTheme.colorScheme.error)
                PermissionButtons(ask, { viewModel.skipToManual(onManualImport) })
            }
        }
    }
}

@Composable
private fun PrivacyCard() {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text("Your data stays on this phone", style = MaterialTheme.typography.titleMedium)
            Text(
                "• Only messages from mobile money providers are read.\n" +
                    "• Everything is stored encrypted on this phone.\n" +
                    "• The app has no internet permission, so nothing can be sent anywhere.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun PermissionButtons(
    onAllow: () -> Unit,
    onManual: () -> Unit,
) {
    Button(onClick = onAllow, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Allow SMS access") }
    OutlinedButton(onClick = onManual, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Import manually instead") }
}

@Composable
private fun ImportProgress(
    done: Int,
    total: Int,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Reading your mobile money messages…", style = MaterialTheme.typography.titleMedium)
        if (total == 0) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(progress = { done.toFloat() / total }, modifier = Modifier.fillMaxWidth())
            Text("$done of $total messages", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun Finished(
    summary: ImportSummary,
    onFinished: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.Start) {
        Text("All done", style = MaterialTheme.typography.titleLarge)
        Text("Found ${summary.imported} transactions.")
        if (summary.duplicates > 0) Text("${summary.duplicates} duplicates skipped.")
        if (summary.unrecognised > 0) {
            Text("${summary.unrecognised} messages weren't recognised. You can review them later in Settings.")
        }
        Button(onClick = onFinished, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("See my money") }
    }
}
