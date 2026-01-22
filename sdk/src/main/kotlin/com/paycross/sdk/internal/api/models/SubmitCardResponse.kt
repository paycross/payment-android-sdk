package com.paycross.sdk.internal.api.models

import com.google.gson.annotations.SerializedName

data class SubmitCardResponse(
    val success: Boolean,
    @SerializedName("transaction_id") val transactionId: String?,
    val cached: Boolean?,
    val error: String?
)
