package com.paycross.sdk.internal.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.wallet.contract.TaskResultContracts
import com.paycross.sdk.PayCross
import com.paycross.sdk.PayCrossResult
import com.paycross.sdk.internal.ui.theme.PayCrossTheme
import com.paycross.sdk.internal.wallet.GooglePayClient
import com.paycross.sdk.internal.wallet.GooglePayRequests

/**
 * Internal activity that hosts the payment flow UI.
 *
 * This activity is launched via [com.paycross.sdk.PayCrossContract] and should not
 * be instantiated directly. Use [newIntent] to create a properly configured intent.
 */
internal class PaymentActivity : ComponentActivity() {

    // The default factory can't see the (SavedStateHandle)-only constructor:
    // Kotlin default arguments don't generate telescoping overloads.
    private val viewModel: PaymentViewModel by viewModels {
        viewModelFactory { initializer { PaymentViewModel(createSavedStateHandle()) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Without this the platform writes a recents snapshot of the filled card
        // form to /data/system_ce/<user>/snapshots, and screen recorders capture it.
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        val sessionToken = intent.getStringExtra(EXTRA_SESSION_TOKEN)
        if (sessionToken.isNullOrEmpty()) {
            finishWithResult(PayCrossResult.Cancelled(transactionId = null))
            return
        }

        viewModel.initialize(sessionToken)

        setContent {
            val brand = remember { PayCross.requireConfig().brandColor?.let { Color(it) } }
            PayCrossTheme(brand = brand) {
                PaymentScreen(
                    viewModel = viewModel,
                    onCancel = {
                        finishWithResult(PayCrossResult.Cancelled(viewModel.lastTransactionId))
                    },
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

    val paymentsClient = remember {
        GooglePayClient.createPaymentsClient(context, PayCross.requireConfig().environment)
    }

    val googlePayLauncher = rememberLauncherForActivityResult(
        TaskResultContracts.GetPaymentDataResult()
    ) { taskResult ->
        when (taskResult.status.statusCode) {
            CommonStatusCodes.SUCCESS ->
                taskResult.result?.toJson()?.let { viewModel.submitGooglePay(context, it) }
                    ?: viewModel.onGooglePayFailed()
            // The shopper closed the sheet; stay on the form silently.
            CommonStatusCodes.CANCELED -> Unit
            else -> viewModel.onGooglePayFailed()
        }
    }

    LaunchedEffect(uiState.claims, uiState.sessionData) {
        if (uiState.claims == null) return@LaunchedEffect
        val ready = paymentsClient != null &&
            GooglePayRequests.isSessionEligible(uiState.sessionData) &&
            GooglePayClient.isReadyToPay(paymentsClient)
        viewModel.onGooglePayReadiness(ready)
    }

    val challenge = uiState.threeDs?.takeIf { it.isChallenge }
    val fingerprint = uiState.threeDs?.takeIf { !it.isChallenge }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            uiState.isLoading && uiState.claims == null -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            challenge != null -> {
                ThreeDsWebView(
                    action = challenge.action,
                    onComplete = { viewModel.clearThreeDs() },
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
                    googlePayAvailable = uiState.googlePayAvailable,
                    onGooglePay = { fieldValues ->
                        viewModel.onGooglePaySheetOpened(fieldValues)
                        val claims = uiState.claims
                        if (paymentsClient == null || claims == null) {
                            viewModel.onGooglePayFailed()
                        } else {
                            // The contract requires a completed task; launching the
                            // resolution from the completion listener keeps it on the
                            // same user gesture.
                            GooglePayClient.loadPaymentDataTask(
                                client = paymentsClient,
                                claims = claims,
                                sessionData = uiState.sessionData,
                                googlePayMerchantId = PayCross.requireConfig().googlePayMerchantId
                            ).addOnCompleteListener(googlePayLauncher::launch)
                        }
                    },
                    onSubmit = { card, fieldValues ->
                        viewModel.submitCard(context, card, fieldValues)
                    }
                )
            }
        }

        // Fingerprint runs in a hidden WebView; the ACS method endpoint is
        // never meant to be shown to the user.
        fingerprint?.let {
            ThreeDsWebView(
                action = it.action,
                onComplete = { viewModel.clearThreeDs() },
                onError = { viewModel.clearThreeDs() },
                modifier = Modifier
                    .size(1.dp)
                    .alpha(0f)
            )
        }

        val showOverlay = uiState.claims != null && challenge == null &&
            (uiState.isLoading || fingerprint != null)
        if (showOverlay) {
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
