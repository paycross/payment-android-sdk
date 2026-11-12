package com.paycross.sdk.internal.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.os.Bundle
import android.os.LocaleList
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.wallet.contract.TaskResultContracts
import com.paycross.sdk.PayCross
import com.paycross.sdk.PayCrossResult
import com.paycross.sdk.R
import com.paycross.sdk.ThemeMode
import com.paycross.sdk.internal.ui.theme.AppearanceResolver
import com.paycross.sdk.internal.ui.theme.PayCrossTheme
import com.paycross.sdk.internal.ui.theme.ResolvedAppearance
import com.paycross.sdk.internal.ui.theme.nightUiMode
import com.paycross.sdk.internal.util.LocaleResolution
import com.paycross.sdk.internal.wallet.GooglePayClient
import com.paycross.sdk.internal.wallet.GooglePayRequests
import kotlinx.coroutines.flow.map

internal class PaymentActivity : ComponentActivity() {
    // WHY: the default factory cannot reach the SavedStateHandle-only constructor, because default arguments generate no overloads.
    private val viewModel: PaymentViewModel by viewModels {
        viewModelFactory { initializer { PaymentViewModel(createSavedStateHandle()) } }
    }

    private val loggedContrastWarnings = mutableSetOf<String>()

    // WHY: the window theme reads resources before onCreate, so a pinned mode is applied here.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(pinnedContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // WHY: FLAG_SECURE also stops the platform saving a recents snapshot of the card form.
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        applyWindowBackground()

        val sessionToken = intent.getStringExtra(EXTRA_SESSION_TOKEN)
        if (sessionToken.isNullOrEmpty()) {
            finishWithResult(PayCrossResult.Cancelled(transactionId = null))
            return
        }

        viewModel.initialize(sessionToken)

        setContent {
            val appearance = remember { PayCross.requireConfig().effectiveAppearance() }
            val merchantLocale = remember { PayCross.getConfigOrNull()?.locale }
            val serverBrand by remember {
                viewModel.uiState.map { it.sessionData?.branding?.brandColor }
            }.collectAsState(initial = null)
            val dark = isSystemInDarkTheme()
            val resolved = remember(appearance, serverBrand, dark) {
                AppearanceResolver.resolve(appearance, serverBrand, dark)
            }
            LaunchedEffect(resolved) { warnAboutContrast(resolved) }

            PayCrossTheme(appearance = resolved) {
                // WHY: Material leaves LocalContentColor black until a Surface sets it.
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val sessionLocale by remember {
                        viewModel.uiState.map { it.sessionData?.locale }
                    }.collectAsState(initial = null)

                    PayCrossLocalization(
                        sessionLocale = sessionLocale,
                        merchantLocale = merchantLocale
                    ) {
                        PaymentScreen(
                            viewModel = viewModel,
                            onCancel = {
                                finishWithResult(
                                    PayCrossResult.Cancelled(viewModel.lastTransactionId)
                                )
                            },
                            onResult = { finishWithResult(it) }
                        )
                    }
                }
            }
        }
    }

    // WHY: takes the base context, because the half-built Activity cannot answer for its resources yet.
    private fun pinnedContext(base: Context): Context {
        val config = PayCross.getConfigOrNull()
        val mode = config?.effectiveAppearance()?.themeMode ?: ThemeMode.SYSTEM
        val locale = LocaleResolution.match(config?.locale)
        if (mode == ThemeMode.SYSTEM && locale == null) return base

        val configuration = Configuration(base.resources.configuration).apply {
            uiMode = nightUiMode(mode, uiMode)
            locale?.let { setLocales(LocaleList(it)) }
        }
        return base.createConfigurationContext(configuration)
    }

    // WHY: set before setContent, or the sheet is framed by a system-coloured band until the first frame.
    private fun applyWindowBackground() {
        val systemDark = resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val resolved = AppearanceResolver.resolve(
            appearance = PayCross.getConfigOrNull()?.effectiveAppearance(),
            serverBrandHex = null,
            systemDark = systemDark
        )
        window.setBackgroundDrawable(resolved.colorScheme.background.toArgb().toDrawable())
    }

    private fun warnAboutContrast(resolved: ResolvedAppearance) {
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        AppearanceResolver.contrastWarnings(resolved)
            .filter { loggedContrastWarnings.add(it) }
            .forEach { Log.w(TAG, it) }
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
    // WHY: Google's payment sheet draws the total label, so it is resolved here and handed over.
    val totalLabel = pcStringResource(R.string.paycross_total)

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

    val publishTestTags = rememberPublishTestTags()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(TestTags.SHEET)
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
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(TestTags.THREE_DS)
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
                            // WHY: Google Pay's resolution must start from the same user gesture, so it is launched from the completion listener.
                            GooglePayClient.loadPaymentDataTask(
                                client = paymentsClient,
                                claims = claims,
                                sessionData = uiState.sessionData,
                                googlePayMerchantId = PayCross.requireConfig().googlePayMerchantId,
                                totalPriceLabel = totalLabel
                            ).addOnCompleteListener(googlePayLauncher::launch)
                        }
                    },
                    onSubmit = { card, fieldValues ->
                        viewModel.submitCard(context, card, fieldValues)
                    }
                )
            }
        }

        // WHY: the 3-D Secure fingerprint runs in a hidden WebView; the ACS method page is never shown to the shopper.
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
internal fun LoadingOverlay() {
    val processing = pcStringResource(R.string.paycross_processing)
    Surface(
        // WHY: one named node, so a screen reader does not step through a spinner and a caption separately.
        modifier = Modifier
            .fillMaxSize()
            .testTag(TestTags.LOADING)
            .semantics(mergeDescendants = true) {
                contentDescription = processing
                liveRegion = LiveRegionMode.Polite
            },
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(processing)
            }
        }
    }
}

// WHY: a dialog runs in its own window, so a locale carried on LocalContext would not reach it.
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun CancelConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val title = pcStringResource(R.string.paycross_cancel_payment_title)
    val publishTestTags = rememberPublishTestTags()
    AlertDialog(
        onDismissRequest = onDismiss,
        // WHY: a pane title, not a contentDescription, because a description on a dialog merges its buttons away.
        modifier = Modifier
            .testTag(TestTags.CANCEL_DIALOG)
            .semantics {
                paneTitle = title
                testTagsAsResourceId = publishTestTags
            },
        title = { Text(title) },
        text = { Text(pcStringResource(R.string.paycross_cancel_payment_message)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag(TestTags.CANCEL_CONFIRM)
            ) {
                Text(pcStringResource(R.string.paycross_cancel_payment_yes))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag(TestTags.CANCEL_DISMISS)
            ) {
                Text(pcStringResource(R.string.paycross_cancel_payment_continue))
            }
        }
    )
}
