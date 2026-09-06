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

    private val loggedContrastWarnings = mutableSetOf<String>()

    // The window background is a resource-qualified theme rather than something
    // Compose draws, so a pinned mode has to reach the resources before the
    // window is themed - which is here, not in onCreate. The activity is a plain
    // ComponentActivity, so there is no AppCompat night mode to ask instead.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(pinnedContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Without this the platform writes a recents snapshot of the filled card
        // form to /data/system_ce/<user>/snapshots, and screen recorders capture it.
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
                // Material leaves LocalContentColor black until a Surface sets
                // it, so this is what any text drawn without an explicit colour
                // reads from, as well as the sheet's own ground.
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Mapped rather than collected whole, like the brand above:
                    // the locale changes once, when the session lands.
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

    /**
     * [base] with its resources reporting whatever the merchant pinned - the
     * mode's night bits, the override locale, or both - and [base] itself when
     * they pinned neither.
     *
     * Only the merchant's locale is applied here. The session's arrives with the
     * payload, long after this, and reaches the sheet through
     * [LocalPayCrossResources] instead. A locale set without a pinned theme has
     * to survive: the early return used to short-circuit on the theme alone,
     * which would have dropped it.
     *
     * Takes the context rather than extending it: an Activity is a Context too,
     * and asking the half-built one for resources here would throw. The config is
     * read through the non-throwing accessor because this runs before onCreate
     * has anywhere to report an uninitialized SDK.
     */
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

    /**
     * Paints the window with the merchant's surface colour.
     *
     * Here rather than in a effect under setContent: the window is themed from a
     * resource, so anything later leaves the sheet framed by a system-coloured
     * band until the first frame lands. Resolved without the session's colour,
     * which reaches the brand role only and so cannot change this one.
     */
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

    /**
     * Debug builds of the host app only. A merchant shipping a release build has
     * already made their colour choices and cannot act on a logcat line, while a
     * developer wiring up an appearance can.
     *
     * Each warning is logged once per sheet. The appearance resolves twice when
     * the merchant's back-office colour lands with the session, and a developer
     * reading logcat should see a new problem rather than the same one again.
     */
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
    // Google's sheet draws this one, so it is resolved here and handed over.
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
                Text(pcStringResource(R.string.paycross_processing))
            }
        }
    }
}

// Internal so the instrumented suite can stand it up on its own: a dialog runs
// in a sub-composition of its own window, which is exactly where a locale
// carried on LocalContext would have stopped.
@Composable
internal fun CancelConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(pcStringResource(R.string.paycross_cancel_payment_title)) },
        text = { Text(pcStringResource(R.string.paycross_cancel_payment_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(pcStringResource(R.string.paycross_cancel_payment_yes))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(pcStringResource(R.string.paycross_cancel_payment_continue))
            }
        }
    )
}
