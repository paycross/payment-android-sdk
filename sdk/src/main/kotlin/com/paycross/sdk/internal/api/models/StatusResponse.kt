package com.paycross.sdk.internal.api.models

import com.google.gson.annotations.SerializedName

internal data class StatusResponse(
    @SerializedName("transaction_id") val transactionId: String,
    val status: String,
    val amount: Long?,
    val currency: String?,
    val action: ThreeDsAction?,
    val recovery: String?,
    // Used_token is not decoded, since the sheet already knows the card it submitted.
    @SerializedName("saved_token") val savedToken: String? = null
)

internal data class ThreeDsAction(
    val url: String,
    val method: String,
    val data: Map<String, String>?
)
