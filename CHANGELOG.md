# Changelog

All notable changes to `com.pay-cross:paycross-android` are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Releases before 0.3.2 predate this file; they are recorded as `v*` git tags.

## [Unreleased]

### Changed — binary-incompatible, and the next release is a MINOR bump

- `PayCross.init` gains a fifth parameter, `appearance: PayCrossAppearance?`,
  appended with a default. Kotlin source is unaffected: every existing call
  still compiles, including one that passes `brandColor`. What changes is the
  ABI and the Java surface. The four-argument descriptor and the old
  `init$default` are gone, so merchant code compiled against 0.6.0 must be
  **recompiled** rather than swapped in, and Java callers — which have no
  default arguments — must pass the extra argument.

### Added

- `PayCrossAppearance` themes the payment sheet: a `PayCrossColors` palette per
  mode with ten roles (`brand`, `onBrand`, `surface`, `component`,
  `componentBorder`, `text`, `textSecondary`, `placeholder`, `icon`, `error`), a
  `ThemeMode` of `SYSTEM`, `LIGHT` or `DARK`, `PayCrossShapes` for the corner
  radii and the field border width, `PayCrossPrimaryButton` for the Pay button,
  and a `PayCrossTypography` size scale clamped to 0.8–1.3.

  Every role is nullable and null means the next source down, so an empty
  appearance changes nothing and one colour is a complete configuration.
  `PayCrossAppearance.brand(color)` is the one-liner: that colour in both modes,
  platform defaults for the rest.

  A pinned mode applies to the sheet's own window and never to the host app.
  Layout, the card fields' internals, the wallet buttons' colours and labels,
  the 3-D Secure page and the error copy stay fixed by design; a merchant logo
  and a custom font family are deferred to a later release.

- The sheet picks up the brand colour the merchant set in the back office, with
  no code at all. Core publishes it into the session blob as
  `branding.brand_color` and the sheet reads it as the default `brand`. An
  appearance set in code wins, per role; a colour the SDK cannot parse costs the
  colour and nothing else; and every session minted before core started
  publishing the key decodes exactly as it does today.

### Deprecated

- `brandColor` on `PayCross.init`, in favour of
  `appearance = PayCrossAppearance.brand(color)`, which sets the same colour in
  both modes and opens the rest of the palette. It still works, still brands the
  sheet when no appearance is given, and loses to one when both are set. Kotlin
  cannot mark a single value parameter deprecated, so the parameter's KDoc and
  this entry are the deprecation; it will be removed a minor release from now.

### Fixed

- A corner radius, height or border width that is not a number no longer
  crashes the sheet. `Float.NaN` survives both `coerceIn` and the `Dp`
  constructor, and `Dp.roundToPx` throws on it rather than rounding, which the
  Google Pay button's radius reached. Every float the appearance accepts is now
  checked: a radius, height or thickness that is infinite, NaN or negative is
  ignored, and a size scale that is infinite or NaN falls back to 1 rather than
  clamping to the end of the range.

- A Pay button given a background and no label colour keeps a readable label.
  It used to hold whatever colour the brand derived, so a white button drew
  white text on it. The label is now derived from the button's own background
  when the merchant does not name one.

- A brand colour written with non-ASCII digits is refused rather than resolved
  to a colour nobody asked for. `Char.isDigit` is Unicode-wide and
  `Long.parseLong` reads those digits by value, so `#٣٣٣` used to parse.

- Text the sheet draws without an explicit colour is no longer black in dark
  mode. Material leaves `LocalContentColor` black until a `Surface` sets it, and
  the sheet's root was a plain `Box`, so under the dark mode that shipped in
  0.5.0 the amount header, the saved-card titles, "Use a new card", the
  save-card label, the CVV prompt, the field-group labels and the processing
  overlay's text were all painted black on a dark ground. The sheet now draws on
  a `Surface`, which is also what carries a merchant's `surface` colour to the
  window rather than leaving it framed by a system-coloured band.

- The Google Pay button follows the sheet's mode. `ButtonTheme.DARK` was
  hardcoded, so under the dark mode that shipped in 0.5.0 the button kept
  Google's dark variant and all but disappeared into the surface behind it.
  Google pairs a dark button with a light surface and a light button with a dark
  one, which is now what the sheet asks for.

## [0.6.0] - 2026-09-05

### Changed — binary-incompatible, and the next release is a MINOR bump

- `PayCrossResult.Success` gains a fifth member, `savedCardToken: String?`. It is
  the token for a card this payment stored, for charging that card again later,
  and it is null on every other success — including a payment made with a card
  that was already stored.

  Kotlin source is unaffected: the member is appended with a default, so
  four-argument construction still compiles and destructuring the four original
  components still works. What changes is the ABI. The four-argument constructor
  and the old `copy` descriptors are gone, so merchant code compiled against
  0.5.0 must be **recompiled** against this release rather than swapped in. Java
  code that constructs a `Success` — test doubles, mostly — needs the extra
  argument, since Java has no default arguments.

### Added

- The saved-card picker can remove a card. When the session carries
  `saved_cards_config.allow_removal`, every stored card gets a delete button that
  raises a confirmation before anything happens; confirming calls
  `DELETE /saved-cards/{uuid}` with the session bearer and drops the card from
  the sheet. A 404 drops it too: the card is not this customer's, either because
  it is already gone or because this session was never allowed to touch it, and
  neither reading justifies still offering it. An unauthorized or transient
  failure keeps the card and shows the error banner, since neither says anything
  about whether the card is still there. Removal is refused while a payment is in
  flight, and a second confirm for a card whose removal has not come back yet is
  ignored. Only the sheet's own list is rewritten: the session blob is written
  once at session creation, so reloading the same session lists the card again
  even after the server has disabled it.

- The first stored card can start selected, when the session carries
  `saved_cards_config.preselect`. Off by default, and off for every session
  minted before the backend shipped the key. It is a merchant opt-in rather than
  the default because a preselected card is one unnoticed tap from a charge; what
  makes it safe is that the CVV stays mandatory for a stored card, so the tap
  alone cannot pay.

- The picker is now a list of selectable rows — brand, `•••• 1234` and the
  expiry, then "Use a new card" — instead of a dropdown. A per-row delete button
  and the dialog it raises do not belong inside a menu that closes on the first
  touch, and the iOS sheet already draws the same list.

### Fixed

- A saved card whose `cardholder_name` is null no longer risks a null in a
  non-null field. The column is nullable in core and the value was passed
  straight through into a non-null Kotlin property, which Gson will write anyway;
  the crash then landed at the first read rather than at the decode.

## [0.5.0] - 2026-09-05

### Changed — binary-incompatible, and the next release is a MINOR bump

- `PayCrossResult.Pending(transactionId, reason)` is a new member of the
  `PayCrossResult` sealed class, so an exhaustive `when (result)` in merchant
  code needs a branch for it. It means the SDK never learned the outcome: the
  payment MAY have succeeded, so the transaction must be reconciled
  server-side against `transactionId` before charging again. Previously this
  arrived as `Failure(transactionId, Recovery.VERIFY_BEFORE_RETRY)`, which
  merchant code that branches on `Failure` reads as a decline — the one
  outcome where treating it as one can charge a shopper twice.

- `Recovery.VERIFY_BEFORE_RETRY` is no longer carried by a `Failure`. The
  member stays in the enum, and `Recovery.fromString("verify_before_retry")`
  still returns it, so the wire value parses and round-trips; but no result the
  SDK produces holds it any more. Both routes to it now end in `Pending`: the
  SDK's own poll deadline as `PendingReason.POLL_TIMEOUT`, and a failed status
  carrying `recovery: verify_before_retry` as `PendingReason.SERVER_VERIFY`.
  Code that matched `Recovery.VERIFY_BEFORE_RETRY` on a `Failure` still
  compiles and is now dead; move it to the `Pending` branch.

- `PendingReason` is a new public enum: `POLL_TIMEOUT`, `RESULT_LOST`,
  `SERVER_VERIFY`. Each member's `wireName` is the value that crosses the
  platform boundary, shared verbatim with the iOS SDK and the Flutter plugin.
  `RESULT_LOST` is produced only by the Flutter plugin, for a result that was
  created but lost before it reached the host app; the native SDK never
  returns it.

### Fixed

- The payment sheet follows the system dark mode. It drew a light Material
  scheme whatever the device was set to, so a shopper in night mode got a white
  sheet over a dark host app. Both the activity's window and the Compose
  content switch now, and a merchant's brand colour still overrides the primary
  in either mode.

- The pay button's label and spinner are no longer always white. Both were
  hardcoded, so a light brand colour left near-white content on a near-white
  button, unreadable and well under the 4.5:1 contrast minimum. The label
  colour is now derived from the brand colour's luminance, at the WCAG
  crossover where black and white contrast equally, and the spinner follows
  the button's content colour.

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

[Unreleased]: https://github.com/paycross/payment-android-sdk/compare/v0.6.0...HEAD
[0.6.0]: https://github.com/paycross/payment-android-sdk/compare/v0.5.0...v0.6.0
[0.5.0]: https://github.com/paycross/payment-android-sdk/compare/v0.4.0...v0.5.0
[0.4.0]: https://github.com/paycross/payment-android-sdk/compare/v0.3.4...v0.4.0
[0.3.4]: https://github.com/paycross/payment-android-sdk/compare/v0.3.3...v0.3.4
[0.3.3]: https://github.com/paycross/payment-android-sdk/compare/v0.3.2...v0.3.3
[0.3.2]: https://github.com/paycross/payment-android-sdk/compare/v0.3.1...v0.3.2
