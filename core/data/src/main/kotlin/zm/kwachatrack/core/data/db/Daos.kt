package zm.kwachatrack.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import zm.kwachatrack.core.domain.model.MatchType
import zm.kwachatrack.core.domain.model.UnparsedStatus

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE timestamp >= :from AND timestamp < :to ORDER BY timestamp DESC")
    fun observeBetween(
        from: Long,
        to: Long,
    ): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    fun observeById(id: Long): Flow<TransactionEntity?>

    @Query("SELECT * FROM transactions ORDER BY timestamp")
    suspend fun getAll(): List<TransactionEntity>

    /** Returns -1 when the (provider, providerTxnId) pair already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: TransactionEntity): Long

    @Query("UPDATE transactions SET categoryId = :categoryId WHERE id = :id")
    suspend fun updateCategory(
        id: Long,
        categoryId: Long,
    )

    /** Matches the domain's normalisation: case-insensitive, trimmed. Internal double spaces are rare enough to ignore here. */
    @Query("UPDATE transactions SET categoryId = :categoryId WHERE UPPER(TRIM(counterparty)) = UPPER(TRIM(:counterparty))")
    suspend fun updateCategoryForCounterparty(
        counterparty: String,
        categoryId: Long,
    )
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY isIncome, name")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY isIncome, name")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT * FROM category_rules ORDER BY createdByUser DESC, pattern")
    fun observeRules(): Flow<List<CategoryRuleEntity>>

    @Query("SELECT * FROM category_rules")
    suspend fun getRules(): List<CategoryRuleEntity>

    @Query("SELECT * FROM category_rules WHERE createdByUser = 1 AND matchType = :matchType AND pattern = :pattern LIMIT 1")
    suspend fun findUserRule(
        matchType: MatchType,
        pattern: String,
    ): CategoryRuleEntity?

    @Upsert
    suspend fun upsertRule(rule: CategoryRuleEntity)

    @Query("DELETE FROM category_rules WHERE id = :id")
    suspend fun deleteRule(id: Long)
}

@Dao
interface UnparsedSmsDao {
    @Query("SELECT * FROM unparsed_sms WHERE status = 'NEW' ORDER BY receivedAt DESC")
    fun observeNew(): Flow<List<UnparsedSmsEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: UnparsedSmsEntity): Long

    @Query("UPDATE unparsed_sms SET status = :status WHERE id = :id")
    suspend fun setStatus(
        id: Long,
        status: UnparsedStatus,
    )
}
