# PayCross Android SDK

Card payments, 3-D Secure v2, saved cards and Google Pay for Android, as a
drop-in sheet. Your app hands it a payment session token and gets a result back.

## Install

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.pay-cross:paycross-android:0.8.5")
}
```

```groovy
// build.gradle
dependencies {
    implementation 'com.pay-cross:paycross-android:0.8.5'
}
```

| | |
|---|---|
| `minSdk` | 24 |
| Language | Kotlin |
| UI | Jetpack Compose, Material 3 |

## Quickstart

```kotlin
PayCross.init(environment = PayCrossEnvironment.PRODUCTION) // Application.onCreate()
// Then, in your Activity or Fragment:
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

## Documentation

- **[Android guide](https://developers.pay-cross.com/guides/android/)** — appearance,
  languages, saved cards, Google Pay, test identifiers and accessibility.
- **[API reference](https://developers.pay-cross.com/reference/android/)** — every public type.
- **[Changelog](https://developers.pay-cross.com/resources/changelogs/android/)** —
  built from this repo's [`CHANGELOG.md`](CHANGELOG.md) at the latest released version.
- **[Support](https://developers.pay-cross.com/resources/support/)** — questions go to support@pay-cross.com.

The SDK's internals and its wire contract with the PayCross backend are written
up for maintainers in [`docs/internal/`](docs/internal/README.md).

## Contributing and security

Report a vulnerability the way [`SECURITY.md`](SECURITY.md) describes, never as a
public issue.
