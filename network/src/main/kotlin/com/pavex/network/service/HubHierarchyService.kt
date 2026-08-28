package com.pavex.network.service

import com.pavex.network.exception.ConflictException
import com.pavex.network.exception.NotFoundException
import com.pavex.network.repository.HubRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class HubHierarchyService(
    private val hubs: HubRepository,
) {
    fun validateParent(hubId: UUID?, parentHubId: UUID?) {
        if (parentHubId == null) return
        if (hubId != null && hubId == parentHubId) {
            throw ConflictException("hub_parent_cycle", "A hub cannot be its own organizational parent.")
        }
        hubs.findById(parentHubId).orElseThrow {
            NotFoundException("parent_hub_not_found", "Parent hub was not found.")
        }
        if (hubId == null) return

        val visited = mutableSetOf<UUID>()
        var cursor: UUID? = parentHubId
        while (cursor != null && visited.add(cursor)) {
            if (cursor == hubId) {
                throw ConflictException("hub_parent_cycle", "Hub hierarchy cannot contain a cycle.")
            }
            cursor = hubs.findById(cursor).orElse(null)?.parentHubId
        }
    }
}
