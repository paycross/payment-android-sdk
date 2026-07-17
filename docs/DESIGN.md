# PayCross Android SDK Design

## Overview

Drop-in payment UI for Android apps. Merchant passes session token, SDK handles card entry, 3DS, and status polling.

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
        brandColor: Color? = null
    ) {
        config = PayCrossConfig(environment, brandColor)
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
    brandColor = Color(0xFF1E88E5)  // optional
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
            // result.recovery (RETRY, CHANGE_METHOD, RESTART, CONTACT_SUPPORT, DO_NOT_RETRY)
        }
        is PayCrossResult.Cancelled -> {
            // User closed the payment screen
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

            _result.value = PayCrossResult.Failure(transactionId, Recovery.RETRY)
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
│  [Saved Card ▼] (if available)  │
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
        val recovery: Recovery
    ) : PayCrossResult()

    @Parcelize
    object Cancelled : PayCrossResult()
}

enum class Recovery {
    RETRY,           // "Try again"
    CHANGE_METHOD,   // "Use a different card"
    RESTART,         // "Start over" (session expired)
    CONTACT_SUPPORT, // "Contact support"
    DO_NOT_RETRY     // Terminal decline; unknown values fail closed to this
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
| `DO_NOT_RETRY` | Dead-end message | Terminal decline (stolen card, do-not-honor); also the fail-closed mapping for unknown values |

Only `RETRY` and `CHANGE_METHOD` re-arm the payment form (`recovery.isRetryable`).

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

## Browser Info Collection

SDK auto-collects for 3DS v2:

```kotlin
BrowserInfo(
    userAgent = WebSettings.getDefaultUserAgent(context),
    ipAddress = IpAddressProvider.get(),  // required by /submit-card
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

**Note:** `browser_info.ip_address` is required by the submit-card API. The SDK resolves the public IP via ipify (as the checkout page does) and falls back to `127.0.0.1` so submission never blocks on the lookup.

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
- Displayed from `/session/{id}` response
- User selects saved card → submit with `saved_uuid` + CVV only
- Can add local caching later without breaking changes

## UI Customization

Minimal for v1 (extendable later):

```kotlin
PayCross.init(
    environment = PayCrossEnvironment.STAGING,
    brandColor = Color(0xFF1E88E5)  // Used for buttons and accents
)
```

## Future Extensions

- Local card caching
- Full UI theming (fonts, colors, labels)
- Google Pay support
- Card scanning via camera
- Compose-only mode (no WebView Fragment)
