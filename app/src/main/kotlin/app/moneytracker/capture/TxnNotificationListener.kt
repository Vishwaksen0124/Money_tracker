package app.moneytracker.capture

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import app.moneytracker.parser.notif.NotifParser
import app.moneytracker.security.SecureLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Reads notifications from UPI payment apps and records the transaction.
 * Only the whitelisted UPI packages are inspected; all other notifications
 * are ignored and never read. Requires the user to grant notification access
 * in Settings (a deliberate, revocable, system-gated permission).
 */
class TxnNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) = handle(sbn)

    /** On (re)connect — including the moment access is granted — sweep the
     *  notifications already in the shade so a payment that arrived before the
     *  user enabled access still gets captured. Dedup keeps it idempotent. */
    override fun onListenerConnected() {
        val active = runCatching { activeNotifications }.getOrNull().orEmpty()
        SecureLogger.d { "listener connected; ${active.size} active notifs" }
        active.forEach(::handle)
    }

    private fun handle(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        if (pkg !in NotifParser.UPI_PACKAGES) return

        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = listOf(
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT),
            extras.getCharSequence(Notification.EXTRA_TEXT),
        ).firstOrNull { !it.isNullOrBlank() }?.toString().orEmpty()

        CoroutineScope(Dispatchers.IO).launch {
            runCatching { NotifCapture.process(pkg, title, text, sbn.postTime) }
                .onFailure { SecureLogger.e(it) { "notif capture failed" } }
        }
    }
}
