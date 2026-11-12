package com.paycross.sdk

/**
 * Action suggested to the shopper after a payment failure.
 */
enum class Recovery {
    /** Retry the same payment. */
    RETRY,

    /** Use a different payment method. */
    CHANGE_METHOD,

    /** Restart the payment flow. */
    RESTART,

    /** Contact customer support. */
    CONTACT_SUPPORT,

    /** Terminal decline. Never offer a retry of this payment. */
    DO_NOT_RETRY,

    /**
     * The outcome is unknown and the payment may have succeeded. Check the transaction status before
     * re-collecting. Never carried by a [PayCrossResult.Failure]; the SDK reports it as [PayCrossResult.Pending].
     */
    VERIFY_BEFORE_RETRY,

    /**
     * The server sent a recovery value this SDK version does not know. Terminal. The raw value is
     * on [PayCrossResult.Failure.recoveryRaw].
     */
    UNRECOGNIZED;

    /** Whether the shopper may retry the payment in the same session. Unknown and terminal recoveries are not. */
    val isRetryable: Boolean
        get() = this == RETRY || this == CHANGE_METHOD

    companion object {
        /**
         * Parses a server recovery value. A null or blank value gives [RETRY]; an unknown value gives [UNRECOGNIZED].
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
