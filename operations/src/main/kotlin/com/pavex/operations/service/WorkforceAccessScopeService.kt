package com.pavex.operations.service

import com.pavex.common.security.HubAccessScope
import com.pavex.operations.security.requirePavexActor
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Service

typealias WorkforceAccessScope = HubAccessScope

@Service
class WorkforceAccessScopeService(
    private val hubScopes: WorkforceHubScopeResolver,
) {
    fun resolve(authentication: Authentication): WorkforceAccessScope {
        val actor = authentication.requirePavexActor()
        if (actor.seesAllHubs) return HubAccessScope.global(actor.userId)
        if (!actor.hasAnyAuthority(WORKFORCE_READ, WORKFORCE_MANAGE, DISPATCH_MANAGE, HUB_MANAGE)) {
            throw AccessDeniedException("Workforce access is not allowed")
        }
        return hubScopes.resolve(actor.userId)
    }

    companion object {
        const val WORKFORCE_READ = "operations.workforce.read"
        const val WORKFORCE_MANAGE = "operations.workforce.manage"
        const val DISPATCH_MANAGE = "operations.dispatch.manage"
        const val HUB_MANAGE = "operations.hub.manage"
    }
}
