package com.example

import com.example.core.security.CryptoEngine
import com.example.core.security.PacketValidator
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OfflineMessengerSecurityTest {

    @Test
    fun `ecdh session derivation produces matching keys and safety code for both peers`() {
        val aliceKeys = CryptoEngine.generateEphemeralEcKeyPair()
        val bobKeys = CryptoEngine.generateEphemeralEcKeyPair()
        val roomCode = "K7M9P4X2"
        val pin = "482910"

        val aliceSession = CryptoEngine.deriveSessionMaterial(
            localPrivateKey = aliceKeys.private,
            localPublicKeyBytes = aliceKeys.public.encoded,
            peerPublicKeyBytes = bobKeys.public.encoded,
            roomCode = roomCode,
            pairingPin = pin
        )
        val bobSession = CryptoEngine.deriveSessionMaterial(
            localPrivateKey = bobKeys.private,
            localPublicKeyBytes = bobKeys.public.encoded,
            peerPublicKeyBytes = aliceKeys.public.encoded,
            roomCode = roomCode,
            pairingPin = pin
        )

        assertEquals(aliceSession.safetyCode, bobSession.safetyCode)
        assertArrayEquals(aliceSession.aesKey.encoded, bobSession.aesKey.encoded)

        val aad = "room1|alice|pkt1|1700000000"
        val secretMsg = "Offline encrypted payload".toByteArray()
        val encrypted = CryptoEngine.encrypt(aliceSession.aesKey, secretMsg, aad)
        val decrypted = CryptoEngine.decrypt(bobSession.aesKey, encrypted.ivB64, encrypted.ciphertextB64, aad)
        assertArrayEquals(secretMsg, decrypted)
    }

    @Test
    fun `wrong pairing pin fails hmac key confirmation`() {
        val aliceKeys = CryptoEngine.generateEphemeralEcKeyPair()
        val bobKeys = CryptoEngine.generateEphemeralEcKeyPair()
        val roomCode = "ABCD2345"

        val aliceSession = CryptoEngine.deriveSessionMaterial(
            aliceKeys.private, aliceKeys.public.encoded, bobKeys.public.encoded, roomCode, "111222"
        )
        val mallorySession = CryptoEngine.deriveSessionMaterial(
            bobKeys.private, bobKeys.public.encoded, aliceKeys.public.encoded, roomCode, "999888"
        )

        val nonce = "nonce123"
        val aliceMac = CryptoEngine.computeKeyConfirmationMac(aliceSession.macKey, "HOST", roomCode, nonce)
        val malloryMac = CryptoEngine.computeKeyConfirmationMac(mallorySession.macKey, "HOST", roomCode, nonce)
        assertFalse(CryptoEngine.verifyMacConstantTime(aliceMac, malloryMac))
    }

    @Test
    fun `packet validator blocks executables oversized files and replay`() {
        val exeCheck = PacketValidator.validateFileMeta("payload.apk", "application/vnd.android.package-archive", 1024)
        assertTrue(exeCheck.isFailure)

        val hugeCheck = PacketValidator.validateFileMeta("video.mp4", "video/mp4", PacketValidator.MAX_FILE_BYTES + 1)
        assertTrue(hugeCheck.isFailure)

        val pdfCheck = PacketValidator.validateFileMeta("../../etc/passwd/report.pdf", "application/pdf", 2048)
        assertTrue(pdfCheck.isSuccess)
        assertEquals("report.pdf", pdfCheck.getOrThrow())

        val safeDir = File(System.getProperty("java.io.tmpdir"), "om_safe_test")
        val resolved = PacketValidator.resolveSafeChildFile(safeDir, "../../evil.txt")
        assertTrue(resolved.canonicalPath.startsWith(safeDir.canonicalFile.path + File.separator))

        val now = System.currentTimeMillis()
        assertTrue(PacketValidator.checkAndRecordPacket("senderA", "pkt-unique-1", now))
        assertFalse(PacketValidator.checkAndRecordPacket("senderA", "pkt-unique-1", now))
    }
}
