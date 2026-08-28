package com.pavex.auth.service

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
            "auth:email:${email.trim().lowercase()}"
        )
    }


    fun lockUserSessions(
        userId: UUID,
    ) {

        lock(
            "auth:user-session:$userId"
        )
    }


    fun lockPasswordReset(
        userId: UUID,
    ) {

        lock(
            "auth:password-reset:$userId"
        )
    }


    fun lockInvitation(
        userId: UUID,
    ) {

        lock(
            "auth:invitation:$userId"
        )
    }


    fun lockEmailVerification(
        userId: UUID,
    ) {

        lock(
            "auth:email-verification:$userId"
        )
    }


    fun lockGoogleIdentity(
        subject: String,
        email: String,
    ) {

        lock(
            "auth:google-sub:$subject"
        )

        lockEmail(
            email
        )
    }


    fun tryLockTokenCleanup(): Boolean {

        return tryLock(
            "auth:token-cleanup"
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
