package app.moneytracker.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single captured transaction. Amounts are stored in **paise** as Long —
 * no floating point. See PLAN.md §6.
 *
 * Hardening §20.4: toString is overridden to expose only the id. Default
 * data-class toString would leak amount and counterparty into any accidental
 * log statement.
 */
@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["rawHash"], unique = true),
        Index(value = ["tsMillis"]),
        Index(value = ["accountId", "tsMillis"]),
    ]
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    val tsMillis: Long,
    val ingestedAtMillis: Long,
    val amountPaise: Long,
    val direction: String,           // DEBIT | CREDIT
    val channel: String,             // UPI | CARD | NEFT | IMPS | ATM | OTHER
    val counterparty: String?,
    val accountId: String?,
    val balanceAfterPaise: Long?,
    val source: String,              // SMS | NOTIF | MANUAL
    val rawHash: String,
    val categoryId: String?,
    val notes: String?,
    val reversedBy: String?,
) {
    override fun toString(): String = "TransactionEntity($id)"
}
