package zm.kwachatrack.app

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import zm.kwachatrack.feature.dashboard.DashboardScreen
import zm.kwachatrack.feature.insights.InsightsScreen
import zm.kwachatrack.feature.onboarding.OnboardingScreen
import zm.kwachatrack.feature.settings.PrivacyPolicyScreen
import zm.kwachatrack.feature.settings.SettingsScreen
import zm.kwachatrack.feature.transactions.PasteImportScreen
import zm.kwachatrack.feature.transactions.ReviewQueueScreen
import zm.kwachatrack.feature.transactions.TransactionDetailScreen
import zm.kwachatrack.feature.transactions.TransactionDetailViewModel
import zm.kwachatrack.feature.transactions.TransactionListScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val TRANSACTIONS = "transactions"
    const val INSIGHTS = "insights"
    const val SETTINGS = "settings"
    const val DETAIL = "transaction/{${TransactionDetailViewModel.ARG_ID}}"
    const val REVIEW = "review"
    const val PASTE = "paste"
    const val PRIVACY = "privacy"

    fun detail(id: Long) = "transaction/$id"
}

private data class Tab(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val TABS =
    listOf(
        Tab(Routes.HOME, "Home", Icons.Filled.Home),
        Tab(Routes.TRANSACTIONS, "Transactions", Icons.AutoMirrored.Filled.ReceiptLong),
        Tab(Routes.INSIGHTS, "Insights", Icons.Filled.Insights),
        Tab(Routes.SETTINGS, "Settings", Icons.Filled.Settings),
    )

private val TITLES =
    mapOf(
        Routes.HOME to "KwachaTrack",
        Routes.TRANSACTIONS to "Transactions",
        Routes.INSIGHTS to "Insights",
        Routes.SETTINGS to "Settings",
        Routes.DETAIL to "Transaction",
        Routes.REVIEW to "Review messages",
        Routes.PASTE to "Paste messages",
        Routes.PRIVACY to "Privacy",
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KwachaNavHost(
    onboarded: Boolean,
    shareCount: Int,
    nav: NavHostController = rememberNavController(),
) {
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val isTab = TABS.any { it.route == route }

    LaunchedEffect(shareCount) { if (shareCount > 0) nav.navigate(Routes.PASTE) { launchSingleTop = true } }

    Scaffold(
        topBar = {
            if (route != null && route != Routes.ONBOARDING) {
                TopAppBar(
                    title = { Text(TITLES[route].orEmpty()) },
                    navigationIcon = {
                        if (!isTab) {
                            IconButton(onClick = { nav.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (isTab) {
                NavigationBar {
                    TABS.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = if (onboarded) Routes.HOME else Routes.ONBOARDING, modifier = Modifier.padding(padding)) {
            composable(Routes.ONBOARDING) {
                val toHome = { nav.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } } }
                OnboardingScreen(
                    onFinished = toHome,
                    onManualImport = {
                        toHome()
                        nav.navigate(Routes.PASTE)
                    },
                )
            }
            composable(Routes.HOME) {
                DashboardScreen(
                    onTransactionClick = { nav.navigate(Routes.detail(it)) },
                    onSeeAll = { nav.navigate(Routes.TRANSACTIONS) },
                    onReview = { nav.navigate(Routes.REVIEW) },
                    onPasteImport = { nav.navigate(Routes.PASTE) },
                )
            }
            composable(Routes.TRANSACTIONS) { TransactionListScreen(onTransactionClick = { nav.navigate(Routes.detail(it)) }) }
            composable(Routes.INSIGHTS) { InsightsScreen() }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onReview = { nav.navigate(Routes.REVIEW) },
                    onPasteImport = { nav.navigate(Routes.PASTE) },
                    onPrivacy = { nav.navigate(Routes.PRIVACY) },
                )
            }
            composable(
                Routes.DETAIL,
                arguments = listOf(navArgument(TransactionDetailViewModel.ARG_ID) { type = NavType.LongType }),
            ) { TransactionDetailScreen() }
            composable(Routes.REVIEW) { ReviewQueueScreen() }
            composable(Routes.PASTE) { PasteImportScreen() }
            composable(Routes.PRIVACY) { PrivacyPolicyScreen() }
        }
    }
}
