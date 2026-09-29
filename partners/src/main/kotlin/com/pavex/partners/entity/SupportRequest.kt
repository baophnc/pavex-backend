package com.pavex.partners.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

enum class SupportRequestKind { CONTACT, SUPPORT, CLAIM, CAREER }

/** NEW until customer care picks it up; RESOLVED or CLOSED (spam, duplicate) end it but can be reopened. */
enum class SupportRequestStatus { NEW, IN_PROGRESS, RESOLVED, CLOSED }

enum class SupportRequestAction { SUBMITTED, STATUS_CHANGED, ASSIGNED, UNASSIGNED }

@Entity
@Table(name = "support_requests", schema = "partners")
class SupportRequest(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(nullable = false, length = 20)
    var code: String = "",
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var kind: SupportRequestKind = SupportRequestKind.CONTACT,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: SupportRequestStatus = SupportRequestStatus.NEW,
    @Column(name = "full_name", nullable = false, length = 100)
    var fullName: String = "",
    @Column(nullable = false, length = 160)
    var email: String = "",
    @Column(length = 20)
    var phone: String? = null,
    @Column(nullable = false, length = 100)
    var topic: String = "",
    @Column(name = "tracking_code", length = 40)
    var trackingCode: String? = null,
    @Column(name = "incident_date")
    var incidentDate: LocalDate? = null,
    @Column(name = "claimed_amount")
    var claimedAmount: Long? = null,
    @Column(name = "portfolio_url", length = 500)
    var portfolioUrl: String? = null,
    @Column(nullable = false, length = 2000)
    var message: String = "",
    @Column(name = "submitted_by_user_id")
    var submittedByUserId: UUID? = null,
    @Column(name = "assignee_user_id")
    var assigneeUserId: UUID? = null,
    @Column(name = "resolution_note", length = 1000)
    var resolutionNote: String? = null,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
    @Column(name = "closed_at")
    var closedAt: Instant? = null,
    @Version
    var version: Long = 0,
) {
    /** Moves the request; finishing it (RESOLVED or CLOSED) needs a note saying what was done. */
    fun changeStatus(next: SupportRequestStatus, note: String?, now: Instant) {
        require(next != status) { "The request is already $status." }
        val normalizedNote = note?.trim()?.takeIf { it.isNotEmpty() }
        require(normalizedNote == null || normalizedNote.length <= 1000) { "The note must not exceed 1000 characters." }
        val finishing = next == SupportRequestStatus.RESOLVED || next == SupportRequestStatus.CLOSED
        require(!finishing || normalizedNote != null) { "Explain how the request was resolved or why it was closed." }
        status = next
        if (finishing) {
            resolutionNote = normalizedNote
            closedAt = now
        } else {
            closedAt = null
        }
        updatedAt = now
    }

    /**
     * Taking a request needs it to be free, so two people never work the same customer. Handing it
     * back returns an in-progress request to NEW, where the shared queue will see it again.
     */
    fun assign(assignee: UUID?, now: Instant) {
        require(assignee != assigneeUserId) { "The request already has this assignee." }
        require(assignee == null || assigneeUserId == null) { "Someone else is already handling this request." }
        assigneeUserId = assignee
        if (assignee != null && status == SupportRequestStatus.NEW) status = SupportRequestStatus.IN_PROGRESS
        if (assignee == null && status == SupportRequestStatus.IN_PROGRESS) status = SupportRequestStatus.NEW
        updatedAt = now
    }
}

@Entity
@Table(name = "support_request_attachments", schema = "partners")
class SupportRequestAttachment(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(name = "request_id", nullable = false)
    var requestId: UUID = UUID.randomUUID(),
    @Column(name = "file_name", nullable = false, length = 200)
    var fileName: String = "",
    @Column(name = "content_type", nullable = false, length = 50)
    var contentType: String = "",
    @Column(name = "size_bytes", nullable = false)
    var sizeBytes: Int = 0,
    @Column(nullable = false)
    var data: ByteArray = ByteArray(0),
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
)

@Entity
@Table(name = "support_request_events", schema = "partners")
class SupportRequestEvent(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(name = "request_id", nullable = false)
    var requestId: UUID = UUID.randomUUID(),
    @Column(name = "actor_user_id")
    var actorUserId: UUID? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var action: SupportRequestAction = SupportRequestAction.SUBMITTED,
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    var fromStatus: SupportRequestStatus? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", length = 20)
    var toStatus: SupportRequestStatus? = null,
    @Column(length = 1000)
    var note: String? = null,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
)
