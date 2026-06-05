package app.moneytracker.parser.sbi

import app.moneytracker.parser.ParsedTxn
import app.moneytracker.parser.Parser
import app.moneytracker.parser.Util
import java.util.Locale

/**
 * Handles SBI savings/current-account SMS formats observed in the wild:
 *   - UPI debit / credit (modern "A/C XXdddd debited by 500.0 trf to …" form)
 *   - Generic "Your A/c is debited/credited by Rs.X. Avl Bal Rs.Y" form
 *   - NEFT/IMPS credits ("credited to A/c No. … by NEFT from …")
 *   - ATM withdrawals ("withdrawn from A/c … at ATM/POS")
 *   - Card swipes ("spent on your SBI Debit Card …")
 *
 * Returns null if direction or amount can't be determined — caller logs a
 * parser_miss (sender + body hash, never body text).
 */
class SbiParser : Parser {

    override val bankCode: String = "SBI"

    override fun matches(sender: String): Boolean =
        SENDER.containsMatchIn(sender.uppercase(Locale.ROOT))

    override fun parse(sender: String, body: String, receivedAtMillis: Long): ParsedTxn? {
        val direction = direction(body) ?: return null
        val amount = amount(body, direction) ?: return null
        if (amount <= 0) return null
        return ParsedTxn(
            tsMillis = date(body) ?: receivedAtMillis,
            amountPaise = amount,
            direction = direction,
            channel = channel(body),
            accountLast4 = last4(body),
            counterparty = counterparty(body, direction),
            balanceAfterPaise = balance(body),
            rawHash = Util.rawHash(bankCode, sender, body),
            bankCode = bankCode,
        )
    }

    private fun direction(body: String): ParsedTxn.Direction? {
        val lower = body.lowercase(Locale.ROOT)
        return when {
            "debited" in lower || "withdrawn" in lower || "spent" in lower -> ParsedTxn.Direction.DEBIT
            "credited" in lower || "received" in lower                    -> ParsedTxn.Direction.CREDIT
            else -> null
        }
    }

    private fun amount(body: String, direction: ParsedTxn.Direction): Long? {
        // Prefer a Rs./INR-prefixed amount (handles bodies where the amount
        // appears before the verb, e.g. "Rs.500 debited from A/c...") — but
        // never the one inside the "Avl Bal Rs.X" phrase, or bodies with a
        // bare txn amount would report the balance as the amount.
        val balance = BALANCE_RX.find(body)?.range
        AMT_AFTER_RS.findAll(body)
            .firstOrNull { balance == null || it.range.first !in balance }
            ?.groupValues?.get(1)?.let(Util::paise)?.let { return it }
        // Fallback for modern UPI form which omits the currency prefix
        // (e.g. "debited by 500.0 trf to ...").
        val anchor = if (direction == ParsedTxn.Direction.DEBIT) DEBIT_ANCHOR else CREDIT_ANCHOR
        return Util.amountAfter(body, anchor)
    }

    private fun last4(body: String): String? = LAST4_RX.find(body)?.groupValues?.get(1)

    private fun balance(body: String): Long? =
        BALANCE_RX.find(body)?.groupValues?.get(1)?.let(Util::paise)

    private fun channel(body: String): ParsedTxn.Channel {
        val l = body.lowercase(Locale.ROOT)
        return when {
            "upi" in l || "vpa" in l || "@ok" in l                    -> ParsedTxn.Channel.UPI
            "neft" in l                                                -> ParsedTxn.Channel.NEFT
            "imps" in l                                                -> ParsedTxn.Channel.IMPS
            "atm" in l && ("withdrawn" in l || "withdrawal" in l)      -> ParsedTxn.Channel.ATM
            "pos" in l                                                 -> ParsedTxn.Channel.POS
            ("debit card" in l || "credit card" in l) && "spent" in l  -> ParsedTxn.Channel.CARD
            else                                                       -> ParsedTxn.Channel.OTHER
        }
    }

    private fun counterparty(body: String, direction: ParsedTxn.Direction): String? {
        val patterns = if (direction == ParsedTxn.Direction.DEBIT) CP_DEBIT else CP_CREDIT
        for (rx in patterns) {
            val cp = rx.find(body)?.groupValues?.get(1)?.trim().orEmpty()
            if (isPlausibleCounterparty(cp)) return cp
        }
        return null
    }

    private fun isPlausibleCounterparty(s: String): Boolean =
        s.length in 2..60 && !s.matches(NUMERIC_ONLY)

    private fun date(body: String): Long? =
        DATE_RX.find(body)?.groupValues?.get(1)?.let(Util::tryParseDate)

    private companion object {
        // SBI sender IDs (with or without carrier prefix like "VK-").
        // Word boundary on each side prevents matching merchant names that contain "SBI".
        val SENDER       = Regex("""\bSBI(?:INB|PSG|UPI|BNK|CRD)?\b""")

        val DEBIT_ANCHOR = Regex(
            """debited\s+(?:by|for|of)?|withdrawn\s+(?:of|from)?|spent\b""",
            RegexOption.IGNORE_CASE,
        )
        val CREDIT_ANCHOR = Regex(
            """credited\s+(?:by|to|with|for)?|received\b""",
            RegexOption.IGNORE_CASE,
        )
        val AMT_AFTER_RS = Regex(
            """(?:Rs\.?|INR)\s*([\d,]+(?:\.\d{1,2})?)""",
            RegexOption.IGNORE_CASE,
        )
        val LAST4_RX = Regex(
            """(?:A/?c|account|card)\s*(?:no\.?\s*)?[xX*]+(\d{3,5})""",
            RegexOption.IGNORE_CASE,
        )
        val BALANCE_RX = Regex(
            """(?:Avl\.?\s*Bal|Available\s+Balance)\.?\s*:?\s*(?:Rs\.?|INR)?\s*([\d,]+(?:\.\d{1,2})?)""",
            RegexOption.IGNORE_CASE,
        )
        val DATE_RX = Regex(
            """\b(\d{1,2}[-/]\d{1,2}[-/]\d{2,4}(?:\s+\d{1,2}:\d{2}(?::\d{2})?)?|\d{1,2}[A-Z][a-z]{2}\d{2,4})\b""",
        )

        // Counterparty extraction: try most-specific markers first, generic last.
        val CP_DEBIT = listOf(
            Regex("""(?:trf|transfer)\s+to\s+([A-Za-z0-9 ._@\-]+?)(?:\s+(?:Refno|Ref|UPI\s*Ref|on)\b|[.,]|$)""", RegexOption.IGNORE_CASE),
            Regex("""\bto\s+VPA\s+([\w._@\-]+)""", RegexOption.IGNORE_CASE),
            Regex("""\bat\s+([A-Za-z0-9 ._@\-]+?)(?:\s+on\b|[.,]|$)""", RegexOption.IGNORE_CASE),
        )
        val CP_CREDIT = listOf(
            Regex("""(?:trf|transfer)\s+from\s+([A-Za-z0-9 ._@\-]+?)(?:\s+(?:Refno|Ref|UPI\s*Ref|on)\b|[.,]|$)""", RegexOption.IGNORE_CASE),
            Regex("""\bfrom\s+VPA\s+([\w._@\-]+)""", RegexOption.IGNORE_CASE),
            Regex("""(?:by\s+NEFT\s+)?\bfrom\s+([A-Za-z0-9 ._@\-]+?)(?:\s+(?:Refno|Ref|on)\b|[.,]|$)""", RegexOption.IGNORE_CASE),
        )

        val NUMERIC_ONLY = Regex("""[\d/\-:.\s]+""")
    }
}
