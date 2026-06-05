package app.moneytracker

import android.app.Application
import app.moneytracker.cloud.SupabaseProvider
import app.moneytracker.data.db.AppDatabase
import app.moneytracker.data.db.DatabaseFactory
import app.moneytracker.security.SecureLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MoneyApp : Application() {

    val database: AppDatabase by lazy { DatabaseFactory.create(this) }

    override fun onCreate() {
        super.onCreate()
        // net.zetetic:sqlcipher-android 4.x: load native lib explicitly.
        System.loadLibrary("sqlcipher")
        // Pre-warm off the main thread: building the Supabase client spins up the
        // ktor CIO engine and restores the saved session. Doing it here means the
        // first Dashboard composition isn't paying for that init (and the TLS
        // connection is already warm by the time the first query fires).
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { SupabaseProvider.client }
                .onFailure { SecureLogger.e(it) { "client prewarm failed" } }
        }
        SecureLogger.d { "App init" }
    }
}
