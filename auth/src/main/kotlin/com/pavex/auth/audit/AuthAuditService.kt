package com.pavex.auth.audit

import com.pavex.auth.entity.AuthAuditLog
import com.pavex.auth.repository.AuthAuditLogRepository
import com.pavex.auth.security.abuse.SecurityKeyHasher
import com.pavex.auth.service.ClientMetadata
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.UUID

@Service
class AuthAuditService(
    private val repository: AuthAuditLogRepository,
    private val securityKeyHasher: SecurityKeyHasher,
    transactionManager: PlatformTransactionManager,
) {

    private val logger =
        LoggerFactory.getLogger(
            AuthAuditService::class.java
        )

    private val requiresNew =
        TransactionTemplate(
            transactionManager
        ).apply {
            propagationBehavior =
                TransactionDefinition.PROPAGATION_REQUIRES_NEW
        }

    fun success(
        action: String,
        metadata: ClientMetadata,
        actorUserId: UUID? = null,
        subject: String? = null,
    ) {
        record(
            action = action,
            outcome = "SUCCESS",
            reasonCode = null,
            metadata = metadata,
            actorUserId = actorUserId,
            subject = subject,
        )
    }

    fun failure(
        action: String,
        reasonCode: String,
        metadata: ClientMetadata,
        actorUserId: UUID? = null,
        subject: String? = null,
    ) {
        record(
            action = action,
            outcome = "FAILURE",
            reasonCode = reasonCode,
            metadata = metadata,
            actorUserId = actorUserId,
            subject = subject,
        )
    }

    private fun record(
        action: String,
        outcome: String,
        reasonCode: String?,
        metadata: ClientMetadata,
        actorUserId: UUID?,
        subject: String?,
    ) {
        try {
            requiresNew.executeWithoutResult {
                repository.save(
                    AuthAuditLog(
                        action = action,
                        outcome = outcome,
                        reasonCode = reasonCode,
                        actorUserId = actorUserId,
                        subjectHash =
                            subject
                                ?.takeIf(String::isNotBlank)
                                ?.let(
                                    securityKeyHasher::hash
                                ),
                        requestId =
                            metadata.requestId
                                ?.take(128),
                        ipAddress =
                            metadata.ipAddress
                                ?.take(64),
                        userAgent =
                            metadata.userAgent
                                ?.take(512),
                        createdAt = Instant.now(),
                    )
                )
            }
        } catch (exception: RuntimeException) {
            // Audit must never turn a successful authentication operation into
            // an application failure. Operational monitoring should alert on
            // this log instead.
            logger.error(
                "Failed to persist auth audit action={} outcome={}",
                action,
                outcome,
                exception,
            )
        }
    }
}
