package com.pavex.network.controller

import com.pavex.contracts.operations.OperationsHubSnapshot
import com.pavex.contracts.operations.OperationsHubSnapshotRequest
import com.pavex.network.repository.HubRepository
import io.swagger.v3.oas.annotations.Hidden
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Hidden
@RestController
@RequestMapping("/internal/v1/network/operations")
class InternalOperationsNetworkController(
    private val hubs: HubRepository,
) {
    @PostMapping("/hubs/snapshots")
    fun hubSnapshots(
        @RequestBody request: OperationsHubSnapshotRequest,
    ): List<OperationsHubSnapshot> {
        val ids = request.hubIds.distinct().take(MAX_BATCH_SIZE)
        if (ids.isEmpty()) return emptyList()
        return hubs.findAllById(ids)
            .sortedBy { it.code }
            .map { hub ->
                OperationsHubSnapshot(
                    hubId = hub.id,
                    code = hub.code,
                    name = hub.name,
                    type = hub.type.name,
                    status = hub.status.name,
                    addressLine = hub.addressLine,
                    provinceCode = hub.provinceCode,
                    wardCode = hub.wardCode,
                    parentHubId = hub.parentHubId,
                )
            }
    }

    companion object {
        private const val MAX_BATCH_SIZE = 1000
    }
}
