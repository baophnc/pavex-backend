package com.pavex.fleet.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** A driver's acceptance of the location-sharing terms; a newer terms version asks again. */
@Entity
@Table(name = "driver_consents", schema = "fleet")
class DriverConsent(
    @Id
    @Column(name = "user_id")
    var userId: UUID = UUID.randomUUID(),
    @Column(name = "terms_version", nullable = false, length = 20)
    var termsVersion: String = "",
    @Column(name = "accepted_at", nullable = false)
    var acceptedAt: Instant = Instant.EPOCH,
)

/** Someone opened the GPS track of a trip. */
@Entity
@Table(name = "trip_access_log", schema = "fleet")
class TripAccessLog(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(name = "trip_id", nullable = false)
    var tripId: UUID = UUID.randomUUID(),
    @Column(name = "viewer_user_id", nullable = false)
    var viewerUserId: UUID = UUID.randomUUID(),
    @Column(name = "viewed_at", nullable = false)
    var viewedAt: Instant = Instant.EPOCH,
)
