package com.paycross.demo

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paycross.sdk.PayCross
import com.paycross.sdk.PayCrossResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

sealed interface Screen {
    data object Home : Screen
    data object Merchants : Screen
    data class MerchantEdit(val merchantId: String?) : Screen
    data class ScenarioEdit(val scenarioId: String?) : Screen
}

/** A checkout handed off to a browser or another device; outcome comes from polling. */
data class ExternalRun(
    val scenarioName: String,
    val sessionId: String,
    val checkoutUrl: String,
    val phase: Phase,
    val detail: String
) {
    enum class Phase { WAITING, SUCCESS, FAILED, TIMEOUT }
}

data class DemoUiState(
    val data: DemoData,
    val screen: Screen = Screen.Home,
    val isRunning: Boolean = false,
    val runError: String? = null,
    val lastResult: PayCrossResult? = null,
    val externalRun: ExternalRun? = null
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
                result.onSuccess { onSessionToken(it.sessionToken) }
            }
        }
    }

    /**
     * Mints a session for a checkout surface outside the SDK sheet (browser tab,
     * copied link, QR on another device) and polls the session for the outcome.
     * [deepLinkReturn] rewrites return/success URLs so an on-device browser
     * bounces back into the harness when the hosted page finishes.
     */
    fun runScenarioExternally(scenario: Scenario, deepLinkReturn: Boolean, onUrl: (String) -> Unit) {
        val merchant = _uiState.value.data.merchants.find { it.id == scenario.merchantId } ?: return
        externalPollJob?.cancel()
        _uiState.update {
            it.copy(isRunning = true, runError = null, lastResult = null, externalRun = null)
        }

        externalPollJob = viewModelScope.launch(ioDispatcher) {
            val minted = runCatching {
                SessionMinter.create(merchant, scenario.requestBody, deepLinkReturn)
            }.getOrElse { error ->
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isRunning = false, runError = error.message) }
                }
                return@launch
            }

            Log.i(TAG, "external checkout_url=${minted.checkoutUrl} session_id=${minted.sessionId}")
            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        isRunning = false,
                        externalRun = ExternalRun(
                            scenarioName = scenario.name,
                            sessionId = minted.sessionId,
                            checkoutUrl = minted.checkoutUrl,
                            phase = ExternalRun.Phase.WAITING,
                            detail = "Waiting for checkout…"
                        )
                    )
                }
                onUrl(minted.checkoutUrl)
            }

            pollExternalRun(minted, merchant.paycrossVersion)
        }
    }

    fun dismissExternalRun() {
        externalPollJob?.cancel()
        externalPollJob = null
        _uiState.update { it.copy(externalRun = null) }
    }

    private suspend fun pollExternalRun(minted: MintedSession, paycrossVersion: String) {
        val deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            delay(POLL_INTERVAL_MS)
            val session = runCatching {
                SessionMinter.fetchSession(minted, paycrossVersion)
            }.getOrNull() ?: continue

            when (session.optString("status")) {
                "completed" -> {
                    updateExternalPhase(outcomeFromSession(session))
                    return
                }
                "failed", "expired", "cancelled" -> {
                    updateExternalPhase(
                        ExternalRun.Phase.FAILED to "Session ${session.optString("status")}"
                    )
                    return
                }
            }
        }
        updateExternalPhase(ExternalRun.Phase.TIMEOUT to "No outcome within ${POLL_TIMEOUT_MS / 60_000} min")
    }

    private fun outcomeFromSession(session: JSONObject): Pair<ExternalRun.Phase, String> {
        val payment = primaryPaymentTransaction(session)
            ?: return ExternalRun.Phase.FAILED to "Completed with no payment transaction"
        val status = payment.optString("status")
        val amount = payment.optLong("amount")
        val currency = payment.optString("currency")
        val phase = if (status in setOf("succeeded", "captured", "authorized")) {
            ExternalRun.Phase.SUCCESS
        } else {
            ExternalRun.Phase.FAILED
        }
        return phase to "$status · $amount $currency"
    }

    private fun primaryPaymentTransaction(session: JSONObject): JSONObject? {
        val transactions = session.optJSONArray("transactions") ?: return null
        var latest: JSONObject? = null
        for (i in 0 until transactions.length()) {
            val txn = transactions.optJSONObject(i) ?: continue
            if (txn.optString("type") != "payment") continue
            if (!txn.isNull("parent_transaction_id") && txn.optString("parent_transaction_id").isNotEmpty()) continue
            val sortKey = txn.optString("updated_at").ifEmpty { txn.optString("created_at") }
            val latestKey = latest?.let { it.optString("updated_at").ifEmpty { it.optString("created_at") } } ?: ""
            if (latest == null || sortKey >= latestKey) latest = txn
        }
        return latest
    }

    private suspend fun updateExternalPhase(outcome: Pair<ExternalRun.Phase, String>) {
        withContext(Dispatchers.Main) {
            _uiState.update { state ->
                state.copy(
                    externalRun = state.externalRun?.copy(phase = outcome.first, detail = outcome.second)
                )
            }
        }
    }

    private var externalPollJob: Job? = null

    private fun updateData(transform: (DemoData) -> DemoData) {
        _uiState.update { it.copy(data = transform(it.data)) }
        store.save(_uiState.value.data)
    }

    private companion object {
        val BRAND_COLOR = 0xFF1E88E5.toInt()
        const val TAG = "PayCrossHarness"
        const val POLL_INTERVAL_MS = 2_500L
        const val POLL_TIMEOUT_MS = 180_000L
    }
}
