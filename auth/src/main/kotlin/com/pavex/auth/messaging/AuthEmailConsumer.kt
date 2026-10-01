package com.pavex.auth.messaging

import com.pavex.auth.entity.ConsumedMessage
import com.pavex.auth.event.EmailVerificationRequestedEvent
import com.pavex.auth.event.InvitationRequestedEvent
import com.pavex.auth.event.PasswordResetRequestedEvent
import com.pavex.auth.repository.ConsumedMessageRepository
import com.pavex.auth.service.AdvisoryLockService
import com.pavex.auth.service.EmailService
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.Message
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID

@Component
@ConditionalOnProperty(
    prefix = "auth.messaging",
    name = ["email-consumer-enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class AuthEmailConsumer(

    private val emailService:
        EmailService,

    private val jsonMapper:
        JsonMapper,

    private val payloadCipher:
        OutboxPayloadCipher,

    private val consumedMessageRepository:
        ConsumedMessageRepository,

    private val advisoryLockService:
        AdvisoryLockService,
) {

    private val logger =
        LoggerFactory.getLogger(
            AuthEmailConsumer::class.java
        )

    @RabbitListener(
        queues = [AuthMessagingTopology.EMAIL_QUEUE],
    )
    @Transactional
    fun handle(
        message: Message,
    ) {
        val properties =
            message.messageProperties

        val messageId =
            properties.messageId
                ?.let {
                    runCatching {
                        UUID.fromString(it)
                    }.getOrNull()
                }
                ?: throw IllegalArgumentException(
                    "Rabbit message is missing a valid messageId"
                )

        val eventType =
            properties.receivedRoutingKey
                ?.takeIf { it.isNotBlank() }
                ?: properties.headers[
                    AuthMessagingTopology.HEADER_EVENT_TYPE
                ]
                    ?.toString()
                    ?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException(
                    "Rabbit message is missing event type"
                )

        val eventVersion =
            (properties.headers[
                AuthMessagingTopology.HEADER_EVENT_VERSION
            ] as? Number)
                ?.toInt()
                ?: AuthMessagingTopology.EVENT_VERSION

        advisoryLockService.lock(
            "auth:message-consumer:${AuthMessagingTopology.EMAIL_CONSUMER}:$messageId"
        )

        if (
            consumedMessageRepository
                .existsByMessageIdAndConsumer(
                    messageId = messageId,
                    consumer =
                        AuthMessagingTopology.EMAIL_CONSUMER,
                )
        ) {
            return
        }

        val encryptedPayload =
            String(
                message.body,
                StandardCharsets.UTF_8,
            )

        val payload =
            payloadCipher.decrypt(
                ciphertext = encryptedPayload,
                eventType = eventType,
                eventVersion = eventVersion,
            )

        when (eventType) {
            AuthMessagingTopology.EMAIL_VERIFICATION_REQUESTED -> {
                val event =
                    jsonMapper.readValue(
                        payload,
                        EmailVerificationRequestedEvent::class.java,
                    )

                emailService.sendVerificationEmail(
                    to = event.email,
                    rawToken = event.rawToken,
                )
            }

            AuthMessagingTopology.PASSWORD_RESET_REQUESTED -> {
                val event =
                    jsonMapper.readValue(
                        payload,
                        PasswordResetRequestedEvent::class.java,
                    )

                emailService.sendPasswordResetEmail(
                    to = event.email,
                    rawToken = event.rawToken,
                    portal = event.portal,
                )
            }

            AuthMessagingTopology.INVITATION_REQUESTED -> {
                val event =
                    jsonMapper.readValue(
                        payload,
                        InvitationRequestedEvent::class.java,
                    )

                emailService.sendInvitationEmail(
                    to = event.email,
                    rawToken = event.rawToken,
                    portal = event.portal,
                )
            }

            else -> {
                throw IllegalArgumentException(
                    "Unsupported auth email event type: $eventType"
                )
            }
        }

        consumedMessageRepository.save(
            ConsumedMessage(
                messageId = messageId,
                consumer =
                    AuthMessagingTopology.EMAIL_CONSUMER,
                processedAt = Instant.now(),
            )
        )

        logger.info(
            "Auth email event delivered messageId={} eventType={}",
            messageId,
            eventType,
        )
    }
}
