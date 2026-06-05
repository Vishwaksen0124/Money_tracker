package app.moneytracker.data.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import app.moneytracker.security.DatabaseKeyProvider
import app.moneytracker.security.SecureLogger
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Opens the encrypted Room database. Passphrase comes from
 * [DatabaseKeyProvider]; SQLCipher zeroes its copy after the first open
 * (clearPassphrase = true). Hardening §20.2 pragmas applied on every open.
 */
object DatabaseFactory {

    private const val DB_NAME = "mt.db"

    fun create(context: Context): AppDatabase {
        val passphrase = DatabaseKeyProvider(context).getOrCreateKey()
        val helper = SupportOpenHelperFactory(passphrase, null, /* clearPassphrase = */ true)
        return Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
            .openHelperFactory(helper)
            .addCallback(HardeningPragmas)
            .build()
            .also { SecureLogger.d { "DB initialized" } }
    }

    private object HardeningPragmas : androidx.room.RoomDatabase.Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) {
            db.execSQL("PRAGMA secure_delete = ON;")
            db.execSQL("PRAGMA cipher_memory_security = ON;")
        }
    }
}
