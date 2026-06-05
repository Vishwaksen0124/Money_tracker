package app.moneytracker.data

import android.content.Context
import app.moneytracker.security.KeystoreManager
import app.moneytracker.security.SecureLogger
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Persists the most recent transactions to an encrypted file so a cold start
 * paints instantly instead of waiting 4–9s on a (possibly cold) Supabase
 * fetch. Plaintext is JSON; at rest it's wrapped with the hardware-backed
 * Keystore key (same KEK as the SQLCipher passphrase), honouring Hardening
 * §20.2's "encrypted at rest" requirement without a second DB.
 *
 * Best-effort: any failure (no key yet, corrupt file, schema drift) just
 * returns null/!saved — the network fetch is always the source of truth.
 */
object TransactionCacheStore {

    private const val FILE = "txn_cache.bin"
    private const val IV_LEN = 12
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = ListSerializer(TransactionRow.serializer())

    fun load(context: Context): List<TransactionRow>? = runCatching {
        val f = File(context.filesDir, FILE)
        if (!f.exists()) return null
        val blob = f.readBytes()
        if (blob.size <= IV_LEN) return null
        val plain = KeystoreManager.decrypt(
            KeystoreManager.WrappedBytes(blob.copyOfRange(0, IV_LEN), blob.copyOfRange(IV_LEN, blob.size))
        )
        json.decodeFromString(serializer, plain.decodeToString())
    }.onFailure { SecureLogger.e(it) { "cache load failed" } }.getOrNull()

    fun save(context: Context, rows: List<TransactionRow>) {
        runCatching {
            val plain = json.encodeToString(serializer, rows).encodeToByteArray()
            val wrapped = KeystoreManager.encrypt(plain)
            val staged = File(context.filesDir, "$FILE.tmp")
            staged.writeBytes(wrapped.iv + wrapped.ciphertext)
            staged.renameTo(File(context.filesDir, FILE))
        }.onFailure { SecureLogger.e(it) { "cache save failed" } }
    }

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE).delete() }
    }
}
