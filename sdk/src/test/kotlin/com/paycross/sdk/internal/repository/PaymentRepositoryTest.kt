package com.paycross.sdk.internal.repository

import com.paycross.sdk.internal.api.PayCrossApi
import com.paycross.sdk.internal.api.models.SessionResponse
import com.paycross.sdk.internal.api.models.StatusResponse
import com.paycross.sdk.internal.api.models.SubmitCardRequest
import com.paycross.sdk.internal.api.models.SubmitCardResponse
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Response
import java.io.IOException

/**
 * The status-code mapping for a card removal, which is the only place in the SDK
 * that reads a raw HTTP code. Every branch decides whether the shopper's card
 * leaves the sheet, so each one is pinned here rather than left to the one
 * ViewModel test that happens to exercise it.
 */
class PaymentRepositoryTest {

    @Test
    fun `204 removes the card`() = runTest {
        assertEquals(RemoveSavedCardResult.Removed, remove(Response.success(204, Unit)))
    }

    @Test
    fun `any 2xx removes the card`() = runTest {
        // The route returns 204 today. Nothing about the sheet's reaction depends
        // on that, so a 200 must not be read as a failure.
        assertEquals(RemoveSavedCardResult.Removed, remove(Response.success(Unit)))
    }

    @Test
    fun `400 is a plain failure`() = runTest {
        // A malformed uuid. The sheet cannot fix it and must not pretend the card
        // is gone, so it reads the same as any other refusal.
        assertEquals(RemoveSavedCardResult.Failed, remove(error(400)))
    }

    @Test
    fun `401 is unauthorized`() = runTest {
        assertEquals(RemoveSavedCardResult.Unauthorized, remove(error(401)))
    }

    @Test
    fun `404 is not found`() = runTest {
        assertEquals(RemoveSavedCardResult.NotFound, remove(error(404)))
    }

    @Test
    fun `500 is a plain failure`() = runTest {
        assertEquals(RemoveSavedCardResult.Failed, remove(error(500)))
    }

    @Test
    fun `a dropped connection is a plain failure`() = runTest {
        // IOException is the one thing Retrofit throws rather than returns for a
        // Response-typed call, so it needs catching or the removal crashes the
        // coroutine instead of showing the banner.
        val api = FakeApi { throw IOException("socket closed") }

        assertEquals(
            RemoveSavedCardResult.Failed,
            PaymentRepository(api).removeSavedCard("card-1", "jwt-token")
        )
    }

    @Test
    fun `the session token is sent as a bearer and the uuid goes in the path`() = runTest {
        val api = FakeApi { Response.success(204, Unit) }

        PaymentRepository(api).removeSavedCard("card-1", "jwt-token")

        assertEquals("Bearer jwt-token", api.lastAuthorization)
        assertEquals("card-1", api.lastUuid)
    }

    private suspend fun remove(response: Response<Unit>): RemoveSavedCardResult =
        PaymentRepository(FakeApi { response }).removeSavedCard("card-1", "jwt-token")

    private fun error(code: Int): Response<Unit> = Response.error(code, "".toResponseBody())

    private class FakeApi(private val respond: () -> Response<Unit>) : PayCrossApi {
        var lastAuthorization: String? = null
        var lastUuid: String? = null

        override suspend fun deleteSavedCard(
            authorization: String,
            uuid: String
        ): Response<Unit> {
            lastAuthorization = authorization
            lastUuid = uuid
            return respond()
        }

        override suspend fun getSession(
            sessionId: String,
            authorization: String?
        ): SessionResponse = unused()

        override suspend fun submitCard(
            idempotencyKey: String,
            request: SubmitCardRequest
        ): SubmitCardResponse = unused()

        override suspend fun getStatus(transactionId: String): StatusResponse = unused()

        private fun unused(): Nothing =
            throw AssertionError("removeSavedCard must not call any other endpoint")
    }
}
