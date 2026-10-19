package com.pavex.operations.service

import com.pavex.contracts.operations.OperationsHubSnapshot
import com.pavex.operations.client.NetworkHubClient
import com.pavex.test.PostgresIntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyCollection
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.support.TransactionTemplate
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/**
 * The roster on a real PostgreSQL, for the local stack's case: a hub with a single line-haul driver
 * (scripts/seed-mini), on his day off and after office hours.
 */
@PostgresIntegrationTest
class ShiftRosterDatabaseTest {
    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Autowired
    private lateinit var transactions: TransactionTemplate

    @Autowired
    private lateinit var dispatchQueue: AssignmentDispatchQueue

    // Monday 19/10/2026 20:00 in Vietnam: the lone driver (position 0) rests on Mondays.
    private val mondayEvening = Instant.parse("2026-10-19T13:00:00Z")

    @Test
    fun `the standing rotation leaves a lone driver off duty on his day off`() {
        val (hub, driver) = hubWithLoneDriver()
        roster(fullDay = false).run(mondayEvening)

        assertEquals(emptyList<UUID>(), onShiftNow(hub))
        assertTrue(scheduledEntries(driver) > 0, "the driver still works the other days")
    }

    @Test
    fun `full-day shifts put everyone on shift every day at any hour`() {
        val (hub, driver) = hubWithLoneDriver()
        val result = roster(fullDay = true).run(mondayEvening)

        assertEquals(listOf(driver), onShiftNow(hub))
        assertEquals(7, scheduledEntries(driver), "one shift a day for the coming week, no day off")
        assertEquals(RosterResult(0, 0, 0), roster(fullDay = true).run(mondayEvening), "a second run adds nothing")
        assertEquals(7, result.rosterEntriesCreated)
    }

    private fun roster(fullDay: Boolean): ShiftRosterService {
        val network = mock(NetworkHubClient::class.java)
        `when`(network.snapshots(anyCollection())).thenAnswer { invocation ->
            invocation.getArgument<Collection<UUID>>(0).map {
                OperationsHubSnapshot(it, "HN-BD", "Bưu cục Ba Đình", "LOCAL", "ACTIVE", "Số 1", "01", "00004")
            }
        }
        return ShiftRosterService(
            jdbc, network, ShiftRosterProperties(fullDay = fullDay), transactions, dispatchQueue,
            Clock.fixed(mondayEvening, ZoneOffset.UTC),
        )
    }

    private fun hubWithLoneDriver(): Pair<UUID, UUID> {
        val hub = UUID.randomUUID()
        val driver = UUID.randomUUID()
        val at = Timestamp.from(mondayEvening.minusSeconds(86_400 * 30))
        jdbc.update(
            "INSERT INTO operations.workforce_members (id, user_id, employee_code, kind, status, created_at, updated_at) VALUES (?, ?, ?, 'LINE_HAUL_DRIVER', 'ACTIVE', ?, ?)",
            driver, UUID.randomUUID(), "TX-" + driver.toString().take(8), at, at,
        )
        jdbc.update(
            "INSERT INTO operations.hub_memberships (id, workforce_member_id, hub_id, is_primary, is_active, assigned_at, updated_at) VALUES (?, ?, ?, TRUE, TRUE, ?, ?)",
            UUID.randomUUID(), driver, hub, at, at,
        )
        return hub to driver
    }

    /** Members rostered on a shift of [hub] that is running at [mondayEvening]. */
    private fun onShiftNow(hub: UUID): List<UUID> = jdbc.queryForList(
        """
        SELECT a.workforce_member_id FROM operations.workforce_shift_assignments a
        JOIN operations.work_shifts s ON s.id = a.shift_id
        WHERE s.hub_id = ? AND s.status = 'SCHEDULED' AND s.starts_at <= ? AND s.ends_at > ?
        """.trimIndent(),
        UUID::class.java, hub, Timestamp.from(mondayEvening), Timestamp.from(mondayEvening),
    ).filterNotNull()

    private fun scheduledEntries(member: UUID): Int = jdbc.queryForObject(
        "SELECT count(*) FROM operations.workforce_shift_assignments WHERE workforce_member_id = ? AND status = 'SCHEDULED'",
        Int::class.java, member,
    )!!
}
