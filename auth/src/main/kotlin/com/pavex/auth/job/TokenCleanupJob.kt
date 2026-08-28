package com.pavex.auth.job

import com.pavex.auth.repository.EmailVerificationTokenRepository
import com.pavex.auth.repository.ConsumedMessageRepository
import com.pavex.auth.repository.InvitationTokenRepository
import com.pavex.auth.repository.OutboxMessageRepository
import com.pavex.auth.repository.PasswordResetTokenRepository
import com.pavex.auth.repository.RefreshSessionRepository
import com.pavex.auth.service.AdvisoryLockService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

@Component
@ConditionalOnProperty(
    prefix = "auth.cleanup",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class TokenCleanupJob(

    private val emailVerificationTokenRepository:
        EmailVerificationTokenRepository,

    private val passwordResetTokenRepository:
        PasswordResetTokenRepository,

    private val invitationTokenRepository:
        InvitationTokenRepository,

    private val refreshSessionRepository:
        RefreshSessionRepository,

    private val advisoryLockService:
        AdvisoryLockService,

    private val outboxMessageRepository:
        OutboxMessageRepository,

    private val consumedMessageRepository:
        ConsumedMessageRepository,

    @Value(
        "\${auth.cleanup.token-retention:P7D}"
    )
    private val tokenRetention:
        Duration,

    @Value(
        "\${auth.outbox.published-retention:P7D}"
    )
    private val outboxPublishedRetention:
        Duration,

    @Value(
        "\${auth.outbox.consumed-retention:P30D}"
    )
    private val consumedMessageRetention:
        Duration,
) {

    private val logger =
        LoggerFactory.getLogger(
            TokenCleanupJob::class.java
        )


    @Scheduled(
        cron =
            "\${auth.cleanup.cron:0 17 * * * *}",
        zone =
            "\${auth.cleanup.zone:UTC}",
    )
    @Transactional
    fun cleanup() {

        /*
         * Với nhiều replica, instance không lấy được lock
         * sẽ SKIP thay vì đứng chờ rồi cleanup lần hai.
         */
        if (
            !advisoryLockService
                .tryLockTokenCleanup()
        ) {
            return
        }

        val cutoff =
            Instant.now()
                .minus(
                    tokenRetention
                )

        val emailVerificationCount =
            emailVerificationTokenRepository
                .deleteStale(
                    cutoff
                )

        val passwordResetCount =
            passwordResetTokenRepository
                .deleteStale(
                    cutoff
                )

        val invitationCount =
            invitationTokenRepository
                .deleteStale(
                    cutoff
                )

        val refreshSessionCount =
            refreshSessionRepository
                .deleteStale(
                    cutoff
                )

        val now =
            Instant.now()

        val outboxCount =
            outboxMessageRepository
                .deletePublishedBefore(
                    now.minus(
                        outboxPublishedRetention
                    )
                )

        val consumedMessageCount =
            consumedMessageRepository
                .deleteProcessedBefore(
                    now.minus(
                        consumedMessageRetention
                    )
                )

        val total =
            emailVerificationCount +
                passwordResetCount +
                invitationCount +
                refreshSessionCount +
                outboxCount +
                consumedMessageCount

        if (total > 0) {

            logger.info(
                "Auth cleanup removed {} records: emailVerification={}, passwordReset={}, invitations={}, refreshSessions={}, outbox={}, consumedMessages={}",
                total,
                emailVerificationCount,
                passwordResetCount,
                invitationCount,
                refreshSessionCount,
                outboxCount,
                consumedMessageCount,
            )
        }
    }
}
