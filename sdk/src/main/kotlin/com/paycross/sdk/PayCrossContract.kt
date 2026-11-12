package com.paycross.sdk

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.content.IntentCompat
import com.paycross.sdk.internal.ui.PaymentActivity

/**
 * Activity result contract that runs the payment sheet. The input is the session token and the
 * result is a [PayCrossResult].
 *
 * ```kotlin
 * val paymentLauncher = registerForActivityResult(PayCrossContract()) { result ->
 *     when (result) {
 *         is PayCrossResult.Success -> handleSuccess(result)
 *         is PayCrossResult.Failure -> handleFailure(result)
 *         // Outcome unknown. Reconcile server-side before charging again.
 *         is PayCrossResult.Pending -> handlePending(result.transactionId, result.reason)
 *         is PayCrossResult.Cancelled -> handleCancellation(result.transactionId)
 *     }
 * }
 *
 * paymentLauncher.launch(sessionToken)
 * ```
 */
class PayCrossContract : ActivityResultContract<String, PayCrossResult>() {
    override fun createIntent(context: Context, input: String): Intent {
        return PaymentActivity.newIntent(context, input)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): PayCrossResult {
        if (resultCode != Activity.RESULT_OK || intent == null) {
            return PayCrossResult.Cancelled(transactionId = null)
        }
        return IntentCompat.getParcelableExtra(
            intent,
            PaymentActivity.EXTRA_RESULT,
            PayCrossResult::class.java
        ) ?: PayCrossResult.Cancelled(transactionId = null)
    }
}
