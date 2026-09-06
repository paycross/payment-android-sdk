package com.paycross.sdk.internal.ui

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonParser
import com.paycross.sdk.PayCrossResult
import com.paycross.sdk.R
import com.paycross.sdk.PendingReason
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

/**
 * UI state for the payment flow.
 *
 * @property isLoading Whether an operation is in progress
 * @property error User-facing error, named rather than built: this class has
 *   no Context, so the sheet resolves the string in the language it drew in
 * @property sessionData Session data from the server
 * @property claims Parsed JWT claims from the session token
 * @property threeDs 3DS step requiring a WebView, if any
 * @property result Final payment result, signals flow completion
 * @property googlePayAvailable Whether to show the Google Pay button (session
 *   gates and device readiness combined)
 * @property selectedSavedCardUuid The stored card the form is on, or null for a
 *   new card. Held here rather than in the form because removing a card has to
 *   drop it from [sessionData] and drop the selection in the same update; split
 *   between two owners, the form would be left pointing at a card that no longer
 *   exists.
 */
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

/**
 * A 3DS step to render. Fingerprint runs in an invisible WebView;
 * only the challenge is shown to the user.
 */
internal data class ThreeDsUi(
    val action: ThreeDsAction,
    val isChallenge: Boolean
)

/**
 * ViewModel managing the payment flow state and operations.
 *
 * Handles session initialization, card submission, and status polling with
 * support for process death recovery via [SavedStateHandle].
 */
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

    // Added from the UI thread and dropped from a background dispatcher, so
    // it cannot be a plain HashSet. add() returning false is the guard: a
    // second confirm for a card already being removed does nothing.
    private val removalsInFlight: MutableSet<String> =
        Collections.newSetFromMap(ConcurrentHashMap())

    private var sessionToken: String
        get() = savedStateHandle[KEY_SESSION_TOKEN] ?: ""
        set(value) { savedStateHandle[KEY_SESSION_TOKEN] = value }

    private var transactionId: String?
        get() = savedStateHandle[KEY_TRANSACTION_ID]
        set(value) { savedStateHandle[KEY_TRANSACTION_ID] = value }

    /**
     * The last transaction this sheet learned of, for a shopper who cancels.
     *
     * Separate from [transactionId], which is the resume pointer and is cleared
     * when a retryable decline re-arms the form so a fresh poll cannot chase a
     * dead transaction. A cancel after that decline still has to name the attempt,
     * or the merchant is left with a transaction the host app cannot correlate.
     */
    var lastTransactionId: String?
        get() = savedStateHandle[KEY_LAST_TRANSACTION_ID]
        private set(value) { savedStateHandle[KEY_LAST_TRANSACTION_ID] = value }

    /**
     * Initializes the payment flow with the given session token.
     *
     * Parses the JWT, fetches session data, and resolves already-terminal
     * sessions (completed/expired) without showing the form. If a transaction
     * is already in flight (process death recovery or a completed submit on a
     * reloaded session), resumes status polling.
     *
     * @param token JWT session token from the merchant backend
     */
    fun initialize(token: String) {
        // Idempotent: the activity calls this on every onCreate so that a
        // fresh ViewModel after process death still initializes and resumes.
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

    /**
     * The card the form starts on. Null unless the merchant opted in, because a
     * preselected card is one unnoticed tap from a charge; the CVV stays
     * mandatory for exactly that reason, so the tap alone cannot pay.
     *
     * Cards arrive most-recently-used first, so the first is the likely one.
     */
    private fun preselectedSavedCardUuid(data: SessionData?): String? =
        if (data?.preselectsSavedCard == true) data.savedCards?.firstOrNull()?.uuid else null

    /**
     * Records the shopper's pick.
     *
     * @param uuid The stored card, or null for "Use a new card".
     */
    fun selectSavedCard(uuid: String?) {
        _uiState.update { it.copy(selectedSavedCardUuid = uuid) }
    }

    /**
     * Removes a stored card, then takes it off the list the sheet is showing.
     *
     * Only the local copy is rewritten: the session blob is written once at
     * creation, so a reload of the same session lists the card again even after
     * the server has disabled it. Nothing but the local list can be kept honest
     * from here.
     *
     * Ignored while a payment is in flight — the sheet is behind the processing
     * overlay and an authorization is the only thing that should be happening —
     * and ignored for a card whose removal has not come back yet.
     *
     * A 404 drops the card too. It says the card is not this customer's, which
     * is either a card that was already removed or one this session was never
     * allowed to touch; in both readings the sheet has no business still
     * offering it. Unauthorized and a transient failure keep the card, because
     * neither says anything about whether it is still there.
     *
     * @param uuid The stored card's uuid.
     */
    fun removeSavedCard(uuid: String) {
        if (_uiState.value.isLoading) return
        if (!removalsInFlight.add(uuid)) return

        viewModelScope.launch(dispatcher) {
            try {
                when (repository.removeSavedCard(uuid, sessionToken)) {
                    RemoveSavedCardResult.Removed,
                    RemoveSavedCardResult.NotFound -> _uiState.update { state ->
                        state.copy(
                            // Only this banner is cleared. A decline the shopper
                            // has not acted on yet is still true, and deleting a
                            // card is not an answer to it.
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

    /**
     * Submits card details to initiate payment processing.
     *
     * For new cards, provide all card details. For saved cards, provide only
     * the savedUuid and CVV. A fresh idempotency key is generated per submit;
     * `retry_after` responses are retried with the same key.
     *
     * @param context Android context for collecting browser info
     * @param card Card details from the form
     * @param fieldValues Field-group values keyed by group then field name
     */
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

    /**
     * Combines device-level Google Pay readiness (isReadyToPay, resolved by the
     * activity) with the session-level gates. Both must pass to show the button.
     */
    fun onGooglePayReadiness(deviceReady: Boolean) {
        _uiState.update {
            it.copy(
                googlePayAvailable = deviceReady && GooglePayRequests.isSessionEligible(it.sessionData)
            )
        }
    }

    /**
     * Stashes validated field-group values when the Google Pay sheet opens.
     * Saved-state backed so a process death while the sheet is up (the sheet
     * runs in Google's UI, our process is backgroundable) still submits with
     * the values the shopper entered.
     */
    fun onGooglePaySheetOpened(fieldValues: Map<String, Map<String, String>>) {
        savedStateHandle[KEY_GOOGLE_PAY_FIELDS] =
            HashMap(fieldValues.mapValues { HashMap(it.value) })
    }

    /** Surfaces the generic error after a non-cancel sheet failure; the form stays armed. */
    fun onGooglePayFailed() {
        _uiState.update { it.copy(isLoading = false, error = PAYMENT_FAILED) }
    }

    /**
     * Submits a Google Pay payment from the sheet's result.
     *
     * @param paymentDataJson The full PaymentData.toJson() string from the sheet
     */
    fun submitGooglePay(context: Context, paymentDataJson: String) {
        viewModelScope.launch(dispatcher) {
            _uiState.update { it.copy(isLoading = true, error = null) }

            // Google's ECv2 signature covers the tokenizationData.token string
            // byte for byte and the edge forwards it to the vault untouched, so
            // paymentMethodData is parsed into a Gson tree and embedded verbatim:
            // no model round-trip that could drop empty fields, reorder
            // reordering-sensitive keys, or re-encode the token string.
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
                            // The server's own sentence wins when there is
                            // one: it is more specific than anything here, and it
                            // is not the SDK's to translate.
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

    /**
     * Polls transaction status until a terminal state or the deadline.
     *
     * Network errors and non-2xx responses (the status item may not exist yet,
     * or polling may be throttled) are treated as transient and polling
     * continues until the deadline.
     */
    private fun pollStatus(transactionId: String) {
        if (pollJob?.isActive == true) return
        // Every route to a transaction id passes through here: a fresh submit, a
        // resume after process death, and a session reloaded with one already open.
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

            // The deadline says the SDK never learned the outcome, not that the
            // payment failed. A cut network is indistinguishable from a blip, so
            // the loop above swallows both and simply runs out - and the
            // authorization may well have completed meanwhile. Reporting a
            // failure here invites a retry over a payment the customer has
            // already made. The transaction id is what the merchant resolves it
            // with out of band.
            _uiState.update {
                it.copy(
                    isLoading = false,
                    threeDs = null,
                    result = PayCrossResult.Pending(transactionId, PendingReason.POLL_TIMEOUT)
                )
            }
        }
    }

    /**
     * Applies a status response to UI state. Returns true when terminal.
     */
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
                    // Checked ahead of the retry branch, not folded into the
                    // decline branch below: this status says the outcome is
                    // unknown, so neither re-arming the form nor reporting a
                    // failure is a correct reading of it.
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
                    // Re-arm the form like the checkout page does; only
                    // non-retryable declines end the payment sheet.
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
                                // Kept for every decline, not just the ones this
                                // version cannot read: what the server actually
                                // said is what a merchant needs in a support
                                // thread, and for UNRECOGNIZED it is the only
                                // place the value survives at all.
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

    /**
     * Ends the sheet once the session token it holds has expired.
     *
     * A retryable decline re-arms the form and ends the poll job cleanly, so
     * POLL_DEADLINE_MS stops applying and nothing bounds the sheet afterwards: it
     * was observed offering a live Pay button 45 minutes on, against a session the
     * server had long since expired. Whatever the session's own state, a submit
     * made with a dead token cannot be authorized, so the sheet resolves rather
     * than take a card it has no way to charge. RESTART is what initialize already
     * reports for the same condition, and it is not retryable, so the sheet ends.
     *
     * @param expiresAtEpochSeconds the token's `exp` claim
     */
    private fun watchSessionExpiry(expiresAtEpochSeconds: Long) {
        sessionExpiryJob = viewModelScope.launch(dispatcher) {
            delay(expiresAtEpochSeconds * 1000 - clock())

            // A submit or a poll still running is an authorization the server may
            // yet complete, and its outcome is the poll's to report. Waiting is
            // bounded: the poll carries POLL_DEADLINE_MS of its own.
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

    /**
     * Clears the current 3DS step after the WebView reports completion.
     * The action stays in the handled set, so polling won't re-show it.
     */
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

        // Fixed cadence matching the checkout page (SETTLEMENT_POLL_INTERVAL /
        // PAYMENT_CHALLENGE_POLL_INTERVAL in paymentConfig.js).
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
