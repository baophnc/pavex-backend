package com.pavex.identity.service

import com.pavex.identity.entity.UserAccount
import com.pavex.identity.entity.UserProfile
import com.pavex.identity.exception.ApiException
import com.pavex.identity.repository.UserAccountRepository
import com.pavex.identity.repository.UserProfileRepository
import com.pavex.storage.ObjectStorage
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.RETURNS_DEFAULTS
import org.mockito.Mockito.mock
import java.util.Optional
import java.util.UUID

class AvatarServiceTest {
    private val userId = UUID.randomUUID()
    private val objects = mutableMapOf<String, ByteArray>()
    private val profiles = mutableMapOf<UUID, UserProfile>()
    private val base = "http://localhost:8080/api/v1/avatars"

    private val storage = object : ObjectStorage {
        override fun put(bucket: String, key: String, bytes: ByteArray, contentType: String) {
            objects[key] = bytes
        }
        override fun get(bucket: String, key: String): ByteArray? = objects[key]
        override fun delete(bucket: String, key: String) {
            objects.remove(key)
        }
    }

    private val service = AvatarService(
        mock(UserAccountRepository::class.java) { invocation ->
            if (invocation.method.name == "findById") {
                Optional.ofNullable(if (invocation.getArgument<UUID>(0) == userId) UserAccount(id = userId) else null)
            } else {
                RETURNS_DEFAULTS.answer(invocation)
            }
        },
        mock(UserProfileRepository::class.java) { invocation ->
            when (invocation.method.name) {
                "findById" -> Optional.ofNullable(profiles[invocation.getArgument(0)])
                "save" -> invocation.getArgument<UserProfile>(0).also { profiles[it.userId] = it }
                else -> RETURNS_DEFAULTS.answer(invocation)
            }
        },
        storage,
        "pavex-avatars",
        base,
    )

    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2)
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 7)

    @Test
    fun `an uploaded photo becomes the profile's public avatar`() {
        val url = service.replace(userId, "image/png", png)

        assertTrue(url.startsWith("$base/"))
        assertEquals(url, profiles.getValue(userId).avatarUrl)
        val image = service.read(UUID.fromString(url.substringAfterLast('/')))!!
        assertEquals("image/png", image.contentType)
        assertArrayEquals(png, image.bytes)
    }

    @Test
    fun `a new photo replaces and removes the previous one`() {
        val first = service.replace(userId, "image/png", png)
        val second = service.replace(userId, "image/jpeg", jpeg)

        assertEquals(1, objects.size)
        assertNull(service.read(UUID.fromString(first.substringAfterLast('/'))))
        assertEquals(second, profiles.getValue(userId).avatarUrl)
    }

    @Test
    fun `a file that is not really an image is refused`() {
        val error = assertThrows(ApiException::class.java) { service.replace(userId, "image/png", "hello".toByteArray()) }

        assertEquals("invalid_avatar_type", error.code)
        assertFalse(profiles.containsKey(userId))
        assertTrue(objects.isEmpty())
    }

    @Test
    fun `photos are limited to 2 MB`() {
        val big = jpeg + ByteArray(2 * 1024 * 1024)

        val error = assertThrows(ApiException::class.java) { service.replace(userId, "image/jpeg", big) }

        assertEquals("invalid_avatar_size", error.code)
    }
}
