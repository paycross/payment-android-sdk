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

The full API and the design behind it are in [`docs/API.md`](docs/API.md) and
[`docs/DESIGN.md`](docs/DESIGN.md).

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
your `values-*` folders is read, and your string still beats ours inside it.

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

See the UI Customization section of [`docs/DESIGN.md`](docs/DESIGN.md) for the
colour roles and the precedence rules.

## Changelog

[`CHANGELOG.md`](CHANGELOG.md).
