package com.paycross.sdk

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
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
 *         is PayCrossResult.Cancelled -> handleCancellation()
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
        if (resultCode != Activity.RESULT_OK || intent == null) {
            return PayCrossResult.Cancelled
        }
        return intent.getParcelableExtra(PaymentActivity.EXTRA_RESULT, PayCrossResult::class.java)
            ?: PayCrossResult.Cancelled
    }
}
