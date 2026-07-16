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
    CONTACT_SUPPORT,

    /**
     * Terminal decline. Never offer a retry of this payment.
     */
    DO_NOT_RETRY;

    /**
     * Whether the user may retry payment within the same session.
     * Unknown or terminal recoveries are not retryable (fail closed).
     */
    val isRetryable: Boolean
        get() = this == RETRY || this == CHANGE_METHOD

    companion object {
        /**
         * Parses a server recovery value to a [Recovery] enum.
         *
         * Absent values default to [RETRY]; unrecognized values fail closed
         * to [DO_NOT_RETRY], matching the checkout page's recovery policy.
         */
        fun fromString(value: String?): Recovery = when (value?.trim()?.lowercase()) {
            null, "" -> RETRY
            "retry" -> RETRY
            "change_method" -> CHANGE_METHOD
            "restart" -> RESTART
            "contact_support", "contact_us" -> CONTACT_SUPPORT
            "do_not_retry" -> DO_NOT_RETRY
            else -> DO_NOT_RETRY
        }
    }
}
