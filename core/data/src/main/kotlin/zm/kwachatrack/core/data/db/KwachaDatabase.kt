package zm.kwachatrack.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import zm.kwachatrack.core.domain.model.SystemCategories

@Database(
    entities = [TransactionEntity::class, CategoryEntity::class, CategoryRuleEntity::class, UnparsedSmsEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class KwachaDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao

    abstract fun categoryDao(): CategoryDao

    abstract fun unparsedSmsDao(): UnparsedSmsDao

    companion object {
        const val NAME = "kwachatrack.db"

        /**
         * Encrypted with SQLCipher. The passphrase comes from [DatabaseKey], wrapped by the Android Keystore.
         * Add each future schema change as a Migration here; never use fallbackToDestructiveMigration,
         * so updates never wipe user data.
         */
        fun create(
            context: Context,
            passphrase: ByteArray,
        ): KwachaDatabase {
            System.loadLibrary("sqlcipher")
            return Room
                .databaseBuilder(context, KwachaDatabase::class.java, NAME)
                .openHelperFactory(SupportOpenHelperFactory(passphrase))
                .addCallback(SeedCallback)
                // .addMigrations(MIGRATION_1_2)
                .build()
        }

        fun inMemory(context: Context): KwachaDatabase =
            Room
                .inMemoryDatabaseBuilder(context, KwachaDatabase::class.java)
                .addCallback(SeedCallback)
                .allowMainThreadQueries()
                .build()
    }
}

/** Seeds categories and starter rules once, when the database file is first created. */
internal object SeedCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        SystemCategories.all.forEach { c ->
            db.execSQL(
                "INSERT INTO categories (id, name, iconKey, colour, isIncome, isSystem) VALUES (?, ?, ?, ?, ?, ?)",
                arrayOf<Any>(c.id, c.name, c.iconKey, c.colour, if (c.isIncome) 1 else 0, 1),
            )
        }
        SystemCategories.seededRules.forEach { r ->
            db.execSQL(
                "INSERT INTO category_rules (matchType, pattern, categoryId, createdByUser) VALUES (?, ?, ?, 0)",
                arrayOf<Any>(r.matchType.name, r.pattern, r.categoryId),
            )
        }
    }
}
