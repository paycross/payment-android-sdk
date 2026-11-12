package com.paycross.sdk

import androidx.annotation.ColorInt
import androidx.annotation.VisibleForTesting
import com.paycross.sdk.internal.api.ApiClient

internal data class PayCrossConfig(
    val environment: PayCrossEnvironment,
    @ColorInt val brandColor: Int?,
    val testCardPrefill: TestCardPrefill? = null,
    val googlePayMerchantId: String? = null,
    val appearance: PayCrossAppearance? = null,
    val locale: String? = null
) {
    internal fun effectiveTestPrefill(): TestCardPrefill? =
        testCardPrefill.takeIf { environment != PayCrossEnvironment.PRODUCTION }

    internal fun effectiveAppearance(): PayCrossAppearance? =
        appearance ?: brandColor?.let(PayCrossAppearance::brand)
}

/**
 * Test card details that prefill the card form. Ignored in [PayCrossEnvironment.PRODUCTION].
 *
 * @property expireYear Four-digit year, e.g. "2028".
 */
data class TestCardPrefill(
    val cardholderName: String = "",
    val pan: String = "",
    val expireMonth: String = "",
    val expireYear: String = "",
    val cvv: String = "",
    val saveCard: Boolean = false
)

/**
 * Entry point for the PayCross SDK. Call [init] before any other use:
 * ```
 * PayCross.init(environment = PayCrossEnvironment.PRODUCTION)
 * ```
 */
object PayCross {
    @Volatile
    private var config: PayCrossConfig? = null

    /**
     * Configures the SDK for all later payments. Call it before the first payment, for example in your Application class.
     *
     * @param brandColor **Deprecated.** Use [appearance] with `PayCrossAppearance.brand(color)`.
     *   Ignored when [appearance] is set.
     * @param testCardPrefill Ignored in production.
     * @param googlePayMerchantId Google Business Console merchant ID. Required for PRODUCTION
     *   Google Pay requests; optional in TEST.
     * @param locale BCP 47 tag, e.g. "fr" or "fr-CA", for the sheet's own copy. Null follows the
     *   payment session's locale, then the device. Applies to the sheet only.
     */
    fun init(
        environment: PayCrossEnvironment,
        @ColorInt brandColor: Int? = null,
        testCardPrefill: TestCardPrefill? = null,
        googlePayMerchantId: String? = null,
        appearance: PayCrossAppearance? = null,
        locale: String? = null
    ) {
        // ApiClient caches its base URL, so a changed environment must reset it.
        val environmentChanged = config?.environment != null && config?.environment != environment
        config = PayCrossConfig(
            environment,
            brandColor,
            testCardPrefill,
            googlePayMerchantId,
            appearance,
            locale
        )
        if (environmentChanged) {
            ApiClient.reset()
        }
    }

    internal fun requireConfig(): PayCrossConfig =
        config ?: throw IllegalStateException(
            "PayCross.init() must be called before using the SDK"
        )

    internal fun getConfigOrNull(): PayCrossConfig? = config

    @VisibleForTesting
    internal fun reset() {
        config = null
        ApiClient.reset()
    }
}
