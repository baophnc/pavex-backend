package com.pavex.partners.controller

import com.pavex.partners.dto.AssignSupportRequest
import com.pavex.partners.dto.ChangeSupportRequestStatus
import com.pavex.partners.dto.SubmitSupportRequest
import com.pavex.partners.dto.SupportRequestCountsResponse
import com.pavex.partners.dto.SupportRequestDetailResponse
import com.pavex.partners.dto.SupportRequestListResponse
import com.pavex.partners.dto.SupportRequestSubmittedResponse
import com.pavex.partners.entity.SupportRequestKind
import com.pavex.partners.entity.SupportRequestStatus
import com.pavex.partners.exception.ApiException
import com.pavex.partners.service.IncomingSupportFile
import com.pavex.partners.service.SupportRequestService
import org.springframework.http.CacheControl
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID

/** Public intake: visitors may submit anonymously; a signed-in customer is linked to the request. */
@RestController
@RequestMapping("/api/v1/support/requests")
class SupportRequestController(
    private val support: SupportRequestService,
) {
    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    fun submit(
        @AuthenticationPrincipal jwt: Jwt?,
        @RequestPart("request") request: SubmitSupportRequest,
        @RequestPart("attachments", required = false) attachments: List<MultipartFile>?,
    ): SupportRequestSubmittedResponse = support.submit(
        jwt?.userId(),
        request,
        attachments.orEmpty().map { IncomingSupportFile(it.originalFilename, it.bytes) },
    )
}

@RestController
@RequestMapping("/api/v1/admin/support/requests")
class AdminSupportRequestController(
    private val support: SupportRequestService,
) {
    @GetMapping
    @PreAuthorize("hasAuthority('support.requests.read')")
    fun list(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") pageSize: Int,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) kind: String?,
        @RequestParam(required = false) mine: Boolean?,
        @RequestParam(required = false) search: String?,
    ): SupportRequestListResponse = support.list(
        page,
        pageSize,
        parse(status, "invalid_support_status") { SupportRequestStatus.valueOf(it) },
        parse(kind, "invalid_support_kind") { SupportRequestKind.valueOf(it) },
        if (mine == true) jwt.userId() else null,
        search,
    )

    @GetMapping("/counts")
    @PreAuthorize("hasAuthority('support.requests.read')")
    fun counts(): SupportRequestCountsResponse = support.counts()

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('support.requests.read')")
    fun get(@PathVariable id: UUID): SupportRequestDetailResponse = support.get(id)

    @GetMapping("/{id}/attachments/{attachmentId}")
    @PreAuthorize("hasAuthority('support.requests.read')")
    fun attachment(@PathVariable id: UUID, @PathVariable attachmentId: UUID): ResponseEntity<ByteArray> {
        val file = support.attachment(id, attachmentId)
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(file.contentType))
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.inline().filename(file.fileName, StandardCharsets.UTF_8).build().toString(),
            )
            .header("X-Content-Type-Options", "nosniff")
            .cacheControl(CacheControl.noStore())
            .body(file.data)
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAuthority('support.requests.manage')")
    fun changeStatus(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: UUID,
        @RequestBody body: ChangeSupportRequestStatus,
    ): SupportRequestDetailResponse = support.changeStatus(id, jwt.userId(), body)

    @PostMapping("/{id}/assignee")
    @PreAuthorize("hasAuthority('support.requests.manage')")
    fun assign(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: UUID,
        @RequestBody body: AssignSupportRequest,
    ): SupportRequestDetailResponse = support.assign(id, jwt.userId(), body)

    private fun <T> parse(value: String?, code: String, convert: (String) -> T): T? {
        if (value.isNullOrBlank()) return null
        return try {
            convert(value.trim().uppercase(Locale.ROOT))
        } catch (_: IllegalArgumentException) {
            throw ApiException(HttpStatus.BAD_REQUEST, code, "Filter value is invalid.")
        }
    }
}
