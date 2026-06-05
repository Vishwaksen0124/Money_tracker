package app.moneytracker.capture

import app.moneytracker.cloud.AuthRepository
import app.moneytracker.data.Category
import app.moneytracker.data.TransactionRow
import app.moneytracker.data.insertIgnoringDuplicate
import app.moneytracker.parser.Util
import app.moneytracker.parser.notif.NotifParser
import app.moneytracker.security.SecureLogger
import kotlinx.datetime.Instant

/**
 * Turns a UPI-app notification into a transaction. Catches payments made from
 * this phone that the bank never SMSes. Deduped against SMS via
 * [CaptureCommon.nearMatch] (SMS wins, since it carries balance), and against
 * its own re-posts via the raw_hash unique constraint.
 */
object NotifCapture {

    /** Whether the user has granted this app notification-listener access. */
    fun accessGranted(context: android.content.Context): Boolean {
        val flat = android.provider.Settings.Secure.getString(
            context.contentResolver, "enabled_notification_listeners"
        ) ?: return false
        return flat.split(":").any { it.substringBefore('/') == context.packageName }
    }

    suspend fun process(pkg: String, title: String, text: String, tsMillis: Long): Boolean {
        SecureLogger.d { "notif seen pkg=$pkg title='$title' text='$text'" }
        val p = NotifParser.parse(pkg, title, text)
        if (p == null) { SecureLogger.d { "notif parse=null" }; return false }
        val userId = AuthRepository.awaitUserId()
        if (userId == null) { SecureLogger.d { "notif no-user" }; return false }

        // If a bank SMS already recorded this money, keep that (richer) row.
        if (CaptureCommon.nearMatch(p.amountPaise, p.direction, tsMillis) != null) {
            SecureLogger.d { "notif dedup-skip ${p.direction}" }
            return false
        }

        val inserted = insertIgnoringDuplicate("transactions", TransactionRow(
            userId = userId,
            ts = Instant.fromEpochMilliseconds(tsMillis).toString(),
            amountPaise = p.amountPaise,
            direction = p.direction,
            channel = "UPI",
            source = "NOTIF",
            rawHash = Util.rawHash("NOTIF", pkg, "$title|$text"),
            category = Category.auto(p.direction, "UPI")?.name,
        ))
        SecureLogger.d { "notif capture from $pkg inserted=$inserted ${p.direction}" }
        return inserted
    }
}
