package zm.kwachatrack.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import zm.kwachatrack.core.domain.repository.CategoryRepository
import zm.kwachatrack.core.domain.repository.DbTransactionRunner
import zm.kwachatrack.core.domain.repository.SettingsRepository
import zm.kwachatrack.core.domain.repository.TransactionParser
import zm.kwachatrack.core.domain.repository.TransactionRepository
import zm.kwachatrack.core.domain.repository.UnparsedSmsRepository
import zm.kwachatrack.core.domain.usecase.CsvExporter
import zm.kwachatrack.core.domain.usecase.ImportSmsUseCase
import zm.kwachatrack.core.domain.usecase.MonthlySummaryUseCase
import zm.kwachatrack.core.domain.usecase.RecategoriseUseCase
import zm.kwachatrack.core.parser.ParserRegistry
import javax.inject.Inject
import javax.inject.Singleton

@HiltAndroidApp
class KwachaApp :
    Application(),
    Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}

/** Wires the pure Kotlin domain and parser (which know nothing about Hilt) into the graph. */
@Module
@InstallIn(SingletonComponent::class)
object DomainModule {
    @Provides
    @Singleton
    fun parser(): TransactionParser = ParserRegistry.fromBundledTemplates()

    @Provides
    fun importSms(
        parser: TransactionParser,
        transactions: TransactionRepository,
        categories: CategoryRepository,
        unparsed: UnparsedSmsRepository,
        settings: SettingsRepository,
        runner: DbTransactionRunner,
    ) = ImportSmsUseCase(parser, transactions, categories, unparsed, settings, runner)

    @Provides
    fun recategorise(
        transactions: TransactionRepository,
        categories: CategoryRepository,
        runner: DbTransactionRunner,
    ) = RecategoriseUseCase(transactions, categories, runner)

    @Provides fun summary() = MonthlySummaryUseCase()

    @Provides fun csv() = CsvExporter()
}
