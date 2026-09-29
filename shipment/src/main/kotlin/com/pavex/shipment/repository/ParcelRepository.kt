package com.pavex.shipment.repository

import com.pavex.shipment.entity.Parcel
import com.pavex.shipment.entity.ParcelCustodyType
import com.pavex.shipment.entity.ParcelStatus
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ParcelRepository : JpaRepository<Parcel, UUID> {
    fun findByCodeIgnoreCase(code: String): Parcel?

    @EntityGraph(attributePaths = ["shipment"])
    fun findAllByCustodyTypeAndCustodyReferenceIdAndStatusIn(
        custodyType: ParcelCustodyType,
        custodyReferenceId: UUID,
        statuses: Collection<ParcelStatus>,
    ): List<Parcel>
}
