package com.pavex.shipment.repository

import com.pavex.shipment.entity.ShipmentCase
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface ShipmentCaseRepository : JpaRepository<ShipmentCase, UUID>, JpaSpecificationExecutor<ShipmentCase> {
    @EntityGraph(attributePaths = ["events"])
    @Query("select c from ShipmentCase c where c.id = :id")
    fun findDetailedById(@Param("id") id: UUID): ShipmentCase?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = ["events"])
    @Query("select c from ShipmentCase c where c.id = :id")
    fun findForUpdateById(@Param("id") id: UUID): ShipmentCase?
}
