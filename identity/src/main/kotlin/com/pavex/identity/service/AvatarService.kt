package com.pavex.identity.service

import com.pavex.identity.entity.UserProfile
import com.pavex.identity.exception.ApiException
import com.pavex.identity.exception.UserNotFoundException
import com.pavex.identity.repository.UserAccountRepository
import com.pavex.identity.repository.UserProfileRepository
import com.pavex.storage.ObjectStorage
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Profile photos. The image is kept in object storage under a random id and served publicly at
 * [avatarBaseUrl]/<id>, so it shows in any portal or the app without a session; the id cannot be guessed.
 */
@Service
class AvatarService(
    private val accounts: UserAccountRepository,
    private val profiles: UserProfileRepository,
    private val storage: ObjectStorage,
    @Value("\${pavex.storage.avatars-bucket}") private val bucket: String,
    @Value("\${pavex.storage.avatar-base-url}") private val avatarBaseUrl: String,
) {
    /** Stores a new photo for [userId], points the profile at it, and drops the previous one. */
    @Transactional
    fun replace(userId: UUID, contentType: String?, bytes: ByteArray): String {
        accounts.findById(userId).orElseThrow { UserNotFoundException() }
        val type = contentType?.substringBefore(';')?.trim()?.lowercase()
        if (type !in ALLOWED_TYPES || imageType(bytes) != type) {
            throw ApiException(HttpStatus.BAD_REQUEST, "invalid_avatar_type", "Ảnh đại diện phải là JPG, PNG hoặc WEBP.")
        }
        if (bytes.size > MAX_BYTES) {
            throw ApiException(HttpStatus.BAD_REQUEST, "invalid_avatar_size", "Ảnh đại diện phải nhỏ hơn 2 MB.")
        }

        val id = UUID.randomUUID()
        storage.put(bucket, key(id), bytes, type!!)
        val now = Instant.now()
        val profile = profiles.findById(userId).orElseGet {
            UserProfile(userId = userId, createdAt = now, updatedAt = now)
        }
        val previous = ownAvatarId(profile.avatarUrl)
        val url = "${avatarBaseUrl.trimEnd('/')}/$id"
        profile.avatarUrl = url
        profile.updatedAt = now
        profiles.save(profile)
        previous?.let { runCatching { storage.delete(bucket, key(it)) } }
        return url
    }

    /** The stored photo, or null when there is none with this id. */
    fun read(id: UUID): AvatarImage? {
        val bytes = storage.get(bucket, key(id)) ?: return null
        return AvatarImage(imageType(bytes) ?: "application/octet-stream", bytes)
    }

    private fun ownAvatarId(url: String?): UUID? {
        val prefix = "${avatarBaseUrl.trimEnd('/')}/"
        if (url == null || !url.startsWith(prefix)) return null
        return runCatching { UUID.fromString(url.removePrefix(prefix)) }.getOrNull()
    }

    private fun key(id: UUID) = "avatars/$id"

    companion object {
        private const val MAX_BYTES = 2 * 1024 * 1024
        private val ALLOWED_TYPES = setOf("image/jpeg", "image/png", "image/webp")

        /** The image format from the file's first bytes, so a renamed file cannot pass as an image. */
        fun imageType(bytes: ByteArray): String? = when {
            bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte() -> "image/jpeg"
            bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(PNG_SIGNATURE) -> "image/png"
            bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
                String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" -> "image/webp"
            else -> null
        }

        private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    }
}

class AvatarImage(
    val contentType: String,
    val bytes: ByteArray,
)
