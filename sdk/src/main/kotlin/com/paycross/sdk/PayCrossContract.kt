package com.paycross.sdk

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.content.IntentCompat
import com.paycross.sdk.internal.ui.PaymentActivity

/**
 * Activity result contract for launching the PayCross payment flow.
 *
 * Use with [androidx.activity.result.ActivityResultLauncher] to start the payment flow
 * and receive the result:
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
 * // Launch payment flow with session token
 * paymentLauncher.launch(sessionToken)
 * ```
 */
class PayCrossContract : ActivityResultContract<String, PayCrossResult>() {

    override fun createIntent(context: Context, input: String): Intent {
        return PaymentActivity.newIntent(context, input)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): PayCrossResult {
        // No id on these two paths by construction: the activity was torn down
        // without setting a result, so nothing it learned survived to be read.
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
