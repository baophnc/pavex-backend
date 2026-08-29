package com.pavex.pricing.repository

import com.pavex.pricing.entity.QuoteOption
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface QuoteOptionRepository : JpaRepository<QuoteOption, UUID> {
    @EntityGraph(attributePaths = ["quoteRequest", "quoteRequest.parcels"])
    @Query("select distinct o from QuoteOption o where o.id = :id")
    fun findDetailedById(@Param("id") id: UUID): QuoteOption?
}
