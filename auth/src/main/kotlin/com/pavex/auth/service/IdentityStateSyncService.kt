package com.pavex.auth.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.util.UUID

@Service
class IdentityStateSyncService(
    private val identityClient: IdentityClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun emailVerifiedAfterCommit(userId: UUID) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                object : TransactionSynchronization {
                    override fun afterCommit() {
                        syncEmailVerified(userId)
                    }
                }
            )
            return
        }

        syncEmailVerified(userId)
    }

    private fun syncEmailVerified(userId: UUID) {
        runCatching {
            identityClient.updateEmailVerification(
                userId = userId,
                emailVerified = true,
            )
        }.onFailure { exception ->
            log.warn(
                "Unable to synchronize verified email state to Identity for user {}. It will self-heal on the next authentication flow.",
                userId,
                exception,
            )
        }
    }
}
