package com.pavex.network.controller

import com.pavex.contracts.network.ReleaseShipmentNetworkRequest
import com.pavex.contracts.network.ReserveShipmentNetworkRequest
import com.pavex.contracts.network.ShipmentHubSnapshot
import com.pavex.contracts.network.ShipmentNetworkReservationSnapshot
import com.pavex.contracts.network.ShipmentOperationalHubSnapshot
import com.pavex.network.service.ShipmentNetworkReservationService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/internal/v1/network/shipments")
class InternalShipmentNetworkController(
    private val reservations: ShipmentNetworkReservationService,
) {
    @PostMapping("/capacity/reservations")
    fun reserve(@RequestBody request: ReserveShipmentNetworkRequest): ShipmentNetworkReservationSnapshot =
        reservations.reserve(request)

    @PostMapping("/capacity/reservations/{shipmentId}/revisions/{routeRevision}/release")
    fun release(
        @PathVariable shipmentId: UUID,
        @PathVariable routeRevision: Int,
        @RequestBody request: ReleaseShipmentNetworkRequest,
    ) {
        reservations.release(shipmentId, routeRevision, request)
    }

    @GetMapping("/hubs/{hubId}")
    fun hub(@PathVariable hubId: UUID): ShipmentHubSnapshot = reservations.resolveHub(hubId)

    @GetMapping("/routes/{shipmentId}/revisions/{routeRevision}/hubs/{hubId}")
    fun operationalHub(
        @PathVariable shipmentId: UUID,
        @PathVariable routeRevision: Int,
        @PathVariable hubId: UUID,
    ): ShipmentOperationalHubSnapshot = reservations.resolveOperationalHub(shipmentId, routeRevision, hubId)
}
