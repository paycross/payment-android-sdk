package com.paycross.demo

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.paycross.sdk.PayCrossContract

class MainActivity : ComponentActivity() {

    private lateinit var paymentLauncher: ActivityResultLauncher<String>

    private val viewModel: DemoViewModel by viewModels {
        viewModelFactory { initializer { DemoViewModel(DemoStore(applicationContext)) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        paymentLauncher = registerForActivityResult(PayCrossContract()) { result ->
            viewModel.onPaymentResult(result)
        }

        setContent {
            MaterialTheme {
                DemoApp(
                    viewModel = viewModel,
                    onLaunchPayment = { paymentLauncher.launch(it) }
                )
            }
        }

        // The VIEW intent stays the task's intent, so onCreate would re-run the
        // scenario on every recreation (rotation, process death) without this.
        if (savedInstanceState == null) {
            handleDeepLink(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    /**
     * `paycross-demo://run?merchant=<name>&scenario=<name>&surface=sdk|browser`
     * lets the adb runner start a scenario without UI navigation:
     * `adb shell am start -a android.intent.action.VIEW -d "<uri>"`.
     * `paycross-demo://result` is the hosted-checkout return bounce — the
     * foreground switch alone is the point; polling supplies the outcome.
     */
    private fun handleDeepLink(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "paycross-demo" || uri.host != "run") return
        viewModel.runFromDeepLink(
            merchantName = uri.getQueryParameter("merchant"),
            scenarioName = uri.getQueryParameter("scenario"),
            surface = uri.getQueryParameter("surface") ?: "sdk",
            onSessionToken = { paymentLauncher.launch(it) },
            onUrl = { url -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        )
    }
}

@Composable
private fun DemoApp(
    viewModel: DemoViewModel,
    onLaunchPayment: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    when (val screen = uiState.screen) {
        Screen.Home -> HomeScreen(
            uiState = uiState,
            onSelectMerchant = viewModel::selectMerchant,
            onManageMerchants = { viewModel.navigate(Screen.Merchants) },
            onAddScenario = { viewModel.navigate(Screen.ScenarioEdit(null)) },
            onEditScenario = { viewModel.navigate(Screen.ScenarioEdit(it)) },
            onDuplicateScenario = viewModel::duplicateScenario,
            onDeleteScenario = viewModel::deleteScenario,
            onRunScenario = { viewModel.runScenario(it, onLaunchPayment) },
            onRunExternally = viewModel::runScenarioExternally,
            onDismissExternalRun = viewModel::dismissExternalRun,
            onOpenHistory = { viewModel.navigate(Screen.History) },
            onConfirmPendingRun = viewModel::confirmPendingRun,
            onCancelPendingRun = viewModel::cancelPendingRun,
            onClearResult = viewModel::clearResult
        )

        Screen.Merchants -> MerchantsScreen(
            merchants = uiState.data.merchants,
            selectedId = uiState.data.selectedMerchantId,
            onSelect = viewModel::selectMerchant,
            onAdd = { viewModel.navigate(Screen.MerchantEdit(null)) },
            onEdit = { viewModel.navigate(Screen.MerchantEdit(it)) },
            onDelete = viewModel::deleteMerchant,
            onBack = { viewModel.navigate(Screen.Home) }
        )

        is Screen.MerchantEdit -> MerchantEditScreen(
            merchant = uiState.data.merchants.find { it.id == screen.merchantId },
            onSave = { merchant, preset ->
                viewModel.saveMerchant(merchant, preset)
                viewModel.navigate(Screen.Merchants)
            },
            onBack = { viewModel.navigate(Screen.Merchants) }
        )

        Screen.History -> HistoryScreen(
            runs = uiState.data.runHistory,
            onOpenRun = { viewModel.navigate(Screen.RunDetail(it)) },
            onClearHistory = viewModel::clearHistory,
            onBack = { viewModel.navigate(Screen.Home) }
        )

        is Screen.RunDetail -> {
            val run = uiState.data.runHistory.find { it.id == screen.runId }
            if (run == null) {
                LaunchedEffect(Unit) { viewModel.navigate(Screen.History) }
            } else {
                RunDetailScreen(
                    run = run,
                    merchantName = uiState.data.merchants.find { it.id == run.merchantId }?.name,
                    inspectedJson = uiState.inspectedJson,
                    inspectLoading = uiState.inspectLoading,
                    curl = viewModel.curlFor(run),
                    onInspectSession = { viewModel.inspectSession(run) },
                    onDismissInspected = viewModel::dismissInspectedSession,
                    onBack = { viewModel.navigate(Screen.History) }
                )
            }
        }

        is Screen.ScenarioEdit -> {
            val merchantId = uiState.data.selectedMerchantId
            if (merchantId == null) {
                // Mutating navigation state during composition corrupts the
                // slot table; defer it to a side effect.
                LaunchedEffect(Unit) { viewModel.navigate(Screen.Home) }
            } else {
                ScenarioEditScreen(
                    scenario = uiState.data.scenarios.find { it.id == screen.scenarioId },
                    merchantId = merchantId,
                    onSave = {
                        viewModel.saveScenario(it)
                        viewModel.navigate(Screen.Home)
                    },
                    onBack = { viewModel.navigate(Screen.Home) }
                )
            }
        }
    }
}
