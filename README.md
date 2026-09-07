# PayCross Android SDK

Card payments, 3-D Secure v2, saved cards and Google Pay for Android, as a
drop-in sheet. Your app hands it a payment session token and gets a result back.

## Install

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.pay-cross:paycross-android:0.7.0")
}
```

| | |
|---|---|
| `minSdk` | 24 |
| Language | Kotlin |
| UI | Jetpack Compose, Material 3 |

## Quickstart

Initialize once, before anything else touches the SDK:

```kotlin
// Application.onCreate()
PayCross.init(environment = PayCrossEnvironment.PRODUCTION)
```

Launch the sheet with a session token minted by your backend, and read the
result:

```kotlin
private val payment = registerForActivityResult(PayCrossContract()) { result ->
    when (result) {
        is PayCrossResult.Success -> // charged; result.transactionId
        is PayCrossResult.Failure -> // declined; result.recovery says what to offer
        is PayCrossResult.Pending -> // outcome unknown; resolve out of band
        is PayCrossResult.Cancelled -> // the shopper backed out
    }
}

payment.launch(sessionToken)
```

The full integration guide is the developer portal at
<https://docs.pay-cross.com>. The SDK's internals and its wire contract with the
PayCross backend are written up for maintainers in
[`docs/internal/`](docs/internal/README.md).

## Languages

The sheet's own copy ships in:

| Language | Tag |
|---|---|
| English | `en` |
| French | `fr` |

The language is the first of these the SDK ships strings for: the merchant's
override, then the payment session's `locale`, then the device, then English.
Each is matched on its own — the whole tag, then its primary subtag, so `fr-CA`
gets French — and one that matches nothing falls through to the next rather than
ending the ladder.

The **amount** is formatted separately, with the first locale anyone named,
whichever language that is. A German handset reads an English sheet over a
`12,34 €` amount rather than losing its own number formatting to a language the
SDK has no words for.

Pin it yourself when your app already knows the shopper's language:

```kotlin
PayCross.init(
    environment = PayCrossEnvironment.PRODUCTION,
    locale = "fr"
)
```

That applies to the payment sheet only. Your app's own language is untouched.

### Changing the wording

Every string the sheet draws is a public `paycross_*` resource key. Declare the
same key in your app and yours wins:

```xml
<string name="paycross_pay_amount">Donate %1$s</string>
```

**Setting `locale` does not turn your overrides off** — the locale picks which of
your `values-*` folders is read, and your string still beats ours inside it. Note
that `locale` can only name a language the SDK ships, so it will not reach a
`values-de` of your own; your German strings are still used when the device or
the session selects German.

The full key list, what each one paints, and how to add a language are in
[`LOCALIZATION.md`](LOCALIZATION.md).

## Appearance

`PayCrossAppearance` themes the sheet: a colour palette per mode, a pinned or
system-following theme mode, corner radii, the Pay button and a type scale.
Every role is nullable, so one colour is a complete configuration:

```kotlin
PayCross.init(
    environment = PayCrossEnvironment.PRODUCTION,
    appearance = PayCrossAppearance.brand(0xFF1E88E5.toInt())
)
```

See the UI Customization section of
[`docs/internal/DESIGN.md`](docs/internal/DESIGN.md) for the colour roles and
the precedence rules.

## Test identifiers

Every element the sheet draws carries a stable identifier, and it is the same
string on iOS, so one selector in your UI tests finds the same element on both
platforms.

| Element | Identifier |
|---|---|
| The sheet itself | `paycross.sheet` |
| Amount | `paycross.amount` |
| Google Pay button | `paycross.walletButton` |
| "Or pay with card" rule | `paycross.walletDivider` |
| Saved-card list | `paycross.savedCards` |
| One saved card | `paycross.savedCard.<uuid>` |
| Its delete button | `paycross.savedCard.<uuid>.delete` |
| "Use a new card" | `paycross.useNewCard` |
| Card number | `paycross.cardNumber` |
| Expiry | `paycross.expiry` |
| CVV | `paycross.cvv` |
| Cardholder name | `paycross.cardholderName` |
| Save-card toggle | `paycross.saveCard` |
| One of your configured fields | `paycross.field.<group>.<name>` |
| That field's validation message | `paycross.field.<group>.<name>.error` |
| Error banner | `paycross.errorBanner` |
| Pay button | `paycross.payButton` |
| Loading overlay | `paycross.loading` |
| 3-D Secure challenge | `paycross.threeDS` |
| Cancel dialog | `paycross.cancelDialog` |
| Its two buttons | `paycross.cancelConfirm`, `paycross.cancelDismiss` |
| Remove-card dialog | `paycross.removeDialog` |
| Its two buttons | `paycross.removeConfirm`, `paycross.removeDismiss` |

`<uuid>` is the saved card's `uuid` as the session sends it. iOS exposes the same
value as `card.id`, so one identifier addresses the same card on both platforms.

`<group>` and `<name>` are the group key and field name from the session, joined
with dots because iOS builds the same string. **Neither may contain a dot or a
space**: a field called `city.error` in group `billing` would produce the
identifier of `city`'s error node, and a space would put a space in a resource
id. Field keys are yours to choose in the back office, so choose them without.

Three identifiers in the shared scheme have no Android element behind them:

- `paycross.brand` — iOS draws a brand badge beside the card number; Android
  draws none.
- `paycross.threeDSCancel` — iOS puts a Cancel button over the 3-D Secure
  challenge; on Android the challenge is left with the system back gesture.
- `paycross.cancel` — iOS has a Cancel button in the sheet's chrome. The Android
  sheet has no close control of its own: it is cancelled with the system back
  gesture, and the SDK catches that to raise the cancel dialog. To cancel from a
  test, press back and then use `paycross.cancelConfirm`.

### These are a debug-build contract

The identifiers reach a UiAutomator or Espresso tree as resource ids **only when
the host app is debuggable**. In a release build the sheet publishes none of
them, because they would hand its structure, and a stored card's uuid with it, to
any accessibility service on the device.

So drive the sheet from a debuggable build of your app. `androidTest` already
builds against the debug variant, so an ordinary instrumented test needs nothing
extra. If your suite runs against a release build, give it a debuggable variant
of its own rather than expecting the ids to appear.

### The two identifiers that wrap a view

`paycross.walletButton` and `paycross.threeDS` name a box the SDK draws around
something it does not own — Google's `PayButton` and the 3-D Secure `WebView`.
The identifier is on the box, because a resource id is published for the SDK's
own elements and a hosted view brings its own, id-less, node instead. The box is
laid out to the wrapped view's exact bounds, so it is the handle to use:

- **Find both by id**, from a Compose test and from a UiAutomator dump alike.
- **Tap `paycross.walletButton` by id.** The tap lands inside Google's button,
  which is what a tap on the button is. Google's own node carries no id and
  renders its label in its own language; if you would rather address the button
  itself, its content description is the only handle it offers.
- **Match the box by id alone.** It holds no click handler of its own — the tap
  falls through to Google's button — so it is reported as `clickable="false"` in
  a dump, and the clickable node is the one inside it. A selector that asks for
  the id *and* `clickable` will find nothing.
- **Do not look for the challenge's contents under `paycross.threeDS`.** What
  the box holds is the issuer's page, rendered by the WebView. Address the
  fields and buttons in it as the issuer names them.

### The field-group error

`paycross.field.<group>.<name>.error` sits inside its field's merged
accessibility node, so a Compose test finds it with `useUnmergedTree = true` and
a UiAutomator dump does not see it at all. From UiAutomator, read the field's own
error state instead.

## Accessibility

The sheet holds to a floor, and the instrumented suite asserts each line of it:

- **Every control has a name.** Card fields, the save-card toggle, the Pay
  button, the delete buttons, the loading overlay. The Pay button keeps its name
  while the spinner is up, when the label is not on screen. Names come from the
  same `paycross_*` resources as everything else, so overriding a string changes
  what is spoken too.
- **A decline is announced, and so is a wait.** The error banner and the busy
  overlay are both polite live regions: a screen reader reads each when it
  appears, without interrupting a shopper who is mid-correction in a field.
- **Colour is never the only signal.** The banner draws a warning glyph beside
  the message.
- **A field speaks its label, its name and its error together**, as one node,
  rather than replacing one with another.
- **The text size is honoured.** Nothing is pinned to a size that crops it: the
  Pay button and the saved-card CVV box take a minimum and grow. A button height
  you set through `PayCrossAppearance` is read as a minimum for the same reason.
- **Touch targets are at least 48dp.**

The amount is marked as a heading, so a screen reader can jump to the top of the
sheet.

## Changelog

[`CHANGELOG.md`](CHANGELOG.md).
