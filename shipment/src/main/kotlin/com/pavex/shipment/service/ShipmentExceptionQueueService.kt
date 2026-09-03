package com.pavex.shipment.service

import com.pavex.common.api.OneBasedPagination
import com.pavex.common.api.PageResponse
import com.pavex.shipment.dto.ShipmentExceptionQueueItemResponse
import com.pavex.shipment.dto.ShipmentExceptionQueueKind
import com.pavex.shipment.dto.ShipmentExceptionQueueStatus
import com.pavex.shipment.dto.ShipmentExceptionQueueSummaryResponse
import com.pavex.shipment.repository.ShipmentExceptionQueueRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ShipmentExceptionQueueService(
    private val queue: ShipmentExceptionQueueRepository,
    private val access: ShipmentExceptionAccessService,
) {
    @Transactional(readOnly = true)
    fun search(
        kind: ShipmentExceptionQueueKind?,
        status: ShipmentExceptionQueueStatus?,
        search: String?,
        authentication: org.springframework.security.core.Authentication,
        pageInput: Int,
        pageSizeInput: Int,
    ): PageResponse<ShipmentExceptionQueueItemResponse> {
        val pagination = OneBasedPagination.normalize(pageInput, pageSizeInput)
        val result = queue.search(kind, status, search, access.resolveRead(authentication), pagination.page, pagination.pageSize)
        return PageResponse(result.items, pagination.page, pagination.pageSize, result.totalCount)
    }

    @Transactional(readOnly = true)
    fun summary(authentication: org.springframework.security.core.Authentication): ShipmentExceptionQueueSummaryResponse =
        queue.summary(access.resolveRead(authentication))
}
