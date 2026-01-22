package com.paycross.sdk.internal.api

import com.paycross.sdk.internal.api.models.SessionResponse
import com.paycross.sdk.internal.api.models.StatusResponse
import com.paycross.sdk.internal.api.models.SubmitCardRequest
import com.paycross.sdk.internal.api.models.SubmitCardResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Retrofit interface for PayCross API endpoints.
 *
 * All methods are suspend functions for coroutine-based async execution.
 */
internal interface PayCrossApi {
    /**
     * Retrieves session details by session ID.
     *
     * @param sessionId The unique session identifier.
     * @return Session data including customer info and saved cards.
     */
    @GET("session/{sessionId}")
    suspend fun getSession(@Path("sessionId") sessionId: String): SessionResponse

    /**
     * Submits card details for payment processing.
     *
     * @param idempotencyKey Unique key to prevent duplicate submissions.
     * @param request Card and billing information.
     * @return Response containing transaction ID or error.
     */
    @POST("submit-card")
    suspend fun submitCard(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: SubmitCardRequest
    ): SubmitCardResponse

    /**
     * Retrieves the current status of a transaction.
     *
     * @param transactionId The unique transaction identifier.
     * @return Current transaction status and any required 3DS action.
     */
    @GET("status/{transactionId}")
    suspend fun getStatus(@Path("transactionId") transactionId: String): StatusResponse
}
