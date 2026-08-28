package com.pavex.auth.messaging

object AuthMessagingTopology {
    const val EVENTS_EXCHANGE = "pavex.events"
    const val DEAD_LETTER_EXCHANGE = "pavex.dlx"

    const val EMAIL_QUEUE = "pavex.auth.email"
    const val EMAIL_DEAD_LETTER_QUEUE = "pavex.auth.email.dlq"
    const val EMAIL_DEAD_LETTER_ROUTING_KEY = "auth.email.dead"
    const val EMAIL_ROUTING_PATTERN = "auth.email.#"

    const val EMAIL_VERIFICATION_REQUESTED =
        "auth.email.verification.requested.v1"

    const val PASSWORD_RESET_REQUESTED =
        "auth.email.password-reset.requested.v1"

    const val INVITATION_REQUESTED =
        "auth.email.invitation.requested.v1"

    const val EVENT_VERSION = 1

    const val HEADER_EVENT_ID = "pavex-event-id"
    const val HEADER_EVENT_TYPE = "pavex-event-type"
    const val HEADER_EVENT_VERSION = "pavex-event-version"
    const val HEADER_PRODUCER = "pavex-producer"
    const val HEADER_OCCURRED_AT = "pavex-occurred-at"

    const val PRODUCER = "auth-service"
    const val EMAIL_CONSUMER = "identity-email-delivery"
}
