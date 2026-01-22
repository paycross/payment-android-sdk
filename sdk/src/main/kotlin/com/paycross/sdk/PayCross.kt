package com.paycross.sdk

import androidx.annotation.ColorInt
import androidx.annotation.VisibleForTesting

/**
 * Configuration for the PayCross SDK.
 *
 * @property environment The target environment for API requests.
 * @property brandColor Optional brand color for UI customization (ARGB format).
 */
data class PayCrossConfig(
    val environment: PayCrossEnvironment,
    @ColorInt val brandColor: Int?
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
     */
    fun init(
        environment: PayCrossEnvironment,
        @ColorInt brandColor: Int? = null
    ) {
        config = PayCrossConfig(environment, brandColor)
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
