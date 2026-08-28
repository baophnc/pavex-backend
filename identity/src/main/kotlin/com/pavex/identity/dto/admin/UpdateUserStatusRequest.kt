package com.pavex.identity.dto.admin

import com.pavex.identity.entity.AccountStatus

data class UpdateUserStatusRequest(
    val status: AccountStatus,
)
