package com.pavex.operations.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID

enum class WorkforceMemberKind { COURIER, WAREHOUSE_OPERATOR, HUB_MANAGER, LINE_HAUL_DRIVER }
enum class WorkforceMemberStatus { ACTIVE, SUSPENDED, OFFBOARDED }

@Entity
@Table(name = "workforce_members", schema = "operations")
class WorkforceMember(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(name = "user_id", nullable = false, unique = true)
    var userId: UUID = UUID.randomUUID(),
    @Column(name = "employee_code", nullable = false, unique = true, length = 32)
    var employeeCode: String = "",
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var kind: WorkforceMemberKind = WorkforceMemberKind.COURIER,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var status: WorkforceMemberStatus = WorkforceMemberStatus.ACTIVE,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
    @Version
    var version: Long = 0,
) {
    fun changeStatus(next: WorkforceMemberStatus, now: Instant): Boolean {
        if (status == next) return false
        if (status == WorkforceMemberStatus.OFFBOARDED) {
            throw IllegalStateException("An offboarded workforce member cannot be reactivated.")
        }
        status = next
        updatedAt = now
        return true
    }

    companion object {
        fun create(userId: UUID, kind: WorkforceMemberKind, now: Instant): WorkforceMember {
            val id = UUID.randomUUID()
            val compact = id.toString().replace("-", "").uppercase()
            return WorkforceMember(
                id = id,
                userId = userId,
                employeeCode = "PAVEX-WF-${compact.take(10)}",
                kind = kind,
                status = WorkforceMemberStatus.ACTIVE,
                createdAt = now,
                updatedAt = now,
            )
        }
    }
}
