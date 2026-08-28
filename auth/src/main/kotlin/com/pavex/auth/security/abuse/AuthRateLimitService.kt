package com.pavex.auth.security.abuse

import com.pavex.auth.exception.RateLimitExceededException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import kotlin.math.max

@Service
class AuthRateLimitService(
    private val jdbcTemplate: JdbcTemplate,
    private val securityKeyHasher: SecurityKeyHasher,
) {

    fun check(
        policy: AuthRateLimitPolicy,
        keyMaterial: String,
    ) {
        if (keyMaterial.isBlank()) {
            return
        }

        val now = Instant.now()
        val cutoff = now.minus(policy.window)
        val expiresAt = now.plus(policy.window)
        val keyHash = securityKeyHasher.hash(keyMaterial)

        val bucket = jdbcTemplate.queryForObject(
            """
            INSERT INTO auth.auth_rate_limit AS bucket
                (policy, key_hash, window_started_at, request_count, expires_at)
            VALUES (?, ?, ?, 1, ?)
            ON CONFLICT (policy, key_hash)
            DO UPDATE SET
                request_count = CASE
                    WHEN bucket.window_started_at <= ? THEN 1
                    ELSE bucket.request_count + 1
                END,
                window_started_at = CASE
                    WHEN bucket.window_started_at <= ? THEN EXCLUDED.window_started_at
                    ELSE bucket.window_started_at
                END,
                expires_at = CASE
                    WHEN bucket.window_started_at <= ? THEN EXCLUDED.expires_at
                    ELSE bucket.expires_at
                END
            RETURNING request_count, window_started_at
            """.trimIndent(),
            { resultSet, _ ->
                RateLimitBucket(
                    requestCount =
                        resultSet.getInt(
                            "request_count"
                        ),
                    windowStartedAt =
                        resultSet
                            .getTimestamp(
                                "window_started_at"
                            )
                            .toInstant(),
                )
            },
            policy.code,
            keyHash,
            Timestamp.from(now),
            Timestamp.from(expiresAt),
            Timestamp.from(cutoff),
            Timestamp.from(cutoff),
            Timestamp.from(cutoff),
        ) ?: error(
            "Rate limit upsert returned no row"
        )

        if (bucket.requestCount > policy.limit) {
            val retryAt =
                bucket.windowStartedAt
                    .plus(policy.window)

            val retryAfter =
                max(
                    1L,
                    Duration.between(
                        Instant.now(),
                        retryAt,
                    ).seconds,
                )

            throw RateLimitExceededException(
                retryAfterSeconds = retryAfter,
            )
        }
    }

    fun deleteExpired(
        now: Instant,
    ): Int =
        jdbcTemplate.update(
            """
            DELETE FROM auth.auth_rate_limit
            WHERE expires_at < ?
            """.trimIndent(),
            Timestamp.from(now),
        )

    private data class RateLimitBucket(
        val requestCount: Int,
        val windowStartedAt: Instant,
    )
}
