package com.paycross.sdk

/**
 * Represents recovery actions that can be suggested to the user after a payment failure.
 */
enum class Recovery {
    /**
     * Retry the same payment operation.
     */
    RETRY,

    /**
     * Use a different payment method.
     */
    CHANGE_METHOD,

    /**
     * Restart the entire payment flow.
     */
    RESTART,

    /**
     * Contact customer support for assistance.
     */
    CONTACT_US;

    companion object {
        /**
         * Parses a string value to a [Recovery] enum.
         *
         * @param value The string representation (e.g., "retry", "change_method").
         * @return The corresponding [Recovery] enum value, defaulting to [RETRY] if unrecognized.
         */
        fun fromString(value: String): Recovery = when (value) {
            "retry" -> RETRY
            "change_method" -> CHANGE_METHOD
            "restart" -> RESTART
            "contact_us" -> CONTACT_US
            else -> RETRY
        }
    }
}
