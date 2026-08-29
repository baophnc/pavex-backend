package com.pavex.pricing.repository

import com.pavex.pricing.entity.QuoteRequest
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface QuoteRequestRepository : JpaRepository<QuoteRequest, UUID> {
    @EntityGraph(attributePaths = ["parcels", "options"])
    @Query("select distinct q from QuoteRequest q where q.id = :id")
    fun findWithDetailsById(@Param("id") id: UUID): QuoteRequest?
}
