package zm.kwachatrack.app

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import zm.kwachatrack.core.domain.repository.SettingsRepository
import zm.kwachatrack.core.ui.KwachaTheme
import zm.kwachatrack.feature.transactions.SharedTextInbox
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var settings: SettingsRepository

    @Inject lateinit var sharedText: SharedTextInbox

    /** Bumped when text is shared in, so the UI navigates to the paste screen. */
    private var shareCount by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Balances are on almost every screen: keep them out of screenshots and the recent-apps view.
        if (!BuildConfig.DEBUG) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        handleShare(intent)
        setContent {
            KwachaTheme {
                val onboarded by settings.observeOnboardingDone().collectAsStateWithLifecycle(initialValue = null)
                onboarded?.let { KwachaNavHost(onboarded = it, shareCount = shareCount) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    private fun handleShare(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }?.let {
                sharedText.put(it)
                shareCount++
            }
        }
    }
}
