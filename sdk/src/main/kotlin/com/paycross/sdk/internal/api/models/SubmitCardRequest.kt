package com.paycross.sdk.internal.api.models

import com.google.gson.annotations.SerializedName

data class SubmitCardRequest(
    val session: String,
    @SerializedName("payment_method") val paymentMethod: String,
    val card: CardData? = null,
    @SerializedName("wallet_token") val walletToken: WalletToken? = null,
    @SerializedName("browser_info") val browserInfo: BrowserInfo,
    @SerializedName("field_groups") val fieldGroups: Map<String, Map<String, String>>? = null
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

data class WalletToken(
    val type: String,
    val data: Any
)
