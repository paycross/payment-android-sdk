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
    @SerializedName("saved_cards_config") val savedCardsConfig: SavedCardsConfig?,
    val wallets: WalletsAvailability?,
    @SerializedName("account_funding") val accountFunding: Boolean?,
    @SerializedName("google_pay") val googlePay: GooglePayConfig?,
    val branding: Branding? = null
) {
    // An absent saved_cards_config reads as off, since not every session carries it.
    val allowsSavedCardRemoval: Boolean
        get() = savedCardsConfig?.allowRemoval == true

    val preselectsSavedCard: Boolean
        get() = savedCardsConfig?.preselect == true
}

internal data class Branding(
    @SerializedName("brand_color") val brandColor: String? = null
)

internal data class SavedCardsConfig(
    @SerializedName("allow_removal") val allowRemoval: Boolean?,
    val preselect: Boolean?
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

// The backend accepts a submission without an opt-in group even when its fields are required.
internal data class FieldGroup(
    val key: String,
    val label: String?,
    @SerializedName("labels") val labels: Map<String, String>? = null,
    @SerializedName("opt_in") val optIn: Boolean? = null,
    val fields: List<FieldDefinition>?
)

internal data class FieldDefinition(
    val name: String,
    val type: String?,
    val label: String?,
    @SerializedName("labels") val labels: Map<String, String>? = null,
    val placeholder: String?,
    @SerializedName("placeholders") val placeholders: Map<String, String>? = null,
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
    val label: String?,
    @SerializedName("labels") val labels: Map<String, String>? = null
)

internal data class FieldValidation(
    val pattern: String?,
    @SerializedName("max_length") val maxLength: Int?,
    val messages: Map<String, String>?,
    // The language is the outer key here, the opposite nesting from messages.
    @SerializedName("messages_i18n") val messagesI18n: Map<String, Map<String, String>>? = null
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
    // Nullable, because Gson would otherwise write a null into a non-null property.
    @SerializedName("cardholder_name") val cardholderName: String?
)

internal object SessionStatus {
    const val OPEN = "open"
    const val COMPLETED = "completed"
    const val EXPIRED = "expired"
}
