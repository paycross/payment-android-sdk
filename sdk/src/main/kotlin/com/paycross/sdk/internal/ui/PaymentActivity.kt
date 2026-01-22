package com.paycross.sdk.internal.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.paycross.sdk.PayCrossResult

/**
 * Internal activity that hosts the payment flow UI.
 *
 * This activity is launched via [com.paycross.sdk.PayCrossContract] and should not
 * be instantiated directly. Use [newIntent] to create a properly configured intent.
 */
internal class PaymentActivity : ComponentActivity() {

    private val viewModel: PaymentViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sessionToken = intent.getStringExtra(EXTRA_SESSION_TOKEN)
        if (sessionToken.isNullOrEmpty()) {
            finishWithResult(PayCrossResult.Cancelled)
            return
        }

        if (savedInstanceState == null) {
            viewModel.initialize(sessionToken)
        }

        setContent {
            MaterialTheme {
                PaymentScreen(
                    viewModel = viewModel,
                    onCancel = { finishWithResult(PayCrossResult.Cancelled) },
                    onResult = { finishWithResult(it) }
                )
            }
        }
    }

    private fun finishWithResult(result: PayCrossResult) {
        val intent = Intent().apply {
            putExtra(EXTRA_RESULT, result)
        }
        setResult(RESULT_OK, intent)
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

@Composable
private fun PaymentScreen(
    viewModel: PaymentViewModel,
    onCancel: () -> Unit,
    onResult: (PayCrossResult) -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var showCancelDialog by remember { mutableStateOf(false) }

    BackHandler {
        showCancelDialog = true
    }

    LaunchedEffect(uiState.result) {
        uiState.result?.let { onResult(it) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            uiState.isLoading && uiState.claims == null -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            uiState.threeDsAction != null -> {
                ThreeDsWebView(
                    action = uiState.threeDsAction!!,
                    onComplete = { viewModel.clearThreeDsAction() },
                    onError = { /* Continue polling, will fail eventually */ },
                    modifier = Modifier.fillMaxSize()
                )
            }
            uiState.claims != null -> {
                CardFormScreen(
                    claims = uiState.claims!!,
                    sessionData = uiState.sessionData,
                    isLoading = uiState.isLoading,
                    error = uiState.error,
                    onSubmit = { formData ->
                        viewModel.submitCard(
                            context = context,
                            cardholderName = formData.cardholderName,
                            pan = formData.pan,
                            expireMonth = formData.expireMonth,
                            expireYear = formData.expireYear,
                            cvv = formData.cvv,
                            savedUuid = formData.savedUuid,
                            saveCard = formData.saveCard
                        )
                    }
                )
            }
        }

        if (uiState.isLoading && uiState.claims != null && uiState.threeDsAction == null) {
            LoadingOverlay()
        }
    }

    if (showCancelDialog) {
        CancelConfirmationDialog(
            onConfirm = {
                showCancelDialog = false
                onCancel()
            },
            onDismiss = { showCancelDialog = false }
        )
    }
}

@Composable
private fun LoadingOverlay() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Processing payment...")
            }
        }
    }
}

@Composable
private fun CancelConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cancel Payment?") },
        text = { Text("Are you sure you want to cancel this payment?") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Yes, Cancel")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Continue Payment")
            }
        }
    )
}
