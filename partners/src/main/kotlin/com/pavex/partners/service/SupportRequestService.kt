package com.pavex.partners.service

import com.pavex.common.api.OneBasedPagination
import com.pavex.partners.dto.AssignSupportRequest
import com.pavex.partners.dto.ChangeSupportRequestStatus
import com.pavex.partners.dto.SubmitSupportRequest
import com.pavex.partners.dto.SupportRequestCountsResponse
import com.pavex.partners.dto.SupportRequestDetailResponse
import com.pavex.partners.dto.SupportRequestEventResponse
import com.pavex.partners.dto.SupportRequestListResponse
import com.pavex.partners.dto.SupportRequestSubmittedResponse
import com.pavex.partners.dto.SupportRequestSummaryResponse
import com.pavex.partners.entity.SupportRequest
import com.pavex.partners.entity.SupportRequestAction
import com.pavex.partners.entity.SupportRequestAttachment
import com.pavex.partners.entity.SupportRequestEvent
import com.pavex.partners.entity.SupportRequestKind
import com.pavex.partners.entity.SupportRequestStatus
import com.pavex.partners.exception.ApiException
import com.pavex.partners.repository.SupportRequestAttachmentRepository
import com.pavex.partners.repository.SupportRequestEventRepository
import com.pavex.partners.repository.SupportRequestRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Sent after commit so a slow mail server never holds the request transaction. Carries no
 * submitted free text: the receipt must not repeat what a stranger typed (see SupportRequestMailer).
 */
data class SupportRequestSubmittedEvent(
    val code: String,
    val kind: SupportRequestKind,
    val email: String,
)

@Service
class SupportRequestService(
    private val requests: SupportRequestRepository,
    private val attachments: SupportRequestAttachmentRepository,
    private val events: SupportRequestEventRepository,
    private val publisher: ApplicationEventPublisher,
) {
    @Transactional
    fun submit(actorUserId: UUID?, request: SubmitSupportRequest, files: List<IncomingSupportFile>): SupportRequestSubmittedResponse {
        val now = Instant.now()
        if (!request.website.isNullOrBlank()) {
            // A bot filled the hidden field: answer like a success, store nothing.
            return SupportRequestSubmittedResponse(SupportRequestCodes.next(now), request.kind, now)
        }
        val normalized = SupportRequestIntake.normalize(request, LocalDate.ofInstant(now, SupportRequestIntake.VIETNAM))
        val normalizedFiles = SupportRequestIntake.normalizeFiles(normalized.kind, files)
        if (requests.countByEmailAndCreatedAtAfter(normalized.email, now.minus(THROTTLE_WINDOW)) >= THROTTLE_LIMIT) {
            throw ApiException(
                HttpStatus.TOO_MANY_REQUESTS,
                "support_request_rate_limited",
                "Bạn đã gửi nhiều yêu cầu trong một giờ qua. Vui lòng thử lại sau.",
            )
        }

        val saved = requests.save(
            SupportRequest(
                code = uniqueCode(now),
                kind = normalized.kind,
                fullName = normalized.fullName,
                email = normalized.email,
                phone = normalized.phone,
                topic = normalized.topic,
                trackingCode = normalized.trackingCode,
                incidentDate = normalized.incidentDate,
                claimedAmount = normalized.claimedAmount,
                portfolioUrl = normalized.portfolioUrl,
                message = normalized.message,
                submittedByUserId = actorUserId,
                createdAt = now,
                updatedAt = now,
            ),
        )
        attachments.saveAll(
            normalizedFiles.map { file ->
                SupportRequestAttachment(
                    requestId = saved.id,
                    fileName = file.fileName,
                    contentType = file.contentType,
                    sizeBytes = file.bytes.size,
                    data = file.bytes,
                    createdAt = now,
                )
            },
        )
        events.save(SupportRequestEvent(requestId = saved.id, actorUserId = actorUserId, action = SupportRequestAction.SUBMITTED, toStatus = saved.status, createdAt = now))
        publisher.publishEvent(SupportRequestSubmittedEvent(saved.code, saved.kind, saved.email))
        return SupportRequestSubmittedResponse(saved.code, saved.kind, saved.createdAt)
    }

    @Transactional(readOnly = true)
    fun list(
        page: Int,
        pageSize: Int,
        status: SupportRequestStatus?,
        kind: SupportRequestKind?,
        assignee: UUID?,
        search: String?,
    ): SupportRequestListResponse {
        val pagination = OneBasedPagination.normalize(page, pageSize)
        val result = requests.search(
            status,
            kind,
            assignee,
            search?.trim().orEmpty(),
            PageRequest.of(pagination.zeroBasedPage, pagination.pageSize, Sort.by(Sort.Direction.DESC, "createdAt")),
        )
        val counts = if (result.content.isEmpty()) emptyMap() else attachments.countByRequestIds(result.content.map { it.id })
            .associate { (it[0] as UUID) to (it[1] as Long) }
        return SupportRequestListResponse(
            result.content.map { it.toSummary(counts[it.id] ?: 0) },
            pagination.page,
            pagination.pageSize,
            result.totalElements,
            result.totalPages,
        )
    }

    @Transactional(readOnly = true)
    fun counts() = SupportRequestCountsResponse(
        new = requests.countByStatus(SupportRequestStatus.NEW),
        inProgress = requests.countByStatus(SupportRequestStatus.IN_PROGRESS),
    )

    @Transactional(readOnly = true)
    fun get(id: UUID): SupportRequestDetailResponse = find(id).toDetail()

    @Transactional(readOnly = true)
    fun attachment(id: UUID, attachmentId: UUID): SupportRequestAttachment =
        attachments.findByIdAndRequestId(attachmentId, id)
            ?: throw ApiException(HttpStatus.NOT_FOUND, "support_attachment_not_found", "Attachment was not found.")

    @Transactional
    fun changeStatus(id: UUID, actorUserId: UUID, body: ChangeSupportRequestStatus): SupportRequestDetailResponse = guarded {
        val request = find(id)
        val from = request.status
        domain { request.changeStatus(body.status, body.note, Instant.now()) }
        events.save(
            SupportRequestEvent(
                requestId = id,
                actorUserId = actorUserId,
                action = SupportRequestAction.STATUS_CHANGED,
                fromStatus = from,
                toStatus = request.status,
                note = body.note?.trim()?.takeIf { it.isNotEmpty() },
                createdAt = request.updatedAt,
            ),
        )
        requests.saveAndFlush(request).toDetail()
    }

    /** Customer care takes a request for themselves or hands it back to the shared queue. */
    @Transactional
    fun assign(id: UUID, actorUserId: UUID, body: AssignSupportRequest): SupportRequestDetailResponse = guarded {
        if (body.assigneeUserId != null && body.assigneeUserId != actorUserId) {
            throw ApiException(HttpStatus.BAD_REQUEST, "support_assign_self_only", "You can only take a request for yourself.")
        }
        val request = find(id)
        if (body.assigneeUserId != null && request.assigneeUserId != null && request.assigneeUserId != body.assigneeUserId) {
            throw ApiException(HttpStatus.CONFLICT, "support_request_taken", "Someone else is already handling this request.")
        }
        val from = request.status
        domain { request.assign(body.assigneeUserId, Instant.now()) }
        events.save(
            SupportRequestEvent(
                requestId = id,
                actorUserId = actorUserId,
                action = if (body.assigneeUserId == null) SupportRequestAction.UNASSIGNED else SupportRequestAction.ASSIGNED,
                fromStatus = from.takeIf { it != request.status },
                toStatus = request.status.takeIf { it != from },
                createdAt = request.updatedAt,
            ),
        )
        requests.saveAndFlush(request).toDetail()
    }

    private fun uniqueCode(now: Instant): String {
        repeat(5) {
            val code = SupportRequestCodes.next(now)
            if (!requests.existsByCode(code)) return code
        }
        throw ApiException(HttpStatus.SERVICE_UNAVAILABLE, "support_code_unavailable", "Could not allocate a request code. Please retry.")
    }

    private fun find(id: UUID): SupportRequest = requests.findById(id).orElseThrow {
        ApiException(HttpStatus.NOT_FOUND, "support_request_not_found", "Support request was not found.")
    }

    private fun <T> domain(block: () -> T): T = try {
        block()
    } catch (exception: IllegalArgumentException) {
        throw ApiException(HttpStatus.CONFLICT, "support_request_state", exception.message ?: "The request cannot change this way.")
    }

    private fun <T> guarded(block: () -> T): T = try {
        block()
    } catch (_: ObjectOptimisticLockingFailureException) {
        throw ApiException(HttpStatus.CONFLICT, "support_request_changed", "Someone else just updated this request. Reload and try again.")
    }

    private fun SupportRequest.toSummary(attachmentCount: Long) = SupportRequestSummaryResponse(
        id, code, kind, status, fullName, email, topic, trackingCode, assigneeUserId, attachmentCount, createdAt, updatedAt,
    )

    private fun SupportRequest.toDetail() = SupportRequestDetailResponse(
        id = id,
        code = code,
        kind = kind,
        status = status,
        fullName = fullName,
        email = email,
        phone = phone,
        topic = topic,
        trackingCode = trackingCode,
        incidentDate = incidentDate,
        claimedAmount = claimedAmount,
        portfolioUrl = portfolioUrl,
        message = message,
        submittedByUserId = submittedByUserId,
        assigneeUserId = assigneeUserId,
        resolutionNote = resolutionNote,
        createdAt = createdAt,
        updatedAt = updatedAt,
        closedAt = closedAt,
        version = version,
        attachments = attachments.findMetadata(id),
        events = events.findAllByRequestIdOrderByCreatedAtAsc(id).map {
            SupportRequestEventResponse(it.action, it.actorUserId, it.fromStatus, it.toStatus, it.note, it.createdAt)
        },
    )

    companion object {
        private const val THROTTLE_LIMIT = 5L
        private val THROTTLE_WINDOW: Duration = Duration.ofHours(1)
    }
}
