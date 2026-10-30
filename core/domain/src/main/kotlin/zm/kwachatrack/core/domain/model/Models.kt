package zm.kwachatrack.core.domain.model

import java.time.ZoneId

/** All dates are interpreted in Zambian local time. */
val LUSAKA: ZoneId = ZoneId.of("Africa/Lusaka")

enum class Provider(val displayName: String) {
    MTN("MTN MoMo"),
    AIRTEL("Airtel Money"),
    ZAMTEL("Zamtel Kwacha"),
}

enum class TxnType(val direction: Direction, val label: String) {
    SENT(Direction.OUT, "Sent"),
    RECEIVED(Direction.IN, "Received"),
    CASH_OUT(Direction.OUT, "Cash out"),
    CASH_IN(Direction.IN, "Cash in"),
    AIRTIME(Direction.OUT, "Airtime"),
    BILL_PAYMENT(Direction.OUT, "Bill payment"),
    MERCHANT_PAYMENT(Direction.OUT, "Merchant payment"),
    OTHER(Direction.OUT, "Other"),
}

enum class Direction { IN, OUT }

/** An SMS as read from the inbox, the receiver, or pasted by the user. */
data class RawSms(
    val sender: String,
    val body: String,
    val receivedAt: Long,
)

/** What the parser extracts from one SMS. Money is always in ngwee (K1 = 100 ngwee). */
data class ParsedTransaction(
    val providerTxnId: String,
    val provider: Provider,
    val type: TxnType,
    val amountNgwee: Long,
    val feeNgwee: Long,
    val counterparty: String?,
    val balanceAfterNgwee: Long?,
    val timestamp: Long,
    val rawSms: String,
    val parserVersion: Int,
    val templateId: String,
)

sealed interface ParseResult {
    data class Success(val transaction: ParsedTransaction) : ParseResult

    data class Failure(val reason: String) : ParseResult
}

data class Transaction(
    val id: Long = 0,
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

fun ParsedTransaction.toTransaction(categoryId: Long?): Transaction =
    Transaction(
        providerTxnId = providerTxnId,
        provider = provider,
        type = type,
        amountNgwee = amountNgwee,
        feeNgwee = feeNgwee,
        counterparty = counterparty,
        balanceAfterNgwee = balanceAfterNgwee,
        timestamp = timestamp,
        categoryId = categoryId,
        rawSms = rawSms,
        parserVersion = parserVersion,
    )

data class Category(
    val id: Long,
    val name: String,
    val iconKey: String,
    val colour: Long,
    val isIncome: Boolean,
    val isSystem: Boolean,
)

enum class MatchType { EXACT, CONTAINS }

data class CategoryRule(
    val id: Long = 0,
    val matchType: MatchType,
    val pattern: String,
    val categoryId: Long,
    val createdByUser: Boolean,
)

enum class UnparsedStatus { NEW, IGNORED, REPORTED }

data class UnparsedSms(
    val id: Long = 0,
    val sender: String,
    val body: String,
    val receivedAt: Long,
    val reason: String,
    val status: UnparsedStatus = UnparsedStatus.NEW,
)

/**
 * Categories seeded on first run. IDs are fixed so rules and defaults can refer to them.
 * Colours are ARGB.
 */
object SystemCategories {
    const val FOOD = 1L
    const val TRANSPORT = 2L
    const val UTILITIES = 3L
    const val AIRTIME = 4L
    const val RENT = 5L
    const val FAMILY = 6L
    const val BUSINESS = 7L
    const val FEES = 8L
    const val INCOME = 9L
    const val OTHER = 10L

    val all: List<Category> =
        listOf(
            Category(FOOD, "Food", "food", 0xFFE57373, isIncome = false, isSystem = true),
            Category(TRANSPORT, "Transport", "transport", 0xFF64B5F6, isIncome = false, isSystem = true),
            Category(UTILITIES, "Utilities", "utilities", 0xFFFFB74D, isIncome = false, isSystem = true),
            Category(AIRTIME, "Airtime", "airtime", 0xFF9575CD, isIncome = false, isSystem = true),
            Category(RENT, "Rent", "rent", 0xFF4DB6AC, isIncome = false, isSystem = true),
            Category(FAMILY, "Family", "family", 0xFFF06292, isIncome = false, isSystem = true),
            Category(BUSINESS, "Business", "business", 0xFF81C784, isIncome = false, isSystem = true),
            Category(FEES, "Fees", "fees", 0xFF90A4AE, isIncome = false, isSystem = true),
            Category(INCOME, "Income", "income", 0xFF2E7D32, isIncome = true, isSystem = true),
            Category(OTHER, "Other", "other", 0xFFBDBDBD, isIncome = false, isSystem = true),
        )

    /** Starter rules; users' own rules always win over these. */
    val seededRules: List<CategoryRule> =
        listOf(
            CategoryRule(matchType = MatchType.CONTAINS, pattern = "ZESCO", categoryId = UTILITIES, createdByUser = false),
            CategoryRule(matchType = MatchType.CONTAINS, pattern = "WATER", categoryId = UTILITIES, createdByUser = false),
            CategoryRule(matchType = MatchType.CONTAINS, pattern = "DSTV", categoryId = UTILITIES, createdByUser = false),
            CategoryRule(matchType = MatchType.CONTAINS, pattern = "GOTV", categoryId = UTILITIES, createdByUser = false),
            CategoryRule(matchType = MatchType.CONTAINS, pattern = "SHOPRITE", categoryId = FOOD, createdByUser = false),
            CategoryRule(matchType = MatchType.CONTAINS, pattern = "PICK N PAY", categoryId = FOOD, createdByUser = false),
            CategoryRule(matchType = MatchType.CONTAINS, pattern = "SPAR", categoryId = FOOD, createdByUser = false),
            CategoryRule(matchType = MatchType.CONTAINS, pattern = "PUMA", categoryId = TRANSPORT, createdByUser = false),
            CategoryRule(matchType = MatchType.CONTAINS, pattern = "TOTAL", categoryId = TRANSPORT, createdByUser = false),
            CategoryRule(matchType = MatchType.CONTAINS, pattern = "BUS", categoryId = TRANSPORT, createdByUser = false),
        )
}
