package com.pavex.shipment.entity

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

enum class ShipmentCaseType { PROBLEM_PARCEL, UNIDENTIFIED_PARCEL }
enum class ShipmentCaseStatus { OPEN, ASSIGNED, INVESTIGATING, RESOLVED, CLOSED }
enum class ShipmentCaseSeverity { LOW, MEDIUM, HIGH, CRITICAL }

/** What the investigation concluded; the shipment follows it (see ShipmentCaseService.resolve). */
enum class ShipmentCaseOutcome { CONTINUE, RETURN_TO_SENDER, LOST, DAMAGED, OTHER }

@Entity
@Table(name = "shipment_cases", schema = "shipments")
class ShipmentCase(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(name = "case_number", nullable = false, unique = true, length = 32)
    var caseNumber: String = "",
    @Enumerated(EnumType.STRING)
    @Column(name = "case_type", nullable = false, length = 30)
    var type: ShipmentCaseType = ShipmentCaseType.PROBLEM_PARCEL,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: ShipmentCaseStatus = ShipmentCaseStatus.OPEN,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var severity: ShipmentCaseSeverity = ShipmentCaseSeverity.MEDIUM,
    @Column(name = "hub_id", nullable = false)
    var hubId: UUID = UUID.randomUUID(),
    @Column(name = "shipment_id")
    var shipmentId: UUID? = null,
    @Column(name = "parcel_id")
    var parcelId: UUID? = null,
    @Column(name = "external_barcode", length = 120)
    var externalBarcode: String? = null,
    @Column(nullable = false, length = 160)
    var title: String = "",
    @Column(nullable = false, length = 2000)
    var description: String = "",
    @Column(name = "reported_by_user_id", nullable = false)
    var reportedByUserId: UUID = UUID.randomUUID(),
    @Column(name = "assigned_to_user_id")
    var assignedToUserId: UUID? = null,
    @Column(length = 2000)
    var resolution: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    var outcome: ShipmentCaseOutcome? = null,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
    @Column(name = "resolved_at")
    var resolvedAt: Instant? = null,
    @Column(name = "closed_at")
    var closedAt: Instant? = null,
    @Version
    var version: Long = 0,
) {
    @OneToMany(mappedBy = "shipmentCase", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("occurredAt ASC, id ASC")
    val events: MutableList<ShipmentCaseEvent> = mutableListOf()

    fun assign(assignedToUserId: UUID, actorUserId: UUID, note: String?, now: Instant) {
        ensureMutable()
        requireUuid(assignedToUserId, "assignedToUserId")
        requireUuid(actorUserId, "actorUserId")
        this.assignedToUserId = assignedToUserId
        status = ShipmentCaseStatus.ASSIGNED
        touch(now)
        addEvent("case.assigned", optionalText(note, 1000) ?: "Đã phân công người xử lý.", actorUserId, now,
            "{\"assignedToUserId\":\"$assignedToUserId\"}")
    }

    fun startInvestigation(actorUserId: UUID, note: String?, now: Instant) {
        ensureMutable()
        requireUuid(actorUserId, "actorUserId")
        check(status == ShipmentCaseStatus.OPEN || status == ShipmentCaseStatus.ASSIGNED) {
            "Only open or assigned cases can enter investigation."
        }
        status = ShipmentCaseStatus.INVESTIGATING
        touch(now)
        addEvent("case.investigating", optionalText(note, 1000) ?: "Bắt đầu điều tra.", actorUserId, now)
    }

    fun addNote(actorUserId: UUID, note: String, now: Instant) {
        ensureNotClosed()
        requireUuid(actorUserId, "actorUserId")
        touch(now)
        addEvent("case.note-added", requireText(note, 2000, "note"), actorUserId, now)
    }

    fun linkToShipment(shipmentId: UUID, parcelId: UUID?, actorUserId: UUID, note: String?, now: Instant) {
        ensureMutable()
        check(type == ShipmentCaseType.UNIDENTIFIED_PARCEL) {
            "Only unidentified parcel cases can be linked after creation."
        }
        requireUuid(shipmentId, "shipmentId")
        requireUuid(actorUserId, "actorUserId")
        if (this.shipmentId != null && this.shipmentId != shipmentId) {
            error("Case is already linked to another shipment.")
        }
        this.shipmentId = shipmentId
        this.parcelId = parcelId?.takeUnless { it == ZERO_UUID }
        touch(now)
        val parcelJson = this.parcelId?.let { "\"$it\"" } ?: "null"
        addEvent(
            "case.shipment-linked",
            optionalText(note, 1000) ?: "Đã xác định vận đơn cho kiện.",
            actorUserId,
            now,
            "{\"shipmentId\":\"$shipmentId\",\"parcelId\":$parcelJson}",
        )
    }

    fun resolve(actorUserId: UUID, resolution: String, now: Instant, outcome: ShipmentCaseOutcome? = null) {
        ensureMutable()
        requireUuid(actorUserId, "actorUserId")
        this.resolution = requireText(resolution, 2000, "resolution")
        this.outcome = outcome
        status = ShipmentCaseStatus.RESOLVED
        resolvedAt = now
        touch(now)
        addEvent("case.resolved", this.resolution!!, actorUserId, now)
    }

    fun close(actorUserId: UUID, note: String?, now: Instant) {
        check(status == ShipmentCaseStatus.RESOLVED) { "Only resolved cases can be closed." }
        requireUuid(actorUserId, "actorUserId")
        status = ShipmentCaseStatus.CLOSED
        closedAt = now
        touch(now)
        addEvent("case.closed", optionalText(note, 1000) ?: "Đã đóng hồ sơ.", actorUserId, now)
    }

    private fun ensureMutable() {
        check(status != ShipmentCaseStatus.RESOLVED && status != ShipmentCaseStatus.CLOSED) {
            "Resolved or closed cases cannot be modified."
        }
    }

    private fun ensureNotClosed() {
        check(status != ShipmentCaseStatus.CLOSED) { "Closed cases cannot be modified." }
    }

    private fun touch(now: Instant) {
        updatedAt = now
    }

    private fun addEvent(code: String, description: String, actorUserId: UUID, occurredAt: Instant, dataJson: String? = null) {
        events += ShipmentCaseEvent.create(this, code, description, actorUserId, occurredAt, dataJson)
    }

    companion object {
        private val ZERO_UUID = UUID(0, 0)
        private val CASE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC)

        fun createProblem(
            shipmentId: UUID,
            parcelId: UUID?,
            hubId: UUID,
            severity: ShipmentCaseSeverity,
            title: String,
            description: String,
            actorUserId: UUID,
            now: Instant,
        ): ShipmentCase {
            requireUuid(shipmentId, "shipmentId")
            return create(
                type = ShipmentCaseType.PROBLEM_PARCEL,
                hubId = hubId,
                severity = severity,
                title = title,
                description = description,
                actorUserId = actorUserId,
                now = now,
                shipmentId = shipmentId,
                parcelId = parcelId,
                externalBarcode = null,
            )
        }

        fun createUnidentified(
            hubId: UUID,
            severity: ShipmentCaseSeverity,
            title: String,
            description: String,
            externalBarcode: String?,
            actorUserId: UUID,
            now: Instant,
        ): ShipmentCase = create(
            type = ShipmentCaseType.UNIDENTIFIED_PARCEL,
            hubId = hubId,
            severity = severity,
            title = title,
            description = description,
            actorUserId = actorUserId,
            now = now,
            shipmentId = null,
            parcelId = null,
            externalBarcode = optionalText(externalBarcode, 120),
        )

        private fun create(
            type: ShipmentCaseType,
            hubId: UUID,
            severity: ShipmentCaseSeverity,
            title: String,
            description: String,
            actorUserId: UUID,
            now: Instant,
            shipmentId: UUID?,
            parcelId: UUID?,
            externalBarcode: String?,
        ): ShipmentCase {
            requireUuid(hubId, "hubId")
            requireUuid(actorUserId, "actorUserId")
            val id = UUID.randomUUID()
            val prefix = if (type == ShipmentCaseType.PROBLEM_PARCEL) "PRB" else "UNID"
            val item = ShipmentCase(
                id = id,
                caseNumber = "$prefix-${CASE_DATE.format(now)}-${id.toString().replace("-", "").take(12).uppercase()}",
                type = type,
                status = ShipmentCaseStatus.OPEN,
                severity = severity,
                hubId = hubId,
                shipmentId = shipmentId,
                parcelId = parcelId?.takeUnless { it == ZERO_UUID },
                externalBarcode = externalBarcode,
                title = requireText(title, 160, "title"),
                description = requireText(description, 2000, "description"),
                reportedByUserId = actorUserId,
                createdAt = now,
                updatedAt = now,
            )
            val shipmentJson = shipmentId?.let { "\"$it\"" } ?: "null"
            val parcelJson = item.parcelId?.let { "\"$it\"" } ?: "null"
            item.addEvent(
                "case.created",
                "Tạo hồ sơ ${if (type == ShipmentCaseType.PROBLEM_PARCEL) "kiện có vấn đề" else "kiện chưa xác định"}.",
                actorUserId,
                now,
                "{\"type\":\"${type.name}\",\"severity\":\"${severity.name}\",\"hubId\":\"$hubId\",\"shipmentId\":$shipmentJson,\"parcelId\":$parcelJson}",
            )
            return item
        }

        private fun requireUuid(value: UUID, field: String) {
            require(value != ZERO_UUID) { "$field is required." }
        }

        private fun requireText(value: String?, maximum: Int, field: String): String =
            value?.trim()?.takeIf { it.isNotEmpty() && it.length <= maximum }
                ?: throw IllegalArgumentException("$field is required and must not exceed $maximum characters.")

        private fun optionalText(value: String?, maximum: Int): String? = value?.trim()?.takeIf { it.isNotEmpty() }?.also {
            require(it.length <= maximum) { "Text must not exceed $maximum characters." }
        }
    }
}

@Entity
@Table(name = "shipment_case_events", schema = "shipments")
class ShipmentCaseEvent(
    @Id
    var id: UUID = UUID.randomUUID(),
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id", nullable = false)
    var shipmentCase: ShipmentCase = ShipmentCase(),
    @Column(nullable = false, length = 80)
    var code: String = "",
    @Column(nullable = false, length = 2000)
    var description: String = "",
    @Column(name = "actor_user_id", nullable = false)
    var actorUserId: UUID = UUID.randomUUID(),
    @Column(name = "occurred_at", nullable = false)
    var occurredAt: Instant = Instant.now(),
    @Column(name = "data_json", columnDefinition = "text")
    var dataJson: String? = null,
) {
    companion object {
        fun create(
            shipmentCase: ShipmentCase,
            code: String,
            description: String,
            actorUserId: UUID,
            occurredAt: Instant,
            dataJson: String?,
        ): ShipmentCaseEvent = ShipmentCaseEvent(
            shipmentCase = shipmentCase,
            code = code.trim().also { require(it.isNotEmpty() && it.length <= 80) { "code is invalid." } },
            description = description.trim().also { require(it.isNotEmpty() && it.length <= 2000) { "description is invalid." } },
            actorUserId = actorUserId.also { require(it != UUID(0, 0)) { "actorUserId is required." } },
            occurredAt = occurredAt,
            dataJson = dataJson,
        )
    }
}
