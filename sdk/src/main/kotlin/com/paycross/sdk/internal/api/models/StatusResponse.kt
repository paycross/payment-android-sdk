package com.paycross.sdk.internal.api.models

import com.google.gson.annotations.SerializedName

internal data class StatusResponse(
    @SerializedName("transaction_id") val transactionId: String,
    val status: String,
    val amount: Long?,
    val currency: String?,
    val action: ThreeDsAction?,
    val recovery: String?,
    // Present on terminal statuses when the shopper asked to store the card, and
    // the merchant's handle on it for later charges. Defaulted so the many
    // positional constructions in tests keep compiling; on the wire it is simply
    // absent whenever nothing was stored. `used_token` is deliberately not
    // decoded: the sheet already knows which stored card it submitted.
    @SerializedName("saved_token") val savedToken: String? = null
)

internal data class ThreeDsAction(
    val url: String,
    val method: String,
    val data: Map<String, String>?
)
