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

internal interface PayCrossApi {
    @GET("session/{sessionId}")
    suspend fun getSession(
        @Path("sessionId") sessionId: String,
        @Header("Authorization") authorization: String?
    ): SessionResponse

    @POST("submit-card")
    suspend fun submitCard(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: SubmitCardRequest
    ): SubmitCardResponse

    @GET("status/{transactionId}")
    suspend fun getStatus(@Path("transactionId") transactionId: String): StatusResponse

    // Returns the raw Response, not Unit, so the repository can read the status code.
    @DELETE("saved-cards/{uuid}")
    suspend fun deleteSavedCard(
        @Header("Authorization") authorization: String,
        @Path("uuid") uuid: String
    ): Response<Unit>
}
