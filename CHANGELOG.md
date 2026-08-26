# Changelog

All notable changes to `com.pay-cross:paycross-android` are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Releases before 0.3.2 predate this file; they are recorded as `v*` git tags.

## [0.3.2] - Unreleased

### Removed

- The SDK no longer contacts `api.ipify.org`; the backend derives the client IP
  from the connection. Privacy: no third-party hosts are contacted.

[0.3.2]: https://github.com/paycross/payment-android-sdk/compare/v0.3.1...v0.3.2
