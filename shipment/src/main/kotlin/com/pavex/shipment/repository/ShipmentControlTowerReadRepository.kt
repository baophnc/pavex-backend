package com.pavex.shipment.repository

import com.pavex.shipment.entity.Parcel
import com.pavex.shipment.entity.Shipment
import com.pavex.shipment.entity.ShipmentLifecycleStatus
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import jakarta.persistence.criteria.JoinType
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
class ShipmentControlTowerReadRepository(
    @PersistenceContext private val entityManager: EntityManager,
) {
    fun findCandidates(recentThreshold: Instant, allowedHubIds: Set<UUID>): List<Shipment> {
        val cb = entityManager.criteriaBuilder
        val query = cb.createQuery(Shipment::class.java)
        val root = query.from(Shipment::class.java)
        query.distinct(true)

        val recentOrActive = cb.or(
            cb.equal(root.get<ShipmentLifecycleStatus>("lifecycleStatus"), ShipmentLifecycleStatus.ACTIVE),
            cb.greaterThanOrEqualTo(root.get("updatedAt"), recentThreshold),
        )

        val predicate = if (allowedHubIds.isEmpty()) {
            recentOrActive
        } else {
            val parcels = root.join<Shipment, Parcel>("parcels", JoinType.LEFT)
            cb.and(
                recentOrActive,
                cb.or(
                    root.get<UUID>("originHubId").`in`(allowedHubIds),
                    root.get<UUID>("destinationHubId").`in`(allowedHubIds),
                    parcels.get<UUID>("currentHubId").`in`(allowedHubIds),
                ),
            )
        }

        query.where(predicate)
        return entityManager.createQuery(query).resultList
    }
}
