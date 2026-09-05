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
     * Never carried by a [PayCrossResult.Failure]. An unknown outcome is not a
     * decline, so it is reported as [PayCrossResult.Pending] instead; this
     * member remains only so the wire value `verify_before_retry` still parses
     * and round-trips. Not retryable, so [isRetryable] fails closed either way.
     */
    VERIFY_BEFORE_RETRY,

    /**
     * The server sent a recovery value this SDK version does not know. Terminal:
     * [isRetryable] is a whitelist, so an unknown instruction is never a retry.
     *
     * The value itself is kept on [PayCrossResult.Failure.recoveryRaw], so a
     * merchant can log or support it even though this SDK cannot act on it.
     */
    UNRECOGNIZED;

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
         * Absent values default to [RETRY]; values this version does not know
         * become [UNRECOGNIZED], which fails closed exactly as [DO_NOT_RETRY]
         * does and keeps the server's own string on the failure result.
         *
         * `verify_before_retry` parses here so the value survives a round trip
         * through a host that carries recoveries as their wire token, but it
         * never reaches a result: a status carrying it becomes
         * [PayCrossResult.Pending], as does the SDK's own poll deadline.
         */
        fun fromString(value: String?): Recovery = when (value?.trim()?.lowercase()) {
            null, "" -> RETRY
            "retry" -> RETRY
            "change_method" -> CHANGE_METHOD
            "restart" -> RESTART
            "contact_support", "contact_us" -> CONTACT_SUPPORT
            "do_not_retry" -> DO_NOT_RETRY
            "verify_before_retry" -> VERIFY_BEFORE_RETRY
            else -> UNRECOGNIZED
        }
    }
}
