package com.paycross.sdk

import androidx.annotation.ColorInt
import androidx.annotation.VisibleForTesting

/**
 * Configuration for the PayCross SDK.
 *
 * @property environment The target environment for API requests.
 * @property brandColor Optional brand color for UI customization (ARGB format).
 * @property testCardPrefill Optional card-form prefill for test runs.
 */
data class PayCrossConfig(
    val environment: PayCrossEnvironment,
    @ColorInt val brandColor: Int?,
    val testCardPrefill: TestCardPrefill? = null
) {
    internal fun effectiveTestPrefill(): TestCardPrefill? =
        testCardPrefill.takeIf { environment != PayCrossEnvironment.PRODUCTION }
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
     * @param brandColor Optional brand color for UI customization (ARGB format).
     * @param testCardPrefill Optional card-form prefill for test runs; ignored in production.
     */
    fun init(
        environment: PayCrossEnvironment,
        @ColorInt brandColor: Int? = null,
        testCardPrefill: TestCardPrefill? = null
    ) {
        config = PayCrossConfig(environment, brandColor, testCardPrefill)
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
     * Returns the current configuration or null if not initialized.
     * For testing purposes only.
     */
    @VisibleForTesting
    internal fun getConfigOrNull(): PayCrossConfig? = config

    /**
     * Resets the SDK state. For testing purposes only.
     */
    @VisibleForTesting
    internal fun reset() {
        config = null
    }
}
