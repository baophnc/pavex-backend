package com.pavex.identity.service

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.util.UUID

@Service
class AdvisoryLockService(

    private val jdbcTemplate:
        JdbcTemplate,
) {

    fun lock(
        key: String,
    ) {

        requireTransaction()

        jdbcTemplate.queryForList(
            """
            SELECT pg_advisory_xact_lock(
                hashtextextended(?, 0)
            )
            """.trimIndent(),
            key,
        )
    }


    fun tryLock(
        key: String,
    ): Boolean {

        requireTransaction()

        return jdbcTemplate.queryForObject(
            """
            SELECT pg_try_advisory_xact_lock(
                hashtextextended(?, 0)
            )
            """.trimIndent(),
            { resultSet, _ ->
                resultSet.getBoolean(1)
            },
            key,
        )
    }


    fun lockEmail(
        email: String,
    ) {

        lock(
            "identity:email:${email.trim().lowercase()}"
        )
    }


    fun lockUserSessions(
        userId: UUID,
    ) {

        lock(
            "identity:user-session:$userId"
        )
    }


    fun lockPasswordReset(
        userId: UUID,
    ) {

        lock(
            "identity:password-reset:$userId"
        )
    }


    fun lockInvitation(
        userId: UUID,
    ) {

        lock(
            "identity:invitation:$userId"
        )
    }


    fun lockEmailVerification(
        userId: UUID,
    ) {

        lock(
            "identity:email-verification:$userId"
        )
    }


    fun lockGoogleIdentity(
        subject: String,
        email: String,
    ) {

        lock(
            "identity:google-sub:$subject"
        )

        lockEmail(
            email
        )
    }


    fun tryLockTokenCleanup(): Boolean {

        return tryLock(
            "identity:token-cleanup"
        )
    }


    private fun requireTransaction() {

        check(
            TransactionSynchronizationManager
                .isActualTransactionActive()
        ) {
            "PostgreSQL advisory transaction lock requires an active transaction"
        }
    }
}
