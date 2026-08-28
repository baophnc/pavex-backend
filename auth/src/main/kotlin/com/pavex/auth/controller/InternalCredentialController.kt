package com.pavex.auth.controller

import com.pavex.auth.service.InternalCredentialService
import com.pavex.contracts.identity.CredentialInvitationRequest
import io.swagger.v3.oas.annotations.Hidden
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Hidden
@RestController
@RequestMapping("/internal/v1/credentials")
class InternalCredentialController(
    private val service: InternalCredentialService,
) {

    @PostMapping("/invitations/provision")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun provisionInvitation(
        @Valid @RequestBody request: CredentialInvitationRequest,
    ) {
        service.provisionInvitation(request)
    }

    @PostMapping("/invitations/send")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun sendInvitation(
        @Valid @RequestBody request: CredentialInvitationRequest,
    ) {
        service.sendInvitation(request)
    }

    @PostMapping("/{userId}/revoke-sessions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun revokeSessions(
        @PathVariable userId: UUID,
    ) {
        service.revokeSessions(userId)
    }
}
