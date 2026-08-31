package com.pavex.operations.service

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@Component
class AssignmentLockService(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun shipment(shipmentId: String) = lock("operations:assignment:shipment:$shipmentId")

    fun pool(hubAndKind: String) = lock("operations:assignment:pool:$hubAndKind")

    private fun lock(key: String) {
        jdbcTemplate.queryForObject(
            "SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtextextended(?, 0))) AS locked",
            Int::class.java,
            key,
        )
    }
}
