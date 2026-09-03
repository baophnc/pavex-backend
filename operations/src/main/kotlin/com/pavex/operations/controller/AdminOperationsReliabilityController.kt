package com.pavex.operations.controller

import com.pavex.operations.dispatch.OperationsOutboxAdminService
import com.pavex.operations.dto.DiscardOperationsOutboxRequest
import com.pavex.operations.dto.OperationalAssignmentEventPage
import com.pavex.operations.dto.OperationsOutboxDeadLetterPage
import com.pavex.operations.dto.OperationsOutboxDeadLetterResponse
import com.pavex.operations.dto.OperationsOutboxSummaryResponse
import com.pavex.operations.entity.OperationalAssignmentEventType
import com.pavex.operations.exception.OperationsNotFoundException
import com.pavex.operations.security.requirePavexActor
import com.pavex.operations.service.AssignmentAccessScope
import com.pavex.operations.service.AssignmentAccessScopeService
import com.pavex.operations.service.OperationalAssignmentEventQueryService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/admin/operations")
@PreAuthorize("hasAnyAuthority('operations.assignments.manage','operations.hub.manage')")
class AdminOperationsReliabilityController(
    private val eventQueries: OperationalAssignmentEventQueryService,
    private val outboxAdmin: OperationsOutboxAdminService,
    private val accessScopes: AssignmentAccessScopeService,
) {
    @GetMapping("/assignments/{id}/events")
    fun assignmentEvents(
        authentication: Authentication,
        @PathVariable id: UUID,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "100") pageSize: Int,
    ): OperationalAssignmentEventPage {
        val scope = accessScopes.resolve(authentication)
        ensureAssignmentAccess(scope, id)
        return eventQueries.assignmentEvents(scope, id, page, pageSize)
    }

    @GetMapping("/dispatch-events")
    fun dispatchEvents(
        authentication: Authentication,
        @RequestParam(required = false) assignmentId: UUID?,
        @RequestParam(required = false) shipmentId: UUID?,
        @RequestParam(required = false) eventType: OperationalAssignmentEventType?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "100") pageSize: Int,
    ): OperationalAssignmentEventPage {
        val scope = accessScopes.resolve(authentication)
        if (assignmentId != null) ensureAssignmentAccess(scope, assignmentId)
        return eventQueries.search(scope, assignmentId, shipmentId, eventType, page, pageSize)
    }

    @GetMapping("/outbox/summary")
    fun outboxSummary(authentication: Authentication): OperationsOutboxSummaryResponse {
        requireGlobal(accessScopes.resolve(authentication))
        return outboxAdmin.summary()
    }

    @GetMapping("/outbox/dead-letters")
    fun deadLetters(
        authentication: Authentication,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "50") pageSize: Int,
    ): OperationsOutboxDeadLetterPage {
        requireGlobal(accessScopes.resolve(authentication))
        return outboxAdmin.deadLetters(page, pageSize)
    }

    @GetMapping("/outbox/dead-letters/{id}")
    fun deadLetter(
        authentication: Authentication,
        @PathVariable id: UUID,
    ): OperationsOutboxDeadLetterResponse {
        requireGlobal(accessScopes.resolve(authentication))
        return outboxAdmin.deadLetter(id)
    }

    @PostMapping("/outbox/dead-letters/{id}/retry")
    fun retryDeadLetter(
        authentication: Authentication,
        @PathVariable id: UUID,
    ): ResponseEntity<Void> {
        requireGlobal(accessScopes.resolve(authentication))
        outboxAdmin.retry(actor(authentication), id)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/outbox/dead-letters/{id}/discard")
    fun discardDeadLetter(
        authentication: Authentication,
        @PathVariable id: UUID,
        @Valid @RequestBody request: DiscardOperationsOutboxRequest,
    ): ResponseEntity<Void> {
        requireGlobal(accessScopes.resolve(authentication))
        outboxAdmin.discard(actor(authentication), id, request.reason)
        return ResponseEntity.noContent().build()
    }

    private fun ensureAssignmentAccess(scope: AssignmentAccessScope, assignmentId: UUID) {
        if (!accessScopes.canAccessAssignment(scope, assignmentId)) {
            throw OperationsNotFoundException("assignment_not_found", "Assignment was not found.")
        }
    }

    private fun requireGlobal(scope: AssignmentAccessScope) {
        if (!scope.isGlobal) throw AccessDeniedException("Global assignment management access is required")
    }

    private fun actor(authentication: Authentication): UUID = authentication.requirePavexActor().userId
}
