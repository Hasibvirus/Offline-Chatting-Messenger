package com.example.core.security

import java.io.File
import java.util.Collections
import kotlin.math.abs

object PacketValidator {
    const val MAX_FRAME_BYTES = 1_500_000 // 1.5 MB hard cap per wire frame
    const val MAX_TEXT_CHARS = 4096
    const val MAX_FILE_BYTES = 25L * 1024L * 1024L // 25 MB limit
    const val FILE_CHUNK_SIZE = 48 * 1024 // 48 KB raw bytes (~64 KB base64)
    private const val MAX_CLOCK_SKEW_MS = 10 * 60 * 1000L

    private val blockedExtensions = setOf(
        "apk", "dex", "jar", "so", "exe", "bat", "cmd", "sh", "elf", "msi", "vbs", "js"
    )

    private val allowedMimePrefixes = listOf(
        "image/",
        "video/",
        "audio/",
        "text/plain",
        "text/csv",
        "application/pdf",
        "application/zip",
        "application/msword",
        "application/vnd.openxmlformats-officedocument",
        "application/json",
        "application/octet-stream"
    )

    private val seenPacketIds: MutableSet<String> = Collections.synchronizedSet(
        Collections.newSetFromMap(
            object : LinkedHashMap<String, Boolean>(512, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean {
                    return size > 2048
                }
            }
        )
    )

    fun validateFrameSize(rawBytes: ByteArray): Boolean {
        return rawBytes.isNotEmpty() && rawBytes.size <= MAX_FRAME_BYTES
    }

    fun validateTextContent(text: String): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) throw IllegalArgumentException("Message cannot be empty")
        if (trimmed.length > MAX_TEXT_CHARS) {
            return trimmed.take(MAX_TEXT_CHARS)
        }
        return trimmed
    }

    fun validateFileMeta(fileName: String, mimeType: String, sizeBytes: Long): Result<String> {
        if (sizeBytes <= 0L) {
            return Result.failure(IllegalArgumentException("File is empty"))
        }
        if (sizeBytes > MAX_FILE_BYTES) {
            return Result.failure(
                IllegalArgumentException("File exceeds maximum allowed size of 25 MB")
            )
        }
        val cleanName = sanitizeFileName(fileName)
        val ext = cleanName.substringAfterLast('.', "").lowercase()
        if (ext in blockedExtensions) {
            return Result.failure(
                SecurityException("Executable file type '.$ext' is blocked for safety")
            )
        }
        val lowerMime = mimeType.lowercase()
        val mimeAllowed = allowedMimePrefixes.any { lowerMime.startsWith(it) }
        if (!mimeAllowed) {
            return Result.failure(
                SecurityException("Unsupported MIME type: $mimeType")
            )
        }
        return Result.success(cleanName)
    }

    fun sanitizeFileName(rawName: String): String {
        val base = rawName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .replace("..", "")
            .replace(Regex("[^A-Za-z0-9._\\- ]"), "_")
            .trim()
        val nonEmpty = base.ifBlank { "shared_file_${System.currentTimeMillis()}" }
        return nonEmpty.take(120)
    }

    fun resolveSafeChildFile(baseDir: File, fileName: String): File {
        if (!baseDir.exists()) baseDir.mkdirs()
        val sanitized = sanitizeFileName(fileName)
        val target = File(baseDir, sanitized)
        val baseCanonical = baseDir.canonicalFile
        val targetCanonical = target.canonicalFile
        if (!targetCanonical.path.startsWith(baseCanonical.path + File.separator)) {
            throw SecurityException("Path traversal attempt blocked")
        }
        return targetCanonical
    }

    fun checkAndRecordPacket(senderId: String, packetId: String, timestamp: Long): Boolean {
        if (senderId.isBlank() || packetId.isBlank()) return false
        val skew = abs(System.currentTimeMillis() - timestamp)
        if (skew > MAX_CLOCK_SKEW_MS) return false
        val key = "$senderId:$packetId"
        return seenPacketIds.add(key)
    }
}
