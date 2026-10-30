package zm.kwachatrack.core.domain.usecase

import zm.kwachatrack.core.domain.model.CategoryRule
import zm.kwachatrack.core.domain.model.ParseResult
import zm.kwachatrack.core.domain.model.RawSms
import zm.kwachatrack.core.domain.model.UnparsedSms
import zm.kwachatrack.core.domain.model.toTransaction
import zm.kwachatrack.core.domain.repository.CategoryRepository
import zm.kwachatrack.core.domain.repository.DbTransactionRunner
import zm.kwachatrack.core.domain.repository.SettingsRepository
import zm.kwachatrack.core.domain.repository.SmsInbox
import zm.kwachatrack.core.domain.repository.TransactionParser
import zm.kwachatrack.core.domain.repository.TransactionRepository
import zm.kwachatrack.core.domain.repository.UnparsedSmsRepository

enum class ImportOutcome { IMPORTED, DUPLICATE, UNRECOGNISED, NOT_PROVIDER, PROVIDER_DISABLED }

data class ImportSummary(
    val scanned: Int = 0,
    val imported: Int = 0,
    val duplicates: Int = 0,
    val unrecognised: Int = 0,
) {
    operator fun plus(outcome: ImportOutcome): ImportSummary =
        when (outcome) {
            ImportOutcome.IMPORTED -> copy(scanned = scanned + 1, imported = imported + 1)
            ImportOutcome.DUPLICATE -> copy(scanned = scanned + 1, duplicates = duplicates + 1)
            ImportOutcome.UNRECOGNISED -> copy(scanned = scanned + 1, unrecognised = unrecognised + 1)
            ImportOutcome.NOT_PROVIDER, ImportOutcome.PROVIDER_DISABLED -> copy(scanned = scanned + 1)
        }
}

/**
 * UC1/UC2/FR8: parse SMS, skip duplicates, categorise, store, and queue failures for review.
 */
class ImportSmsUseCase(
    private val parser: TransactionParser,
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
    private val unparsed: UnparsedSmsRepository,
    private val settings: SettingsRepository,
    private val dbTransaction: DbTransactionRunner,
    private val categoriser: Categoriser = Categoriser(),
) {
    /** One SMS from the receiver. */
    suspend fun importOne(sms: RawSms): ImportOutcome {
        if (!parser.isProviderSender(sms.sender)) return ImportOutcome.NOT_PROVIDER
        return handle(sms, parser.parse(sms), categories.getRules(), queueFailure = true)
    }

    /** First-run inbox scan. Reports progress as (done, total). */
    suspend fun importInbox(
        inbox: SmsInbox,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): ImportSummary {
        val messages = inbox.read(parser::isProviderSender).sortedBy { it.receivedAt }
        val rules = categories.getRules()
        var summary = ImportSummary()
        // Commit in chunks: fast like one big transaction, but progress is visible.
        messages.chunked(CHUNK).forEachIndexed { index, chunk ->
            dbTransaction.run {
                chunk.forEach { sms -> summary += handle(sms, parser.parse(sms), rules, queueFailure = true) }
            }
            onProgress(minOf((index + 1) * CHUNK, messages.size), messages.size)
        }
        if (messages.isEmpty()) onProgress(0, 0)
        return summary
    }

    /**
     * FR8: text pasted or shared by the user. Several messages may be separated by blank lines.
     * Failures are returned rather than queued, because the user is looking at them right now.
     */
    suspend fun importPasted(
        text: String,
        now: Long,
    ): PasteResult {
        val rules = categories.getRules()
        val parts = splitPasted(text)
        var summary = ImportSummary()
        val failed = mutableListOf<String>()
        dbTransaction.run {
            parts.forEach { body ->
                val result = parser.parseAnySender(body, now)
                val outcome = handle(RawSms(PASTED_SENDER, body, now), result, rules, queueFailure = false)
                if (outcome == ImportOutcome.UNRECOGNISED) failed += body
                summary += outcome
            }
        }
        return PasteResult(summary, failed)
    }

    private suspend fun handle(
        sms: RawSms,
        result: ParseResult,
        rules: List<CategoryRule>,
        queueFailure: Boolean,
    ): ImportOutcome =
        when (result) {
            is ParseResult.Failure -> {
                if (queueFailure) {
                    unparsed.add(UnparsedSms(sender = sms.sender, body = sms.body, receivedAt = sms.receivedAt, reason = result.reason))
                }
                ImportOutcome.UNRECOGNISED
            }
            is ParseResult.Success -> {
                val parsed = result.transaction
                if (parsed.provider !in settings.enabledProviders()) {
                    ImportOutcome.PROVIDER_DISABLED
                } else {
                    val categoryId = categoriser.categorise(parsed.counterparty, parsed.type, rules)
                    if (transactions.insertIfNew(parsed.toTransaction(categoryId))) {
                        ImportOutcome.IMPORTED
                    } else {
                        ImportOutcome.DUPLICATE
                    }
                }
            }
        }

    data class PasteResult(
        val summary: ImportSummary,
        val failedMessages: List<String>,
    )

    companion object {
        const val PASTED_SENDER = "PASTED"
        private const val CHUNK = 250

        fun splitPasted(text: String): List<String> =
            text
                .replace("\r\n", "\n")
                .split(Regex("\\n\\s*\\n"))
                .map { it.replace('\n', ' ').trim() }
                .filter { it.isNotEmpty() }
    }
}
