package com.pavex.identity.job

import com.pavex.identity.repository.EmailVerificationTokenRepository
import com.pavex.identity.repository.InvitationTokenRepository
import com.pavex.identity.repository.PasswordResetTokenRepository
import com.pavex.identity.repository.RefreshSessionRepository
import com.pavex.identity.service.AdvisoryLockService
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
    prefix = "identity.cleanup",
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

    @Value(
        "\${identity.cleanup.token-retention:P7D}"
    )
    private val tokenRetention:
        Duration,
) {

    private val logger =
        LoggerFactory.getLogger(
            TokenCleanupJob::class.java
        )


    @Scheduled(
        cron =
            "\${identity.cleanup.cron:0 17 * * * *}",
        zone =
            "\${identity.cleanup.zone:UTC}",
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

        val total =
            emailVerificationCount +
                passwordResetCount +
                invitationCount +
                refreshSessionCount

        if (total > 0) {

            logger.info(
                "Identity token cleanup removed {} records: emailVerification={}, passwordReset={}, invitations={}, refreshSessions={}",
                total,
                emailVerificationCount,
                passwordResetCount,
                invitationCount,
                refreshSessionCount,
            )
        }
    }
}
