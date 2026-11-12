package com.paycross.sdk.internal.validation

internal enum class CardType(
    val displayName: String,
    val cvvLength: Int,
    private val prefixPattern: Regex
) {
    VISA("Visa", 3, Regex("^4")),
    MASTERCARD("Mastercard", 3, Regex("^(5[1-5]|2[2-7])")),
    AMEX("American Express", 4, Regex("^3[47]")),
    DISCOVER("Discover", 3, Regex("^6(?:011|5)")),
    UNKNOWN("Card", 3, Regex("^$"));

    companion object {
        fun detect(cardNumber: String): CardType {
            val cleaned = cardNumber.replace("\\s".toRegex(), "")
            return entries.firstOrNull { it != UNKNOWN && it.prefixPattern.containsMatchIn(cleaned) }
                ?: UNKNOWN
        }

        // Card_brand arrives in any case and sometimes spaced, such as "AMERICAN EXPRESS".
        fun fromBrand(brand: String?): CardType =
            when (brand?.trim()?.lowercase()?.replace(" ", "")) {
                "visa" -> VISA
                "mastercard", "master", "mc" -> MASTERCARD
                "amex", "americanexpress" -> AMEX
                "discover" -> DISCOVER
                else -> UNKNOWN
            }
    }
}
