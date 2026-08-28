package com.pavex.contracts.identity

import java.util.UUID

data class MerchantRoleSyncRequest(
    val enabled: Boolean,
    val assignedByUserId: UUID?,
)
