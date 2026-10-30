package zm.kwachatrack.core.data.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import zm.kwachatrack.core.data.db.KwachaDatabase
import zm.kwachatrack.core.data.repository.PrefsSettingsRepository
import zm.kwachatrack.core.data.repository.RoomCategoryRepository
import zm.kwachatrack.core.data.repository.RoomTransactionRepository
import zm.kwachatrack.core.data.repository.RoomTransactionRunner
import zm.kwachatrack.core.data.repository.RoomUnparsedSmsRepository
import zm.kwachatrack.core.data.security.DatabaseKey
import zm.kwachatrack.core.data.sms.ContentResolverSmsInbox
import zm.kwachatrack.core.domain.repository.CategoryRepository
import zm.kwachatrack.core.domain.repository.DbTransactionRunner
import zm.kwachatrack.core.domain.repository.SettingsRepository
import zm.kwachatrack.core.domain.repository.SmsInbox
import zm.kwachatrack.core.domain.repository.TransactionRepository
import zm.kwachatrack.core.domain.repository.UnparsedSmsRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(
        @ApplicationContext context: Context,
    ): KwachaDatabase = KwachaDatabase.create(context, DatabaseKey(context).passphrase())

    @Provides fun transactionDao(db: KwachaDatabase) = db.transactionDao()

    @Provides fun categoryDao(db: KwachaDatabase) = db.categoryDao()

    @Provides fun unparsedDao(db: KwachaDatabase) = db.unparsedSmsDao()

    /** Repositories and the SMS inbox take a plain Context; supply the application one. */
    @Provides fun context(
        @ApplicationContext context: Context,
    ): Context = context
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun transactions(impl: RoomTransactionRepository): TransactionRepository

    @Binds abstract fun categories(impl: RoomCategoryRepository): CategoryRepository

    @Binds abstract fun unparsed(impl: RoomUnparsedSmsRepository): UnparsedSmsRepository

    @Binds abstract fun settings(impl: PrefsSettingsRepository): SettingsRepository

    @Binds abstract fun runner(impl: RoomTransactionRunner): DbTransactionRunner

    @Binds abstract fun inbox(impl: ContentResolverSmsInbox): SmsInbox
}
