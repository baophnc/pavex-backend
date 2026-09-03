package com.pavex.common.security

import java.util.UUID

enum class DataAccessScopeKind {
    GLOBAL,
    ASSIGNED_HUBS,
    OWN,
    OWN_MERCHANT,
    ASSIGNED_TO_ME,
}

interface DataAccessScope {
    val actorUserId: UUID
    val kind: DataAccessScopeKind
}

data class HubAccessScope private constructor(
    override val actorUserId: UUID,
    override val kind: DataAccessScopeKind,
    val hubIds: Set<UUID>,
) : DataAccessScope {
    init {
        require(kind == DataAccessScopeKind.GLOBAL || kind == DataAccessScopeKind.ASSIGNED_HUBS) {
            "HubAccessScope only supports GLOBAL or ASSIGNED_HUBS"
        }
        require(kind != DataAccessScopeKind.GLOBAL || hubIds.isEmpty()) {
            "Global hub scope must not carry hub ids"
        }
    }

    val isGlobal: Boolean get() = kind == DataAccessScopeKind.GLOBAL
    val global: Boolean get() = isGlobal

    fun canAccessHub(hubId: UUID): Boolean = isGlobal || hubId in hubIds

    companion object {
        fun global(actorUserId: UUID): HubAccessScope =
            HubAccessScope(actorUserId, DataAccessScopeKind.GLOBAL, emptySet())

        fun assignedHubs(actorUserId: UUID, hubIds: Collection<UUID>): HubAccessScope =
            HubAccessScope(actorUserId, DataAccessScopeKind.ASSIGNED_HUBS, hubIds.toSet())
    }
}

data class OwnDataAccessScope(
    override val actorUserId: UUID,
) : DataAccessScope {
    override val kind: DataAccessScopeKind = DataAccessScopeKind.OWN
}

data class OwnMerchantDataAccessScope(
    override val actorUserId: UUID,
    val merchantId: UUID,
) : DataAccessScope {
    override val kind: DataAccessScopeKind = DataAccessScopeKind.OWN_MERCHANT
}

data class AssignedToMeDataAccessScope(
    override val actorUserId: UUID,
) : DataAccessScope {
    override val kind: DataAccessScopeKind = DataAccessScopeKind.ASSIGNED_TO_ME
}
