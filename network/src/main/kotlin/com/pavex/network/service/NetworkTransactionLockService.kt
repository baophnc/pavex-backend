package com.pavex.network.service

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@Component
class NetworkTransactionLockService(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun lock(key: String) {
        jdbcTemplate.queryForList(
            "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
            key,
        )
    }

    fun lockAll(keys: Collection<String>) {
        keys.distinct().sorted().forEach(::lock)
    }
}
