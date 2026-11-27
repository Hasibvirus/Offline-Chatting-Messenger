package com.example.core.security

import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class EncryptedEnvelope(
    val ivB64: String,
    val ciphertextB64: String
)

data class DerivedSessionMaterial(
    val aesKey: SecretKey,
    val macKey: SecretKey,
    val safetyCode: String
)

object CryptoEngine {
    private const val ROOM_CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    private const val GCM_IV_BYTES = 12
    private const val GCM_TAG_BITS = 128
    private const val AES_KEY_BITS = 256
    private const val PBKDF2_ITERATIONS = 120_000
    private val secureRandom = SecureRandom()

    fun generateRoomCode(length: Int = 8): String {
        val chars = CharArray(length) {
            ROOM_CODE_ALPHABET[secureRandom.nextInt(ROOM_CODE_ALPHABET.length)]
        }
        return String(chars)
    }

    fun generatePairingPin(): String {
        val number = secureRandom.nextInt(1_000_000)
        return "%06d".format(number)
    }

    fun generateEphemeralEcKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"), secureRandom)
        return kpg.generateKeyPair()
    }

    fun decodeEcPublicKey(x509Bytes: ByteArray): java.security.PublicKey {
        val kf = KeyFactory.getInstance("EC")
        return kf.generatePublic(X509EncodedKeySpec(x509Bytes))
    }

    fun deriveSessionMaterial(
        localPrivateKey: PrivateKey,
        localPublicKeyBytes: ByteArray,
        peerPublicKeyBytes: ByteArray,
        roomCode: String,
        pairingPin: String
    ): DerivedSessionMaterial {
        val peerPublicKey = decodeEcPublicKey(peerPublicKeyBytes)
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(localPrivateKey)
        agreement.doPhase(peerPublicKey, true)
        val ecdhSecret = agreement.generateSecret()

        // Sort public keys canonically so both peers compute the exact same transcript & safety code
        val (firstPub, secondPub) = if (compareLex(localPublicKeyBytes, peerPublicKeyBytes) <= 0) {
            localPublicKeyBytes to peerPublicKeyBytes
        } else {
            peerPublicKeyBytes to localPublicKeyBytes
        }

        val saltDigest = MessageDigest.getInstance("SHA-256").apply {
            update(roomCode.trim().uppercase().toByteArray(StandardCharsets.UTF_8))
            update(":".toByteArray(StandardCharsets.UTF_8))
            update(pairingPin.trim().toByteArray(StandardCharsets.UTF_8))
        }.digest()

        val prk = hkdfExtract(salt = saltDigest, ikm = ecdhSecret)
        val info = ByteArray(firstPub.size + secondPub.size + 32).let { _ ->
            val tag = "OfflineMessenger-AES256GCM-v1".toByteArray(StandardCharsets.UTF_8)
            tag + firstPub + secondPub
        }
        val expanded = hkdfExpand(prk = prk, info = info, length = 72)

        val aesKeyBytes = expanded.copyOfRange(0, 32)
        val macKeyBytes = expanded.copyOfRange(32, 64)
        val sasBytes = expanded.copyOfRange(64, 72)

        ecdhSecret.fill(0)
        prk.fill(0)

        val sasNumber = ((sasBytes[0].toInt() and 0x7F) shl 24) or
            ((sasBytes[1].toInt() and 0xFF) shl 16) or
            ((sasBytes[2].toInt() and 0xFF) shl 8) or
            (sasBytes[3].toInt() and 0xFF)
        val sixDigitSas = "%06d".format(sasNumber % 1_000_000)
        val formattedSas = "${sixDigitSas.substring(0, 3)}-${sixDigitSas.substring(3, 6)}"

        return DerivedSessionMaterial(
            aesKey = SecretKeySpec(aesKeyBytes, "AES"),
            macKey = SecretKeySpec(macKeyBytes, "HmacSHA256"),
            safetyCode = formattedSas
        )
    }

    /**
     * Derives an initial room key from roomCode + pairingPin using PBKDF2-HMAC-SHA256.
     * Upgraded via ephemeral ECDH whenever a peer connects.
     */
    fun deriveRoomKeyFromSecret(roomCode: String, pairingPin: String): SecretKey {
        val normalizedCode = roomCode.trim().uppercase()
        val normalizedPin = pairingPin.trim()
        val salt = MessageDigest.getInstance("SHA-256")
            .digest("OfflineMessenger-RoomSalt|$normalizedCode".toByteArray(StandardCharsets.UTF_8))
        val spec = PBEKeySpec(
            "$normalizedCode:$normalizedPin".toCharArray(),
            salt,
            PBKDF2_ITERATIONS,
            AES_KEY_BITS
        )
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(keyBytes, "AES")
    }

    fun computeSafetyFingerprint(roomCode: String, pairingPin: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(
            "SAS|$roomCode|$pairingPin".toByteArray(StandardCharsets.UTF_8)
        )
        val value = ((digest[0].toInt() and 0x7F) shl 16) or
            ((digest[1].toInt() and 0xFF) shl 8) or
            (digest[2].toInt() and 0xFF)
        val six = "%06d".format(value % 1_000_000)
        return "${six.substring(0, 3)}-${six.substring(3, 6)}"
    }

    fun computeKeyConfirmationMac(
        macKey: SecretKey,
        roleTag: String,
        roomCode: String,
        nonceB64: String
    ): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(macKey)
        mac.update(roleTag.toByteArray(StandardCharsets.UTF_8))
        mac.update("|".toByteArray(StandardCharsets.UTF_8))
        mac.update(roomCode.toByteArray(StandardCharsets.UTF_8))
        mac.update("|".toByteArray(StandardCharsets.UTF_8))
        mac.update(nonceB64.toByteArray(StandardCharsets.UTF_8))
        return mac.doFinal()
    }

    fun verifyMacConstantTime(expected: ByteArray, actual: ByteArray): Boolean {
        return expected.isNotEmpty() && MessageDigest.isEqual(expected, actual)
    }

    fun encrypt(aesKey: SecretKey, plaintext: ByteArray, aad: String): EncryptedEnvelope {
        val iv = ByteArray(GCM_IV_BYTES)
        secureRandom.nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(aad.toByteArray(StandardCharsets.UTF_8))
        val ciphertext = cipher.doFinal(plaintext)
        return EncryptedEnvelope(
            ivB64 = encodeB64(iv),
            ciphertextB64 = encodeB64(ciphertext)
        )
    }

    fun decrypt(aesKey: SecretKey, ivB64: String, ciphertextB64: String, aad: String): ByteArray {
        val iv = decodeB64(ivB64)
        if (iv.size != GCM_IV_BYTES) {
            throw SecurityException("Invalid AES-GCM IV length: ${iv.size}")
        }
        val ciphertext = decodeB64(ciphertextB64)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(aad.toByteArray(StandardCharsets.UTF_8))
        return cipher.doFinal(ciphertext)
    }

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun encodeB64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    fun decodeB64(encoded: String): ByteArray = Base64.getDecoder().decode(encoded)

    private fun hkdfExtract(salt: ByteArray, ikm: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        return mac.doFinal(ikm)
    }

    private fun hkdfExpand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        val result = ByteArray(length)
        var t = ByteArray(0)
        var offset = 0
        var counter = 1
        while (offset < length) {
            mac.reset()
            mac.update(t)
            mac.update(info)
            mac.update(counter.toByte())
            t = mac.doFinal()
            val toCopy = minOf(t.size, length - offset)
            System.arraycopy(t, 0, result, offset, toCopy)
            offset += toCopy
            counter++
        }
        return result
    }

    private fun compareLex(a: ByteArray, b: ByteArray): Int {
        val minLen = minOf(a.size, b.size)
        for (i in 0 until minLen) {
            val cmp = (a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF)
            if (cmp != 0) return cmp
        }
        return a.size - b.size
    }
}
