package com.pavex.identity.messaging

object IdentityMessagingTopology {
    const val EVENTS_EXCHANGE = "pavex.events"
    const val DEAD_LETTER_EXCHANGE = "pavex.dlx"

    const val EMAIL_QUEUE = "pavex.identity.email"
    const val EMAIL_DEAD_LETTER_QUEUE = "pavex.identity.email.dlq"
    const val EMAIL_DEAD_LETTER_ROUTING_KEY = "identity.email.dead"
    const val EMAIL_ROUTING_PATTERN = "identity.email.#"

    const val EMAIL_VERIFICATION_REQUESTED =
        "identity.email.verification.requested.v1"

    const val PASSWORD_RESET_REQUESTED =
        "identity.email.password-reset.requested.v1"

    const val INVITATION_REQUESTED =
        "identity.email.invitation.requested.v1"

    const val EVENT_VERSION = 1

    const val HEADER_EVENT_ID = "pavex-event-id"
    const val HEADER_EVENT_TYPE = "pavex-event-type"
    const val HEADER_EVENT_VERSION = "pavex-event-version"
    const val HEADER_PRODUCER = "pavex-producer"
    const val HEADER_OCCURRED_AT = "pavex-occurred-at"

    const val PRODUCER = "identity-service"
    const val EMAIL_CONSUMER = "identity-email-delivery"
}
