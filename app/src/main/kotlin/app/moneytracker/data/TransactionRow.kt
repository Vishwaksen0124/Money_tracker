package app.moneytracker.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire-format for the `transactions` table.
 * Amounts are paise (`bigint`); timestamps are ISO-8601 with timezone.
 *
 * `counterparty_ciphertext` / `notes_ciphertext` are intentionally omitted
 * here — v0.5 will add the E2EE layer and start populating those.
 */
@Serializable
data class TransactionRow(
    val id: String? = null,
    @SerialName("user_id")        val userId: String,
    val ts: String,
    @SerialName("amount_paise")   val amountPaise: Long,
    val direction: String,                  // "DEBIT" | "CREDIT"
    val channel: String,                    // "UPI"|"CARD"|"NEFT"|"IMPS"|"ATM"|"POS"|"OTHER"
    val source: String,                     // "SMS" | "NOTIF" | "MANUAL" | "RECON"
    @SerialName("raw_hash")       val rawHash: String,
    @SerialName("balance_after_paise") val balanceAfterPaise: Long? = null,
    @SerialName("account_last4")  val accountLast4: String? = null,
    val category: String? = null,           // Category enum name, or null = uncategorized
) {
    override fun toString(): String = "TransactionRow(${id ?: "new"})"
}
