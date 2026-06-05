package app.moneytracker.security

import android.content.Context
import java.io.File
import java.security.SecureRandom

/**
 * Manages the SQLCipher passphrase.
 *
 * On first launch: generates 32 random bytes via SecureRandom, wraps via
 * [KeystoreManager] AES-GCM (hardware-backed key), persists `iv || ciphertext`
 * to `<filesDir>/db_key.bin`. The plaintext key never touches disk.
 *
 * On subsequent launches: reads the file, decrypts via Keystore, returns the
 * raw 32 bytes. Callers must zero the array as soon as it has been handed to
 * SQLCipher's helper factory (which can be told to clear it itself).
 */
class DatabaseKeyProvider(private val context: Context) {

    fun getOrCreateKey(): ByteArray {
        val keyFile = File(context.filesDir, KEY_FILE)
        if (keyFile.exists()) {
            val blob = keyFile.readBytes()
            require(blob.size > IV_LEN) { "Corrupt DB key file" }
            val iv = blob.copyOfRange(0, IV_LEN)
            val ct = blob.copyOfRange(IV_LEN, blob.size)
            return KeystoreManager.decrypt(KeystoreManager.WrappedBytes(iv, ct))
        }
        val raw = ByteArray(KEY_BYTES).also { SecureRandom().nextBytes(it) }
        val wrapped = KeystoreManager.encrypt(raw)
        // Atomic-ish write: stage then rename so a half-written file can't brick startup.
        val staged = File(context.filesDir, "$KEY_FILE.tmp")
        staged.writeBytes(wrapped.iv + wrapped.ciphertext)
        check(staged.renameTo(keyFile)) { "Failed to persist DB key file" }
        // App sandbox already restricts access; explicit perms are best-effort.
        keyFile.setReadable(false, /* ownerOnly = */ false)
        keyFile.setReadable(true,  /* ownerOnly = */ true)
        keyFile.setWritable(false, /* ownerOnly = */ false)
        keyFile.setWritable(true,  /* ownerOnly = */ true)
        return raw
    }

    companion object {
        private const val KEY_FILE = "db_key.bin"
        private const val IV_LEN = 12
        private const val KEY_BYTES = 32
    }
}
