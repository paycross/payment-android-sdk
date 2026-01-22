package com.paycross.sdk.internal.api.models

import com.google.gson.annotations.SerializedName

data class SubmitCardRequest(
    val session: String,
    val card: CardData,
    @SerializedName("browser_info") val browserInfo: BrowserInfo,
    @SerializedName("billing_address") val billingAddress: Address? = null
)

data class CardData(
    @SerializedName("saved_uuid") val savedUuid: String? = null,
    @SerializedName("cardholder_name") val cardholderName: String? = null,
    val pan: String? = null,
    @SerializedName("expire_year") val expireYear: String? = null,
    @SerializedName("expire_month") val expireMonth: String? = null,
    val cvv: String,
    val save: Boolean? = null
)
