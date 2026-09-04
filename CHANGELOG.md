# Changelog

All notable changes to `com.pay-cross:paycross-android` are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Releases before 0.3.2 predate this file; they are recorded as `v*` git tags.

## [Unreleased]

## [0.4.0] - 2026-09-04

### Changed — source-incompatible

This release carries every public-API break together, so merchants absorb one.
Each entry here names what stops compiling and what to do about it.

- `Recovery.VERIFY_BEFORE_RETRY` is a new member, meaning the SDK never
  observed the payment's outcome and the transaction must be checked before
  re-collecting. It is not retryable. An exhaustive `when (recovery)` in
  merchant code needs a branch for it.

- `Recovery.UNRECOGNIZED` replaces `DO_NOT_RETRY` as the parse result for a
  recovery value this SDK version does not know, and `PayCrossResult.Failure`
  gains `recoveryRaw` holding the server's value verbatim. Previously an
  unknown value collapsed to `DO_NOT_RETRY` and the string was gone, so it
  could not be logged or quoted in a support thread, and Android reported it
  differently from iOS for the same response. Retry behaviour is unchanged:
  `isRetryable` is a whitelist and `UNRECOGNIZED` is not on it, so unknown
  instructions still fail closed. Two breaks to absorb: a new enum member, so
  an exhaustive `when (recovery)` needs a branch; and a third property on
  `Failure`, which has a default so construction still compiles but the
  two-argument constructor is gone at the binary level. `recoveryRaw` is set
  for every server decline, not only unrecognised ones, and is null when the
  SDK raised the failure itself.

- `PayCrossResult.Cancelled` carries the last known transaction id. It was a
  `data object` and is now `data class Cancelled(val transactionId: String?)`,
  so `is PayCrossResult.Cancelled` still matches but code that used
  `PayCrossResult.Cancelled` as a value must construct one. A shopper can
  cancel after a decline or part-way through a 3-D Secure challenge, both of
  which leave a real transaction on the merchant's side, and the host app had
  no way to correlate the attempt it had just abandoned. The id is null when
  the sheet was cancelled before any transaction existed.

### Fixed

- A saved American Express card can now have its 4-digit CID entered. The
  stored-card CVV box was built at `CardType.UNKNOWN`, so it capped input at 3
  digits and validated at 3 whatever the card's brand, leaving a saved Amex
  permanently unpayable. The length now comes from the brand the card was saved
  with.

- A CVV typed for one card is no longer carried across to another. One
  form-level `cvv` backs both entry modes and the saved-card selector left it
  alone, so a shopper who typed a new card's CVV and then picked a stored card
  submitted the first card's CVV against the second card's token, over a
  prompt that already looked answered. Picking a different card now clears it,
  in both directions.

- A status poll that reaches its deadline no longer reports `Recovery.RETRY`.
  The loop treats network errors and non-2xx responses as transient and keeps
  going, which is right for a blip but indistinguishable from a network that is
  gone for good, so the loop simply ran out and the deadline branch asserted a
  retryable failure over an outcome it had never seen. Measured twice against
  payments that had succeeded server-side with liability shifted to the issuer,
  where a merchant acting on `RETRY` re-collects money already taken. The
  deadline now reports `Recovery.VERIFY_BEFORE_RETRY` and still carries the
  transaction id, which is what resolves the outcome out of band.

- The payment sheet no longer outlives the session it was opened for. A
  retryable decline re-arms the card form and ends the poll job cleanly, so the
  poll deadline stopped applying and nothing bounded the sheet afterwards: it
  was observed still offering a live Pay button 45 minutes on, against a session
  the server had already expired. The sheet now resolves with a terminal
  `Failure` carrying `Recovery.RESTART` once the session token expires, which is
  what the SDK already reports for the same condition at launch. A submit or a
  poll in flight is never cut short; its outcome stays the poll's to report.

## [0.3.4] - 2026-09-03

### Changed

- The Google Pay button is no longer hidden on account-funding sessions. The
  backend now accepts wallet payments on those sessions and forwards the
  account-funding block to the acquirer, so `account_funding` in the session
  snapshot marks the session as a transfer rather than switching wallets off.
  An explicit `wallets.google_pay: false` still hides the button.

## [0.3.3] - 2026-08-28

### Removed

- The SDK no longer contacts `api.ipify.org`; the backend derives the client IP
  from the connection. Privacy: no third-party hosts are contacted.

## [0.3.2] - 2026-08-28

### Fixed

- Typing a card number no longer corrupts it. The card-number and expiry fields
  formatted their own value, which left the caret behind each inserted separator,
  so every digit after the fourth was entered one position too early: typing
  `4111111111170000` produced `4111 1111 1700 0011`. Grouping is now drawn with a
  `VisualTransformation` over the raw digits. Pasted and prefilled numbers were
  never affected.

[Unreleased]: https://github.com/paycross/payment-android-sdk/compare/v0.4.0...HEAD
[0.4.0]: https://github.com/paycross/payment-android-sdk/compare/v0.3.4...v0.4.0
[0.3.4]: https://github.com/paycross/payment-android-sdk/compare/v0.3.3...v0.3.4
[0.3.3]: https://github.com/paycross/payment-android-sdk/compare/v0.3.2...v0.3.3
[0.3.2]: https://github.com/paycross/payment-android-sdk/compare/v0.3.1...v0.3.2
