package com.pavex.partners.controller

import com.pavex.partners.repository.MerchantRepository
import jakarta.validation.Valid
import jakarta.validation.constraints.Size
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class MerchantNamesRequest(@field:Size(max = 200) val ids: List<UUID>)

data class MerchantNameResponse(val id: UUID, val name: String)

/**
 * Shop names behind merchant ids, for finance and operations screens (invoices, settlements, cash)
 * that would otherwise print an id. Only the business name; contacts stay behind the merchant pages.
 */
@RestController
@RequestMapping("/api/v1/admin/merchants/names")
class MerchantNamesController(private val merchants: MerchantRepository) {
    @PostMapping
    @Transactional(readOnly = true)
    @PreAuthorize(
        "hasAnyAuthority('partners.merchants.read','scope.all-hubs','billing.manage','billing.cash.read-hub','operations.hub.read')",
    )
    fun names(@Valid @RequestBody request: MerchantNamesRequest): List<MerchantNameResponse> {
        val ids = request.ids.toSet()
        if (ids.isEmpty()) return emptyList()
        return merchants.findAllById(ids).map { MerchantNameResponse(it.id, it.businessName) }
    }
}
