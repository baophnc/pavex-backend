package com.pavex.billing.payment

import org.springframework.boot.context.properties.ConfigurationProperties
import java.net.URI
import java.time.Duration

@ConfigurationProperties("billing.payments.payos")
data class PayOsProperties(
    val enabled: Boolean = false,
    val apiBaseUrl: String = "https://api-merchant.payos.vn",
    val clientId: String = "",
    val apiKey: String = "",
    val checksumKey: String = "",
    val returnUrl: String = "",
    val cancelUrl: String = "",
    val timeout: Duration = Duration.ofSeconds(15),
) {
    fun isConfigured(): Boolean =
        clientId.isNotBlank() && apiKey.isNotBlank() && checksumKey.isNotBlank() &&
            isAbsoluteUrl(apiBaseUrl) && isAbsoluteUrl(returnUrl) && isAbsoluteUrl(cancelUrl) &&
            timeout >= Duration.ofSeconds(1) && timeout <= Duration.ofMinutes(2)

    private fun isAbsoluteUrl(value: String): Boolean =
        runCatching { URI(value).isAbsolute }.getOrDefault(false)
}
