package com.pavex.shipment.repository

import com.pavex.shipment.entity.ParcelStatus
import com.pavex.shipment.entity.ShipmentEvent
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface EventCodeCount {
    fun getCode(): String
    fun getTotal(): Long
}

interface CollectedTotal {
    fun getAmount(): Long?
    fun getShipments(): Long
}

/** Read models behind the field app's daily reports. */
interface WorkforceReportRepository : JpaRepository<ShipmentEvent, UUID> {
    @Query(
        """
        select e.code as code, count(e) as total from ShipmentEvent e
        where e.actorUserId = :actor and e.occurredAt >= :from and e.occurredAt < :to and e.code in :codes
        group by e.code
        """,
    )
    fun countByActor(
        @Param("actor") actor: UUID,
        @Param("from") from: Instant,
        @Param("to") to: Instant,
        @Param("codes") codes: Collection<String>,
    ): List<EventCodeCount>

    /** Movements at the hub, plus deliveries and cancellations of shipments the hub serves. */
    @Query(
        """
        select e.code as code, count(e) as total from ShipmentEvent e
        where e.occurredAt >= :from and e.occurredAt < :to and e.code in :codes
          and (e.hubId = :hubId
               or (e.code in ('parcel.delivered', 'parcel.delivery-failed') and e.shipment.destinationHubId = :hubId)
               or (e.code = 'shipment.cancelled' and e.shipment.originHubId = :hubId))
        group by e.code
        """,
    )
    fun countByHub(
        @Param("hubId") hubId: UUID,
        @Param("from") from: Instant,
        @Param("to") to: Instant,
        @Param("codes") codes: Collection<String>,
    ): List<EventCodeCount>

    @Query(
        """
        select e from ShipmentEvent e join fetch e.shipment
        where e.actorUserId = :actor and e.occurredAt >= :from and e.occurredAt < :to and e.code = :code
        order by e.occurredAt desc
        """,
    )
    fun eventsByActor(
        @Param("actor") actor: UUID,
        @Param("from") from: Instant,
        @Param("to") to: Instant,
        @Param("code") code: String,
        pageable: Pageable,
    ): List<ShipmentEvent>

    @Query(
        """
        select e from ShipmentEvent e join fetch e.shipment s
        where e.occurredAt >= :from and e.occurredAt < :to and e.code = :code
          and (e.hubId = :hubId
               or (e.code in ('parcel.delivered', 'parcel.delivery-failed') and s.destinationHubId = :hubId)
               or (e.code = 'shipment.cancelled' and s.originHubId = :hubId))
        order by e.occurredAt desc
        """,
    )
    fun eventsByHub(
        @Param("hubId") hubId: UUID,
        @Param("from") from: Instant,
        @Param("to") to: Instant,
        @Param("code") code: String,
        pageable: Pageable,
    ): List<ShipmentEvent>

    @Query(
        """
        select sum(s.collectedAmount) as amount, count(s) as shipments from Shipment s
        where s.collectedByUserId = :actor and s.collectedAt >= :from and s.collectedAt < :to
        """,
    )
    fun collectedByActor(@Param("actor") actor: UUID, @Param("from") from: Instant, @Param("to") to: Instant): CollectedTotal

    @Query(
        """
        select sum(s.collectedAmount) as amount, count(s) as shipments from Shipment s
        where s.destinationHubId = :hubId and s.collectedAt >= :from and s.collectedAt < :to
        """,
    )
    fun collectedByHub(@Param("hubId") hubId: UUID, @Param("from") from: Instant, @Param("to") to: Instant): CollectedTotal

    @Query("select count(p) from Parcel p where p.currentHubId = :hubId and p.status in :statuses")
    fun countInStock(@Param("hubId") hubId: UUID, @Param("statuses") statuses: Collection<ParcelStatus>): Long

    @Query(
        """
        select p.shipment.id from Parcel p
        where p.currentHubId = :hubId and p.status in :statuses
        order by p.updatedAt desc
        """,
    )
    fun shipmentIdsInStock(
        @Param("hubId") hubId: UUID,
        @Param("statuses") statuses: Collection<ParcelStatus>,
        pageable: Pageable,
    ): List<UUID>
}
