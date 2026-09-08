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
    /**
     * Whether the sheet may offer to delete a stored card. Both this and
     * [preselectsSavedCard] read an absent config as off: `saved_cards_config` is
     * a sibling the backend added after these sessions' shape was fixed, so every
     * session minted before it, and every merchant who did not opt in, arrives
     * without the object at all.
     */
    val allowsSavedCardRemoval: Boolean
        get() = savedCardsConfig?.allowRemoval == true

    /** Whether the first stored card starts selected. Merchant opt-in. */
    val preselectsSavedCard: Boolean
        get() = savedCardsConfig?.preselect == true
}

/**
 * The merchant's back-office branding, as far as a native sheet reads it.
 *
 * Defaulted and nullable all the way down: the key arrives only on sessions
 * minted after core started publishing it, and a colour the back office cannot
 * express should cost the colour rather than the session.
 */
internal data class Branding(
    @SerializedName("brand_color") val brandColor: String? = null
)

/**
 * Merchant opt-ins that govern the saved-card picker, carried alongside
 * `saved_cards` rather than inside it: the list is a flat array on the wire and
 * has nowhere to hang a flag.
 */
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

internal data class FieldGroup(
    val key: String,
    val label: String?,
    @SerializedName("labels") val labels: Map<String, String>? = null,
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
    // Language outer, rule inner - the opposite nesting from `messages`, which
    // is one language's rules on their own.
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
    // Nullable because the column is: core projects cardholder_name straight
    // out of customer_saved_cards, and Gson will happily write a null into a
    // non-null Kotlin property, so the crash lands at the first read instead of
    // at the decode.
    @SerializedName("cardholder_name") val cardholderName: String?
)

internal object SessionStatus {
    const val OPEN = "open"
    const val COMPLETED = "completed"
    const val EXPIRED = "expired"
}
