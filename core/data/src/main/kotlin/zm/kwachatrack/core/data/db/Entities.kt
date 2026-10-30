package zm.kwachatrack.core.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import zm.kwachatrack.core.domain.model.MatchType
import zm.kwachatrack.core.domain.model.Provider
import zm.kwachatrack.core.domain.model.TxnType
import zm.kwachatrack.core.domain.model.UnparsedStatus

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["provider", "providerTxnId"], unique = true),
        Index("timestamp"),
        Index("categoryId"),
    ],
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val providerTxnId: String,
    val provider: Provider,
    val type: TxnType,
    val amountNgwee: Long,
    val feeNgwee: Long = 0,
    val counterparty: String?,
    val balanceAfterNgwee: Long?,
    val timestamp: Long,
    val categoryId: Long?,
    val rawSms: String,
    val parserVersion: Int,
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val iconKey: String,
    val colour: Long,
    val isIncome: Boolean,
    val isSystem: Boolean,
)

@Entity(
    tableName = "category_rules",
    indices = [Index("categoryId"), Index(value = ["matchType", "pattern", "createdByUser"], unique = true)],
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class CategoryRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val matchType: MatchType,
    val pattern: String,
    val categoryId: Long,
    val createdByUser: Boolean,
)

@Entity(
    tableName = "unparsed_sms",
    indices = [Index(value = ["sender", "receivedAt", "body"], unique = true), Index("status")],
)
data class UnparsedSmsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    val body: String,
    val receivedAt: Long,
    val reason: String,
    val status: UnparsedStatus,
)
