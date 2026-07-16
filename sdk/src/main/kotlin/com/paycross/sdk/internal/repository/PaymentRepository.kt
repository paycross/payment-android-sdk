package com.paycross.sdk.internal.repository

import com.paycross.sdk.internal.api.ApiClient
import com.paycross.sdk.internal.api.models.SessionResponse
import com.paycross.sdk.internal.api.models.StatusResponse
import com.paycross.sdk.internal.api.models.SubmitCardRequest
import com.paycross.sdk.internal.api.models.SubmitCardResponse

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
}
