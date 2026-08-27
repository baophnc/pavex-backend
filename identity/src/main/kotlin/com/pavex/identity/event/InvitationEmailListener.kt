package com.pavex.identity.event

import com.pavex.identity.service.EmailService
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class InvitationEmailListener(

    private val emailService:
        EmailService,
) {

    @TransactionalEventListener(
        phase = TransactionPhase.AFTER_COMMIT,
    )
    fun onInvitationRequested(
        event: InvitationRequestedEvent,
    ) {

        emailService.sendInvitationEmail(
            to = event.email,
            rawToken = event.rawToken,
        )
    }
}
