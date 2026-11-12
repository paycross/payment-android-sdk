package com.paycross.sdk.internal.ui

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonParser
import com.paycross.sdk.PayCrossResult
import com.paycross.sdk.PendingReason
import com.paycross.sdk.R
import com.paycross.sdk.Recovery
import com.paycross.sdk.internal.api.JwtClaims
import com.paycross.sdk.internal.api.JwtParser
import com.paycross.sdk.internal.api.models.BrowserInfo
import com.paycross.sdk.internal.api.models.CardData
import com.paycross.sdk.internal.api.models.SessionData
import com.paycross.sdk.internal.api.models.SessionStatus
import com.paycross.sdk.internal.api.models.StatusResponse
import com.paycross.sdk.internal.api.models.SubmitCardRequest
import com.paycross.sdk.internal.api.models.ThreeDsAction
import com.paycross.sdk.internal.api.models.WalletToken
import com.paycross.sdk.internal.repository.PaymentRepository
import com.paycross.sdk.internal.repository.RemoveSavedCardResult
import com.paycross.sdk.internal.util.BrowserInfoProvider
import com.paycross.sdk.internal.util.IdempotencyKey
import com.paycross.sdk.internal.util.UiText
import com.paycross.sdk.internal.wallet.GooglePayRequests
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

internal data class PaymentUiState(
    val isLoading: Boolean = true,
    val error: UiText? = null,
    val sessionData: SessionData? = null,
    val claims: JwtClaims? = null,
    val threeDs: ThreeDsUi? = null,
    val result: PayCrossResult? = null,
    val googlePayAvailable: Boolean = false,
    val selectedSavedCardUuid: String? = null
)

internal data class ThreeDsUi(
    val action: ThreeDsAction,
    val isChallenge: Boolean
)

internal class PaymentViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val repository: PaymentRepository = PaymentRepository(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val browserInfoProvider: (Context) -> BrowserInfo = BrowserInfoProvider::collect,
    private val clock: () -> Long = System::currentTimeMillis
) : ViewModel() {

    private val _uiState = MutableStateFlow(PaymentUiState())
    val uiState: StateFlow<PaymentUiState> = _uiState.asStateFlow()

    private var pollJob: Job? = null
    private var sessionExpiryJob: Job? = null
    private val handledThreeDsActions = mutableSetOf<String>()

    // Added on the UI thread and removed on a background dispatcher, so the set must be concurrent. add() returning false is the guard against a second confirm.
    private val removalsInFlight: MutableSet<String> =
        Collections.newSetFromMap(ConcurrentHashMap())

    private var sessionToken: String
        get() = savedStateHandle[KEY_SESSION_TOKEN] ?: ""
        set(value) { savedStateHandle[KEY_SESSION_TOKEN] = value }

    private var transactionId: String?
        get() = savedStateHandle[KEY_TRANSACTION_ID]
        set(value) { savedStateHandle[KEY_TRANSACTION_ID] = value }

    var lastTransactionId: String?
        get() = savedStateHandle[KEY_LAST_TRANSACTION_ID]
        private set(value) { savedStateHandle[KEY_LAST_TRANSACTION_ID] = value }

    fun initialize(token: String) {
        if (_uiState.value.claims != null) return

        sessionToken = token

        viewModelScope.launch(dispatcher) {
            val claims = try {
                JwtParser.parse(token)
            } catch (e: IllegalArgumentException) {
                failInitialization(UiText.Resource(R.string.paycross_error_invalid_token))
                return@launch
            }

            if (claims.isExpired(clock() / 1000)) {
                failInitialization(UiText.Resource(R.string.paycross_session_expired))
                return@launch
            }

            val session = try {
                repository.getSession(claims.sessionId, token)
            } catch (e: IOException) {
                null
            } catch (e: HttpException) {
                null
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    sessionData = session?.data,
                    claims = claims,
                    selectedSavedCardUuid = preselectedSavedCardUuid(session?.data)
                )
            }

            val resumeTransactionId = transactionId ?: session?.latestTransactionId

            when (session?.status) {
                SessionStatus.EXPIRED -> _uiState.update {
                    it.copy(result = PayCrossResult.Failure(resumeTransactionId, Recovery.RESTART))
                }
                SessionStatus.COMPLETED -> when (resumeTransactionId) {
                    null -> _uiState.update {
                        it.copy(
                            result = PayCrossResult.Success(
                                transactionId = "",
                                status = "success",
                                amount = claims.amount,
                                currency = claims.currency
                            )
                        )
                    }
                    else -> pollStatus(resumeTransactionId)
                }
                else -> {
                    claims.expiresAt?.let { watchSessionExpiry(it) }
                    resumeTransactionId?.let { pollStatus(it) }
                }
            }
        }
    }

    private fun failInitialization(message: UiText) {
        _uiState.update {
            it.copy(
                isLoading = false,
                error = message,
                result = PayCrossResult.Failure(null, Recovery.RESTART)
            )
        }
    }

    private fun preselectedSavedCardUuid(data: SessionData?): String? =
        if (data?.preselectsSavedCard == true) data.savedCards?.firstOrNull()?.uuid else null

    fun selectSavedCard(uuid: String?) {
        _uiState.update { it.copy(selectedSavedCardUuid = uuid) }
    }

    fun removeSavedCard(uuid: String) {
        if (_uiState.value.isLoading) return
        if (!removalsInFlight.add(uuid)) return

        viewModelScope.launch(dispatcher) {
            try {
                when (repository.removeSavedCard(uuid, sessionToken)) {
                    RemoveSavedCardResult.Removed,
                    RemoveSavedCardResult.NotFound -> _uiState.update { state ->
                        state.copy(
                            error = state.error.takeIf { it != REMOVE_CARD_ERROR },
                            sessionData = state.sessionData?.let { data ->
                                data.copy(
                                    savedCards = data.savedCards?.filterNot { it.uuid == uuid }
                                )
                            },
                            selectedSavedCardUuid =
                                state.selectedSavedCardUuid.takeIf { it != uuid }
                        )
                    }
                    else -> _uiState.update { it.copy(error = REMOVE_CARD_ERROR) }
                }
            } finally {
                removalsInFlight.remove(uuid)
            }
        }
    }

    fun submitCard(
        context: Context,
        card: CardFormData,
        fieldValues: Map<String, Map<String, String>>
    ) {
        viewModelScope.launch(dispatcher) {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val cardData = if (card.savedUuid != null) {
                CardData(savedUuid = card.savedUuid, cvv = card.cvv)
            } else {
                CardData(
                    cardholderName = card.cardholderName,
                    pan = card.pan?.replace("\\s".toRegex(), ""),
                    expireMonth = card.expireMonth,
                    expireYear = card.expireYear,
                    cvv = card.cvv,
                    save = if (card.saveCard) true else null
                )
            }

            val request = SubmitCardRequest(
                session = sessionToken,
                paymentMethod = "card",
                card = cardData,
                browserInfo = browserInfoProvider(context),
                fieldGroups = fieldValues.takeIf { it.isNotEmpty() }
            )

            submitWithRetry(request)
        }
    }

    fun onGooglePayReadiness(deviceReady: Boolean) {
        _uiState.update {
            it.copy(
                googlePayAvailable = deviceReady && GooglePayRequests.isSessionEligible(it.sessionData)
            )
        }
    }

    // SavedStateHandle, so a process death while the Google Pay sheet is up (it runs in Google's UI) still submits the entered values.
    fun onGooglePaySheetOpened(fieldValues: Map<String, Map<String, String>>) {
        savedStateHandle[KEY_GOOGLE_PAY_FIELDS] =
            HashMap(fieldValues.mapValues { HashMap(it.value) })
    }

    fun onGooglePayFailed() {
        _uiState.update { it.copy(isLoading = false, error = PAYMENT_FAILED) }
    }

    fun submitGooglePay(context: Context, paymentDataJson: String) {
        viewModelScope.launch(dispatcher) {
            _uiState.update { it.copy(isLoading = true, error = null) }

            // Google's ECv2 signature covers the token string byte for byte, so the payload is embedded as a Gson tree unchanged.
            val paymentMethodData = try {
                JsonParser.parseString(paymentDataJson).asJsonObject
                    .getAsJsonObject("paymentMethodData")
            } catch (e: RuntimeException) {
                null
            }
            if (paymentMethodData == null) {
                _uiState.update {
                    it.copy(isLoading = false, error = PAYMENT_FAILED)
                }
                return@launch
            }

            val fieldValues: Map<String, Map<String, String>>? =
                savedStateHandle.get<HashMap<String, HashMap<String, String>>>(KEY_GOOGLE_PAY_FIELDS)

            val request = SubmitCardRequest(
                session = sessionToken,
                paymentMethod = "google_pay",
                walletToken = WalletToken(type = "google_pay", data = paymentMethodData),
                browserInfo = browserInfoProvider(context),
                fieldGroups = fieldValues?.takeIf { it.isNotEmpty() }
            )

            submitWithRetry(request)
        }
    }

    private suspend fun submitWithRetry(request: SubmitCardRequest) {
        // A fresh idempotency key per submit; retry_after responses reuse the same key.
        val idempotencyKey = IdempotencyKey.generate()

        repeat(MAX_SUBMIT_ATTEMPTS) {
            val response = try {
                repository.submitCard(idempotencyKey, request)
            } catch (e: IOException) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = UiText.Resource(R.string.paycross_error_network)
                    )
                }
                return
            } catch (e: HttpException) {
                _uiState.update {
                    it.copy(isLoading = false, error = SUBMISSION_FAILED)
                }
                return
            }

            when {
                response.success == true && response.transactionId != null -> {
                    transactionId = response.transactionId
                    pollStatus(response.transactionId)
                    return
                }
                response.retryAfter != null -> delay(response.retryAfter * 1000L)
                else -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = response.error?.let(UiText::Raw) ?: SUBMISSION_FAILED
                        )
                    }
                    return
                }
            }
        }

        _uiState.update {
            it.copy(isLoading = false, error = SUBMISSION_FAILED)
        }
    }

    private fun pollStatus(transactionId: String) {
        if (pollJob?.isActive == true) return
        lastTransactionId = transactionId

        pollJob = viewModelScope.launch(dispatcher) {
            val deadline = clock() + POLL_DEADLINE_MS

            while (clock() < deadline) {
                try {
                    if (handleStatus(repository.getStatus(transactionId))) return@launch
                } catch (e: IOException) {
                } catch (e: HttpException) {
                }

                delay(POLL_INTERVAL_MS)
            }

            // A deadline is not a failed payment: the authorization may still complete, so no failure is reported.
            _uiState.update {
                it.copy(
                    isLoading = false,
                    threeDs = null,
                    result = PayCrossResult.Pending(transactionId, PendingReason.POLL_TIMEOUT)
                )
            }
        }
    }

    private fun handleStatus(status: StatusResponse): Boolean {
        val claims = _uiState.value.claims

        when (status.status) {
            STATUS_SUCCESS, STATUS_AUTHORIZED -> _uiState.update {
                it.copy(
                    isLoading = false,
                    threeDs = null,
                    result = PayCrossResult.Success(
                        transactionId = status.transactionId,
                        status = status.status,
                        amount = status.amount ?: claims?.amount ?: 0L,
                        currency = status.currency ?: claims?.currency ?: "",
                        savedCardToken = status.savedToken
                    )
                )
            }
            STATUS_FAILED -> {
                val recovery = Recovery.fromString(status.recovery)
                if (recovery == Recovery.VERIFY_BEFORE_RETRY) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            threeDs = null,
                            result = PayCrossResult.Pending(
                                transactionId = status.transactionId,
                                reason = PendingReason.SERVER_VERIFY
                            )
                        )
                    }
                } else if (recovery.isRetryable) {
                    transactionId = null
                    handledThreeDsActions.clear()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            threeDs = null,
                            error = PAYMENT_FAILED
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            threeDs = null,
                            result = PayCrossResult.Failure(
                                transactionId = status.transactionId,
                                recovery = recovery,
                                recoveryRaw = status.recovery
                            )
                        )
                    }
                }
            }
            STATUS_THREEDS_FINGERPRINT, STATUS_THREEDS_CHALLENGE -> {
                val action = status.action ?: return false
                val key = "${status.status}|${action.url}|${action.data}"
                if (handledThreeDsActions.add(key)) {
                    _uiState.update {
                        it.copy(
                            threeDs = ThreeDsUi(
                                action = action,
                                isChallenge = status.status == STATUS_THREEDS_CHALLENGE
                            )
                        )
                    }
                }
                return false
            }
            else -> return false
        }
        return true
    }

    private fun watchSessionExpiry(expiresAtEpochSeconds: Long) {
        sessionExpiryJob = viewModelScope.launch(dispatcher) {
            delay(expiresAtEpochSeconds * 1000 - clock())

            while (isPaymentInFlight()) delay(POLL_INTERVAL_MS)
            if (_uiState.value.result != null) return@launch

            _uiState.update {
                it.copy(
                    isLoading = false,
                    threeDs = null,
                    result = PayCrossResult.Failure(transactionId, Recovery.RESTART)
                )
            }
        }
    }

    private fun isPaymentInFlight(): Boolean =
        _uiState.value.isLoading || pollJob?.isActive == true

    fun clearThreeDs() {
        _uiState.update { it.copy(threeDs = null) }
    }

    companion object {
        private const val KEY_SESSION_TOKEN = "session_token"
        private const val KEY_TRANSACTION_ID = "transaction_id"
        private const val KEY_LAST_TRANSACTION_ID = "last_transaction_id"
        private const val KEY_GOOGLE_PAY_FIELDS = "google_pay_field_values"

        private const val MAX_SUBMIT_ATTEMPTS = 5
        private const val POLL_DEADLINE_MS = 8 * 60 * 1000L

        private const val POLL_INTERVAL_MS = 2000L

        private val REMOVE_CARD_ERROR = UiText.Resource(R.string.paycross_remove_card_failed)
        private val PAYMENT_FAILED = UiText.Resource(R.string.paycross_error_payment_failed)
        private val SUBMISSION_FAILED = UiText.Resource(R.string.paycross_error_submission_failed)

        private const val STATUS_SUCCESS = "success"
        private const val STATUS_AUTHORIZED = "authorized"
        private const val STATUS_FAILED = "failed"
        private const val STATUS_THREEDS_FINGERPRINT = "threeds_fingerprint"
        private const val STATUS_THREEDS_CHALLENGE = "threeds_challenge"
    }
}
