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
    DO_NOT_RETRY,

    /**
     * The outcome was never observed. Check the transaction's status before
     * re-collecting: it may already have succeeded.
     *
     * Every other member asserts something about an outcome the SDK saw. This one
     * is returned when it saw none, so a merchant integration has something
     * correct to act on instead of a retry over a payment the customer may
     * already have made. Not retryable, so [isRetryable] still fails closed.
     */
    VERIFY_BEFORE_RETRY;

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
         *
         * The server does not send `verify_before_retry` - the SDK raises it
         * itself when a poll ends without an outcome - but it parses here so the
         * value survives a round trip through a host that carries recoveries as
         * their wire token.
         */
        fun fromString(value: String?): Recovery = when (value?.trim()?.lowercase()) {
            null, "" -> RETRY
            "retry" -> RETRY
            "change_method" -> CHANGE_METHOD
            "restart" -> RESTART
            "contact_support", "contact_us" -> CONTACT_SUPPORT
            "do_not_retry" -> DO_NOT_RETRY
            "verify_before_retry" -> VERIFY_BEFORE_RETRY
            else -> DO_NOT_RETRY
        }
    }
}
