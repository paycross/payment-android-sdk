# PayCross Android SDK Design

## Overview

Drop-in payment UI for Android apps. Merchant passes session token, SDK handles card
entry, 3DS, and status polling.

**Approach:** Pre-built UI (like iOS SDK) with WebView for 3DS challenges.

## Requirements

| Requirement | Value | Reason |
|-------------|-------|--------|
| `minSdk` | 24 (Android 7.0) | Covers 99%+ devices, modern APIs |
| UI Framework | Jetpack Compose | Less boilerplate, modern, easier theming |
| Kotlin | 1.9+ | Coroutines, sealed classes |

## Initialization

Thread-safe singleton pattern:

```kotlin
object PayCross {
    @Volatile
    private var config: PayCrossConfig? = null

    fun init(
        environment: PayCrossEnvironment,
        @ColorInt brandColor: Int? = null,
        testCardPrefill: TestCardPrefill? = null,
        googlePayMerchantId: String? = null,
        appearance: PayCrossAppearance? = null
    ) {
        config = PayCrossConfig(
            environment, brandColor, testCardPrefill, googlePayMerchantId, appearance
        )
    }

    internal fun requireConfig(): PayCrossConfig =
        config ?: throw IllegalStateException("PayCross.init() must be called first")
}
```

**Usage:**

```kotlin
// In Application.onCreate()
PayCross.init(
    environment = PayCrossEnvironment.STAGING,
    appearance = PayCrossAppearance.brand(0xFF1E88E5.toInt())  // optional
)
```

**Environments:**

| Environment | Base URL |
|-------------|----------|
| `STAGING` | `https://checkout.test-pay-cross.com/api` |
| `PRODUCTION` | `https://checkout.pay-cross.com/api` |

## Starting a Payment

```kotlin
// In Activity/Fragment
private val paymentLauncher = registerForActivityResult(PayCrossContract()) { result ->
    when (result) {
        is PayCrossResult.Success -> {
            // result.transactionId, result.status, result.amount, result.currency
        }
        is PayCrossResult.Failure -> {
            // result.recovery (RETRY, CHANGE_METHOD, RESTART, CONTACT_SUPPORT,
            //                  DO_NOT_RETRY, UNRECOGNIZED)
            // result.recoveryRaw is what the server actually sent, if anything
        }
        is PayCrossResult.Pending -> {
            // Outcome unknown. Reconcile server-side before charging again.
            // result.transactionId is what you reconcile against;
            // result.reason is POLL_TIMEOUT or SERVER_VERIFY
        }
        is PayCrossResult.Cancelled -> {
            // User closed the payment screen; result.transactionId names the
            // attempt they abandoned, or is null if there was none yet
        }
    }
}

// Launch payment
paymentLauncher.launch("eyJhbGciOiJSUzI1NiIs...")  // session token from merchant backend
```

## Payment Flow

```
1. Merchant app calls merchant backend: "Create payment for $99"

2. Merchant backend calls PayCross API: POST /api/v1/payment_sessions
   (using M2M JWT auth)

3. PayCross API returns session token to merchant backend

4. Merchant backend returns session token to merchant app

5. Merchant app calls: paymentLauncher.launch(sessionToken)

6. SDK parses JWT → extracts session_id (sub claim)

7. SDK calls: GET /session/{session_id}
   → Session status (open/completed/expired) + latest transaction
   → Field-group definitions (customer/billing inputs, prefill)
   → Saved cards list
   → Save-card config

8. SDK shows PaymentActivity:
   - Saved cards selector (if any)
   - Card entry form
   - "Save card" checkbox (if enabled)
   - Pay button with amount

9. User submits → SDK calls: POST /submit-card
   Headers: Idempotency-Key (UUID)
   Body: session token, card data, browser_info
   Response: transaction_id

10. SDK polls: GET /status/{transaction_id}
    - processing → continue polling
    - threeds_fingerprint → auto-POST in hidden WebView, continue polling
    - threeds_challenge → show WebView fullscreen, continue polling
    - success/authorized → return Success result
    - failed → return Failure result with recovery hint (fail closed on unknown)

11. Result returned via ActivityResult to merchant app
```

## Polling with ViewModel

Use ViewModel + viewModelScope for lifecycle-safe polling:

```kotlin
class PaymentViewModel(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    // Survive process death
    private var transactionId: String?
        get() = savedStateHandle["transaction_id"]
        set(value) { savedStateHandle["transaction_id"] = value }

    fun pollStatus(transactionId: String) {
        this.transactionId = transactionId

        viewModelScope.launch {
            val deadline = clock() + POLL_DEADLINE_MS

            while (clock() < deadline) {
                val status = api.getStatus(transactionId)

                when (status.status) {
                    "success", "authorized", "failed" -> {
                        _result.value = status.toResult()
                        return@launch
                    }
                    "threeds_fingerprint", "threeds_challenge" -> {
                        _threeDsAction.value = status.action
                    }
                }

                // Fixed 2s cadence, same as the checkout page
                delay(POLL_INTERVAL_MS)
            }

            // The deadline means the outcome was never observed, not that the
            // payment failed. It may have succeeded.
            _result.value = PayCrossResult.Pending(transactionId, PendingReason.POLL_TIMEOUT)
        }
    }

    // Resume polling after process death
    fun resumeIfNeeded() {
        transactionId?.let { pollStatus(it) }
    }

    companion object {
        private const val MAX_ATTEMPTS = 60  // ~3 minutes with backoff
    }
}
```

**Polling Strategy:**
- Start at 1 second interval
- Exponential backoff: 1s → 1.5s → 2.25s → ... → max 5s
- Max 60 attempts (~3 minutes total)
- Reduces server load during outages

## 3DS WebView Security

Critical security hardening for WebView:

```kotlin
class SecureWebViewFragment : Fragment() {

    private var webView: WebView? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        webView = view.findViewById<WebView>(R.id.webView).apply {
            settings.apply {
                javaScriptEnabled = true        // Required for 3DS
                allowFileAccess = false         // Security: no file access
                allowContentAccess = false      // Security: no content provider access
                domStorageEnabled = true        // Some 3DS pages need this
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            }

            webViewClient = object : WebViewClient() {
                override fun onReceivedSslError(
                    view: WebView,
                    handler: SslErrorHandler,
                    error: SslError
                ) {
                    handler.cancel()  // NEVER call proceed() in production
                    onThreeDsError("SSL certificate error")
                }

                override fun onPageFinished(view: WebView, url: String) {
                    // Check for return URL
                    if (isReturnUrl(url)) {
                        onThreeDsComplete()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        // Prevent memory leaks
        webView?.apply {
            stopLoading()
            clearHistory()
            removeAllViews()
            destroy()
        }
        webView = null
        super.onDestroyView()
    }
}
```

## Lifecycle Handling

Handle process death and long 3DS flows:

```kotlin
class PaymentActivity : ComponentActivity() {

    private val viewModel: PaymentViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState != null) {
            // Returning from process death
            viewModel.resumeIfNeeded()
        }

        // Observe results
        viewModel.result.observe(this) { result ->
            setResult(RESULT_OK, Intent().putExtra("result", result))
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        // Check if session still valid after being backgrounded
        viewModel.validateSessionIfNeeded()
    }
}
```

**Edge Cases:**

| Scenario | SDK Behavior |
|----------|--------------|
| Process death during 3DS | Resume polling via SavedStateHandle |
| App backgrounded 10+ min | Check session validity on resume, return RESTART if expired |
| Screen rotation | ViewModel survives, no data loss |
| Back button during 3DS | Show confirmation dialog |

## UI Components

| Screen | Purpose |
|--------|---------|
| `PaymentActivity` | Main container, handles result |
| `CardFormScreen` | Compose: card entry + saved cards |
| `WebViewFragment` | 3DS fingerprint/challenge |
| `LoadingOverlay` | Compose: processing state |

**Card Form Layout:**

```
┌─────────────────────────────────┐
│  ◉ Visa •••• 0366          🗑   │  ← One row per saved card; the bin
│    Expires 12/30                │    appears only with allow_removal
│  ○ Mastercard •••• 4444    🗑   │
│    Expires 01/29                │
│  ○ Use a new card               │
├─────────────────────────────────┤
│  Card Number                    │
│  ┌─────────────────────────┐    │
│  │ 4532 0100 0000 0366  💳 │    │  ← Auto-formatted with spaces
│  └─────────────────────────┘    │
│                                 │
│  Expiry          CVV           │
│  ┌──────────┐   ┌──────────┐   │
│  │ 12/25    │   │ •••      │   │  ← Auto-formatted MM/YY
│  └──────────┘   └──────────┘   │
│                                 │
│  Cardholder Name               │
│  ┌─────────────────────────┐   │
│  │ JOHN DOE                │   │  ← Auto-uppercase
│  └─────────────────────────┘   │
│                                 │
│  ☐ Save card for future use    │
│                                 │
│  ┌─────────────────────────┐   │
│  │        PAY €99.99       │   │ ← brandColor
│  └─────────────────────────┘   │
└─────────────────────────────────┘
```

**Card Input Formatting:**
- Card number: spaces every 4 digits (4532 0100 0000 0366)
- Expiry: auto-insert slash (12/25)
- Cardholder: auto-uppercase
- Card type icon updates based on BIN

## Accessibility

All inputs must be accessible:

```kotlin
@Composable
fun CardNumberField(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = formatCardNumber(value),
        onValueChange = { onValueChange(it.filter { c -> c.isDigit() }) },
        label = { Text("Card Number") },
        modifier = Modifier.semantics {
            contentDescription = "Card number input field"
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}
```

Requirements:
- `contentDescription` on all inputs
- Sufficient color contrast (WCAG AA)
- Support for TalkBack screen reader
- Minimum touch target size (48dp)

## Module Structure

**Package:** `com.paycross.sdk`

```
paycross-android-sdk/
├── src/main/kotlin/com/paycross/sdk/
│   ├── PayCross.kt                 # Public API (init, config)
│   ├── PayCrossContract.kt         # ActivityResultContract
│   ├── PayCrossResult.kt           # Success/Failure/Cancelled
│   ├── PayCrossEnvironment.kt      # STAGING/PRODUCTION enum
│   │
│   ├── internal/
│   │   ├── api/
│   │   │   ├── PayCrossApi.kt      # Retrofit interface
│   │   │   ├── models/             # Request/Response DTOs
│   │   │   └── JwtParser.kt        # Parse session token
│   │   │
│   │   ├── ui/
│   │   │   ├── PaymentActivity.kt
│   │   │   ├── PaymentViewModel.kt
│   │   │   ├── CardFormScreen.kt   # Compose
│   │   │   ├── WebViewFragment.kt
│   │   │   └── components/         # Compose components
│   │   │
│   │   ├── validation/
│   │   │   ├── CardValidator.kt    # Luhn, expiry, CVV
│   │   │   └── CardType.kt         # Visa, MC, Amex detection
│   │   │
│   │   └── util/
│   │       ├── BrowserInfoProvider.kt
│   │       └── IdempotencyKey.kt
│   │
│   └── res/
│       ├── layout/                 # WebView only
│       └── values/
│
├── build.gradle.kts
├── proguard-rules.pro
└── consumer-rules.pro
```

**Dependencies:**
- Retrofit + OkHttp (networking)
- Kotlin Coroutines (async)
- Jetpack Compose (UI)
- AndroidX Lifecycle (ViewModel, SavedStateHandle)

## ProGuard Rules

Required for release builds (`consumer-rules.pro` - auto-applied to consumers):

```proguard
# Keep Retrofit DTOs
-keep class com.paycross.sdk.internal.api.models.** { *; }

# Keep result classes (passed via Intent)
-keep class com.paycross.sdk.PayCrossResult { *; }
-keep class com.paycross.sdk.PayCrossResult$* { *; }
-keep enum com.paycross.sdk.Recovery { *; }
-keep enum com.paycross.sdk.PendingReason { *; }

# Retrofit
-keepattributes Signature
-keepattributes *Annotation*
```

## Result Types

```kotlin
sealed class PayCrossResult : Parcelable {
    @Parcelize
    data class Success(
        val transactionId: String,
        val status: String,        // "success" or "authorized"
        val amount: Long,
        val currency: String
    ) : PayCrossResult()

    @Parcelize
    data class Failure(
        val transactionId: String?,
        val recovery: Recovery,
        val recoveryRaw: String? = null  // the server's own value, for support
    ) : PayCrossResult()

    // The outcome was never observed. NOT a decline: the payment may have
    // succeeded, so reconcile on transactionId before charging again.
    @Parcelize
    data class Pending(
        val transactionId: String?,
        val reason: PendingReason
    ) : PayCrossResult()

    @Parcelize
    data class Cancelled(
        val transactionId: String?   // last known attempt, null if none
    ) : PayCrossResult()
}

enum class Recovery {
    RETRY,           // "Try again"
    CHANGE_METHOD,   // "Use a different card"
    RESTART,         // "Start over" (session expired)
    CONTACT_SUPPORT, // "Contact support"
    DO_NOT_RETRY,    // Terminal decline
    VERIFY_BEFORE_RETRY, // Parses off the wire, but never reaches a Failure:
                         // an unknown outcome is reported as Pending instead
    UNRECOGNIZED     // Server value this version cannot read; see recoveryRaw
}

enum class PendingReason {
    POLL_TIMEOUT,    // The SDK's own status poll reached its deadline
    RESULT_LOST,     // Produced only by the Flutter plugin, never natively
    SERVER_VERIFY;   // The server said verify_before_retry on a failed status

    // What crosses the platform boundary, shared verbatim with iOS and the
    // Flutter plugin: poll_timeout, result_lost, server_verify.
    val wireName: String get() = name.lowercase()
}
```

## Error Handling

**Recovery Actions:**

| Recovery | User Message | When Used |
|----------|--------------|-----------|
| `RETRY` | "Try again" | Timeout, gateway error, issuer unavailable |
| `CHANGE_METHOD` | "Use a different card" | Declined, insufficient funds, expired, fraud, 3DS failed |
| `RESTART` | "Start over" | Duplicate transaction, session expired, abandoned |
| `CONTACT_SUPPORT` | "Contact support" | Invalid request, merchant config issue |
| `DO_NOT_RETRY` | Dead-end message | Terminal decline (stolen card, do-not-honor) |
| `UNRECOGNIZED` | Dead-end message | The server sent a value this SDK version does not know. Fails closed like a terminal decline; the value itself is on `Failure.recoveryRaw` |
| `VERIFY_BEFORE_RETRY` | n/a | Never carried by a `Failure`. It parses off the wire so the value round-trips, but the outcome it describes is reported as `PayCrossResult.Pending` |

Only `RETRY` and `CHANGE_METHOD` re-arm the payment form (`recovery.isRetryable`).

**Pending Reasons:**

| Reason | Wire name | User Message | When Used |
|--------|-----------|--------------|-----------|
| `POLL_TIMEOUT` | `poll_timeout` | "We could not confirm this payment" | The SDK's status poll reached its deadline without an outcome |
| `RESULT_LOST` | `result_lost` | "We could not confirm this payment" | The result was produced but lost before reaching the host app. Flutter plugin only; the native SDK never returns it |
| `SERVER_VERIFY` | `server_verify` | "We could not confirm this payment" | A failed status carried `recovery: verify_before_retry` |

None of these is a decline. The payment may have succeeded, so the merchant must
check the transaction against `transactionId` before re-collecting.

**Validation Errors (shown inline):**
- Invalid card number (Luhn check)
- Expired card
- Invalid CVV length (3 digits, 4 for Amex)
- Empty cardholder name

## API Endpoints

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/session/{session_id}` | GET | Fetch prefill data + saved cards |
| `/submit-card` | POST | Submit card payment |
| `/status/{transaction_id}` | GET | Poll transaction status |
| `/saved-cards/{uuid}` | DELETE | Remove a stored card (session bearer) |

## Browser Info Collection

SDK auto-collects for 3DS v2:

```kotlin
BrowserInfo(
    userAgent = WebSettings.getDefaultUserAgent(context),
    screenWidth = displayMetrics.widthPixels,
    screenHeight = displayMetrics.heightPixels,
    colorDepth = 24,  // Android standard
    // Minutes west of UTC (JS getTimezoneOffset convention)
    timezoneOffset = -TimeZone.getDefault().getOffset(now) / 60000,
    language = Locale.getDefault().toLanguageTag(),
    acceptHeader = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    javaEnabled = false,
    javascriptEnabled = true
)
```

**Note:** `browser_info.ip_address` is not collected or sent. The submit-card API
derives it from the connection (`CF-Connecting-IP`, falling back to the API Gateway
source IP) whenever the client omits it, so the SDK contacts no third-party host.

## Testing

**Test Cards (Staging only):**

| Card Number | Scenario |
|-------------|----------|
| 4532 0100 0000 0366 | Success (no 3DS) |
| 4532 0100 0000 0374 | Success (3DS challenge) |
| 4532 0100 0000 0382 | Declined |
| 4532 0100 0000 0390 | Insufficient funds |

**Test CVV:** Any 3 digits (4 for Amex)
**Test Expiry:** Any future date

**Integration Testing:**

```kotlin
// In merchant's test code
PayCross.init(environment = PayCrossEnvironment.STAGING)

// Use test cards above
paymentLauncher.launch(testSessionToken)
```

## Saved Cards

- Server-only (no local caching)
- Displayed from `/session/{id}` response, most-recently-used first
- One selectable row per card (brand, `•••• 1234`, expiry), then "Use a new card"
- User selects saved card → submit with `saved_uuid` + CVV only
- Can add local caching later without breaking changes

**Removal** is session-driven. `saved_cards_config.allow_removal` on the blob
decides whether each row carries a delete button; the merchant sets it when the
session is created. Confirming the dialog calls
`DELETE /saved-cards/{uuid}` with the session bearer, and the sheet then drops
the card from the list it is showing. Only the local list is rewritten: the
session blob is written once at session creation and never rebuilt, so a reload
of the same session lists the card again even though the server has disabled it.
A 404 also drops the card: it means the card is not this customer's, either
because it is already gone or because this session was never allowed to touch it,
and neither reading justifies still offering it. An unauthorized or transient
failure leaves the list alone and shows the error banner, since neither says
anything about whether the card is still there. Retrying is always safe, because
the endpoint is idempotent.

Removal is refused outright while a payment is in flight, and a second confirm
for a card whose removal has not come back yet is ignored.

**Preselection** is `saved_cards_config.preselect`, also merchant opt-in. When
set, the first card in the list starts selected; otherwise the form opens on
"Use a new card". The selection lives in `PaymentUiState`, not in the form,
because a removal has to drop the card and drop the selection in the same update.

**CVC stays mandatory for a stored card.** Issuer rules require it, and it is
what makes preselection safe: with a card already selected, the CVV field is the
only thing standing between an accidental tap on Pay and a charge. There is no
one-tap path through this sheet.

**Result.** A payment that stored a card comes back as
`PayCrossResult.Success(..., savedCardToken = "tok_…")`, read from `saved_token`
on the terminal status. It is null on every other success, including a payment
made with a card that was already stored.

## UI Customization

`PayCrossAppearance` themes the sheet. Every role is nullable and null means
"the next source down", so an empty appearance changes nothing and one colour
is a complete configuration.

```kotlin
PayCross.init(
    environment = PayCrossEnvironment.PRODUCTION,
    appearance = PayCrossAppearance(
        light = PayCrossColors(brand = 0xFF1E88E5.toInt(), surface = 0xFFFFFFFF.toInt()),
        dark = PayCrossColors(brand = 0xFF64B5F6.toInt(), surface = 0xFF121212.toInt()),
        themeMode = ThemeMode.SYSTEM,
        shapes = PayCrossShapes(cornerRadius = 16f, buttonCornerRadius = 28f),
        primaryButton = PayCrossPrimaryButton(height = 64f),
        typography = PayCrossTypography(sizeScaleFactor = 1.1f)
    )
)
```

### Precedence

Per role, in this order:

1. **The appearance set in code**, for the mode in force.
2. **The merchant's brand colour from the back office**, which core publishes
   into the session blob as `branding.brand_color`. It applies to `brand` only,
   and to both modes, because the branding record holds one colour and no
   light/dark variants. It is read as `#RRGGBB` or `#RGB`, hash required, in
   either case; eight digits are refused, because core normalises the field
   before it reaches the blob and alpha has no meaning on a colour the sheet
   fills a button with. iOS parses the same two shapes.
3. **The platform default**, which is the Material colour the sheet already
   draws.

So a merchant who sets a colour in the back office and writes no Kotlin gets a
branded sheet; one who sets both gets what the code says.

### Colour roles

| Role | Where it lands |
|------|----------------|
| `brand` | Pay button fill, checkbox and radio selection, the focused field border |
| `onBrand` | The Pay button's label and spinner. Null derives it from `brand`'s own luminance, so a light brand gets a dark label |
| `surface` | The sheet's background, and the window behind it, so the sheet is not framed by a system-coloured band. It is also what text drawn without an explicit colour contrasts against |
| `component` | The input fields' container, and the dialogs' |
| `componentBorder` | The fields' resting border |
| `text` | Primary text |
| `textSecondary` | Labels, hints and supporting text |
| `placeholder` | Empty-input placeholder text |
| `icon` | The picker's delete glyph. Defaults to `textSecondary` |
| `error` | Error text and invalid field borders |

The focused border stays `brand` even when `componentBorder` is set. Once a
merchant sets one `borderWidth` for both states, colour is the only thing left
indicating focus.

### Theme mode

`ThemeMode.SYSTEM` follows the device. `LIGHT` and `DARK` pin the sheet, and
only the sheet: the mode is applied to the payment Activity's own configuration
in `attachBaseContext`, which is early enough for the resource-qualified window
theme and late enough that the host app never sees it.

### Shapes and type

`cornerRadius` and `borderWidth` are in dp and reach the input fields;
`buttonCornerRadius` reaches the Pay button and the Google Pay button, falling
back to `cornerRadius`. Dialogs keep Material's own radius.

`sizeScaleFactor` multiplies every type size and is clamped to 0.8–1.3. Sizes
stay in sp, so it composes with the device's font scale rather than replacing
it.

A radius, height or thickness that is negative, infinite or not a number is
ignored, and so is a scale that is infinite or not a number. Zero is a real
value: it squares the corners and removes the border.

### Fixed by design

Layout and spacing, the card fields' internals, the wallet buttons' own colours
and labels (Apple and Google's brand guidelines allow only the radius), the
3-D Secure page's content, and the error copy.

A merchant logo in the sheet and a custom font family are deferred: the sheet
has no logo slot, and adding one is a layout change.

### Contrast

The resolver computes the WCAG ratio for the `brand`/`onBrand` pair, for
`surface` against the text drawn on it, and for a merchant-set Pay button pair, and
anything under 4.5:1 is logged once per
sheet — only when the host app is debuggable, because a merchant shipping a
release build cannot act on a logcat line. The resolver returns the warnings and
the sheet logs them, so the rule stays a pure function under unit test. A derived
`onBrand` never trips it: black and
white are chosen at the 0.179 luminance crossover, which guarantees at least
4.58:1.

### Deprecated

`brandColor` still works and still brands the sheet when no appearance is
given. It maps to `PayCrossAppearance.brand(color)`, an appearance wins when
both are set, and it will be removed a minor release from now.

## Future Extensions

- Local card caching
- A custom font family, and a merchant logo slot in the sheet
- Card scanning via camera
- Compose-only mode (no WebView Fragment)
