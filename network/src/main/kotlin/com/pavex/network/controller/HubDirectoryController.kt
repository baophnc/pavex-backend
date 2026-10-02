package com.pavex.network.controller

import com.pavex.network.entity.HubStatus
import com.pavex.network.entity.HubType
import com.pavex.network.repository.HubRepository
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** Enough of a hub to name it on a staff screen or offer it in a filter. */
data class HubDirectoryEntry(
    val id: UUID,
    val code: String,
    val name: String,
    val type: HubType,
    val status: HubStatus,
    val provinceCode: String,
    val wardCode: String,
    val latitude: Double?,
    val longitude: Double?,
)

/**
 * Every hub by code and name, for any staff member: screens show "HN-BD · Bưu cục Ba Đình" where
 * they used to show an id, and head office picks a hub to filter by. Addresses and settings stay
 * behind network.read.
 */
@RestController
@RequestMapping("/api/v1/admin/network/hubs/directory")
class HubDirectoryController(private val hubs: HubRepository) {
    @GetMapping
    @Transactional(readOnly = true)
    @PreAuthorize(
        "hasAnyAuthority('network.read','network.manage','scope.all-hubs','operations.hub.read','assignments.read-own')",
    )
    fun list(): List<HubDirectoryEntry> = hubs.findAll()
        .sortedBy { it.code }
        .map { HubDirectoryEntry(it.id, it.code, it.name, it.type, it.status, it.provinceCode, it.wardCode, it.latitude, it.longitude) }
}
