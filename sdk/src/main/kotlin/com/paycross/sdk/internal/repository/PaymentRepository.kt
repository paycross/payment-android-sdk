package com.paycross.sdk.internal.repository

import com.paycross.sdk.internal.api.ApiClient
import com.paycross.sdk.internal.api.models.SessionResponse
import com.paycross.sdk.internal.api.models.StatusResponse
import com.paycross.sdk.internal.api.models.SubmitCardRequest
import com.paycross.sdk.internal.api.models.SubmitCardResponse
import java.io.IOException

private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_NOT_FOUND = 404

/**
 * The outcome of removing a stored card, in the terms the sheet reacts to.
 *
 * Retrofit types stop here: the ViewModel decides what the shopper sees, and it
 * should not have to read status codes to do that.
 */
internal sealed class RemoveSavedCardResult {
    /** The card is gone. The server treats a repeat as the same success. */
    data object Removed : RemoveSavedCardResult()

    /** Not this customer's card, so this session may not remove it. */
    data object NotFound : RemoveSavedCardResult()

    /** The session token was rejected. */
    data object Unauthorized : RemoveSavedCardResult()

    /** Network trouble, a malformed request, or a server error. Retrying is safe. */
    data object Failed : RemoveSavedCardResult()
}

/**
 * Repository for payment-related API operations.
 */
internal class PaymentRepository {
    private val api = ApiClient.get()

    suspend fun getSession(sessionId: String, sessionToken: String?): SessionResponse {
        return api.getSession(sessionId, sessionToken?.let { "Bearer $it" })
    }

    suspend fun submitCard(
        idempotencyKey: String,
        request: SubmitCardRequest
    ): SubmitCardResponse {
        return api.submitCard(idempotencyKey, request)
    }

    suspend fun getStatus(transactionId: String): StatusResponse {
        return api.getStatus(transactionId)
    }

    /**
     * Removes a stored card, translating the response into [RemoveSavedCardResult].
     *
     * @param uuid The stored card's uuid.
     * @param sessionToken The session JWT the sheet was opened with.
     */
    suspend fun removeSavedCard(uuid: String, sessionToken: String): RemoveSavedCardResult {
        val response = try {
            api.deleteSavedCard("Bearer $sessionToken", uuid)
        } catch (e: IOException) {
            return RemoveSavedCardResult.Failed
        }

        return when {
            response.isSuccessful -> RemoveSavedCardResult.Removed
            response.code() == HTTP_UNAUTHORIZED -> RemoveSavedCardResult.Unauthorized
            response.code() == HTTP_NOT_FOUND -> RemoveSavedCardResult.NotFound
            else -> RemoveSavedCardResult.Failed
        }
    }
}
