package com.paycross.sdk.internal.api

import com.paycross.sdk.internal.api.models.SessionResponse
import com.paycross.sdk.internal.api.models.StatusResponse
import com.paycross.sdk.internal.api.models.SubmitCardRequest
import com.paycross.sdk.internal.api.models.SubmitCardResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Retrofit interface for the PayCross public checkout API
 * (`https://checkout.{env}/api`).
 */
internal interface PayCrossApi {
    /**
     * Retrieves session details by session ID.
     *
     * @param sessionId The unique session identifier.
     * @param authorization Bearer session JWT.
     * @return Session status, latest transaction, and checkout data blob.
     */
    @GET("session/{sessionId}")
    suspend fun getSession(
        @Path("sessionId") sessionId: String,
        @Header("Authorization") authorization: String?
    ): SessionResponse

    /**
     * Submits a payment for processing.
     *
     * @param idempotencyKey Unique key to prevent duplicate submissions.
     * @param request Payment method, card or wallet data, browser info, and field groups.
     * @return Response containing transaction ID, error, or retry-after hint.
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

    /**
     * Removes one of the customer's stored cards.
     *
     * Returns the raw [Response] rather than Unit so the repository can read the
     * status code: 204 removed, 400 malformed uuid, 401 bad or expired token,
     * 404 not this customer's card, 5xx transient. A Unit return would collapse
     * all four failures into one HttpException.
     *
     * @param authorization Bearer session JWT; its `customer` claim is the
     * ownership check the server applies.
     * @param uuid The stored card's uuid, from the session blob.
     */
    @DELETE("saved-cards/{uuid}")
    suspend fun deleteSavedCard(
        @Header("Authorization") authorization: String,
        @Path("uuid") uuid: String
    ): Response<Unit>
}
