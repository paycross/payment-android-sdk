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

class PaymentRepositoryTest {

    @Test
    fun `204 removes the card`() = runTest {
        assertEquals(RemoveSavedCardResult.Removed, remove(Response.success(204, Unit)))
    }

    @Test
    fun `any 2xx removes the card`() = runTest {
        assertEquals(RemoveSavedCardResult.Removed, remove(Response.success(Unit)))
    }

    @Test
    fun `400 is a plain failure`() = runTest {
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
