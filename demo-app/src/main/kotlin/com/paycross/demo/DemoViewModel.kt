package com.paycross.demo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paycross.sdk.PayCross
import com.paycross.sdk.PayCrossResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

sealed interface Screen {
    data object Home : Screen
    data object Merchants : Screen
    data class MerchantEdit(val merchantId: String?) : Screen
    data class ScenarioEdit(val scenarioId: String?) : Screen
}

data class DemoUiState(
    val data: DemoData,
    val screen: Screen = Screen.Home,
    val isRunning: Boolean = false,
    val runError: String? = null,
    val lastResult: PayCrossResult? = null
) {
    val selectedMerchant: Merchant?
        get() = data.merchants.find { it.id == data.selectedMerchantId }

    val selectedScenarios: List<Scenario>
        get() = data.scenarios.filter { it.merchantId == data.selectedMerchantId }
}

class DemoViewModel(
    private val store: DemoStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow(DemoUiState(data = store.load()))
    val uiState: StateFlow<DemoUiState> = _uiState.asStateFlow()

    fun navigate(screen: Screen) = _uiState.update { it.copy(screen = screen) }

    fun selectMerchant(id: String) = updateData { it.copy(selectedMerchantId = id) }

    fun saveMerchant(merchant: Merchant, preset: ScenarioPreset) = updateData { data ->
        val exists = data.merchants.any { it.id == merchant.id }
        val merchants = if (exists) {
            data.merchants.map { if (it.id == merchant.id) merchant else it }
        } else {
            data.merchants + merchant
        }
        // New merchants start with their preset's scenario set.
        val scenarios = if (exists) {
            data.scenarios
        } else {
            data.scenarios + DemoSeeds.scenariosFor(merchant, preset)
        }
        data.copy(
            merchants = merchants,
            scenarios = scenarios,
            selectedMerchantId = data.selectedMerchantId ?: merchant.id
        )
    }

    fun deleteMerchant(id: String) = updateData { data ->
        val merchants = data.merchants.filterNot { it.id == id }
        data.copy(
            merchants = merchants,
            scenarios = data.scenarios.filterNot { it.merchantId == id },
            selectedMerchantId = if (data.selectedMerchantId == id) {
                merchants.firstOrNull()?.id
            } else {
                data.selectedMerchantId
            }
        )
    }

    fun saveScenario(scenario: Scenario) = updateData { data ->
        val exists = data.scenarios.any { it.id == scenario.id }
        val scenarios = if (exists) {
            data.scenarios.map { if (it.id == scenario.id) scenario else it }
        } else {
            data.scenarios + scenario
        }
        data.copy(scenarios = scenarios)
    }

    fun duplicateScenario(id: String) = updateData { data ->
        val source = data.scenarios.find { it.id == id } ?: return@updateData data
        val copy = source.copy(
            id = UUID.randomUUID().toString(),
            name = "${source.name} (copy)"
        )
        data.copy(scenarios = data.scenarios + copy)
    }

    fun deleteScenario(id: String) = updateData { data ->
        data.copy(scenarios = data.scenarios.filterNot { it.id == id })
    }

    fun onPaymentResult(result: PayCrossResult) =
        _uiState.update { it.copy(lastResult = result) }

    fun clearResult() = _uiState.update { it.copy(lastResult = null, runError = null) }

    fun runScenario(scenario: Scenario, onSessionToken: (String) -> Unit) {
        val merchant = _uiState.value.data.merchants.find { it.id == scenario.merchantId } ?: return
        _uiState.update { it.copy(isRunning = true, runError = null, lastResult = null) }

        viewModelScope.launch(ioDispatcher) {
            val result = runCatching {
                PayCross.init(
                    environment = merchant.sdkEnvironment,
                    brandColor = BRAND_COLOR,
                    testCardPrefill = scenario.card.toTestCardPrefillOrNull()
                )
                SessionMinter.create(merchant, scenario.requestBody)
            }
            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(isRunning = false, runError = result.exceptionOrNull()?.message)
                }
                result.onSuccess(onSessionToken)
            }
        }
    }

    private fun updateData(transform: (DemoData) -> DemoData) {
        _uiState.update { it.copy(data = transform(it.data)) }
        store.save(_uiState.value.data)
    }

    private companion object {
        val BRAND_COLOR = 0xFF1E88E5.toInt()
    }
}
