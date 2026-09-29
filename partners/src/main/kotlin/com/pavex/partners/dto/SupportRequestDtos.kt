package com.pavex.partners.dto

import com.pavex.partners.entity.SupportRequestAction
import com.pavex.partners.entity.SupportRequestKind
import com.pavex.partners.entity.SupportRequestStatus
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** The JSON part of the public multipart submission; files arrive as `attachments` parts. */
data class SubmitSupportRequest(
    val kind: SupportRequestKind,
    val fullName: String = "",
    val email: String = "",
    val phone: String? = null,
    val topic: String = "",
    val trackingCode: String? = null,
    val incidentDate: LocalDate? = null,
    val claimedAmount: Long? = null,
    val portfolioUrl: String? = null,
    val message: String = "",
    /** Hidden form field real visitors never see; bots that fill it get a fake receipt. */
    val website: String? = null,
)

data class SupportRequestSubmittedResponse(
    val code: String,
    val kind: SupportRequestKind,
    val createdAt: Instant,
)

data class SupportRequestSummaryResponse(
    val id: UUID,
    val code: String,
    val kind: SupportRequestKind,
    val status: SupportRequestStatus,
    val fullName: String,
    val email: String,
    val topic: String,
    val trackingCode: String?,
    val assigneeUserId: UUID?,
    val attachmentCount: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class SupportRequestListResponse(
    val content: List<SupportRequestSummaryResponse>,
    val page: Int,
    val pageSize: Int,
    val totalElements: Long,
    val totalPages: Int,
)

data class SupportAttachmentResponse(
    val id: UUID,
    val fileName: String,
    val contentType: String,
    val sizeBytes: Int,
    val createdAt: Instant,
)

data class SupportRequestEventResponse(
    val action: SupportRequestAction,
    val actorUserId: UUID?,
    val fromStatus: SupportRequestStatus?,
    val toStatus: SupportRequestStatus?,
    val note: String?,
    val createdAt: Instant,
)

data class SupportRequestDetailResponse(
    val id: UUID,
    val code: String,
    val kind: SupportRequestKind,
    val status: SupportRequestStatus,
    val fullName: String,
    val email: String,
    val phone: String?,
    val topic: String,
    val trackingCode: String?,
    val incidentDate: LocalDate?,
    val claimedAmount: Long?,
    val portfolioUrl: String?,
    val message: String,
    val submittedByUserId: UUID?,
    val assigneeUserId: UUID?,
    val resolutionNote: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val closedAt: Instant?,
    val version: Long,
    val attachments: List<SupportAttachmentResponse>,
    val events: List<SupportRequestEventResponse>,
)

data class ChangeSupportRequestStatus(
    val status: SupportRequestStatus,
    val note: String? = null,
)

data class AssignSupportRequest(
    /** Null releases the request back to the shared queue. */
    val assigneeUserId: UUID? = null,
)

data class SupportRequestCountsResponse(
    val new: Long,
    val inProgress: Long,
)
