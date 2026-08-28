package com.pavex.auth.dto.admin

import com.pavex.auth.entity.AccountStatus

data class UpdateUserStatusRequest(
    val status: AccountStatus,
)
