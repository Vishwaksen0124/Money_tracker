package app.moneytracker.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec

/**
 * Wraps an Android Keystore AES-GCM key used as the KEK (key-encryption-key)
 * for the SQLCipher database passphrase. The wrapping key never leaves the
 * Keystore; only its public handle (alias) is referenced.
 *
 * Hardening §20.8:
 *   - 256-bit AES key, GCM mode, no padding.
 *   - Hardware-backed key required (TEE or StrongBox). We refuse to operate
 *     on software-only implementations and surface a user-visible warning.
 */
object KeystoreManager {

    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "mt_db_kek_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128

    /** Thrown when the device does not provide hardware-backed key storage. */
    class HardwareBackingUnavailableException : SecurityException(
        "Hardening §20.8: device does not provide hardware-backed key storage. " +
            "Refusing to create a software-only encryption key."
    )

    data class WrappedBytes(val iv: ByteArray, val ciphertext: ByteArray)

    fun encrypt(plaintext: ByteArray): WrappedBytes {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        }
        val iv = cipher.iv
        val ct = cipher.doFinal(plaintext)
        return WrappedBytes(iv = iv, ciphertext = ct)
    }

    fun decrypt(wrapped: WrappedBytes): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(GCM_TAG_BITS, wrapped.iv)
            )
        }
        return cipher.doFinal(wrapped.ciphertext)
    }

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { existing ->
            assertHardwareBacked(existing)
            return existing
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        val key = generator.generateKey()
        assertHardwareBacked(key)
        return key
    }

    private fun assertHardwareBacked(key: SecretKey) {
        val factory = SecretKeyFactory.getInstance(key.algorithm, KEYSTORE)
        val info = factory.getKeySpec(key, KeyInfo::class.java) as KeyInfo
        val ok: Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            info.securityLevel == KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT ||
                info.securityLevel == KeyProperties.SECURITY_LEVEL_STRONGBOX
        } else {
            @Suppress("DEPRECATION")
            info.isInsideSecureHardware
        }
        if (!ok) throw HardwareBackingUnavailableException()
    }
}
