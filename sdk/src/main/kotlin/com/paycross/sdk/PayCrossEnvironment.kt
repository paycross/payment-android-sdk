package com.paycross.sdk

/**
 * Environment that the SDK sends its API requests to.
 *
 * @property baseUrl Base URL of the API in this environment.
 */
enum class PayCrossEnvironment(val baseUrl: String) {
    /** Test environment for development. */
    STAGING("https://checkout.test-pay-cross.com/api"),

    /** Live environment for real payments. */
    PRODUCTION("https://checkout.pay-cross.com/api")
}
