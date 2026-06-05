package app.moneytracker.security

import android.util.Log
import app.moneytracker.BuildConfig

/**
 * Hardening §20.4: logging that compiles to a no-op in release builds.
 *
 * Even in debug, never pass amounts, balances, counterparties, or raw SMS
 * bodies through here. Log event names and ids only.
 */
object SecureLogger {
    @PublishedApi internal const val TAG: String = "MT"
    @PublishedApi internal val enabled: Boolean = BuildConfig.DEBUG

    inline fun d(message: () -> String) {
        if (enabled) Log.d(TAG, message())
    }

    inline fun w(message: () -> String) {
        if (enabled) Log.w(TAG, message())
    }

    inline fun e(t: Throwable? = null, message: () -> String) {
        if (enabled) Log.e(TAG, message(), t)
    }
}
