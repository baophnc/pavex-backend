package com.pavex.shipment.repository

import com.pavex.shipment.entity.Parcel
import com.pavex.shipment.entity.Shipment
import com.pavex.shipment.entity.ShipmentCase
import com.pavex.shipment.entity.ShipmentCaseStatus
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
    fun findActiveCases(allowedHubIds: Set<UUID>): List<ShipmentCase> {
        val cb = entityManager.criteriaBuilder
        val query = cb.createQuery(ShipmentCase::class.java)
        val root = query.from(ShipmentCase::class.java)
        val status = root.get<ShipmentCaseStatus>("status")
        val active = cb.and(
            cb.notEqual(status, ShipmentCaseStatus.RESOLVED),
            cb.notEqual(status, ShipmentCaseStatus.CLOSED),
        )
        val predicate = if (allowedHubIds.isEmpty()) {
            active
        } else {
            cb.and(active, root.get<UUID>("hubId").`in`(allowedHubIds))
        }
        query.where(predicate)
        query.orderBy(cb.asc(root.get<Instant>("createdAt")), cb.asc(root.get<UUID>("id")))
        return entityManager.createQuery(query).resultList
    }

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
