package app.moneytracker.parser.notif

import app.moneytracker.parser.Util

/**
 * Parses UPI payment-app notifications ("Paid ₹250 to X", "₹40 received from
 * Y"). Deliberately app-agnostic: wording differs across PhonePe / GPay /
 * Paytm and changes often, so we key on amount + a direction verb rather than
 * per-app templates. Anything that isn't clearly a completed payment
 * (requests, offers, reminders) is rejected.
 */
object NotifParser {

    /** Packages whose notifications we read. Everything else is ignored. */
    val UPI_PACKAGES = setOf(
        "com.phonepe.app",
        "com.google.android.apps.nbu.paisa.user",   // Google Pay
        "net.one97.paytm",                           // Paytm
        "in.org.npci.upiapp",                        // BHIM
        "in.amazon.mShop.android.shopping",          // Amazon Pay
    )

    private val DEBIT = Regex("""\b(paid|sent|debited|spent|paying)\b""", RegexOption.IGNORE_CASE)
    private val CREDIT = Regex("""\b(received|credited|added|got)\b""", RegexOption.IGNORE_CASE)
    private val EXCLUDE = Regex(
        """\b(request|requesting|requested|offer|cashback|reward|scratch|reminder|due|failed|pending|will)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val AMOUNT = Regex("""(?:₹|Rs\.?|INR)\s?([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)

    data class Parsed(val amountPaise: Long, val direction: String)

    fun parse(pkg: String, title: String, text: String): Parsed? {
        if (pkg !in UPI_PACKAGES) return null
        val body = "$title $text"
        if (EXCLUDE.containsMatchIn(body)) return null
        val amount = AMOUNT.find(body)?.groupValues?.get(1)?.let(Util::paise) ?: return null
        if (amount <= 0) return null
        val direction = when {
            DEBIT.containsMatchIn(body)  -> "DEBIT"
            CREDIT.containsMatchIn(body) -> "CREDIT"
            else -> return null
        }
        return Parsed(amount, direction)
    }
}
