package app.moneytracker.capture

import app.moneytracker.cloud.AuthRepository
import app.moneytracker.cloud.SupabaseProvider
import app.moneytracker.data.Category
import app.moneytracker.data.TransactionRow
import app.moneytracker.data.insertIgnoringDuplicate
import app.moneytracker.parser.Util
import app.moneytracker.parser.notif.NotifParser
import io.github.jan.supabase.auth.auth
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
        val p = NotifParser.parse(pkg, title, text) ?: return false
        SupabaseProvider.client.auth.awaitInitialization()
        val userId = AuthRepository.currentUserId() ?: return false

        // If a bank SMS already recorded this money, keep that (richer) row.
        if (CaptureCommon.nearMatch(p.amountPaise, p.direction, tsMillis) != null) return false

        return insertIgnoringDuplicate("transactions", TransactionRow(
            userId = userId,
            ts = Instant.fromEpochMilliseconds(tsMillis).toString(),
            amountPaise = p.amountPaise,
            direction = p.direction,
            channel = "UPI",
            source = "NOTIF",
            rawHash = Util.rawHash("NOTIF", pkg, "$title|$text"),
            category = Category.auto(p.direction, "UPI")?.name,
        ))
    }
}
