package com.paycross.demo

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
            onSave = {
                viewModel.saveMerchant(it)
                viewModel.navigate(Screen.Merchants)
            },
            onBack = { viewModel.navigate(Screen.Merchants) }
        )

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
