package com.paycross.sdk

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Outcome of a payment started with the PayCross SDK. Handle every case:
 * - [Success]: the payment completed
 * - [Failure]: the payment was declined or failed, with a suggested [Recovery]
 * - [Pending]: the outcome is unknown; the payment may have succeeded
 * - [Cancelled]: the shopper cancelled the flow
 */
sealed class PayCrossResult : Parcelable {
    /**
     * The payment completed successfully.
     *
     * @property transactionId Transaction identifier. Empty when the session was already completed
     *   and the server returned no reference.
     * @property status Final transaction status, e.g. "success" or "authorized".
     * @property amount Amount in minor units, e.g. cents.
     * @property currency ISO 4217 currency code, e.g. "EUR".
     * @property savedCardToken Token to charge the saved card again later. Set only when the shopper
     *   asked to save the card and the server stored it; null otherwise, including when the card was
     *   already stored.
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
     * The payment failed.
     *
     * @property transactionId Transaction identifier, or null for early failures.
     * @property recoveryRaw The server's recovery value, unmapped, for logging and support. Null when the
     *   SDK raised the failure without a server value. Always set for [Recovery.UNRECOGNIZED].
     */
    @Parcelize
    data class Failure(
        val transactionId: String?,
        val recovery: Recovery,
        val recoveryRaw: String? = null
    ) : PayCrossResult()

    /**
     * The outcome is unknown and the payment may have succeeded. Reconcile server-side against
     * [transactionId] before charging again. Unlike [Failure], do not ask the shopper to pay again.
     *
     * @property transactionId Transaction to reconcile against, or null if the sheet never got one.
     * @property reason Why the outcome is unknown. See [PendingReason].
     */
    @Parcelize
    data class Pending(val transactionId: String?, val reason: PendingReason) : PayCrossResult()

    /**
     * The shopper cancelled the payment flow.
     *
     * @property transactionId Last transaction the sheet created, or null if none existed. A cancel
     *   after a decline or during 3-D Secure can leave a real transaction on the merchant's side.
     */
    @Parcelize
    data class Cancelled(val transactionId: String?) : PayCrossResult()
}
