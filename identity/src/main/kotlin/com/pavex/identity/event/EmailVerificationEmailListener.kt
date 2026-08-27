package com.pavex.identity.event

import com.pavex.identity.service.EmailService
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class EmailVerificationEmailListener(

    private val emailService:
        EmailService,
) {

    @TransactionalEventListener(
        phase = TransactionPhase.AFTER_COMMIT,
    )
    fun handle(
        event:
            EmailVerificationRequestedEvent,
    ) {

        emailService.sendVerificationEmail(
            event.email,
            event.rawToken,
        )
    }
}
