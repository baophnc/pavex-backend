package com.pavex.contracts.operations

import java.util.UUID

data class OperationsHubSnapshotRequest(
    val hubIds: List<UUID>,
)

data class OperationsHubSnapshot(
    val hubId: UUID,
    val code: String,
    val name: String,
    val type: String,
    val status: String,
    val addressLine: String,
    val provinceCode: String,
    val wardCode: String,
    val parentHubId: UUID? = null,
)
