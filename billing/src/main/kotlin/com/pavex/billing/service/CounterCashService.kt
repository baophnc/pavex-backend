package com.pavex.billing.service

import com.pavex.billing.client.OperationsClient
import com.pavex.billing.dto.CounterSettlementResponse
import com.pavex.billing.entity.CashCollection
import com.pavex.billing.entity.CashSource
import com.pavex.billing.entity.CodPayee
import com.pavex.billing.entity.CounterSettlement
import com.pavex.billing.entity.CounterSettlementKind
import com.pavex.billing.entity.CounterSettlementStatus
import com.pavex.billing.entity.CounterShipment
import com.pavex.billing.exception.BillingConflictException
import com.pavex.billing.exception.BillingForbiddenException
import com.pavex.billing.exception.BillingNotFoundException
import com.pavex.billing.exception.InvalidBillingRequestException
import com.pavex.billing.repository.CashCollectionRepository
import com.pavex.billing.repository.CounterSettlementRepository
import com.pavex.billing.repository.CounterShipmentRepository
import com.pavex.common.contact.VietnamesePhone
import com.pavex.contracts.operations.OperationsCapability
import org.springframework.data.domain.PageRequest
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * Cash between a hub counter and walk-in senders: their COD, paid out once finance has the
 * courier's cash, and the fee of a returned shipment the recipient never paid.
 */
@Service
class CounterCashService(
    private val settlements: CounterSettlementRepository,
    private val counterShipments: CounterShipmentRepository,
    private val collections: CashCollectionRepository,
    private val operations: OperationsClient,
    private val clock: Clock,
) {
    /** Opens what the counter owes or is owed for [counter]; once per shipment and kind. */
    @Transactional
    fun open(counter: CounterShipment, kind: CounterSettlementKind, amount: Long, at: Instant): Boolean {
        if (amount <= 0 || settlements.existsByShipmentIdAndKind(counter.shipmentId, kind)) return false
        settlements.save(CounterSettlement.of(counter, kind, amount, at))
        return true
    }

    /**
     * Finance approved courier cash: the COD of each walk-in shipment in it is now Pavex's to pay
     * back, at the counter that booked the shipment.
     */
    @Transactional
    fun onApproved(approved: Collection<CashCollection>, at: Instant) {
        approved
            .filter { it.source == CashSource.DELIVERY && it.codPayee == CodPayee.SENDER && it.codAmount > 0 }
            .forEach { collection ->
                val counter = counterShipments.findById(collection.shipmentId).orElseThrow {
                    BillingConflictException("counter_shipment_unknown", "Billing has no counter record for ${collection.trackingCode}.")
                }
                open(counter, CounterSettlementKind.PAYOUT_COD, collection.codAmount, at)
            }
    }

    @Transactional(readOnly = true)
    fun hubList(
        userId: UUID,
        hubId: UUID,
        status: CounterSettlementStatus?,
        rawPhone: String?,
        rawTrackingCode: String?,
    ): List<CounterSettlementResponse> {
        requireCounter(userId, hubId)
        val phone = rawPhone?.trim()?.takeIf(String::isNotEmpty)?.let {
            VietnamesePhone.normalize(it) ?: throw InvalidBillingRequestException("invalid_phone", "The phone number is not a Vietnamese phone number.")
        }
        val code = rawTrackingCode?.trim()?.takeIf(String::isNotEmpty)?.uppercase()
        return settlements.search(hubId, status, phone, code, PageRequest.of(0, LIST_LIMIT)).map(CounterSettlementResponse::from)
    }

    /**
     * Pays the COD out, or takes the return fee, once the sender gives the phone number the
     * shipment was booked with. A taken fee is hub cash that goes to finance like any other.
     */
    @Transactional
    fun complete(userId: UUID, settlementId: UUID, rawPhone: String, note: String?): CounterSettlementResponse {
        val settlement = settlements.findById(settlementId).orElseThrow {
            BillingNotFoundException("counter_settlement_not_found", "Counter settlement was not found.")
        }
        requireCounter(userId, settlement.hubId)
        val phone = VietnamesePhone.normalize(rawPhone)
        if (phone == null || phone != settlement.senderPhone) {
            throw InvalidBillingRequestException("counter_phone_mismatch", "The phone number does not match the sender of this shipment.")
        }
        if (settlement.status != CounterSettlementStatus.PENDING) {
            throw BillingConflictException("counter_settlement_done", "This counter settlement is already done.")
        }
        val now = clock.instant()
        settlement.complete(userId, note, now)
        if (settlement.kind == CounterSettlementKind.COLLECT_RETURN_FEE &&
            !collections.existsByShipmentIdAndSource(settlement.shipmentId, CashSource.COUNTER)
        ) {
            collections.save(
                CashCollection.fromCounter(
                    shipmentId = settlement.shipmentId,
                    trackingCode = settlement.trackingCode,
                    clerkUserId = userId,
                    hubId = settlement.hubId,
                    feeAmount = settlement.amount,
                    reference = "CNT-RET-${settlement.trackingCode}",
                    paidAt = now,
                ),
            )
        }
        return try {
            CounterSettlementResponse.from(settlements.saveAndFlush(settlement))
        } catch (_: ObjectOptimisticLockingFailureException) {
            throw BillingConflictException("counter_settlement_modified", "Someone settled this at the same time. Reload and check.")
        }
    }

    /** Finance's view of what counters paid out and took, to reconcile with hub cash. */
    @Transactional(readOnly = true)
    fun adminList(hubId: UUID?, status: CounterSettlementStatus?): List<CounterSettlementResponse> =
        settlements.search(hubId, status, null, null, PageRequest.of(0, ADMIN_LIST_LIMIT)).map(CounterSettlementResponse::from)

    private fun requireCounter(userId: UUID, hubId: UUID) {
        if (!operations.canOperateAtHub(userId, hubId, OperationsCapability.CASH_HANDLING)) {
            throw BillingForbiddenException("cash_hub_forbidden", "The user cannot handle cash at this hub.")
        }
    }

    companion object {
        private const val LIST_LIMIT = 200
        private const val ADMIN_LIST_LIMIT = 500
    }
}
