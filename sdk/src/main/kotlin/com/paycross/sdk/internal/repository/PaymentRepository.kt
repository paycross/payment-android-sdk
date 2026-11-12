package com.paycross.sdk.internal.repository

import com.paycross.sdk.internal.api.ApiClient
import com.paycross.sdk.internal.api.PayCrossApi
import com.paycross.sdk.internal.api.models.SessionResponse
import com.paycross.sdk.internal.api.models.StatusResponse
import com.paycross.sdk.internal.api.models.SubmitCardRequest
import com.paycross.sdk.internal.api.models.SubmitCardResponse
import java.io.IOException

private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_NOT_FOUND = 404

internal sealed class RemoveSavedCardResult {
    data object Removed : RemoveSavedCardResult()

    data object NotFound : RemoveSavedCardResult()

    data object Unauthorized : RemoveSavedCardResult()

    data object Failed : RemoveSavedCardResult()
}

internal class PaymentRepository(private val api: PayCrossApi = ApiClient.get()) {
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
