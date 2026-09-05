package com.paycross.sdk

/**
 * Why a [PayCrossResult.Pending] outcome is unknown.
 *
 * The [wireName] of each member is shared verbatim with the iOS SDK and the
 * Flutter plugin, so renaming a member changes the vocabulary on all three.
 */
enum class PendingReason {
    /** The SDK's own status poll reached its deadline without an outcome. */
    POLL_TIMEOUT,

    /**
     * The result was produced but lost before it reached the host app. Produced
     * only by the Flutter plugin; the native SDK never returns it.
     */
    RESULT_LOST,

    /** The server said `verify_before_retry` on a failed transaction. */
    SERVER_VERIFY;

    /**
     * The value that crosses the platform boundary: `poll_timeout`,
     * `result_lost`, `server_verify`.
     *
     * Derived from the member name rather than stored beside it, so the two
     * cannot disagree.
     */
    val wireName: String get() = name.lowercase()
}
