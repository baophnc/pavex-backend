package com.pavex.operations.controller

import com.pavex.contracts.operations.ShipmentCaseAdminScopeSnapshot
import com.pavex.operations.service.ShipmentCaseAdminScopeService
import io.swagger.v3.oas.annotations.Hidden
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** The hubs a user works at, so Partners shows hub staff the merchants of their hubs only. */
@Hidden
@RestController
@RequestMapping("/internal/v1/operations/partners")
class InternalPartnersOperationsController(
    private val scopes: ShipmentCaseAdminScopeService,
) {
    @GetMapping("/hub-scope/{userId}")
    fun hubScope(@PathVariable userId: UUID): ShipmentCaseAdminScopeSnapshot = scopes.scope(userId)
}
