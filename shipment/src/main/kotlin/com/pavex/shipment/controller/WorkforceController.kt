package com.pavex.shipment.controller

import com.pavex.shipment.dto.DeliveryEvidenceResponse
import com.pavex.shipment.dto.WorkforceLookupResponse
import com.pavex.shipment.dto.WorkforceReportItem
import com.pavex.shipment.dto.WorkforceReportKind
import com.pavex.shipment.dto.WorkforceReportSummaryResponse
import com.pavex.shipment.dto.WorkforceScanRequest
import com.pavex.shipment.dto.WorkforceScanResponse
import com.pavex.shipment.dto.WorkforceTaskResponse
import com.pavex.shipment.security.requirePavexActor
import com.pavex.shipment.service.DeliveryEvidenceService
import com.pavex.shipment.service.WorkforceReportService
import com.pavex.shipment.service.WorkforceScanService
import com.pavex.shipment.service.WorkforceViewService
import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
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
import java.time.LocalDate
import java.util.UUID

/** Endpoints behind the Pavex field app (couriers, hub staff and line-haul drivers). */
@RestController
@RequestMapping("/api/v1/workforce")
class WorkforceController(
    private val views: WorkforceViewService,
    private val scans: WorkforceScanService,
    private val reports: WorkforceReportService,
    private val evidence: DeliveryEvidenceService,
) {
    @GetMapping("/tasks")
    @PreAuthorize("hasAuthority('assignments.read-own')")
    fun tasks(authentication: Authentication): List<WorkforceTaskResponse> =
        views.tasks(authentication.requirePavexActor())

    @GetMapping("/lookup/{code}")
    @PreAuthorize("hasAnyAuthority('assignments.read-own','shipments.read-hub','shipments.admin.read')")
    fun lookup(authentication: Authentication, @PathVariable code: String): WorkforceLookupResponse =
        views.lookup(authentication.requirePavexActor(), code)

    @PostMapping("/scans")
    @PreAuthorize("hasAuthority('scans.create')")
    fun scan(authentication: Authentication, @Valid @RequestBody request: WorkforceScanRequest): WorkforceScanResponse =
        scans.scan(authentication.requirePavexActor(), request)

    @PostMapping("/evidence", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('deliveries.complete-own')")
    fun uploadEvidence(authentication: Authentication, @RequestPart("file") file: MultipartFile): DeliveryEvidenceResponse =
        evidence.upload(authentication.requirePavexActor(), file.contentType, file.bytes)

    @GetMapping("/evidence/{id}")
    @PreAuthorize("hasAnyAuthority('deliveries.complete-own','shipments.read-hub','shipments.admin.read')")
    fun readEvidence(authentication: Authentication, @PathVariable id: UUID): ResponseEntity<ByteArray> {
        val item = evidence.read(authentication.requirePavexActor(), id)
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(item.contentType))
            .cacheControl(CacheControl.noStore())
            .body(item.data)
    }

    @GetMapping("/reports/summary")
    @PreAuthorize("hasAnyAuthority('assignments.read-own','shipments.read-hub')")
    fun reportSummary(
        authentication: Authentication,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
        @RequestParam(required = false) hubId: UUID?,
    ): WorkforceReportSummaryResponse = reports.summary(authentication.requirePavexActor(), from, to, hubId)

    @GetMapping("/reports/items")
    @PreAuthorize("hasAnyAuthority('assignments.read-own','shipments.read-hub')")
    fun reportItems(
        authentication: Authentication,
        @RequestParam kind: WorkforceReportKind,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
        @RequestParam(required = false) hubId: UUID?,
    ): List<WorkforceReportItem> = reports.items(authentication.requirePavexActor(), kind, from, to, hubId)
}
