package com.pavex.identity.controller

import com.pavex.contracts.operations.WorkforceIdentitySnapshot
import com.pavex.contracts.operations.WorkforceIdentitySnapshotRequest
import com.pavex.identity.service.WorkforceIdentityLookupService
import io.swagger.v3.oas.annotations.Hidden
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Hidden
@RestController
@RequestMapping("/internal/v1/operations/identities")
class InternalOperationsIdentityController(
    private val workforceLookup: WorkforceIdentityLookupService,
) {
    @PostMapping("/snapshots")
    fun snapshots(
        @RequestBody request: WorkforceIdentitySnapshotRequest,
    ): List<WorkforceIdentitySnapshot> = workforceLookup.snapshots(request)
}
