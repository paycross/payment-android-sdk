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

        /**
         * Maps a stored card's `card_brand` to a type. The value is a verbatim BIN
         * database string, so it arrives in any case and sometimes spaced
         * ("AMERICAN EXPRESS"), and the backend substitutes "unknown" when the BIN
         * lookup found nothing. Brands with no entry of their own take UNKNOWN,
         * whose 3-digit CVV is correct for every one of them. Mirrors the iOS SDK's
         * SessionResponse.brand(from:).
         */
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
