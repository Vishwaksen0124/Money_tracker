package app.moneytracker.parser

/**
 * One implementation per bank. The dispatcher tries each `matches(sender)`
 * in turn; the first match parses. Returning null from parse() means "this
 * looks like our bank but I don't recognise the body" — caller records a
 * parser_miss with the body hash, not the body.
 */
interface Parser {
    val bankCode: String
    fun matches(sender: String): Boolean
    fun parse(sender: String, body: String, receivedAtMillis: Long): ParsedTxn?
}
