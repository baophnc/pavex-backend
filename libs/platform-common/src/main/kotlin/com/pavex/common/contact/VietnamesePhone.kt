package com.pavex.common.contact

/**
 * Vietnamese phone numbers as Pavex stores them: 10 digits starting with 0. Spaces, dots, dashes
 * and brackets are ignored, and a +84 or 84 prefix becomes 0. Pickup addresses (Partners) and
 * shipment contacts (Shipment) use this one rule, so a saved pickup point can always become the
 * sender of a shipment.
 */
object VietnamesePhone {
    private val separators = Regex("[().\\s-]")
    private val localNumber = Regex("^0\\d{9}$")

    /** Returns the number in `0xxxxxxxxx` form, or null when it is not a 10-digit Vietnamese number. */
    fun normalize(value: String): String? {
        val compact = value.trim().replace(separators, "")
        val local = when {
            compact.startsWith("+84") -> "0${compact.drop(3)}"
            compact.startsWith("84") && compact.length == 11 -> "0${compact.drop(2)}"
            else -> compact
        }
        return local.takeIf(localNumber::matches)
    }
}
