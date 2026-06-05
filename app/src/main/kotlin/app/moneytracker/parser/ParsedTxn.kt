package app.moneytracker.parser

/**
 * What every bank parser emits. Pure data; no Android types so it can be
 * unit-tested without an emulator.
 *
 * Amounts in **paise** as Long. Timestamps in epoch millis.
 * `counterparty` is plaintext here — the encryption layer wraps it before
 * it ever leaves the device.
 */
data class ParsedTxn(
    val tsMillis: Long,
    val amountPaise: Long,
    val direction: Direction,
    val channel: Channel,
    val accountLast4: String?,
    val counterparty: String?,
    val balanceAfterPaise: Long?,
    val rawHash: String,
    val bankCode: String,
) {
    enum class Direction { DEBIT, CREDIT }
    enum class Channel { UPI, CARD, NEFT, IMPS, ATM, POS, OTHER }

    override fun toString(): String = "ParsedTxn($bankCode/$direction/$channel)"
}
