package com.pavex.identity.event

data class InvitationRequestedEvent(
    val email: String,
    val rawToken: String,
)
