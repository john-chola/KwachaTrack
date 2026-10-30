package zm.kwachatrack.core.parser

import zm.kwachatrack.core.domain.model.ParseResult
import zm.kwachatrack.core.domain.model.Provider
import zm.kwachatrack.core.domain.model.RawSms
import zm.kwachatrack.core.domain.model.TxnType
import zm.kwachatrack.core.domain.repository.TransactionParser

/** Picks the provider parser by sender. Pure Fabrication; implements the domain's port. */
class ParserRegistry(
    private val parsers: List<SmsParser>,
    val templateVersion: Int,
) : TransactionParser {
    override fun isProviderSender(sender: String): Boolean = parsers.any { it.canHandle(sender) }

    override fun parse(sms: RawSms): ParseResult =
        parsers.firstOrNull { it.canHandle(sms.sender) }?.parse(sms)
            ?: ParseResult.Failure("Unknown sender")

    override fun parseAnySender(
        body: String,
        receivedAt: Long,
    ): ParseResult {
        // Prefer a "template matched but a field was bad" reason over "nothing matched".
        var specific: ParseResult.Failure? = null
        for (parser in parsers) {
            when (val r = parser.parse(RawSms(parser.provider.name, body, receivedAt))) {
                is ParseResult.Success -> return r
                is ParseResult.Failure -> if (!r.reason.startsWith(NO_MATCH_PREFIX)) specific = specific ?: r
            }
        }
        return specific ?: ParseResult.Failure("Not a recognised mobile money message")
    }

    companion object {
        const val RESOURCE = "/templates.json"
        internal const val NO_MATCH_PREFIX = "No "

        /** Loads the bundled templates from the classpath (works on the JVM and on Android). */
        fun fromBundledTemplates(): ParserRegistry {
            val text =
                ParserRegistry::class.java
                    .getResourceAsStream(RESOURCE)
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    ?: error("templates.json missing from parser resources")
            return TemplateLoader.load(text)
        }
    }
}

/**
 * Reads templates.json:
 * { "version": 1, "providers": [ { "provider": "MTN", "senders": [...], "templates": [ {id,type,pattern,dateFormat} ] } ] }
 */
object TemplateLoader {
    fun load(json: String): ParserRegistry {
        val root = MiniJson.parse(json) as? Map<*, *> ?: throw IllegalArgumentException("Root must be an object")
        val version = (root["version"] as? Double)?.toInt() ?: throw IllegalArgumentException("Missing version")
        val providers = root["providers"] as? List<*> ?: throw IllegalArgumentException("Missing providers")
        val parsers =
            providers.map { p ->
                val obj = p as? Map<*, *> ?: throw IllegalArgumentException("Provider must be an object")
                val provider = Provider.valueOf(obj.string("provider"))
                val senders = (obj["senders"] as? List<*>)?.map { it.toString() }?.toSet().orEmpty()
                require(senders.isNotEmpty()) { "$provider has no senders" }
                val templates =
                    (obj["templates"] as? List<*>).orEmpty().map { t ->
                        val tObj = t as? Map<*, *> ?: throw IllegalArgumentException("Template must be an object")
                        val id = tObj.string("id")
                        val template =
                            SmsTemplate(
                                id = id,
                                type = TxnType.valueOf(tObj.string("type")),
                                regex = Regex(tObj.string("pattern"), RegexOption.IGNORE_CASE),
                                dateFormat = tObj["dateFormat"] as? String,
                            )
                        require("amount" in template.groupNames) { "$id has no (?<amount>) group" }
                        require("txnId" in template.groupNames) { "$id has no (?<txnId>) group" }
                        if ("date" in template.groupNames) require(template.dateFormat != null) { "$id has a date group but no dateFormat" }
                        template
                    }
                TemplateSmsParser(provider, senders, templates, version)
            }
        val ids =
            providers.flatMap { p -> ((p as Map<*, *>)["templates"] as? List<*>).orEmpty().map { (it as Map<*, *>)["id"] } }
        val duplicates =
            ids
                .groupingBy { it }
                .eachCount()
                .filterValues { it > 1 }
                .keys
        require(duplicates.isEmpty()) { "Duplicate template ids: $duplicates" }
        return ParserRegistry(parsers, version)
    }

    private fun Map<*, *>.string(key: String): String = this[key] as? String ?: throw IllegalArgumentException("Missing \"$key\"")
}
