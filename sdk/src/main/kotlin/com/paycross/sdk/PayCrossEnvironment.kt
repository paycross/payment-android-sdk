package com.paycross.sdk

/**
 * Represents the target environment for PayCross SDK operations.
 *
 * @property baseUrl The base URL for API requests in this environment.
 */
enum class PayCrossEnvironment(val baseUrl: String) {
    /**
     * Staging environment for testing and development.
     */
    STAGING("https://checkout.test-pay-cross.com"),

    /**
     * Production environment for live transactions.
     */
    PRODUCTION("https://checkout.pay-cross.com")
}
