package com.paycross.sdk.internal.api.models

import com.google.gson.annotations.SerializedName

data class StatusResponse(
    @SerializedName("transaction_id") val transactionId: String,
    val status: String,
    val amount: Long,
    val currency: String,
    val action: ThreeDsAction?,
    val recovery: String?
)

data class ThreeDsAction(
    val url: String,
    val method: String,
    val data: Map<String, String>?
)
