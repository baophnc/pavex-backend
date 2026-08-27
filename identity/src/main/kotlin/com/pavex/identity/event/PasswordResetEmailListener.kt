package com.pavex.identity.event

import com.pavex.identity.service.EmailService
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class PasswordResetEmailListener(
    private val emailService: EmailService,
) {

    @TransactionalEventListener(
        phase = TransactionPhase.AFTER_COMMIT,
    )
    fun handle(
        event: PasswordResetRequestedEvent,
    ) {
        emailService.sendPasswordResetEmail(
            event.email,
            event.rawToken,
        )
    }
}
