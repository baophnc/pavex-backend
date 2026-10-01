package com.pavex.partners.service

import com.pavex.partners.repository.MerchantPickupAddressRepository
import com.pavex.partners.repository.MerchantRepository
import com.pavex.partners.repository.UserAddressRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

/**
 * Files each merchant under the hub serving its default pickup point. Done right after the pickup
 * points change, and caught up periodically for merchants Network could not be asked about.
 */
@Service
class MerchantServingHubService(
    private val merchants: MerchantRepository,
    private val pickups: MerchantPickupAddressRepository,
    private val userAddresses: UserAddressRepository,
    private val hubs: HubDirectoryClient,
    private val transactions: TransactionTemplate,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Re-files the merchant once the current transaction has committed its new pickup points. */
    fun refreshAfterCommit(merchantId: UUID) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return refresh(merchantId)
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() = refresh(merchantId)
        })
    }

    /**
     * Re-files one merchant by its default pickup point or, before it has one (an application under
     * review), by the applicant's default address. A failure keeps the previous hub; [catchUp] retries.
     */
    fun refresh(merchantId: UUID) {
        val merchant = merchants.findById(merchantId).orElse(null) ?: return
        val location = pickups.findFirstByMerchantIdAndIsDefaultTrue(merchantId)?.let { it.provinceCode to it.wardCode }
            ?: userAddresses.findFirstByUserIdAndIsDefaultTrue(merchant.userId)?.let { it.provinceCode to it.wardCode }
            ?: return
        val hubId = hubs.servingHub(location.first, location.second) ?: return
        transactions.executeWithoutResult {
            merchants.findById(merchantId).ifPresent { merchant ->
                if (merchant.servingHubId != hubId) {
                    merchant.servingHubId = hubId
                    merchants.save(merchant)
                }
            }
        }
    }

    @Scheduled(initialDelayString = "PT30S", fixedDelayString = "\${partners.serving-hub.catch-up-interval:PT10M}")
    fun catchUp() {
        try {
            merchants.findAllByServingHubIdIsNull().forEach { refresh(it.id) }
        } catch (exception: RuntimeException) {
            log.warn("Serving hub catch-up skipped: {}", exception.message)
        }
    }
}
