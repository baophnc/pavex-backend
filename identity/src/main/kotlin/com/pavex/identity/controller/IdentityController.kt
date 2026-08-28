package com.pavex.identity.controller

import com.pavex.identity.dto.IdentityProfileResponse
import com.pavex.identity.dto.UpdateIdentityProfileRequest
import com.pavex.identity.service.IdentityProfileService
import jakarta.validation.Valid
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/identities")
class IdentityController(
    private val service: IdentityProfileService,
) {
    @GetMapping("/me")
    fun me(authentication: JwtAuthenticationToken): IdentityProfileResponse =
        service.get(UUID.fromString(authentication.token.subject))

    @PatchMapping("/me")
    fun updateMe(
        authentication: JwtAuthenticationToken,
        @Valid @RequestBody request: UpdateIdentityProfileRequest,
    ): IdentityProfileResponse =
        service.update(UUID.fromString(authentication.token.subject), request)
}
