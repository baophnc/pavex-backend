package com.pavex.shipment.demo

import com.pavex.storage.ObjectStorage
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class DemoResetResponse(
    val service: String,
    val clearedTables: List<String>,
    val purgedMessages: Int,
)

/**
 * Local demo only: forgets every shipment, parcel, scan, delivery and case, and the events not sent yet,
 * so a test run can start again from the seed data. Reset this service first: once its outbox is empty
 * nothing new reaches the others. The endpoint does not exist unless PAVEX_DEMO_RESET_ENABLED=true.
 */
@RestController
@RequestMapping("/api/v1/admin/shipments/demo-reset")
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
    private val transactions: TransactionTemplate,
    private val storage: ObjectStorage,
    @Value("\${pavex.storage.evidence-bucket}") private val bucket: String,
) {
    fun reset(): DemoResetResponse {
        val photoKeys = transactions.execute {
            val keys = jdbc.queryForList(
                "SELECT object_key FROM $SCHEMA.delivery_evidence WHERE object_key IS NOT NULL",
                String::class.java,
            ).filterNotNull()
            jdbc.execute("TRUNCATE TABLE ${TABLES.joinToString { "$SCHEMA.$it" }} CASCADE")
            keys
        }.orEmpty()
        // Only once the rows are gone, so a failed reset never leaves evidence pointing at no photo.
        val deleted = photoKeys.count { key -> runCatching { storage.delete(bucket, key) }.isSuccess }
        log.warn("Demo reset cleared {} table(s) and {} of {} delivery photo(s)", TABLES.size, deleted, photoKeys.size)
        return DemoResetResponse("shipment", TABLES, 0)
    }

    private companion object {
        val log = LoggerFactory.getLogger(DemoResetService::class.java)
        const val SCHEMA = "shipments"
        val TABLES = listOf(
            "delivery_evidence",
            "delivery_attempts",
            "shipment_weight_adjustments",
            "shipment_exception_requests",
            "shipment_control_signals",
            "shipment_case_events",
            "shipment_cases",
            "shipment_events",
            "parcels",
            "shipments",
            "outbox_events",
        )
    }
}
