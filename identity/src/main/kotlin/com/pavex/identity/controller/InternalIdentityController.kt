package com.pavex.identity.controller

import com.pavex.contracts.identity.IdentityAuthContext
import com.pavex.identity.dto.IdentityProfileResponse
import com.pavex.contracts.identity.ProvisionIdentityRequest
import com.pavex.identity.service.IdentityProfileService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/internal/v1/identities")
class InternalIdentityController(
    private val service: IdentityProfileService,
) {
    @PostMapping
    fun provision(@Valid @RequestBody request: ProvisionIdentityRequest): IdentityProfileResponse =
        service.provision(request)

    @GetMapping("/{userId}/auth-context")
    fun authContext(@PathVariable userId: UUID): IdentityAuthContext = service.authContext(userId)
}
