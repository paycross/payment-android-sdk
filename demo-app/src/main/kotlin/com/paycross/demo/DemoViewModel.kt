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
    data object History : Screen
    data class RunDetail(val runId: String) : Screen
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

/** A run held back by the production gate until the tester confirms it. */
data class PendingRun(
    val scenarioName: String,
    val merchantName: String,
    val start: () -> Unit
)

data class DemoUiState(
    val data: DemoData,
    val screen: Screen = Screen.Home,
    val isRunning: Boolean = false,
    val runError: String? = null,
    val lastResult: PayCrossResult? = null,
    val externalRun: ExternalRun? = null,
    val inspectedJson: String? = null,
    val inspectLoading: Boolean = false,
    val pendingRun: PendingRun? = null
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

    /** Replaces a merchant's scenarios with fresh preset seeds — the upgrade path
     * for installs seeded before the current seed data. */
    fun reseedScenarios(merchantId: String, preset: ScenarioPreset) = updateData { data ->
        val merchant = data.merchants.find { it.id == merchantId } ?: return@updateData data
        data.copy(
            scenarios = data.scenarios.filterNot { it.merchantId == merchantId } +
                DemoSeeds.scenariosFor(merchant, preset)
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

    fun onPaymentResult(result: PayCrossResult) {
        _uiState.update { it.copy(lastResult = result) }
        // A result can arrive on a fresh ViewModel (process death mid-payment),
        // so fall back to the newest still-open SDK record.
        val runId = activeSdkRunId
            ?: _uiState.value.data.runHistory
                .firstOrNull { it.surface == "SDK" && it.outcome == "pending" }
                ?.id
            ?: return
        activeSdkRunId = null
        updateRun(runId) { run ->
            when (result) {
                is PayCrossResult.Success -> run.copy(
                    outcome = "succeeded",
                    transactionId = result.transactionId,
                    amount = result.amount,
                    currency = result.currency
                )
                is PayCrossResult.Failure -> run.copy(
                    outcome = "failed · ${result.recovery}",
                    transactionId = result.transactionId
                )
                // "unknown", not "pending": a record's outcome is "pending"
                // exactly while its run is still in flight, and three lookups
                // in this file match on that string exactly.
                is PayCrossResult.Pending -> run.copy(
                    outcome = "unknown · ${result.reason.name.lowercase()}",
                    transactionId = result.transactionId
                )
                is PayCrossResult.Cancelled -> run.copy(
                    outcome = "cancelled",
                    transactionId = result.transactionId
                )
            }
        }
    }

    fun clearResult() = _uiState.update { it.copy(lastResult = null, runError = null) }

    /**
     * Production merchants create real payment sessions, so every entry point —
     * UI actions and the exported `paycross-demo://run` deep link alike — routes
     * through here rather than through a screen-local check.
     */
    private fun gateProduction(merchant: Merchant, scenario: Scenario, start: () -> Unit) {
        if (merchant.environment != Merchant.ENV_PRODUCTION) {
            start()
            return
        }
        _uiState.update {
            it.copy(pendingRun = PendingRun(scenario.name, merchant.name, start))
        }
    }

    fun confirmPendingRun() {
        val pending = _uiState.value.pendingRun ?: return
        _uiState.update { it.copy(pendingRun = null) }
        pending.start()
    }

    fun cancelPendingRun() = _uiState.update { it.copy(pendingRun = null) }

    fun runScenario(scenario: Scenario, onSessionToken: (String) -> Unit) {
        val merchant = _uiState.value.data.merchants.find { it.id == scenario.merchantId } ?: return
        gateProduction(merchant, scenario) { startScenario(scenario, merchant, onSessionToken) }
    }

    private fun startScenario(
        scenario: Scenario,
        merchant: Merchant,
        onSessionToken: (String) -> Unit
    ) {
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
                val error = result.exceptionOrNull()
                _uiState.update { it.copy(isRunning = false, runError = error?.message) }
                if (error != null) {
                    logMintFailure(scenario, "SDK", error)
                }
                result.onSuccess { minted ->
                    activeSdkRunId = recordRun(scenario, "SDK", minted)
                    onSessionToken(minted.sessionToken)
                }
            }
        }
    }

    /**
     * Mints a session for a checkout surface outside the SDK sheet (browser tab,
     * copied link, QR on another device) and polls the session for the outcome.
     * [deepLinkReturn] rewrites return/success URLs so an on-device browser
     * bounces back into the harness when the hosted page finishes.
     */
    fun runScenarioExternally(
        scenario: Scenario,
        deepLinkReturn: Boolean,
        surface: String,
        onUrl: (String) -> Unit
    ) {
        val merchant = _uiState.value.data.merchants.find { it.id == scenario.merchantId } ?: return
        gateProduction(merchant, scenario) {
            startScenarioExternally(scenario, merchant, deepLinkReturn, surface, onUrl)
        }
    }

    private fun startScenarioExternally(
        scenario: Scenario,
        merchant: Merchant,
        deepLinkReturn: Boolean,
        surface: String,
        onUrl: (String) -> Unit
    ) {
        stopPolling("superseded by a new run")
        _uiState.update {
            it.copy(isRunning = true, runError = null, lastResult = null, externalRun = null)
        }

        externalPollJob = viewModelScope.launch(ioDispatcher) {
            val minted = runCatching {
                SessionMinter.create(merchant, scenario.requestBody, deepLinkReturn)
            }.getOrElse { error ->
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isRunning = false, runError = error.message) }
                    logMintFailure(scenario, surface, error)
                }
                return@launch
            }

            Log.i(TAG, "external checkout_url=${minted.checkoutUrl} session_id=${minted.sessionId}")
            val runId = withContext(Dispatchers.Main) {
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
                val id = recordRun(scenario, surface, minted)
                onUrl(minted.checkoutUrl)
                id
            }

            pollingRunId = runId
            pollExternalRun(minted, merchant.paycrossVersion, runId)
            pollingRunId = null
        }
    }

    fun dismissExternalRun() {
        stopPolling("dismissed while waiting")
        _uiState.update { it.copy(externalRun = null) }
    }

    /**
     * Cancels the poll job and closes its record — the poll is the only writer of
     * an external run's outcome, so without this the record stays "pending"
     * forever and no run_result line is ever logged for that session.
     */
    private fun stopPolling(reason: String) {
        externalPollJob?.cancel()
        externalPollJob = null
        pollingRunId?.let { runId ->
            pollingRunId = null
            updateRun(runId) { run ->
                if (run.outcome == "pending") run.copy(outcome = "unresolved · $reason") else run
            }
        }
    }

    private suspend fun pollExternalRun(minted: MintedSession, paycrossVersion: String, runId: String) {
        val deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            delay(POLL_INTERVAL_MS)
            val session = runCatching {
                SessionMinter.fetchSession(minted, paycrossVersion)
            }.getOrNull() ?: continue

            when (session.optString("status")) {
                "completed" -> {
                    updateExternalPhase(outcomeFromSession(session), runId, session)
                    return
                }
                "failed", "expired", "cancelled" -> {
                    updateExternalPhase(
                        ExternalRun.Phase.FAILED to "Session ${session.optString("status")}",
                        runId,
                        session
                    )
                    return
                }
            }
        }
        updateExternalPhase(
            ExternalRun.Phase.TIMEOUT to "No outcome within ${POLL_TIMEOUT_MS / 60_000} min",
            runId,
            session = null
        )
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

    /** Mirrors payx-tkg getPrimaryPaymentTransaction (src/lib/session-actions.mjs). */
    private fun primaryPaymentTransaction(session: JSONObject): JSONObject? {
        val transactions = session.optJSONArray("transactions") ?: return null
        var latest: JSONObject? = null
        for (i in 0 until transactions.length()) {
            val txn = transactions.optJSONObject(i) ?: continue
            if (txn.optString("type") != "payment") continue
            if (!txn.isNull("parent_transaction_id") && txn.optString("parent_transaction_id").isNotEmpty()) continue
            if (latest == null || sortKey(txn) >= sortKey(latest!!)) latest = txn
        }
        return latest
    }

    // optString returns the literal "null" for a JSON null, which would sort above
    // every ISO timestamp, so read the fields through isNull first.
    private fun sortKey(txn: JSONObject): String {
        val updated = if (txn.isNull("updated_at")) "" else txn.optString("updated_at")
        return updated.ifEmpty { if (txn.isNull("created_at")) "" else txn.optString("created_at") }
    }

    private fun JSONObject.stringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).ifEmpty { null }

    private suspend fun updateExternalPhase(
        outcome: Pair<ExternalRun.Phase, String>,
        runId: String,
        session: JSONObject?
    ) {
        withContext(Dispatchers.Main) {
            _uiState.update { state ->
                state.copy(
                    externalRun = state.externalRun?.copy(phase = outcome.first, detail = outcome.second)
                )
            }
            val payment = session?.let { primaryPaymentTransaction(it) }
            updateRun(runId) { run ->
                run.copy(
                    outcome = when (outcome.first) {
                        ExternalRun.Phase.SUCCESS -> "succeeded"
                        ExternalRun.Phase.TIMEOUT -> "timeout"
                        else -> outcome.second
                    },
                    transactionId = payment?.stringOrNull("id") ?: run.transactionId,
                    amount = payment?.optLong("amount") ?: run.amount,
                    currency = payment?.stringOrNull("currency") ?: run.currency
                )
            }
        }
    }

    /** Starts a scenario from a `paycross-demo://run` deep link (adb runner entry). */
    fun runFromDeepLink(
        merchantName: String?,
        scenarioName: String?,
        surface: String,
        onSessionToken: (String) -> Unit,
        onUrl: (String) -> Unit
    ) {
        val data = _uiState.value.data
        val merchant = data.merchants.find {
            it.name.equals(merchantName, ignoreCase = true) || it.id == merchantName
        }
        if (merchant == null) {
            deepLinkError("merchant not found: $merchantName")
            return
        }
        val scenario = data.scenarios.find {
            it.merchantId == merchant.id && it.name.equals(scenarioName, ignoreCase = true)
        }
        if (scenario == null) {
            deepLinkError("scenario not found: $scenarioName")
            return
        }

        selectMerchant(merchant.id)
        when (surface.lowercase()) {
            "browser" -> runScenarioExternally(scenario, true, "Browser", onUrl)
            else -> runScenario(scenario, onSessionToken)
        }
    }

    private fun deepLinkError(reason: String) {
        Log.i(TAG, "run_error reason=\"$reason\"")
        _uiState.update { it.copy(runError = "Deep link: $reason") }
    }

    /** Mint failures must still produce a terminal line for log-scraping runners. */
    private fun logMintFailure(scenario: Scenario, surface: String, error: Throwable) {
        Log.i(
            TAG,
            "run_error scenario=\"${scenario.name}\" surface=$surface " +
                "reason=\"mint failed: ${error.message?.take(200)}\""
        )
    }

    private fun recordRun(scenario: Scenario, surface: String, minted: MintedSession): String {
        val record = RunRecord(
            timestamp = System.currentTimeMillis(),
            merchantId = scenario.merchantId,
            scenarioName = scenario.name,
            surface = surface,
            sessionId = minted.sessionId,
            sessionUrl = minted.sessionUrl,
            checkoutUrl = minted.checkoutUrl,
            requestBody = minted.sentBody
        )
        Log.i(
            TAG,
            "run_start scenario=\"${record.scenarioName}\" surface=${record.surface} session_id=${record.sessionId}"
        )
        updateData { data ->
            data.copy(runs = (listOf(record) + data.runHistory).take(MAX_RUNS))
        }
        return record.id
    }

    private fun updateRun(runId: String, transform: (RunRecord) -> RunRecord) = updateData { data ->
        data.copy(
            runs = data.runHistory.map { run ->
                if (run.id != runId) return@map run
                transform(run).also { closed ->
                    if (closed.outcome != "pending") {
                        Log.i(
                            TAG,
                            "run_result scenario=\"${closed.scenarioName}\" surface=${closed.surface} " +
                                "outcome=\"${closed.outcome}\" session_id=${closed.sessionId} " +
                                "transaction_id=${closed.transactionId ?: "-"}"
                        )
                    }
                }
            }
        )
    }

    fun clearHistory() = updateData { it.copy(runs = emptyList()) }

    fun inspectSession(run: RunRecord) {
        val merchant = _uiState.value.data.merchants.find { it.id == run.merchantId }
        if (merchant == null) {
            _uiState.update { it.copy(inspectedJson = "Merchant for this run no longer exists.") }
            return
        }
        _uiState.update { it.copy(inspectLoading = true, inspectedJson = null) }
        viewModelScope.launch(ioDispatcher) {
            val text = runCatching {
                JSONObject(SessionMinter.fetchSessionAsMerchant(merchant, run.sessionUrl)).toString(2)
            }.getOrElse { "Fetch failed: ${it.message}" }
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(inspectLoading = false, inspectedJson = text) }
            }
        }
    }

    fun dismissInspectedSession() =
        _uiState.update { it.copy(inspectedJson = null, inspectLoading = false) }

    /** Shareable repro of the mint request; credentials stay as shell placeholders. */
    fun curlFor(run: RunRecord): String {
        val merchant = _uiState.value.data.merchants.find { it.id == run.merchantId }
        val tokenUrl = merchant?.tokenUrl ?: "<token-url>"
        val apiUrl = merchant?.paymentApiUrl ?: run.sessionUrl.substringBeforeLast('/')
        val version = merchant?.paycrossVersion ?: "<version>"
        return """
            AUTH=$(printf '%s:%s' "${'$'}CLIENT_ID" "${'$'}CLIENT_SECRET" | openssl base64 -A)

            TOKEN=$(curl -s -X POST '$tokenUrl' \
              -H "Authorization: Basic ${'$'}AUTH" \
              --data grant_type=client_credentials | jq -r .access_token)

            curl -s -X POST '$apiUrl' \
              -H "Authorization: Bearer ${'$'}TOKEN" \
              -H 'PayCross-Version: $version' \
              -H "Idempotency-Key: $(uuidgen)" \
              -H 'Content-Type: application/json' \
              --data '${run.requestBody.replace("'", "'\\''")}'
        """.trimIndent()
    }

    private var externalPollJob: Job? = null
    private var pollingRunId: String? = null
    private var activeSdkRunId: String? = null

    private fun updateData(transform: (DemoData) -> DemoData) {
        _uiState.update { it.copy(data = transform(it.data)) }
        store.save(_uiState.value.data)
    }

    private companion object {
        val BRAND_COLOR = 0xFF1E88E5.toInt()
        const val TAG = "PayCrossHarness"
        const val POLL_INTERVAL_MS = 2_500L
        const val POLL_TIMEOUT_MS = 180_000L
        const val MAX_RUNS = 50
    }
}
