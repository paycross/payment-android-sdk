package com.paycross.sdk.internal.api.models

import com.google.gson.annotations.SerializedName

data class SessionResponse(
    @SerializedName("session_id") val sessionId: String,
    val data: SessionData?
)

data class SessionData(
    val email: String?,
    val phone: String?,
    @SerializedName("billing_address") val billingAddress: Address?,
    @SerializedName("saved_cards") val savedCards: List<SavedCard>?,
    @SerializedName("stored_credentials") val storedCredentials: StoredCredentials?
)

data class Address(
    val line1: String?,
    val line2: String?,
    val city: String?,
    val state: String?,
    @SerializedName("postal_code") val postalCode: String?,
    val country: String?
)

data class SavedCard(
    val uuid: String,
    @SerializedName("masked_pan") val maskedPan: String,
    @SerializedName("expire_month") val expireMonth: String,
    @SerializedName("expire_year") val expireYear: String,
    @SerializedName("cardholder_name") val cardholderName: String
)

data class StoredCredentials(
    @SerializedName("show_saved") val showSaved: Boolean?,
    val save: SaveConfig?
)

data class SaveConfig(
    val usage: String?
)
