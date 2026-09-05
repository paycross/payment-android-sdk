package com.paycross.sdk

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents the outcome of a payment flow initiated via the PayCross SDK.
 *
 * This sealed class provides exhaustive handling of all possible payment outcomes:
 * - [Success]: Payment completed successfully
 * - [Failure]: Payment failed with a suggested recovery action
 * - [Pending]: The outcome is unknown; the payment may have succeeded
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
     * @property savedCardToken The token for the card this payment stored, for
     * charging it again later. Present only when the shopper asked to save the
     * card and the server stored one; null on every other success, including a
     * payment made with a card that was already stored.
     */
    @Parcelize
    data class Success(
        val transactionId: String,
        val status: String,
        val amount: Long,
        val currency: String,
        val savedCardToken: String? = null
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
     * The outcome is not known. The payment MAY have succeeded: reconcile
     * server-side against [transactionId] before charging again.
     *
     * Distinct from [Failure] because the two demand opposite handling. A
     * failure is an observed decline and the shopper can be asked to pay again;
     * this one was never observed, so asking again risks charging twice.
     *
     * @property transactionId The transaction to reconcile against, or null when
     * the sheet never got one.
     * @property reason Why the outcome is unknown. See [PendingReason].
     */
    @Parcelize
    data class Pending(val transactionId: String?, val reason: PendingReason) : PayCrossResult()

    /**
     * User cancelled the payment flow.
     *
     * @property transactionId The last transaction this payment sheet knew about,
     * or null if it was cancelled before one existed. A shopper can cancel after a
     * decline or part-way through a 3-D Secure challenge, which leaves a real
     * transaction on the merchant's side; without this the host app has no way to
     * correlate the attempt it just abandoned.
     */
    @Parcelize
    data class Cancelled(val transactionId: String?) : PayCrossResult()
}
