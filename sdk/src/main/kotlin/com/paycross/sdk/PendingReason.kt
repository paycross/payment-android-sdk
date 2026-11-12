package com.paycross.sdk

/**
 * Why a [PayCrossResult.Pending] outcome is unknown.
 *
 * [wireName] values are shared with the iOS SDK and the Flutter plugin, so do not rename members.
 */
enum class PendingReason {
    /** The SDK's status poll reached its deadline without an outcome. */
    POLL_TIMEOUT,

    /** The result was lost before it reached the host app. Only the Flutter plugin produces it. */
    RESULT_LOST,

    /** The server asked to verify the transaction before retrying. */
    SERVER_VERIFY;

    /** Wire value of this member, e.g. `poll_timeout`. */
    val wireName: String get() = name.lowercase()
}
