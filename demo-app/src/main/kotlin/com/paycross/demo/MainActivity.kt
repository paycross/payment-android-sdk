package com.paycross.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.paycross.sdk.PayCross
import com.paycross.sdk.PayCrossContract
import com.paycross.sdk.PayCrossEnvironment
import com.paycross.sdk.PayCrossResult
import com.paycross.sdk.TestCardPrefill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var paymentLauncher: ActivityResultLauncher<String>
    private var lastResult: PayCrossResult? by mutableStateOf(null)
    private var isCreatingSession by mutableStateOf(false)
    private var sessionError: String? by mutableStateOf(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize SDK
        PayCross.init(
            environment = PayCrossEnvironment.STAGING,
            brandColor = 0xFF1E88E5.toInt(),
            testCardPrefill = TestCardPrefill(
                cardholderName = "John Doe",
                pan = "4111111111153220",
                expireMonth = "12",
                expireYear = "2028",
                cvv = "123"
            )
        )

        // Register for payment results
        paymentLauncher = registerForActivityResult(PayCrossContract()) { result ->
            lastResult = result
        }

        setContent {
            MaterialTheme {
                DemoScreen(
                    lastResult = lastResult,
                    isCreatingSession = isCreatingSession,
                    sessionError = sessionError,
                    onPayClick = { launchPayment() },
                    onClearResult = { lastResult = null }
                )
            }
        }
    }

    private fun launchPayment() {
        isCreatingSession = true
        sessionError = null
        lastResult = null

        lifecycleScope.launch {
            val token = withContext(Dispatchers.IO) { runCatching { StagingSession.create() } }
            isCreatingSession = false
            token
                .onSuccess { paymentLauncher.launch(it) }
                .onFailure { sessionError = it.message ?: "Could not create session" }
        }
    }
}

@Composable
private fun DemoScreen(
    lastResult: PayCrossResult?,
    isCreatingSession: Boolean,
    sessionError: String?,
    onPayClick: () -> Unit,
    onClearResult: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "PayCross Demo",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onPayClick,
                enabled = !isCreatingSession,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(if (isCreatingSession) "Creating session\u2026" else "Pay \u20AC10.00")
            }

            sessionError?.let {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Show result
            lastResult?.let { result ->
                ResultCard(result = result, onClear = onClearResult)
            }
        }
    }
}

@Composable
private fun ResultCard(
    result: PayCrossResult,
    onClear: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (result) {
                is PayCrossResult.Success -> Color(0xFF4CAF50)
                is PayCrossResult.Failure -> Color(0xFFF44336)
                is PayCrossResult.Cancelled -> Color(0xFF9E9E9E)
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = when (result) {
                    is PayCrossResult.Success -> "Payment Successful!"
                    is PayCrossResult.Failure -> "Payment Failed"
                    is PayCrossResult.Cancelled -> "Payment Cancelled"
                },
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = when (result) {
                    is PayCrossResult.Success ->
                        "Transaction: ${result.transactionId}\n" +
                            "Amount: ${result.amount} ${result.currency}"
                    is PayCrossResult.Failure ->
                        "Recovery: ${result.recovery}"
                    is PayCrossResult.Cancelled -> "User cancelled"
                },
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = onClear,
                colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
            ) {
                Text("Clear")
            }
        }
    }
}
