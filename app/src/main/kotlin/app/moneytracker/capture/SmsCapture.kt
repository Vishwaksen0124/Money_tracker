package app.moneytracker.capture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import app.moneytracker.cloud.AuthRepository
import app.moneytracker.cloud.SupabaseProvider
import app.moneytracker.data.Category
import app.moneytracker.data.TransactionRow
import app.moneytracker.data.insertIgnoringDuplicate
import app.moneytracker.parser.Parser
import app.moneytracker.parser.Util
import app.moneytracker.parser.sbi.SbiParser
import app.moneytracker.security.SecureLogger
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Real-time SMS → transaction pipeline. One path serves both the live
 * receiver and the inbox backfill: parse, then insert with the
 * `unique(user_id, raw_hash)` constraint making re-processing idempotent —
 * the same SMS seen twice (receiver + backfill) stays one row.
 *
 * Unparseable messages from bank senders are logged to `parser_misses` as
 * sender + hash only — never the body (PLAN.md §7).
 */
object SmsCapture {

    private val parsers: List<Parser> = listOf(SbiParser())

    val PERMISSIONS = arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)

    fun hasPermissions(context: Context): Boolean = PERMISSIONS.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    /** Parses and uploads one SMS. Returns true if a new row was inserted. */
    suspend fun process(sender: String, body: String, receivedAtMillis: Long): Boolean {
        val parser = parsers.firstOrNull { it.matches(sender) } ?: return false
        SupabaseProvider.client.auth.awaitInitialization()
        val userId = AuthRepository.currentUserId() ?: return false

        val parsed = parser.parse(sender, body, receivedAtMillis)
        if (parsed == null) {
            insertIgnoringDuplicate("parser_misses", ParserMissRow(
                userId = userId,
                sender = sender,
                bodyHash = Util.rawHash(parser.bankCode, sender, body),
            ))
            return false
        }
        // SMS wins over a notification for the same money — it carries balance
        // and account, which power reconciliation. Drop the notif row if present.
        CaptureCommon.nearMatch(parsed.amountPaise, parsed.direction.name, parsed.tsMillis)?.let { near ->
            if (near.source == "NOTIF" && near.id != null) CaptureCommon.delete(near.id)
            else return false   // already captured by SMS/manual/recon
        }

        return insertIgnoringDuplicate("transactions", TransactionRow(
            userId = userId,
            ts = Instant.fromEpochMilliseconds(parsed.tsMillis).toString(),
            amountPaise = parsed.amountPaise,
            direction = parsed.direction.name,
            channel = parsed.channel.name,
            source = "SMS",
            rawHash = parsed.rawHash,
            balanceAfterPaise = parsed.balanceAfterPaise,
            accountLast4 = parsed.accountLast4,
            category = Category.auto(parsed.direction.name, parsed.channel.name)?.name,
        ))
    }

    /** Re-scans the inbox (default last 7 days) through [process]. Catches
     *  anything the receiver missed (app not installed yet, offline, reboot).
     *  Returns how many new rows were inserted. */
    suspend fun backfill(context: Context, days: Int = 7): Int = withContext(Dispatchers.IO) {
        if (!hasPermissions(context)) return@withContext 0
        val since = System.currentTimeMillis() - days * 86_400_000L
        var inserted = 0
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.DATE} > ?",
            arrayOf(since.toString()),
            "${Telephony.Sms.DATE} DESC",
        )?.use { c ->
            val ai = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bi = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val di = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (c.moveToNext()) {
                val sender = c.getString(ai) ?: continue
                val body = c.getString(bi) ?: continue
                runCatching { if (process(sender, body, c.getLong(di))) inserted++ }
                    .onFailure { SecureLogger.e(it) { "backfill item failed" } }
            }
        }
        SecureLogger.d { "backfill inserted=$inserted" }
        inserted
    }

    @Serializable
    data class ParserMissRow(
        @SerialName("user_id")  val userId: String,
        val sender: String,
        @SerialName("body_hash") val bodyHash: String,
    ) {
        override fun toString(): String = "ParserMissRow($sender)"
    }
}
