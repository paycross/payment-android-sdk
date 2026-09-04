package com.paycross.sdk.internal.ui

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.paycross.sdk.PayCrossResult
import com.paycross.sdk.Recovery
import com.paycross.sdk.internal.api.models.BrowserInfo
import com.paycross.sdk.internal.api.models.SessionData
import com.paycross.sdk.internal.api.models.SessionResponse
import com.paycross.sdk.internal.api.models.StatusResponse
import com.paycross.sdk.internal.api.models.WalletsAvailability
import com.paycross.sdk.internal.api.models.SubmitCardRequest
import com.paycross.sdk.internal.api.models.SubmitCardResponse
import com.paycross.sdk.internal.api.models.ThreeDsAction
import com.paycross.sdk.internal.repository.PaymentRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class PaymentViewModelTest {

    // Payload: {"sub":"session-123","merchant":"merchant-456","amount":9999,"currency":"EUR","exp":4102444800}
    private val token = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJzZXNzaW9uLTEyMyIsIm1lcmNoYW50IjoibWVyY2hhbnQtNDU2IiwiYW1vdW50Ijo5OTk5LCJjdXJyZW5jeSI6IkVVUiIsImV4cCI6NDEwMjQ0NDgwMH0.sig" // gitleaks:allow

    // Same claims, but the session dies 1800 virtual seconds in, so a test can sit
    // on the form until it does. Payload: {..., "exp": 1800}
    private val shortLivedToken = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJzZXNzaW9uLTEyMyIsIm1lcmNoYW50IjoibWVyY2hhbnQtNDU2IiwiYW1vdW50Ijo5OTk5LCJjdXJyZW5jeSI6IkVVUiIsImV4cCI6MTgwMH0.sig" // gitleaks:allow

    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<PaymentRepository>()
    private val context = mockk<Context>(relaxed = true)

    private fun viewModel(clock: () -> Long = System::currentTimeMillis) = PaymentViewModel(
        savedStateHandle = SavedStateHandle(),
        repository = repository,
        dispatcher = dispatcher,
        browserInfoProvider = { browserInfo() },
        clock = clock
    )

    // The poll deadline and the session expiry are both measured against the clock
    // while delay() runs on the test scheduler, so a test that wants to reach
    // either has to hand the view model the scheduler's virtual time. On the real
    // clock those loops never finish.
    private fun TestScope.virtualClock(): () -> Long = { testScheduler.currentTime }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `open session with latest transaction resumes polling`() = runTest(dispatcher.scheduler) {
        coEvery { repository.getSession("session-123", token) } returns
            sessionResponse(status = "open", latestTransactionId = "tx-1")
        coEvery { repository.getStatus("tx-1") } returns
            StatusResponse("tx-1", "success", 9999, "EUR", null, null)

        val vm = viewModel()
        vm.initialize(token)
        advanceUntilIdle()

        val result = vm.uiState.value.result as PayCrossResult.Success
        assertEquals("tx-1", result.transactionId)
        assertEquals(9999, result.amount)
    }

    @Test
    fun `expired session fails with restart`() = runTest(dispatcher.scheduler) {
        coEvery { repository.getSession(any(), any()) } returns
            sessionResponse(status = "expired", latestTransactionId = null)

        val vm = viewModel()
        vm.initialize(token)
        advanceUntilIdle()

        val result = vm.uiState.value.result as PayCrossResult.Failure
        assertEquals(Recovery.RESTART, result.recovery)
    }

    @Test
    fun `poll tolerates early 404 and completes on success`() = runTest(dispatcher.scheduler) {
        coEvery { repository.getSession(any(), any()) } returns
            sessionResponse(status = "open", latestTransactionId = null)
        coEvery { repository.submitCard(any(), any()) } returns
            SubmitCardResponse(true, "tx-2", null, null, null)
        coEvery { repository.getStatus("tx-2") } throws httpException(404) andThenThrows
            httpException(404) andThen StatusResponse("tx-2", "success", null, null, null, null)

        val vm = viewModel()
        vm.initialize(token)
        advanceUntilIdle()
        vm.submitCard(context, newCard(), emptyMap())
        advanceUntilIdle()

        val result = vm.uiState.value.result as PayCrossResult.Success
        assertEquals("tx-2", result.transactionId)
        assertEquals(9999, result.amount)
        assertEquals("EUR", result.currency)
    }

    @Test
    fun `unknown recovery fails closed and keeps what the server said`() = runTest(dispatcher.scheduler) {
        coEvery { repository.getSession(any(), any()) } returns
            sessionResponse(status = "open", latestTransactionId = null)
        coEvery { repository.submitCard(any(), any()) } returns
            SubmitCardResponse(true, "tx-3", null, null, null)
        coEvery { repository.getStatus("tx-3") } returns
            StatusResponse("tx-3", "failed", null, null, null, "brand_new_value")

        val vm = viewModel()
        vm.initialize(token)
        advanceUntilIdle()
        vm.submitCard(context, newCard(), emptyMap())
        advanceUntilIdle()

        val result = vm.uiState.value.result as PayCrossResult.Failure
        assertEquals(Recovery.UNRECOGNIZED, result.recovery)
        assertFalse(result.recovery.isRetryable)
        assertEquals("brand_new_value", result.recoveryRaw)
    }

    @Test
    fun `a recognised recovery keeps the server's value too`() = runTest(dispatcher.scheduler) {
        coEvery { repository.getSession(any(), any()) } returns
            sessionResponse(status = "open", latestTransactionId = null)
        coEvery { repository.submitCard(any(), any()) } returns
            SubmitCardResponse(true, "tx-30", null, null, null)
        coEvery { repository.getStatus("tx-30") } returns
            StatusResponse("tx-30", "failed", null, null, null, "do_not_retry")

        val vm = viewModel()
        vm.initialize(token)
        advanceUntilIdle()
        vm.submitCard(context, newCard(), emptyMap())
        advanceUntilIdle()

        val result = vm.uiState.value.result as PayCrossResult.Failure
        assertEquals(Recovery.DO_NOT_RETRY, result.recovery)
        assertEquals("do_not_retry", result.recoveryRaw)
    }

    @Test
    fun `a failure the SDK raised itself carries no server value`() = runTest(dispatcher.scheduler) {
        coEvery { repository.getSession(any(), any()) } returns
            sessionResponse(status = "expired", latestTransactionId = null)

        val vm = viewModel()
        vm.initialize(token)
        advanceUntilIdle()

        val result = vm.uiState.value.result as PayCrossResult.Failure
        assertEquals(Recovery.RESTART, result.recovery)
        assertNull(result.recoveryRaw)
    }

    @Test
    fun `a poll that runs out of time never claims the payment can be retried`() = runTest(dispatcher.scheduler) {
        // The network is gone for good, so every poll throws and the loop simply
        // runs to POLL_DEADLINE_MS. Server-side the authorization may well have
        // completed: measured twice, both times over a succeeded, liability-shifted
        // payment. Reporting a retry there re-collects money already taken.
        coEvery { repository.getSession(any(), any()) } returns
            sessionResponse(status = "open", latestTransactionId = null)
        coEvery { repository.submitCard(any(), any()) } returns
            SubmitCardResponse(true, "tx-8", null, null, null)
        coEvery { repository.getStatus("tx-8") } throws IOException("network is unreachable")

        val vm = viewModel(clock = virtualClock())
        vm.initialize(token)
        advanceUntilIdle()
        vm.submitCard(context, newCard(), emptyMap())
        advanceUntilIdle()

        val result = vm.uiState.value.result as PayCrossResult.Failure
        assertEquals(Recovery.VERIFY_BEFORE_RETRY, result.recovery)
        assertFalse(result.recovery.isRetryable)
        // The merchant resolves the outcome out of band, so the id has to be there.
        assertEquals("tx-8", result.transactionId)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `a poll that runs out of time behind a throttling gateway says the same`() = runTest(dispatcher.scheduler) {
        coEvery { repository.getSession(any(), any()) } returns
            sessionResponse(status = "open", latestTransactionId = null)
        coEvery { repository.submitCard(any(), any()) } returns
            SubmitCardResponse(true, "tx-9", null, null, null)
        coEvery { repository.getStatus("tx-9") } throws httpException(429)

        val vm = viewModel(clock = virtualClock())
        vm.initialize(token)
        advanceUntilIdle()
        vm.submitCard(context, newCard(), emptyMap())
        advanceUntilIdle()

        val result = vm.uiState.value.result as PayCrossResult.Failure
        assertEquals(Recovery.VERIFY_BEFORE_RETRY, result.recovery)
        assertEquals("tx-9", result.transactionId)
    }

    @Test
    fun `retry_after resubmits the same request with the same idempotency key`() = runTest(dispatcher.scheduler) {
        coEvery { repository.getSession(any(), any()) } returns
            sessionResponse(status = "open", latestTransactionId = null)

        val keys = mutableListOf<String>()
        val requests = mutableListOf<SubmitCardRequest>()
        coEvery { repository.submitCard(capture(keys), capture(requests)) } returns
            SubmitCardResponse(null, null, null, "Request already processing", 1) andThen
            SubmitCardResponse(true, "tx-4", true, null, null)
        coEvery { repository.getStatus("tx-4") } returns
            StatusResponse("tx-4", "success", null, null, null, null)

        val vm = viewModel()
        vm.initialize(token)
        advanceUntilIdle()
        vm.submitCard(
            context,
            newCard(),
            mapOf("billing_address" to mapOf("country" to "DE"))
        )
        advanceUntilIdle()

        assertEquals(2, keys.size)
        assertEquals(keys[0], keys[1])

        val request = requests.first()
        assertEquals(token, request.session)
        assertEquals("card", request.paymentMethod)
        assertEquals("4111111111111111", request.card?.pan)
        assertEquals("ua", request.browserInfo.userAgent)
        assertEquals(mapOf("billing_address" to mapOf("country" to "DE")), request.fieldGroups)
        assertTrue(vm.uiState.value.result is PayCrossResult.Success)
    }

    @Test
    fun `retryable failure re-arms the form instead of finishing`() = runTest(dispatcher.scheduler) {
        coEvery { repository.getSession(any(), any()) } returns
            sessionResponse(status = "open", latestTransactionId = null)
        coEvery { repository.submitCard(any(), any()) } returns
            SubmitCardResponse(true, "tx-6", null, null, null)
        coEvery { repository.getStatus("tx-6") } returns
            StatusResponse("tx-6", "failed", null, null, null, "change_method")

        val vm = viewModel()
        vm.initialize(token)
        advanceTimeBy(FORM_SETTLE_MS)
        vm.submitCard(context, newCard(), emptyMap())
        advanceTimeBy(FORM_SETTLE_MS)

        assertNull(vm.uiState.value.result)
        assertNotNull(vm.uiState.value.error)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `a form re-armed by a decline does not outlive the session`() = runTest(dispatcher.scheduler) {
        // The retryable-decline branch ends the poll job cleanly, so nothing bounds
        // the sheet afterwards: observed sitting on a live Pay button for 45 minutes
        // against a session the server had already expired.
        coEvery { repository.getSession(any(), any()) } returns
            sessionResponse(status = "open", latestTransactionId = null)
        coEvery { repository.submitCard(any(), any()) } returns
            SubmitCardResponse(true, "tx-7", null, null, null)
        coEvery { repository.getStatus("tx-7") } returns
            StatusResponse("tx-7", "failed", null, null, null, "change_method")

        val vm = viewModel(clock = virtualClock())
        vm.initialize(shortLivedToken)
        advanceTimeBy(FORM_SETTLE_MS)
        vm.submitCard(context, newCard(), emptyMap())
        advanceTimeBy(FORM_SETTLE_MS)

        // The form is armed and still inside the session.
        assertNull(vm.uiState.value.result)
        assertNotNull(vm.uiState.value.error)

        advanceUntilIdle()

        val result = vm.uiState.value.result as PayCrossResult.Failure
        assertEquals(Recovery.RESTART, result.recovery)
        assertFalse(result.recovery.isRetryable)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `an untouched form does not outlive the session either`() = runTest(dispatcher.scheduler) {
        coEvery { repository.getSession(any(), any()) } returns
            sessionResponse(status = "open", latestTransactionId = null)

        val vm = viewModel(clock = virtualClock())
        vm.initialize(shortLivedToken)
        advanceTimeBy(FORM_SETTLE_MS)

        assertNull(vm.uiState.value.result)

        advanceUntilIdle()

        val result = vm.uiState.value.result as PayCrossResult.Failure
        assertEquals(Recovery.RESTART, result.recovery)
    }

    @Test
    fun `expiry does not cut in over a payment the poll has already resolved`() = runTest(dispatcher.scheduler) {
        coEvery { repository.getSession(any(), any()) } returns
            sessionResponse(status = "open", latestTransactionId = null)
        coEvery { repository.submitCard(any(), any()) } returns
            SubmitCardResponse(true, "tx-10", null, null, null)
        coEvery { repository.getStatus("tx-10") } returns
            StatusResponse("tx-10", "success", 9999, "EUR", null, null)

        val vm = viewModel(clock = virtualClock())
        vm.initialize(shortLivedToken)
        advanceTimeBy(FORM_SETTLE_MS)
        vm.submitCard(context, newCard(), emptyMap())
        advanceUntilIdle()

        // Long past the session's expiry by now; the success must stand.
        val result = vm.uiState.value.result as PayCrossResult.Success
        assertEquals("tx-10", result.transactionId)
    }

    @Test
    fun `fingerprint action surfaces once as hidden step`() = runTest(dispatcher.scheduler) {
        coEvery { repository.getSession(any(), any()) } returns
            sessionResponse(status = "open", latestTransactionId = null)
        coEvery { repository.submitCard(any(), any()) } returns
            SubmitCardResponse(true, "tx-5", null, null, null)

        val action = ThreeDsAction("https://acs.bank.com/3ds/method", "POST", mapOf("threeDSMethodData" to "abc"))
        coEvery { repository.getStatus("tx-5") } returns
            StatusResponse("tx-5", "threeds_fingerprint", null, null, action, null) andThen
            StatusResponse("tx-5", "threeds_fingerprint", null, null, action, null) andThen
            StatusResponse("tx-5", "success", null, null, null, null)

        val vm = viewModel()
        vm.initialize(token)
        advanceUntilIdle()
        vm.submitCard(context, newCard(), emptyMap())

        dispatcher.scheduler.advanceTimeBy(1500)
        dispatcher.scheduler.runCurrent()
        val threeDs = vm.uiState.value.threeDs
        assertNotNull(threeDs)
        assertFalse(threeDs!!.isChallenge)

        vm.clearThreeDs()
        dispatcher.scheduler.advanceTimeBy(2500)
        dispatcher.scheduler.runCurrent()
        assertNull(vm.uiState.value.threeDs)

        advanceUntilIdle()
        assertTrue(vm.uiState.value.result is PayCrossResult.Success)
    }

    @Test
    fun `google pay readiness needs device support and session gates together`() = runTest(dispatcher.scheduler) {
        coEvery { repository.getSession(any(), any()) } returns
            sessionResponse(sessionData(googlePay = true))

        val vm = viewModel()
        vm.initialize(token)
        advanceUntilIdle()

        vm.onGooglePayReadiness(deviceReady = true)
        assertTrue(vm.uiState.value.googlePayAvailable)

        vm.onGooglePayReadiness(deviceReady = false)
        assertFalse(vm.uiState.value.googlePayAvailable)
    }

    @Test
    fun `google pay shows on account funding sessions`() = runTest(dispatcher.scheduler) {
        coEvery { repository.getSession(any(), any()) } returns
            sessionResponse(sessionData(googlePay = true, accountFunding = true))

        val vm = viewModel()
        vm.initialize(token)
        advanceUntilIdle()

        vm.onGooglePayReadiness(deviceReady = true)
        assertTrue(vm.uiState.value.googlePayAvailable)
    }

    @Test
    fun `google pay shows for sessions without a wallets block`() = runTest(dispatcher.scheduler) {
        coEvery { repository.getSession(any(), any()) } returns
            sessionResponse(sessionData(googlePay = null))

        val vm = viewModel()
        vm.initialize(token)
        advanceUntilIdle()

        vm.onGooglePayReadiness(deviceReady = true)
        assertTrue(vm.uiState.value.googlePayAvailable)
    }

    @Test
    fun `google pay submit sends the wallet token with stashed field groups and no card`() =
        runTest(dispatcher.scheduler) {
            coEvery { repository.getSession(any(), any()) } returns
                sessionResponse(sessionData(googlePay = true))
            val requestSlot = slot<SubmitCardRequest>()
            coEvery { repository.submitCard(any(), capture(requestSlot)) } returns
                SubmitCardResponse(true, "tx-9", null, null, null)
            coEvery { repository.getStatus("tx-9") } returns
                StatusResponse("tx-9", "success", 9999, "EUR", null, null)

            val vm = viewModel()
            vm.initialize(token)
            advanceUntilIdle()

            vm.onGooglePaySheetOpened(mapOf("billing_address" to mapOf("country" to "US")))
            vm.submitGooglePay(context, paymentDataJson)
            advanceUntilIdle()

            val request = requestSlot.captured
            assertEquals("google_pay", request.paymentMethod)
            assertNull(request.card)
            assertEquals("google_pay", request.walletToken?.type)
            val data = request.walletToken?.data as com.google.gson.JsonObject
            assertEquals(
                """{"signature":"MEQ==","protocolVersion":"ECv2","signedMessage":"{}"}""",
                data.getAsJsonObject("tokenizationData").get("token").asString
            )
            assertEquals(mapOf("billing_address" to mapOf("country" to "US")), request.fieldGroups)
            assertTrue(vm.uiState.value.result is PayCrossResult.Success)
        }

    @Test
    fun `google pay submit with malformed payment data re-arms the form`() = runTest(dispatcher.scheduler) {
        coEvery { repository.getSession(any(), any()) } returns
            sessionResponse(sessionData(googlePay = true))

        val vm = viewModel()
        vm.initialize(token)
        advanceTimeBy(FORM_SETTLE_MS)

        vm.submitGooglePay(context, "not json")
        advanceTimeBy(FORM_SETTLE_MS)

        assertEquals("Payment failed. Please try again.", vm.uiState.value.error)
        assertFalse(vm.uiState.value.isLoading)
        assertNull(vm.uiState.value.result)
        coVerify(exactly = 0) { repository.submitCard(any(), any()) }
    }

    @Test
    fun `google pay sheet failure re-arms the form with the generic copy`() = runTest(dispatcher.scheduler) {
        coEvery { repository.getSession(any(), any()) } returns
            sessionResponse(sessionData(googlePay = true))

        val vm = viewModel()
        vm.initialize(token)
        advanceTimeBy(FORM_SETTLE_MS)

        vm.onGooglePayFailed()

        assertEquals("Payment failed. Please try again.", vm.uiState.value.error)
        assertNull(vm.uiState.value.result)
    }

    private fun sessionResponse(status: String, latestTransactionId: String?) =
        SessionResponse("session-123", status, latestTransactionId, null)

    private fun sessionResponse(data: SessionData) =
        SessionResponse("session-123", "open", null, data)

    private fun sessionData(googlePay: Boolean?, accountFunding: Boolean? = null) = SessionData(
        locale = null,
        returnUrl = null,
        successUrl = null,
        fieldGroups = null,
        merchantCountry = null,
        saveCardConfig = null,
        savedCards = null,
        wallets = googlePay?.let { WalletsAvailability(applePay = null, googlePay = it) },
        accountFunding = accountFunding,
        googlePay = null
    )

    private fun newCard() = CardFormData(
        cardholderName = "JOHN DOE",
        pan = "4111111111111111",
        expireMonth = "12",
        expireYear = "2030",
        cvv = "123",
        savedUuid = null,
        saveCard = false
    )

    private fun browserInfo() = BrowserInfo(
        userAgent = "ua",
        screenWidth = 1,
        screenHeight = 1,
        colorDepth = 24,
        timezoneOffset = 0,
        language = "en",
        acceptHeader = "*/*",
        javaEnabled = false,
        javascriptEnabled = true
    )

    // Trimmed but structurally real PaymentData.toJson() output.
    private val paymentDataJson = """
        {
          "apiVersionMinor": 0,
          "apiVersion": 2,
          "paymentMethodData": {
            "description": "Visa 1234",
            "tokenizationData": {
              "type": "PAYMENT_GATEWAY",
              "token": "{\"signature\":\"MEQ==\",\"protocolVersion\":\"ECv2\",\"signedMessage\":\"{}\"}"
            },
            "type": "CARD",
            "info": {"cardNetwork": "VISA", "cardDetails": "1234"}
          }
        }
    """.trimIndent()

    // Long enough for a submit and its first poll to settle, short enough that the
    // session is still alive: these tests are about the armed form, not expiry.
    private companion object {
        const val FORM_SETTLE_MS = 60_000L
    }

    private fun httpException(code: Int) =
        HttpException(Response.error<Any>(code, "".toResponseBody()))
}
