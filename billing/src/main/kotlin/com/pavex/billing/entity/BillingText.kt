package com.pavex.billing.entity

internal object BillingText {
    fun required(value: String, maxLength: Int, field: String): String {
        val normalized = value.trim()
        require(normalized.isNotEmpty()) { "$field is required." }
        require(normalized.length <= maxLength) { "$field exceeds $maxLength characters." }
        return normalized
    }

    fun optional(value: String?, maxLength: Int, field: String): String? =
        value?.trim()?.takeIf { it.isNotEmpty() }?.also {
            require(it.length <= maxLength) { "$field exceeds $maxLength characters." }
        }
}
