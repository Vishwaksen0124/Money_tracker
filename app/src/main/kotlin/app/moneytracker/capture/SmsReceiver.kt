package app.moneytracker.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import app.moneytracker.security.SecureLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Real-time capture: parses bank SMS the moment they arrive, even with the
 * app closed. Failures are silent by design — the inbox backfill re-processes
 * recent messages on the next dashboard open, and the raw_hash dedupe makes
 * that safe.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (parts.isEmpty()) return
        // Multipart SMS arrive as fragments of one logical message.
        val sender = parts.first().displayOriginatingAddress ?: return
        val body = parts.joinToString("") { it.messageBody.orEmpty() }
        val receivedAt = parts.first().timestampMillis

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                SmsCapture.process(sender, body, receivedAt)
            } catch (t: Throwable) {
                SecureLogger.e(t) { "live sms capture failed; backfill will retry" }
            } finally {
                pending.finish()
            }
        }
    }
}
