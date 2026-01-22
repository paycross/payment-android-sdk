package com.paycross.sdk.internal.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.paycross.sdk.PayCrossResult

/**
 * Internal activity that hosts the payment flow UI.
 *
 * This activity is launched via [com.paycross.sdk.PayCrossContract] and should not
 * be instantiated directly. Use [newIntent] to create a properly configured intent.
 */
internal class PaymentActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Placeholder - will be implemented in subsequent tasks
        setResult(RESULT_CANCELED)
        finish()
    }

    companion object {
        const val EXTRA_SESSION_TOKEN = "session_token"
        const val EXTRA_RESULT = "result"

        fun newIntent(context: Context, sessionToken: String): Intent {
            return Intent(context, PaymentActivity::class.java).apply {
                putExtra(EXTRA_SESSION_TOKEN, sessionToken)
            }
        }
    }
}
