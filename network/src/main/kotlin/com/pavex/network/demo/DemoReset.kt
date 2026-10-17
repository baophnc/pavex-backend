package com.pavex.network.demo

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class DemoResetResponse(
    val service: String,
    val clearedTables: List<String>,
    val purgedMessages: Int,
)

/**
 * Local demo only: forgets the room shipments reserved on the lanes,
 * so a test run can start again from the seed data.
 * The endpoint does not exist unless PAVEX_DEMO_RESET_ENABLED=true.
 */
@RestController
@RequestMapping("/api/v1/admin/network/demo-reset")
@PreAuthorize("hasRole('ADMIN')")
@ConditionalOnProperty(prefix = "pavex.demo-reset", name = ["enabled"], havingValue = "true")
class DemoResetController(
    private val demoReset: DemoResetService,
) {
    @PostMapping
    fun reset(): DemoResetResponse = demoReset.reset()
}

@Service
@ConditionalOnProperty(prefix = "pavex.demo-reset", name = ["enabled"], havingValue = "true")
class DemoResetService(
    private val jdbc: JdbcTemplate,
) {
    @Transactional
    fun reset(): DemoResetResponse {
        val purged = 0
        jdbc.execute("TRUNCATE TABLE ${TABLES.joinToString { "$SCHEMA.$it" }} CASCADE")
        log.warn("Demo reset cleared {} table(s) and {} queued message(s)", TABLES.size, purged)
        return DemoResetResponse("network", TABLES, purged)
    }

    private companion object {
        val log = LoggerFactory.getLogger(DemoResetService::class.java)
        const val SCHEMA = "network"
        val TABLES = listOf(
            "lane_capacity_reservations",
        )
    }
}
