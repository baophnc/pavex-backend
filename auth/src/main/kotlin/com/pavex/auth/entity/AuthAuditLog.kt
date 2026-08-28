package com.pavex.auth.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "auth_audit_log",
    schema = "auth",
)
class AuthAuditLog(

    @Id
    var id: UUID = UUID.randomUUID(),

    @Column(
        nullable = false,
        length = 96,
    )
    var action: String = "",

    @Column(
        nullable = false,
        length = 16,
    )
    var outcome: String = "",

    @Column(
        name = "reason_code",
        length = 96,
    )
    var reasonCode: String? = null,

    @Column(name = "actor_user_id")
    var actorUserId: UUID? = null,

    @Column(
        name = "subject_hash",
        length = 64,
    )
    var subjectHash: String? = null,

    @Column(
        name = "request_id",
        length = 128,
    )
    var requestId: String? = null,

    @Column(
        name = "ip_address",
        length = 64,
    )
    var ipAddress: String? = null,

    @Column(
        name = "user_agent",
        length = 512,
    )
    var userAgent: String? = null,

    @Column(
        name = "created_at",
        nullable = false,
    )
    var createdAt: Instant = Instant.now(),
)
