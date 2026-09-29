package com.pavex.operations.controller

import com.pavex.operations.dto.MyHubScopeResponse
import com.pavex.operations.security.requirePavexActor
import com.pavex.operations.service.MyHubScopeService
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/operations/me")
class OperationsHubScopeController(
    private val scopes: MyHubScopeService,
) {
    /** Any signed-in user may ask; someone without memberships simply gets no hubs. */
    @GetMapping("/hub-scope")
    fun hubScope(authentication: Authentication): MyHubScopeResponse =
        scopes.resolve(authentication.requirePavexActor())
}
