package com.paycross.sdk

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents the outcome of a payment flow initiated via the PayCross SDK.
 *
 * This sealed class provides exhaustive handling of all possible payment outcomes:
 * - [Success]: Payment completed successfully
 * - [Failure]: Payment failed with a suggested recovery action
 * - [Cancelled]: User cancelled the payment flow
 */
sealed class PayCrossResult : Parcelable {
    /**
     * Payment completed successfully.
     *
     * @property transactionId Unique identifier for the transaction. Empty in
     * the edge case where the session was already completed and no transaction
     * reference was available from the server.
     * @property status Final status of the transaction (e.g., "success", "authorized")
     * @property amount Transaction amount in minor units (e.g., cents)
     * @property currency ISO 4217 currency code (e.g., "EUR", "USD")
     */
    @Parcelize
    data class Success(
        val transactionId: String,
        val status: String,
        val amount: Long,
        val currency: String
    ) : PayCrossResult()

    /**
     * Payment failed.
     *
     * @property transactionId Transaction identifier, if available (may be null for early failures)
     * @property recovery Suggested action for the user to recover from the failure
     * @property recoveryRaw The recovery value exactly as the server sent it, for
     * logging and support. Null when the SDK raised the failure itself and no
     * server value exists, such as an invalid token or a status poll that ran out
     * of time. Always set when [recovery] is [Recovery.UNRECOGNIZED], which is the
     * only case where [recovery] cannot tell you what the server said.
     */
    @Parcelize
    data class Failure(
        val transactionId: String?,
        val recovery: Recovery,
        val recoveryRaw: String? = null
    ) : PayCrossResult()

    /**
     * User cancelled the payment flow.
     */
    @Parcelize
    data object Cancelled : PayCrossResult()
}
