package com.paycross.sdk.internal.api.models

import com.google.gson.annotations.SerializedName

internal data class SessionResponse(
    @SerializedName("session_id") val sessionId: String,
    val status: String?,
    @SerializedName("latest_transaction_id") val latestTransactionId: String?,
    val data: SessionData?
)

internal data class SessionData(
    val locale: String?,
    @SerializedName("return_url") val returnUrl: String?,
    @SerializedName("success_url") val successUrl: String?,
    @SerializedName("field_groups") val fieldGroups: List<FieldGroup>?,
    @SerializedName("merchant_country") val merchantCountry: String?,
    @SerializedName("save_card_config") val saveCardConfig: SaveCardConfig?,
    @SerializedName("saved_cards") val savedCards: List<SavedCard>?,
    val wallets: WalletsAvailability?,
    @SerializedName("account_funding") val accountFunding: Boolean?,
    @SerializedName("google_pay") val googlePay: GooglePayConfig?
)

internal data class WalletsAvailability(
    @SerializedName("apple_pay") val applePay: Boolean?,
    @SerializedName("google_pay") val googlePay: Boolean?
)

internal data class GooglePayConfig(
    @SerializedName("merchant_origin") val merchantOrigin: String?,
    @SerializedName("merchant_name") val merchantName: String?,
    @SerializedName("billing_address_required") val billingAddressRequired: Boolean?
)

internal data class FieldGroup(
    val key: String,
    val label: String?,
    val fields: List<FieldDefinition>?
)

internal data class FieldDefinition(
    val name: String,
    val type: String?,
    val label: String?,
    val placeholder: String?,
    val required: Boolean?,
    val readonly: Boolean?,
    val value: String?,
    val condition: FieldCondition?,
    val options: List<FieldOption>?,
    val validation: FieldValidation?
)

internal data class FieldCondition(
    @SerializedName("when") val whenField: String,
    @SerializedName("in") val whenIn: List<String>?,
    val display: String?,
    val default: String?
)

internal data class FieldOption(
    val value: String,
    val label: String?
)

internal data class FieldValidation(
    val pattern: String?,
    @SerializedName("max_length") val maxLength: Int?,
    val messages: Map<String, String>?
)

internal data class SaveCardConfig(
    val usage: String?
)

internal data class SavedCard(
    val uuid: String,
    @SerializedName("masked_pan") val maskedPan: String,
    @SerializedName("card_brand") val cardBrand: String?,
    @SerializedName("expire_month") val expireMonth: String,
    @SerializedName("expire_year") val expireYear: String,
    @SerializedName("cardholder_name") val cardholderName: String
)

internal object SessionStatus {
    const val OPEN = "open"
    const val COMPLETED = "completed"
    const val EXPIRED = "expired"
}
