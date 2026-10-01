package com.pavex.billing.service

import com.pavex.billing.client.OperationsClient
import com.pavex.billing.dto.CashCollectionResponse
import com.pavex.billing.dto.CashRemittanceResponse
import com.pavex.billing.entity.CashCollection
import com.pavex.billing.entity.CashCollectionStatus
import com.pavex.billing.entity.CashRemittance
import com.pavex.billing.entity.CashRemittanceStatus
import com.pavex.billing.exception.BillingConflictException
import com.pavex.billing.exception.BillingForbiddenException
import com.pavex.billing.exception.BillingNotFoundException
import com.pavex.billing.exception.InvalidBillingRequestException
import com.pavex.billing.repository.CashCollectionRepository
import com.pavex.billing.repository.CashRemittanceRepository
import com.pavex.billing.service.InvoiceService.Companion.domain
import com.pavex.contracts.operations.OperationsCapability
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Custody of cash couriers collect on delivery (ported from core-api's CashCollectionService):
 * the courier holds it, submits it to their hub in a remittance, and finance approves it.
 * Approved COD is what settlement pays out to merchants.
 */
@Service
class CashService(
    private val collections: CashCollectionRepository,
    private val remittances: CashRemittanceRepository,
    private val operations: OperationsClient,
    private val counterCash: CounterCashService,
    transactionManager: PlatformTransactionManager,
    private val clock: Clock,
) {
    private val tx = TransactionTemplate(transactionManager)
    private val readTx = TransactionTemplate(transactionManager).apply { isReadOnly = true }

    fun mine(userId: UUID, status: CashCollectionStatus?): List<CashCollectionResponse> = inReadTx {
        val page = PageRequest.of(0, LIST_LIMIT)
        val items = if (status == null) {
            collections.findByCollectedByUserIdOrderByCollectedAtDesc(userId, page)
        } else {
            collections.findByCollectedByUserIdAndStatusOrderByCollectedAtDesc(userId, status, page)
        }
        items.map(CashCollectionResponse::from)
    }

    fun submitRemittance(userId: UUID, hubId: UUID, collectionIds: List<UUID>, declaredAmount: Long): CashRemittanceResponse {
        if (collectionIds.isEmpty() || collectionIds.size > CashRemittance.MAX_ITEMS || collectionIds.toSet().size != collectionIds.size) {
            throw InvalidBillingRequestException("invalid_cash_selection", "Select between 1 and ${CashRemittance.MAX_ITEMS} unique cash collections.")
        }
        if (!operations.canOperateAtHub(userId, hubId, OperationsCapability.LAST_MILE_DELIVERY)) {
            throw BillingForbiddenException("cash_hub_forbidden", "Only an active courier of this hub can submit cash to it.")
        }
        return inTx {
            val selected = collections.findAllById(collectionIds)
            if (selected.size != collectionIds.size) throw BillingNotFoundException("cash_collection_not_found", "One or more cash collections were not found.")
            if (selected.any { !it.isHeldBy(userId) }) {
                throw BillingConflictException("cash_collection_not_held", "All selected collections must be cash currently held by this courier.")
            }
            if (selected.any { it.originHubId != hubId }) {
                throw BillingConflictException("cash_hub_mismatch", "Cash must be submitted to the hub it was collected for.")
            }
            val expected = selected.fold(0L) { total, c -> Math.addExact(total, c.amount) }
            if (declaredAmount != expected) {
                throw InvalidBillingRequestException("cash_remittance_mismatch", "Declared amount $declaredAmount must equal the selected cash total $expected.")
            }
            val now = clock.instant()
            val remittance = domain("cash_remittance_conflict") {
                CashRemittance.create(remittanceNumber(), userId, hubId, selected, declaredAmount, now)
            }
            remittances.saveAndFlush(remittance)
            domain("cash_remittance_conflict") { selected.forEach { it.submitToHub(remittance.id, hubId, now) } }
            collections.saveAllAndFlush(selected)
            CashRemittanceResponse.from(remittance)
        }
    }

    fun myRemittances(userId: UUID, status: CashRemittanceStatus?): List<CashRemittanceResponse> = inReadTx {
        remittances.search(null, userId, status, PageRequest.of(0, LIST_LIMIT)).map(CashRemittanceResponse::from)
    }

    fun hubCollections(userId: UUID, hubId: UUID, status: CashCollectionStatus?): List<CashCollectionResponse> {
        requireHubCashAccess(userId, hubId)
        return inReadTx {
            val page = PageRequest.of(0, LIST_LIMIT)
            val items = if (status == null) {
                collections.findByCurrentHubIdOrderByUpdatedAtDesc(hubId, page)
            } else {
                collections.findByCurrentHubIdAndStatusOrderByUpdatedAtDesc(hubId, status, page)
            }
            items.map(CashCollectionResponse::from)
        }
    }

    /** A hub sends cash that finance rejected back for review, e.g. after a recount. */
    fun resubmitFromHub(userId: UUID, collectionId: UUID): CashCollectionResponse {
        val hubId = inReadTx { loadCollection(collectionId).currentHubId }
            ?: throw BillingConflictException("cash_not_at_hub", "Cash collection is not currently held at a hub.")
        requireHubCashAccess(userId, hubId)
        return inTx {
            val collection = loadCollection(collectionId)
            if (collection.currentHubId != hubId) throw BillingConflictException("cash_collection_modified", "Cash collection moved. Reload and retry.")
            domain("cash_resubmit_conflict") { collection.resubmitFromHub(clock.instant()) }
            CashCollectionResponse.from(collections.saveAndFlush(collection))
        }
    }

    fun adminCollections(hubId: UUID?, collectorUserId: UUID?, status: CashCollectionStatus?): List<CashCollectionResponse> = inReadTx {
        collections.search(hubId, collectorUserId, status, PageRequest.of(0, ADMIN_LIST_LIMIT)).map(CashCollectionResponse::from)
    }

    fun adminRemittances(hubId: UUID?, courierUserId: UUID?, status: CashRemittanceStatus?): List<CashRemittanceResponse> = inReadTx {
        remittances.search(hubId, courierUserId, status, PageRequest.of(0, ADMIN_LIST_LIMIT)).map(CashRemittanceResponse::from)
    }

    fun approveRemittance(remittanceId: UUID, actorUserId: UUID): CashRemittanceResponse = inTx {
        val remittance = loadRemittance(remittanceId)
        if (remittance.status != CashRemittanceStatus.SUBMITTED) {
            throw BillingConflictException("cash_remittance_not_submitted", "Only submitted remittances can be approved.")
        }
        val items = remittanceCollections(remittance)
        val now = clock.instant()
        domain("cash_settlement_conflict") {
            items.forEach { it.approve(actorUserId, now) }
            remittance.approve(actorUserId, now)
        }
        collections.saveAllAndFlush(items)
        counterCash.onApproved(items, now)
        CashRemittanceResponse.from(remittances.saveAndFlush(remittance))
    }

    fun rejectRemittance(remittanceId: UUID, actorUserId: UUID, reason: String): CashRemittanceResponse = inTx {
        val remittance = loadRemittance(remittanceId)
        val items = remittanceCollections(remittance)
        val now = clock.instant()
        domain("cash_remittance_reject_conflict") {
            remittance.reject(actorUserId, reason, now)
            items.forEach { it.rejectToHub(remittance.hubId, actorUserId, reason, now) }
        }
        collections.saveAllAndFlush(items)
        CashRemittanceResponse.from(remittances.saveAndFlush(remittance))
    }

    /** Reviews cash a hub resubmitted on its own, outside any courier remittance. */
    fun reviewCollection(collectionId: UUID, actorUserId: UUID, approve: Boolean, reason: String?): CashCollectionResponse = inTx {
        val collection = loadCollection(collectionId)
        if (collection.currentRemittanceId != null) {
            throw BillingConflictException("cash_collection_in_remittance", "Courier cash must be approved or rejected through its remittance.")
        }
        if (collection.status != CashCollectionStatus.SUBMITTED) {
            throw BillingConflictException("cash_collection_not_submitted", "Only submitted cash can be reviewed.")
        }
        val now = clock.instant()
        domain("cash_review_conflict") {
            if (approve) {
                collection.approve(actorUserId, now)
            } else {
                val hubId = collection.currentHubId ?: throw BillingConflictException("cash_not_at_hub", "Cash is not held at a hub.")
                collection.rejectToHub(hubId, actorUserId, reason?.trim()?.ifEmpty { null } ?: "Rejected by finance review.", now)
            }
        }
        val saved = collections.saveAndFlush(collection)
        if (approve) counterCash.onApproved(listOf(saved), now)
        CashCollectionResponse.from(saved)
    }

    private fun remittanceCollections(remittance: CashRemittance): List<CashCollection> {
        val ids = remittance.items.map { it.collectionId }
        val items = collections.findAllById(ids)
        if (items.size != ids.size || items.any { it.status != CashCollectionStatus.SUBMITTED || it.currentRemittanceId != remittance.id }) {
            throw BillingConflictException("cash_remittance_collection_conflict", "Remittance cash collections are no longer in submitted state.")
        }
        return items
    }

    private fun requireHubCashAccess(userId: UUID, hubId: UUID) {
        if (!operations.canOperateAtHub(userId, hubId, OperationsCapability.CASH_HANDLING)) {
            throw BillingForbiddenException("cash_hub_forbidden", "The user cannot handle cash at this hub.")
        }
    }

    private fun loadCollection(id: UUID): CashCollection =
        collections.findById(id).orElseThrow { BillingNotFoundException("cash_collection_not_found", "Cash collection was not found.") }

    private fun loadRemittance(id: UUID): CashRemittance =
        remittances.findWithItemsById(id) ?: throw BillingNotFoundException("cash_remittance_not_found", "Cash remittance was not found.")

    private fun remittanceNumber(): String =
        "REM-${DAY.format(clock.instant().atOffset(ZoneOffset.UTC))}-${InvoiceService.compactId(UUID.randomUUID()).take(10)}"

    private fun <T> inTx(block: () -> T): T = tx.execute { block() } as T

    private fun <T> inReadTx(block: () -> T): T = readTx.execute { block() } as T

    companion object {
        private const val LIST_LIMIT = 200
        private const val ADMIN_LIST_LIMIT = 500
        private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
    }
}
