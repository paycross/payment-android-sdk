package com.paycross.sdk.internal.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.wallet.contract.TaskResultContracts
import com.paycross.sdk.PayCross
import com.paycross.sdk.PayCrossResult
import com.paycross.sdk.ThemeMode
import com.paycross.sdk.internal.ui.theme.AppearanceResolver
import com.paycross.sdk.internal.ui.theme.PayCrossTheme
import com.paycross.sdk.internal.ui.theme.ResolvedAppearance
import com.paycross.sdk.internal.wallet.GooglePayClient
import com.paycross.sdk.internal.wallet.GooglePayRequests
import kotlinx.coroutines.flow.map

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

    // The window background is a resource-qualified theme rather than something
    // Compose draws, so a pinned mode has to reach the resources before the
    // window is themed - which is here, not in onCreate. The activity is a plain
    // ComponentActivity, so there is no AppCompat night mode to ask instead.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(pinnedModeContext(newBase))
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
            val appearance = remember { PayCross.requireConfig().effectiveAppearance() }
            // Mapped rather than collected whole: the brand changes once, when the
            // session lands, and the theme has no business recomposing on every
            // form keystroke.
            val serverBrand by remember {
                viewModel.uiState.map { it.sessionData?.branding?.brandColor }
            }.collectAsState(initial = null)
            val dark = isSystemInDarkTheme()
            val resolved = remember(appearance, serverBrand, dark) {
                AppearanceResolver.resolve(appearance, serverBrand, dark)
            }
            LaunchedEffect(resolved) { warnAboutContrast(resolved) }

            PayCrossTheme(appearance = resolved) {
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

    /**
     * [base] with its resources reporting the pinned mode's night bits, or [base]
     * itself when the mode follows the device.
     *
     * Takes the context rather than extending it: an Activity is a Context too,
     * and asking the half-built one for resources here would throw. The config is
     * read through the non-throwing accessor because this runs before onCreate
     * has anywhere to report an uninitialized SDK.
     */
    private fun pinnedModeContext(base: Context): Context {
        val mode = PayCross.getConfigOrNull()?.effectiveAppearance()?.themeMode ?: ThemeMode.SYSTEM
        if (mode == ThemeMode.SYSTEM) return base

        val night = if (mode == ThemeMode.DARK) {
            Configuration.UI_MODE_NIGHT_YES
        } else {
            Configuration.UI_MODE_NIGHT_NO
        }
        val configuration = Configuration(base.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
        }
        return base.createConfigurationContext(configuration)
    }

    /**
     * Debug builds of the host app only. A merchant shipping a release build has
     * already made their colour choices and cannot act on a logcat line, while a
     * developer wiring up an appearance can.
     */
    private fun warnAboutContrast(resolved: ResolvedAppearance) {
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        AppearanceResolver.contrastWarnings(resolved).forEach { Log.w(TAG, it) }
    }

    private fun finishWithResult(result: PayCrossResult) {
        val intent = Intent().apply {
            putExtra(EXTRA_RESULT, result)
        }
        setResult(RESULT_OK, intent)
        finish()
    }

    companion object {
        private const val TAG = "PayCross"
        const val EXTRA_SESSION_TOKEN = "session_token"
        const val EXTRA_RESULT = "result"

        fun newIntent(context: Context, sessionToken: String): Intent {
            return Intent(context, PaymentActivity::class.java).apply {
                putExtra(EXTRA_SESSION_TOKEN, sessionToken)
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
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

    // FLAG_SECURE blanks screenshots, so the E2E rig drives the sheet from
    // UiAutomator dumps instead; without this the testTags never reach the view
    // hierarchy as resource ids and nothing in the sheet is addressable there.
    // Debug builds only: in a merchant's release build these ids would publish
    // the sheet's structure, and the saved-card tags carry a card uuid, to any
    // accessibility service on the device.
    val publishTestTags = remember(context) {
        context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .semantics { testTagsAsResourceId = publishTestTags }
    ) {
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
                    selectedSavedCardUuid = uiState.selectedSavedCardUuid,
                    isLoading = uiState.isLoading,
                    error = uiState.error,
                    googlePayAvailable = uiState.googlePayAvailable,
                    onSavedCardSelected = viewModel::selectSavedCard,
                    onSavedCardRemoved = viewModel::removeSavedCard,
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
