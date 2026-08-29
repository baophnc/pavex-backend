package com.pavex.shipment.repository

import com.pavex.shipment.entity.Parcel
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ParcelRepository : JpaRepository<Parcel, UUID> {
    fun findByCodeIgnoreCase(code: String): Parcel?
}
