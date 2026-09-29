package com.pavex.partners.repository

import com.pavex.partners.dto.SupportAttachmentResponse
import com.pavex.partners.entity.SupportRequest
import com.pavex.partners.entity.SupportRequestAttachment
import com.pavex.partners.entity.SupportRequestEvent
import com.pavex.partners.entity.SupportRequestKind
import com.pavex.partners.entity.SupportRequestStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface SupportRequestRepository : JpaRepository<SupportRequest, UUID> {
    fun existsByCode(code: String): Boolean

    fun countByEmailAndCreatedAtAfter(email: String, after: Instant): Long

    fun countByStatus(status: SupportRequestStatus): Long

    @Query(
        """
    SELECT r FROM SupportRequest r
    WHERE (:status IS NULL OR r.status = :status)
      AND (:kind IS NULL OR r.kind = :kind)
      AND (:assignee IS NULL OR r.assigneeUserId = :assignee)
      AND (
           LOWER(r.code) LIKE LOWER(CONCAT('%', :search, '%')) OR
           LOWER(r.fullName) LIKE LOWER(CONCAT('%', :search, '%')) OR
           LOWER(r.email) LIKE LOWER(CONCAT('%', :search, '%')) OR
           LOWER(COALESCE(r.phone, '')) LIKE LOWER(CONCAT('%', :search, '%')) OR
           LOWER(COALESCE(r.trackingCode, '')) LIKE LOWER(CONCAT('%', :search, '%'))
      )
    """,
    )
    fun search(
        @Param("status") status: SupportRequestStatus?,
        @Param("kind") kind: SupportRequestKind?,
        @Param("assignee") assignee: UUID?,
        @Param("search") search: String,
        pageable: Pageable,
    ): Page<SupportRequest>
}

interface SupportRequestAttachmentRepository : JpaRepository<SupportRequestAttachment, UUID> {
    /** Metadata only: the file bytes stay in the database until someone opens the file. */
    @Query(
        """
    SELECT new com.pavex.partners.dto.SupportAttachmentResponse(a.id, a.fileName, a.contentType, a.sizeBytes, a.createdAt)
    FROM SupportRequestAttachment a
    WHERE a.requestId = :requestId
    ORDER BY a.createdAt ASC
    """,
    )
    fun findMetadata(@Param("requestId") requestId: UUID): List<SupportAttachmentResponse>

    @Query("SELECT a.requestId, COUNT(a) FROM SupportRequestAttachment a WHERE a.requestId IN :requestIds GROUP BY a.requestId")
    fun countByRequestIds(@Param("requestIds") requestIds: Collection<UUID>): List<Array<Any>>

    fun findByIdAndRequestId(id: UUID, requestId: UUID): SupportRequestAttachment?
}

interface SupportRequestEventRepository : JpaRepository<SupportRequestEvent, UUID> {
    fun findAllByRequestIdOrderByCreatedAtAsc(requestId: UUID): List<SupportRequestEvent>
}
