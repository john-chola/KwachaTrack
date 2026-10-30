package zm.kwachatrack.core.parser

import zm.kwachatrack.core.domain.model.ParseResult
import zm.kwachatrack.core.domain.model.ParsedTransaction
import zm.kwachatrack.core.domain.model.Provider
import zm.kwachatrack.core.domain.model.RawSms
import zm.kwachatrack.core.domain.model.TxnType

/** Strategy: one implementation per provider. */
interface SmsParser {
    val provider: Provider

    fun canHandle(sender: String): Boolean

    fun parse(sms: RawSms): ParseResult
}

/**
 * One SMS format. Named groups the regex may define:
 * amount (required), txnId (required), fee, counterparty, balance, date.
 */
data class SmsTemplate(
    val id: String,
    val type: TxnType,
    val regex: Regex,
    val dateFormat: String? = null,
) {
    val groupNames: Set<String> = GROUP_NAME.findAll(regex.pattern).map { it.groupValues[1] }.toSet()

    private companion object {
        val GROUP_NAME = Regex("\\(\\?<([a-zA-Z][a-zA-Z0-9]*)>")
    }
}

/**
 * Tries each template in order (most specific first); the first match wins.
 * Templates are data (templates.json), so new formats need no code change: Protected Variations.
 */
class TemplateSmsParser(
    override val provider: Provider,
    senders: Set<String>,
    private val templates: List<SmsTemplate>,
    private val parserVersion: Int,
) : SmsParser {
    private val senderKeys = senders.map(::senderKey).toSet()

    override fun canHandle(sender: String): Boolean = senderKey(sender) in senderKeys

    override fun parse(sms: RawSms): ParseResult {
        val body = sms.body.replace(Regex("\\s+"), " ").trim()
        var lastProblem: String? = null
        for (template in templates) {
            val match = template.regex.find(body) ?: continue
            when (val r = extract(template, match, sms, body)) {
                is ParseResult.Success -> return r
                is ParseResult.Failure -> lastProblem = "${template.id}: ${r.reason}"
            }
        }
        return ParseResult.Failure(lastProblem ?: "No ${provider.displayName} template matched")
    }

    private fun extract(
        template: SmsTemplate,
        match: MatchResult,
        sms: RawSms,
        body: String,
    ): ParseResult {
        fun group(name: String): String? = if (name in template.groupNames) match.groups[name]?.value else null

        val amount = AmountNormaliser.toNgwee(group("amount")) ?: return ParseResult.Failure("Bad or missing amount")
        if (amount <= 0) return ParseResult.Failure("Amount must be above zero")

        val txnId = group("txnId")?.trim()?.trimEnd('.')
        if (txnId.isNullOrEmpty()) return ParseResult.Failure("Missing transaction ID")

        val feeText = group("fee")
        val fee = if (feeText == null) 0L else AmountNormaliser.toNgwee(feeText) ?: return ParseResult.Failure("Bad fee")

        val balanceText = group("balance")
        val balance =
            if (balanceText == null) null else AmountNormaliser.toNgwee(balanceText) ?: return ParseResult.Failure("Bad balance")

        val timestamp = DateNormaliser.toEpochMillis(group("date"), template.dateFormat) ?: sms.receivedAt

        return ParseResult.Success(
            ParsedTransaction(
                providerTxnId = txnId,
                provider = provider,
                type = template.type,
                amountNgwee = amount,
                feeNgwee = fee,
                counterparty = NameNormaliser.clean(group("counterparty")),
                balanceAfterNgwee = balance,
                timestamp = timestamp,
                rawSms = body,
                parserVersion = parserVersion,
                templateId = template.id,
            ),
        )
    }

    companion object {
        /** Sender IDs vary in case and spacing ("MoMo", "MOMO", "Airtel Money"). */
        fun senderKey(sender: String): String = sender.uppercase().filter { it.isLetterOrDigit() }
    }
}
