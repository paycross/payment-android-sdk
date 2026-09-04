# Changelog

All notable changes to `com.pay-cross:paycross-android` are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Releases before 0.3.2 predate this file; they are recorded as `v*` git tags.

## [Unreleased]

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

[Unreleased]: https://github.com/paycross/payment-android-sdk/compare/v0.3.4...HEAD
[0.3.4]: https://github.com/paycross/payment-android-sdk/compare/v0.3.3...v0.3.4
[0.3.3]: https://github.com/paycross/payment-android-sdk/compare/v0.3.2...v0.3.3
[0.3.2]: https://github.com/paycross/payment-android-sdk/compare/v0.3.1...v0.3.2
