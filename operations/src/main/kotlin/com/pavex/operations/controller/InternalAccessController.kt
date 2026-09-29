package com.pavex.operations.controller

import com.pavex.contracts.operations.OperationsAccessCheckRequest
import com.pavex.contracts.operations.OperationsAccessCheckResponse
import com.pavex.operations.service.ShipmentOperationsAccessService
import io.swagger.v3.oas.annotations.Hidden
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Read-only hub access check for services other than Shipment (currently Billing). */
@Hidden
@RestController
@RequestMapping("/internal/v1/operations/access")
class InternalAccessController(
    private val access: ShipmentOperationsAccessService,
) {
    @PostMapping("/check")
    fun check(@RequestBody request: OperationsAccessCheckRequest): OperationsAccessCheckResponse =
        OperationsAccessCheckResponse(access.canOperateAtHub(request.userId, request.hubId, request.capability))
}
