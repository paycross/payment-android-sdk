package com.paycross.sdk

import androidx.annotation.ColorInt
import androidx.annotation.VisibleForTesting
import com.paycross.sdk.internal.api.ApiClient

/**
 * Configuration for the PayCross SDK.
 *
 * @property environment The target environment for API requests.
 * @property brandColor Deprecated brand color (ARGB format).
 * @property testCardPrefill Optional card-form prefill for test runs.
 * @property googlePayMerchantId Google Business Console merchant ID for Google Pay.
 * @property appearance How the payment sheet looks.
 * @property locale BCP 47 tag pinning the sheet's language, or null to follow
 *   the session and then the device.
 */
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

    /**
     * The appearance the sheet draws with. A merchant still passing the
     * deprecated [brandColor] gets the same colour through the new model, and
     * an appearance wins when both are set.
     */
    internal fun effectiveAppearance(): PayCrossAppearance? =
        appearance ?: brandColor?.let(PayCrossAppearance::brand)
}

/**
 * Prefills the card form with test card details so manual test runs don't
 * require retyping them. Ignored in [PayCrossEnvironment.PRODUCTION].
 *
 * @property expireYear Four-digit year (e.g. "2028").
 * @property saveCard Pre-ticks the "save card" checkbox when the session allows saving.
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
 * Main entry point for the PayCross SDK.
 *
 * Initialize the SDK in your Application class or before any SDK usage:
 * ```
 * PayCross.init(environment = PayCrossEnvironment.PRODUCTION)
 * ```
 */
object PayCross {
    @Volatile
    private var config: PayCrossConfig? = null

    /**
     * Initializes the PayCross SDK with the specified configuration.
     *
     * @param environment The target environment for API requests.
     * @param brandColor **Deprecated.** Use `appearance = PayCrossAppearance.brand(color)`,
     *   which sets the same colour in both light and dark and opens the rest of the palette.
     *   Still honoured, and still the sheet's brand when no [appearance] is given; ignored
     *   when one is. It will be removed a minor release from now. Kotlin cannot mark a single
     *   parameter deprecated, so this note is the deprecation.
     * @param testCardPrefill Optional card-form prefill for test runs; ignored in production.
     * @param googlePayMerchantId Google Business Console merchant ID. Google requires it in
     *   merchantInfo for PRODUCTION Google Pay requests; the TEST environment works without
     *   one, so it is optional and simply omitted from the request when null.
     * @param appearance How the payment sheet looks: colours per mode, theme mode, shapes,
     *   the Pay button and a size scale. Null keeps the platform defaults, and the merchant's
     *   back-office brand colour still applies underneath.
     * @param locale BCP 47 language tag pinning the sheet's own copy, e.g. "fr" or "fr-CA".
     *   Null, the default, follows the payment session's `locale` and then the device.
     *   A tag the SDK ships no strings for is ignored rather than rejected, and a tag set
     *   here never disables the merchant's own `paycross_*` string overrides. Applies to
     *   the sheet's window only; the host app's language is untouched.
     */
    fun init(
        environment: PayCrossEnvironment,
        @ColorInt brandColor: Int? = null,
        testCardPrefill: TestCardPrefill? = null,
        googlePayMerchantId: String? = null,
        appearance: PayCrossAppearance? = null,
        locale: String? = null
    ) {
        // The API client caches its base URL from the config it was built with,
        // so a changed environment has to invalidate it.
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

    /**
     * Returns the current configuration or throws if not initialized.
     *
     * @throws IllegalStateException if [init] has not been called.
     */
    internal fun requireConfig(): PayCrossConfig =
        config ?: throw IllegalStateException(
            "PayCross.init() must be called before using the SDK"
        )

    /**
     * Returns the current configuration or null if not initialized. Read before
     * the sheet has anywhere to report an error to, and by tests.
     */
    internal fun getConfigOrNull(): PayCrossConfig? = config

    /**
     * Resets the SDK state. For testing purposes only.
     */
    @VisibleForTesting
    internal fun reset() {
        config = null
        ApiClient.reset()
    }
}
