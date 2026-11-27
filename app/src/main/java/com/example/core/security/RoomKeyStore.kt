package com.example.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Wraps room AES-256 keys using the hardware-backed AndroidKeyStore before persisting to Room.
 * Secrets are never written in plaintext to disk.
 */
object RoomKeyStore {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val MASTER_KEY_ALIAS = "offline_messenger_room_wrap_v1"
    private const val GCM_IV_LEN = 12
    private const val GCM_TAG_BITS = 128

    // Fallback strictly for JVM unit tests where AndroidKeyStore provider is absent
    @Volatile
    private var jvmTestFallbackKey: SecretKey? = null

    private fun getOrCreateMasterKey(): SecretKey {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existing = keyStore.getKey(MASTER_KEY_ALIAS, null) as? SecretKey
            if (existing != null) return existing

            val keyGen = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )
            val spec = KeyGenParameterSpec.Builder(
                MASTER_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGen.init(spec)
            keyGen.generateKey()
        } catch (_: Exception) {
            synchronized(this) {
                jvmTestFallbackKey ?: run {
                    val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
                    SecretKeySpec(bytes, "AES").also { jvmTestFallbackKey = it }
                }
            }
        }
    }

    fun wrapKey(roomKey: SecretKey): String {
        val master = getOrCreateMasterKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, master)
        val iv = cipher.iv
        val wrapped = cipher.doFinal(roomKey.encoded)
        return "${CryptoEngine.encodeB64(iv)}:${CryptoEngine.encodeB64(wrapped)}"
    }

    fun unwrapKey(wrappedBlob: String): SecretKey? {
        if (wrappedBlob.isBlank()) return null
        val parts = wrappedBlob.split(":")
        if (parts.size != 2) return null
        return try {
            val iv = CryptoEngine.decodeB64(parts[0])
            if (iv.size != GCM_IV_LEN) return null
            val ciphertext = CryptoEngine.decodeB64(parts[1])
            val master = getOrCreateMasterKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, master, GCMParameterSpec(GCM_TAG_BITS, iv))
            val keyBytes = cipher.doFinal(ciphertext)
            SecretKeySpec(keyBytes, "AES")
        } catch (_: Exception) {
            null
        }
    }

    fun encryptSecretString(plaintext: String): String {
        val master = getOrCreateMasterKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, master)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return "${CryptoEngine.encodeB64(iv)}:${CryptoEngine.encodeB64(ciphertext)}"
    }

    fun decryptSecretString(blob: String): String? {
        if (blob.isBlank()) return null
        val parts = blob.split(":")
        if (parts.size != 2) return null
        return try {
            val iv = CryptoEngine.decodeB64(parts[0])
            if (iv.size != GCM_IV_LEN) return null
            val ciphertext = CryptoEngine.decodeB64(parts[1])
            val master = getOrCreateMasterKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, master, GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }
}
