package com.paycross.sdk.internal.ui

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paycross.sdk.PayCrossResult
import com.paycross.sdk.Recovery
import com.paycross.sdk.internal.api.JwtClaims
import com.paycross.sdk.internal.api.JwtParser
import com.paycross.sdk.internal.api.models.CardData
import com.paycross.sdk.internal.api.models.SessionData
import com.paycross.sdk.internal.api.models.SubmitCardRequest
import com.paycross.sdk.internal.api.models.ThreeDsAction
import com.paycross.sdk.internal.repository.PaymentRepository
import com.paycross.sdk.internal.util.BrowserInfoProvider
import com.paycross.sdk.internal.util.IdempotencyKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * UI state for the payment flow.
 *
 * @property isLoading Whether an operation is in progress
 * @property error User-facing error message, if any
 * @property sessionData Session data from the server
 * @property claims Parsed JWT claims from the session token
 * @property threeDsAction 3DS action requiring user interaction
 * @property result Final payment result, signals flow completion
 */
internal data class PaymentUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val sessionData: SessionData? = null,
    val claims: JwtClaims? = null,
    val threeDsAction: ThreeDsAction? = null,
    val result: PayCrossResult? = null
)

/**
 * ViewModel managing the payment flow state and operations.
 *
 * Handles session initialization, card submission, and status polling with
 * support for process death recovery via [SavedStateHandle].
 *
 * @property savedStateHandle Persists critical state across process death
 * @property repository Repository for payment API operations
 * @property dispatcher Coroutine dispatcher for background work
 */
internal class PaymentViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val repository: PaymentRepository = PaymentRepository(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow(PaymentUiState())
    val uiState: StateFlow<PaymentUiState> = _uiState.asStateFlow()

    private var sessionToken: String
        get() = savedStateHandle[KEY_SESSION_TOKEN] ?: ""
        set(value) { savedStateHandle[KEY_SESSION_TOKEN] = value }

    private var transactionId: String?
        get() = savedStateHandle[KEY_TRANSACTION_ID]
        set(value) { savedStateHandle[KEY_TRANSACTION_ID] = value }

    private var idempotencyKey: String
        get() = savedStateHandle[KEY_IDEMPOTENCY] ?: IdempotencyKey.generate().also {
            savedStateHandle[KEY_IDEMPOTENCY] = it
        }
        set(value) { savedStateHandle[KEY_IDEMPOTENCY] = value }

    /**
     * Initializes the payment flow with the given session token.
     *
     * Parses the JWT to extract claims and fetches session data from the server.
     * If a transaction ID exists in saved state (process death recovery),
     * resumes status polling automatically.
     *
     * @param token JWT session token from the merchant backend
     */
    fun initialize(token: String) {
        sessionToken = token

        viewModelScope.launch(dispatcher) {
            try {
                val claims = JwtParser.parse(token)
                val session = repository.getSession(claims.sessionId)

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        sessionData = session.data,
                        claims = claims
                    )
                }
            } catch (e: IllegalArgumentException) {
                handleInitializationError("Invalid session token")
            } catch (e: IOException) {
                handleInitializationError("Network error. Please check your connection.")
            }
        }

        // Resume polling if we have a transaction ID (process death recovery)
        transactionId?.let { pollStatus(it) }
    }

    private fun handleInitializationError(message: String) {
        _uiState.update {
            it.copy(
                isLoading = false,
                error = message,
                result = PayCrossResult.Failure(null, Recovery.RESTART)
            )
        }
    }

    /**
     * Submits card details to initiate payment processing.
     *
     * For new cards, provide all card details. For saved cards, provide only
     * the savedUuid and CVV. The idempotency key ensures safe retries.
     *
     * @param context Android context for collecting browser info
     * @param cardholderName Cardholder name (new cards only)
     * @param pan Card number without spaces (new cards only)
     * @param expireMonth Expiration month MM format (new cards only)
     * @param expireYear Expiration year YY format (new cards only)
     * @param cvv Card verification value (required)
     * @param savedUuid UUID of a saved card (use instead of card details)
     * @param saveCard Whether to save the card for future use
     */
    fun submitCard(
        context: Context,
        cardholderName: String?,
        pan: String?,
        expireMonth: String?,
        expireYear: String?,
        cvv: String,
        savedUuid: String?,
        saveCard: Boolean
    ) {
        viewModelScope.launch(dispatcher) {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                val browserInfo = BrowserInfoProvider.collect(context)

                val cardData = if (savedUuid != null) {
                    CardData(savedUuid = savedUuid, cvv = cvv)
                } else {
                    CardData(
                        cardholderName = cardholderName,
                        pan = pan?.replace("\\s".toRegex(), ""),
                        expireMonth = expireMonth,
                        expireYear = expireYear,
                        cvv = cvv,
                        save = if (saveCard) true else null
                    )
                }

                val request = SubmitCardRequest(
                    session = sessionToken,
                    card = cardData,
                    browserInfo = browserInfo
                )

                val response = repository.submitCard(idempotencyKey, request)

                if (response.success && response.transactionId != null) {
                    transactionId = response.transactionId
                    pollStatus(response.transactionId)
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = response.error ?: "Payment submission failed"
                        )
                    }
                }
            } catch (e: IOException) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Network error. Please try again."
                    )
                }
            }
        }
    }

    /**
     * Polls transaction status until a terminal state is reached.
     *
     * Uses exponential backoff starting at 1 second, capped at 5 seconds.
     * Handles 3DS intermediate states by updating the UI for user action.
     *
     * @param transactionId Transaction ID to poll
     */
    private fun pollStatus(transactionId: String) {
        viewModelScope.launch(dispatcher) {
            var attempts = 0
            var delayMs = INITIAL_POLL_DELAY_MS

            while (attempts < MAX_POLL_ATTEMPTS) {
                try {
                    val status = repository.getStatus(transactionId)

                    when (status.status) {
                        STATUS_SUCCESS, STATUS_AUTHORIZED -> {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    result = PayCrossResult.Success(
                                        transactionId = status.transactionId,
                                        status = status.status,
                                        amount = status.amount,
                                        currency = status.currency
                                    )
                                )
                            }
                            return@launch
                        }
                        STATUS_FAILED -> {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    result = PayCrossResult.Failure(
                                        transactionId = status.transactionId,
                                        recovery = Recovery.fromString(status.recovery ?: "retry")
                                    )
                                )
                            }
                            return@launch
                        }
                        STATUS_THREEDS_FINGERPRINT, STATUS_THREEDS_CHALLENGE -> {
                            _uiState.update { it.copy(threeDsAction = status.action) }
                        }
                    }
                } catch (e: IOException) {
                    // Continue polling on network errors
                }

                delay(delayMs)
                delayMs = (delayMs * BACKOFF_MULTIPLIER).toLong().coerceAtMost(MAX_POLL_DELAY_MS)
                attempts++
            }

            // Timeout - polling exceeded max attempts
            _uiState.update {
                it.copy(
                    isLoading = false,
                    result = PayCrossResult.Failure(transactionId, Recovery.RETRY)
                )
            }
        }
    }

    /**
     * Clears the current 3DS action after it has been handled.
     *
     * Call this after the WebView completes 3DS fingerprint or challenge flow
     * to resume status polling without displaying the action again.
     */
    fun clearThreeDsAction() {
        _uiState.update { it.copy(threeDsAction = null) }
    }

    companion object {
        private const val KEY_SESSION_TOKEN = "session_token"
        private const val KEY_TRANSACTION_ID = "transaction_id"
        private const val KEY_IDEMPOTENCY = "idempotency_key"

        private const val MAX_POLL_ATTEMPTS = 60
        private const val INITIAL_POLL_DELAY_MS = 1000L
        private const val MAX_POLL_DELAY_MS = 5000L
        private const val BACKOFF_MULTIPLIER = 1.5

        private const val STATUS_SUCCESS = "success"
        private const val STATUS_AUTHORIZED = "authorized"
        private const val STATUS_FAILED = "failed"
        private const val STATUS_THREEDS_FINGERPRINT = "threeds_fingerprint"
        private const val STATUS_THREEDS_CHALLENGE = "threeds_challenge"
    }
}
