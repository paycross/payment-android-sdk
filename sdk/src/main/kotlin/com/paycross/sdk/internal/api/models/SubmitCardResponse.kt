package com.paycross.sdk.internal.api.models

import com.google.gson.annotations.SerializedName

internal data class SubmitCardResponse(
    val success: Boolean?,
    @SerializedName("transaction_id") val transactionId: String?,
    val cached: Boolean?,
    val error: String?,
    @SerializedName("retry_after") val retryAfter: Int?
)
