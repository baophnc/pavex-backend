package com.pavex.identity.messaging

import com.pavex.identity.entity.ConsumedMessage
import com.pavex.identity.event.EmailVerificationRequestedEvent
import com.pavex.identity.event.InvitationRequestedEvent
import com.pavex.identity.event.PasswordResetRequestedEvent
import com.pavex.identity.repository.ConsumedMessageRepository
import com.pavex.identity.service.AdvisoryLockService
import com.pavex.identity.service.EmailService
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
    prefix = "identity.messaging",
    name = ["email-consumer-enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class IdentityEmailConsumer(

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
            IdentityEmailConsumer::class.java
        )

    @RabbitListener(
        queues = [IdentityMessagingTopology.EMAIL_QUEUE],
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
                    IdentityMessagingTopology.HEADER_EVENT_TYPE
                ]
                    ?.toString()
                    ?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException(
                    "Rabbit message is missing event type"
                )

        val eventVersion =
            (properties.headers[
                IdentityMessagingTopology.HEADER_EVENT_VERSION
            ] as? Number)
                ?.toInt()
                ?: IdentityMessagingTopology.EVENT_VERSION

        advisoryLockService.lock(
            "identity:message-consumer:${IdentityMessagingTopology.EMAIL_CONSUMER}:$messageId"
        )

        if (
            consumedMessageRepository
                .existsByMessageIdAndConsumer(
                    messageId = messageId,
                    consumer =
                        IdentityMessagingTopology.EMAIL_CONSUMER,
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
            IdentityMessagingTopology.EMAIL_VERIFICATION_REQUESTED -> {
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

            IdentityMessagingTopology.PASSWORD_RESET_REQUESTED -> {
                val event =
                    jsonMapper.readValue(
                        payload,
                        PasswordResetRequestedEvent::class.java,
                    )

                emailService.sendPasswordResetEmail(
                    to = event.email,
                    rawToken = event.rawToken,
                )
            }

            IdentityMessagingTopology.INVITATION_REQUESTED -> {
                val event =
                    jsonMapper.readValue(
                        payload,
                        InvitationRequestedEvent::class.java,
                    )

                emailService.sendInvitationEmail(
                    to = event.email,
                    rawToken = event.rawToken,
                )
            }

            else -> {
                throw IllegalArgumentException(
                    "Unsupported identity email event type: $eventType"
                )
            }
        }

        consumedMessageRepository.save(
            ConsumedMessage(
                messageId = messageId,
                consumer =
                    IdentityMessagingTopology.EMAIL_CONSUMER,
                processedAt = Instant.now(),
            )
        )

        logger.info(
            "Identity email event delivered messageId={} eventType={}",
            messageId,
            eventType,
        )
    }
}
