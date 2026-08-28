package com.pavex.identity.controller

import com.pavex.contracts.identity.MerchantRoleSyncRequest
import io.swagger.v3.oas.annotations.Hidden
import com.pavex.contracts.identity.IdentityAuthContext
import com.pavex.contracts.identity.MerchantEligibilityResponse
import com.pavex.identity.dto.IdentityProfileResponse
import com.pavex.contracts.identity.ProvisionIdentityRequest
import com.pavex.contracts.identity.UpdateIdentityEmailVerificationRequest
import com.pavex.identity.service.IdentityProfileService
import com.pavex.identity.service.RoleService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Hidden
@RestController
@RequestMapping("/internal/v1/identities")
class InternalIdentityController(
    private val service: IdentityProfileService,
    private val roleService: RoleService,
) {
    @PostMapping
    fun provision(@Valid @RequestBody request: ProvisionIdentityRequest): IdentityProfileResponse =
        service.provision(request)

    @GetMapping("/{userId}/auth-context")
    fun authContext(@PathVariable userId: UUID): IdentityAuthContext = service.authContext(userId)

    @GetMapping("/{userId}/merchant-eligibility")
    fun merchantEligibility(
        @PathVariable userId: UUID,
    ): MerchantEligibilityResponse =
        service.merchantEligibility(userId)

    @PutMapping("/{userId}/merchant-role")
    fun synchronizeMerchantRole(
        @PathVariable userId: UUID,
        @RequestBody request: MerchantRoleSyncRequest,
    ) = roleService.synchronizeMerchantRole(
        actorUserId = request.assignedByUserId ?: userId,
        targetUserId = userId,
        enabled = request.enabled,
    )

    @PatchMapping("/{userId}/email-verification")
    fun updateEmailVerification(
        @PathVariable userId: UUID,
        @Valid @RequestBody request: UpdateIdentityEmailVerificationRequest,
    ): IdentityAuthContext =
        service.updateEmailVerification(
            userId = userId,
            emailVerified = request.emailVerified,
        )
}
