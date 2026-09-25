package com.pavex.partners.service

import com.pavex.contracts.identity.MerchantEligibilityResponse
import com.pavex.contracts.identity.MerchantRoleSyncRequest
import com.pavex.partners.entity.MerchantRoleOutboxMessage
import com.pavex.partners.repository.MerchantRoleOutboxRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.time.Duration
import java.time.Instant
import java.util.UUID

class MerchantRoleOutboxProcessorTest {
    @Test
    fun `stale role grant is superseded instead of re-granting after a suspension`() {
        val userId = UUID.randomUUID()
        val staleGrant = message(userId, enabled = true, occurredAt = Instant.now().minusSeconds(60))
        val identity = RecordingIdentity()

        processor(claimed = listOf(staleGrant), newerExists = { true }, identity = identity).dispatchPending()

        assertTrue(identity.calls.isEmpty(), "A superseded message must never reach Identity")
        assertNotNull(staleGrant.supersededAt)
        assertNotNull(staleGrant.processedAt)
    }

    @Test
    fun `latest role change is sent and closed`() {
        val userId = UUID.randomUUID()
        val revoke = message(userId, enabled = false)
        val identity = RecordingIdentity()

        processor(claimed = listOf(revoke), newerExists = { false }, identity = identity).dispatchPending()

        assertEquals(listOf(userId to false), identity.calls)
        assertNotNull(revoke.processedAt)
        assertNull(revoke.supersededAt)
        assertNull(revoke.lastError)
    }

    @Test
    fun `failed dispatch stays pending with backoff`() {
        val message = message(UUID.randomUUID(), enabled = true)
        val before = Instant.now()
        val identity = RecordingIdentity(failure = IllegalStateException("identity down"))

        processor(claimed = listOf(message), newerExists = { false }, identity = identity).dispatchPending()

        assertNull(message.processedAt)
        assertEquals(1, message.attemptCount)
        assertEquals("identity down", message.lastError)
        assertTrue(!message.availableAt.isBefore(before.plusSeconds(5)))
    }

    @Test
    fun `retry delay grows exponentially and is capped`() {
        val processor = processor(claimed = emptyList(), newerExists = { false }, identity = RecordingIdentity())

        assertEquals(Duration.ofSeconds(5), processor.retryDelay(1))
        assertEquals(Duration.ofSeconds(10), processor.retryDelay(2))
        assertEquals(Duration.ofSeconds(1280), processor.retryDelay(9))
        assertEquals(Duration.ofSeconds(1280), processor.retryDelay(50))
    }

    private fun message(userId: UUID, enabled: Boolean, occurredAt: Instant = Instant.now()) =
        MerchantRoleOutboxMessage(
            merchantId = UUID.randomUUID(),
            userId = userId,
            enabled = enabled,
            occurredAt = occurredAt,
            availableAt = occurredAt,
        )

    private fun processor(
        claimed: List<MerchantRoleOutboxMessage>,
        newerExists: (MerchantRoleOutboxMessage) -> Boolean,
        identity: IdentityMerchantGateway,
    ): MerchantRoleOutboxProcessor {
        val repository = Proxy.newProxyInstance(
            MerchantRoleOutboxRepository::class.java.classLoader,
            arrayOf(MerchantRoleOutboxRepository::class.java),
        ) { instance, method, args ->
            when (method.name) {
                "claimDispatchable" -> claimed
                "existsNewerRoleChange" -> {
                    val id = args?.get(2) as UUID
                    newerExists(claimed.first { it.id == id })
                }
                "save" -> args?.first()
                "toString" -> "MerchantRoleOutboxRepositoryTestProxy"
                "hashCode" -> System.identityHashCode(instance)
                "equals" -> instance === args?.firstOrNull()
                else -> error("Unexpected repository call: ${method.name}")
            }
        } as MerchantRoleOutboxRepository
        return MerchantRoleOutboxProcessor(repository, identity)
    }

    private class RecordingIdentity(
        private val failure: RuntimeException? = null,
    ) : IdentityMerchantGateway {
        val calls = mutableListOf<Pair<UUID, Boolean>>()

        override fun findMerchantEligibility(userId: UUID): MerchantEligibilityResponse? = null

        override fun synchronizeMerchantRole(userId: UUID, request: MerchantRoleSyncRequest) {
            failure?.let { throw it }
            calls += userId to request.enabled
        }
    }
}
