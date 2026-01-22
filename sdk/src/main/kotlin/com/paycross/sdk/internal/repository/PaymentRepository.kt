package com.paycross.sdk.internal.repository

import com.paycross.sdk.internal.api.ApiClient
import com.paycross.sdk.internal.api.models.SessionResponse
import com.paycross.sdk.internal.api.models.StatusResponse
import com.paycross.sdk.internal.api.models.SubmitCardRequest
import com.paycross.sdk.internal.api.models.SubmitCardResponse

/**
 * Repository for payment-related API operations.
 *
 * Provides a clean abstraction over the API client, delegating network calls
 * to the underlying Retrofit service.
 */
internal class PaymentRepository {
    private val api = ApiClient.get()

    /**
     * Retrieves session details by session ID.
     *
     * @param sessionId The unique session identifier.
     * @return Session data including customer info and saved cards.
     */
    suspend fun getSession(sessionId: String): SessionResponse {
        return api.getSession(sessionId)
    }

    /**
     * Submits card details for payment processing.
     *
     * @param idempotencyKey Unique key to prevent duplicate submissions.
     * @param request Card and billing information.
     * @return Response containing transaction ID or error.
     */
    suspend fun submitCard(
        idempotencyKey: String,
        request: SubmitCardRequest
    ): SubmitCardResponse {
        return api.submitCard(idempotencyKey, request)
    }

    /**
     * Retrieves the current status of a transaction.
     *
     * @param transactionId The unique transaction identifier.
     * @return Current transaction status and any required 3DS action.
     */
    suspend fun getStatus(transactionId: String): StatusResponse {
        return api.getStatus(transactionId)
    }
}
