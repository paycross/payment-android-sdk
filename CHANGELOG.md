# Changelog

All notable changes to `com.pay-cross:paycross-android` are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Releases before 0.3.2 predate this file; they are recorded as `v*` git tags.

## [Unreleased]

### Removed

- The SDK no longer contacts `api.ipify.org`; the backend derives the client IP
  from the connection. Privacy: no third-party hosts are contacted.

## [0.3.2] - Unreleased

### Fixed

- Typing a card number no longer corrupts it. The card-number and expiry fields
  formatted their own value, which left the caret behind each inserted separator,
  so every digit after the fourth was entered one position too early: typing
  `4111111111170000` produced `4111 1111 1700 0011`. Grouping is now drawn with a
  `VisualTransformation` over the raw digits. Pasted and prefilled numbers were
  never affected.

[Unreleased]: https://github.com/paycross/payment-android-sdk/compare/v0.3.2...HEAD
[0.3.2]: https://github.com/paycross/payment-android-sdk/compare/v0.3.1...v0.3.2
