# Payment API Documentation

## Base URLs

| Environment | Base URL |
|-------------|----------|
| Test | `https://checkout.test-pay-cross.com/api` |
| Production | `https://checkout.pay-cross.com/api` |

All endpoints below are relative to the base URL.

## Authentication

### Payment Session Token (JWT)

The checkout URL contains a JWT token that authenticates public API requests during payment.

**URL Format:**
```
{checkout_url}?session={jwt_token}
```

### Token Claims

| Claim | Type | Description |
|-------|------|-------------|
| `sub` | string | Payment session UUID |
| `merchant` | string | Merchant UUID |
| `customer` | string | Customer UUID |
| `branding` | string\|null | Merchant branding UUID |
| `amount` | int | Payment amount in minor units (e.g. cents) |
| `currency` | string | Currency code (e.g., EUR) |
| `iat` | int | Issued at (unix timestamp) |
| `exp` | int | Expiration (iat + TTL) |
| `jti` | string | Unique token ID (UUID) |

### Token Details

- **Algorithm:** RS256
- **Signing:** AWS KMS
- **TTL:** 900 seconds (15 minutes)

### Example Decoded Token

```json
{
  "sub": "550e8400-e29b-41d4-a716-446655440000",
  "merchant": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "customer": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "branding": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "amount": 9999,
  "currency": "EUR",
  "iat": 1704067200,
  "exp": 1704068100,
  "jti": "123e4567-e89b-12d3-a456-426614174000"
}
```

---

## Card Submission

Submit card details to initiate a payment transaction.

**Endpoint:** `POST /submit-card`

### Headers

| Header | Required | Description |
|--------|----------|-------------|
| `Content-Type` | Yes | `application/json` |
| `Idempotency-Key` | Yes | UUID for idempotent requests |

### Request Body

Three modes are supported: **new card**, **saved card**, or **wallet** (Apple Pay / Google Pay).

#### New Card (with optional save)

```json
{
  "session": "eyJhbGciOiJSUzI1NiIs...",
  "payment_method": "card",
  "card": {
    "cardholder_name": "John Doe",
    "pan": "4532010000000366",
    "expire_year": "2025",
    "expire_month": "12",
    "cvv": "123",
    "save": true
  },
  "browser_info": {
    "user_agent": "Mozilla/5.0...",
    "ip_address": "192.168.1.100",
    "screen_width": 1920,
    "screen_height": 1080,
    "color_depth": 24,
    "timezone_offset": -300,
    "language": "en-US",
    "accept_header": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    "java_enabled": false,
    "javascript_enabled": true
  },
  "field_groups": {
    "billing_address": {
      "line1": "123 Main St",
      "line2": "Apt 4B",
      "city": "New York",
      "state": "NY",
      "postal_code": "10001",
      "country": "US"
    },
    "customer_info": {
      "email": "john@example.com",
      "phone": "+12025551234"
    }
  }
}
```

Field group values are nested under a single `field_groups` object keyed by group name (`billing_address`, `shipping_address`, `customer_info`, `aft_sender`, `aft_recipient`). Only groups the merchant has configured with non-empty user-provided values are included; the `field_groups` object itself is omitted when empty.

#### Saved Card (CVV only)

```json
{
  "session": "eyJhbGciOiJSUzI1NiIs...",
  "payment_method": "card",
  "card": {
    "saved_uuid": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
    "cvv": "123"
  },
  "browser_info": {
    "user_agent": "Mozilla/5.0...",
    "ip_address": "192.168.1.100",
    "screen_width": 1920,
    "screen_height": 1080,
    "color_depth": 24,
    "timezone_offset": -300,
    "language": "en-US",
    "accept_header": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    "java_enabled": false,
    "javascript_enabled": true
  },
  "field_groups": {
    "billing_address": {
      "line1": "123 Main St",
      "country": "US"
    }
  }
}
```

Saved card submissions use the same nested `field_groups` object when configured.

#### Wallet Payment (Apple Pay / Google Pay)

```json
{
  "session": "eyJhbGciOiJSUzI1NiIs...",
  "payment_method": "apple_pay",
  "wallet_token": {
    "type": "apple_pay",
    "data": {
      "paymentData": { ... },
      "paymentMethod": { ... },
      "transactionIdentifier": "..."
    }
  },
  "browser_info": {
    "user_agent": "Mozilla/5.0...",
    "ip_address": "192.168.1.100",
    "screen_width": 1920,
    "screen_height": 1080,
    "color_depth": 24,
    "timezone_offset": -300,
    "language": "en-US",
    "accept_header": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    "java_enabled": false,
    "javascript_enabled": true
  }
}
```

### Request Fields

#### Root

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `session` | string | Yes | JWT token from checkout URL |
| `payment_method` | string | Yes | Payment type: `card`, `apple_pay`, or `google_pay` |
| `card` | object | If payment_method=card | Card details (new or saved) |
| `wallet_token` | object | If payment_method=apple_pay or google_pay | Wallet token data |
| `browser_info` | object | Yes | Browser/device fingerprint for 3DS |
| `field_groups` | object | No | Nested object of configured field group values (see below) |

#### Card Object (new card)

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `cardholder_name` | string | Yes | Full name on card |
| `pan` | string | Yes | Card number (no spaces/dashes) |
| `expire_year` | string | Yes | 4-digit year (e.g., "2025") |
| `expire_month` | string | Yes | 2-digit month (01-12) |
| `cvv` | string | Yes | 3 or 4 digit security code |
| `save` | bool | No | Save card for future use |

#### Card Object (saved card)

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `saved_uuid` | string | Yes | UUID of previously saved card |
| `cvv` | string | Yes | 3 or 4 digit security code |

#### Wallet Token Object (payment_method=apple_pay or google_pay)

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `type` | string | Yes | Wallet type; must match `payment_method` (`apple_pay` or `google_pay`) |
| `data` | object | Yes | Token data from wallet provider |

#### Browser Info Object (Required for 3DS v2)

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `user_agent` | string | Yes | Browser user agent |
| `ip_address` | string | No | Customer IP address. Derived from the connection (`CF-Connecting-IP`, else the API Gateway source IP) when omitted; a supplied value wins over both. The Android SDK omits it. |
| `screen_width` | int | Yes | Screen width in pixels |
| `screen_height` | int | Yes | Screen height in pixels |
| `color_depth` | int | Yes | Color depth (1,4,8,15,16,24,32,48) |
| `timezone_offset` | int | Yes | UTC offset in minutes (-720 to 840) |
| `language` | string | Yes | Browser language (e.g., "en-US") |
| `accept_header` | string | Yes | HTTP Accept header |
| `java_enabled` | bool | Yes | Java enabled (usually false) |
| `javascript_enabled` | bool | Yes | JavaScript enabled (usually true) |

#### Field Groups Object (Dynamic)

Field group values are sent under a single top-level `field_groups` object, keyed by group name matching `field_groups[].key` from session data. Only non-empty groups with user-provided values are included. Field names within each group match the `field_groups[].fields[].name` values. The entire `field_groups` object is omitted when there are no values to send.

Example: if the session data includes a `billing_address` field group with fields `line1`, `city`, `country`, the submission sends:

```json
{
  "field_groups": {
    "billing_address": {
      "line1": "123 Main St",
      "city": "New York",
      "country": "US"
    }
  }
}
```

### Response

#### Success (200)

```json
{
  "success": true,
  "transaction_id": "6432de70-9663-4269-91f5-2b80534808b6"
}
```

#### Cached Response (200)

```json
{
  "success": true,
  "transaction_id": "6432de70-9663-4269-91f5-2b80534808b6",
  "cached": true
}
```

#### Session Expired (401)

```json
{
  "error": "Payment session expired"
}
```

#### Invalid Request (400)

```json
{
  "error": "Missing Idempotency-Key header"
}
```

#### Already Processing (409)

```json
{
  "error": "Request already processing",
  "retry_after": 2
}
```

---

## Transaction Status Polling

Poll for transaction status updates during payment processing.

**Endpoint:** `GET /status/{transaction_id}`

**Authentication:** None required (transaction UUID is unguessable)

**Rate Limit:** 10 requests/second per IP

### Status Values

| Status | Description | Action |
|--------|-------------|--------|
| `processing` | Transaction is being processed | Continue polling (3s interval) |
| `threeds_fingerprint` | 3DS fingerprint required | Render hidden iframe with action data |
| `threeds_challenge` | 3DS challenge required | Render challenge iframe with action data |
| `authorized` | Payment authorized | Show success |
| `success` | Payment completed | Show success |
| `failed` | Payment failed | Show error with recovery hint |

### Response Examples

#### Processing

```json
{
  "transaction_id": "550e8400-e29b-41d4-a716-446655440000",
  "status": "processing",
  "amount": 10000,
  "currency": "EUR"
}
```

#### 3DS Fingerprint Required

```json
{
  "transaction_id": "550e8400-e29b-41d4-a716-446655440000",
  "status": "threeds_fingerprint",
  "amount": 10000,
  "currency": "EUR",
  "action": {
    "url": "https://acs.bank.com/3ds/method",
    "method": "POST",
    "data": {
      "threeDSMethodData": "eyJ0aHJlZURTU2VydmVy..."
    }
  }
}
```

#### 3DS Challenge Required

```json
{
  "transaction_id": "550e8400-e29b-41d4-a716-446655440000",
  "status": "threeds_challenge",
  "amount": 10000,
  "currency": "EUR",
  "action": {
    "url": "https://acs.bank.com/3ds/challenge",
    "method": "POST",
    "data": {
      "creq": "eyJhY3NUcmFuc0lEIjoi..."
    }
  }
}
```

#### Success

```json
{
  "transaction_id": "550e8400-e29b-41d4-a716-446655440000",
  "status": "success",
  "amount": 10000,
  "currency": "EUR"
}
```

#### Failed with Recovery

```json
{
  "transaction_id": "550e8400-e29b-41d4-a716-446655440000",
  "status": "failed",
  "amount": 10000,
  "currency": "EUR",
  "recovery": "change_method"
}
```

#### Not Found (404)

```json
{
  "error": "Transaction not found"
}
```

### Recovery Values

| Value | User Action |
|-------|-------------|
| `retry` | Try the same card again |
| `change_method` | Use a different card |
| `restart` | Start a new payment session |
| `contact_support` | Contact merchant support |
| `do_not_retry` | Terminal decline — never offer a retry |

Unrecognized values must be treated as non-retryable (fail closed). `amount` and `currency` are omitted from status responses when not yet recorded.

### Polling Strategy

```
1. Submit card → get transaction_id
2. Poll /status/{transaction_id} every 3 seconds
3. Handle status:
   - processing: continue polling
   - threeds_fingerprint: render iframe, continue polling
   - threeds_challenge: render iframe, continue polling
   - success/authorized: payment complete
   - failed: show error based on recovery value
```

---

## Session Data (Prefill)

Fetch session data for checkout form prefill, field requirements, and saved cards.

**Endpoint:** `GET /session/{session_id}`

**Authentication:** None required (session UUID is unguessable)

**Rate Limit:** 10 requests/second per IP

### Response

#### With Field Groups, Saved Cards, and Save-Card Config

```json
{
  "session_id": "550e8400-e29b-41d4-a716-446655440000",
  "status": "open",
  "data": {
    "locale": "en",
    "return_url": "https://merchant.example.com/cart",
    "success_url": "https://merchant.example.com/thank-you",
    "merchant_country": "GB",
    "field_groups": [
      {
        "key": "customer_info",
        "label": "Customer Information",
        "display": "expanded",
        "fields": [
          {
            "name": "email",
            "type": "email",
            "label": "Email",
            "placeholder": "Email address",
            "required": true,
            "readonly": false,
            "value": "john@example.com",
            "validation": {
              "max_length": 254,
              "messages": {
                "required": "This field is required",
                "max_length": "Must not exceed 254 characters"
              }
            }
          },
          {
            "name": "phone",
            "type": "tel",
            "label": "Phone",
            "placeholder": "Phone number",
            "required": false,
            "readonly": false,
            "value": "+12025551234"
          }
        ]
      },
      {
        "key": "billing_address",
        "label": "Billing Address",
        "display": "expanded",
        "fields": [
          {
            "name": "line1",
            "type": "text",
            "label": "Address Line 1",
            "placeholder": "Street address",
            "required": true,
            "readonly": false,
            "value": "123 Main Street"
          },
          {
            "name": "country",
            "type": "select",
            "label": "Country",
            "placeholder": "Select a country",
            "required": true,
            "readonly": false,
            "value": "LV",
            "options": [
              { "value": "LV", "label": "Latvia" },
              { "value": "US", "label": "United States" }
            ],
            "validation": {
              "max_length": 2,
              "pattern": "^[A-Z]{2}$",
              "messages": {
                "required": "This field is required",
                "max_length": "Must not exceed 2 characters"
              }
            }
          }
        ]
      }
    ],
    "saved_cards": [
      {
        "uuid": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
        "masked_pan": "453201******0366",
        "card_brand": "visa",
        "expire_month": "12",
        "expire_year": "2025",
        "cardholder_name": "John Doe"
      }
    ],
    "save_card_config": {
      "usage": "card_on_file"
    }
  }
}
```

#### Empty Data (No Field Configuration)

```json
{
  "session_id": "550e8400-e29b-41d4-a716-446655440000",
  "status": "open",
  "data": {
    "locale": "en",
    "return_url": "https://merchant.example.com/cart",
    "success_url": "https://merchant.example.com/thank-you",
    "field_groups": [],
    "saved_cards": []
  }
}
```

### Top-Level Response Fields

| Field | Type | Description |
|-------|------|-------------|
| `session_id` | string | Session UUID |
| `status` | string | Session status: `open`, `completed`, `expired` |
| `latest_transaction_id` | string | Most recent transaction on the session (omitted until one exists) |
| `data` | object | Checkout data blob (below) |

A `completed` or `expired` session must not show the payment form; when
`latest_transaction_id` is present with an `open` session, resume status
polling for that transaction instead of re-arming the form.

#### Not Found (404)

```json
{
  "error": "Session not found"
}
```

### Data Blob Fields

| Field | Type | Description |
|-------|------|-------------|
| `locale` | string | Locale code (e.g., `en`), controls label/message language |
| `return_url` | string | URL the checkout returns to on cancel/failure |
| `success_url` | string | URL the checkout redirects to on success |
| `merchant_country` | string | Merchant's country code (omitted when unset) |
| `field_groups` | array | Ordered list of field groups to render (may be empty) |
| `saved_cards` | array | Customer's saved cards (empty array if none) |
| `save_card_config` | object | Card saving options (only present if configured on session) |

### Field Group Object

Each entry in `field_groups` represents a section of the checkout form.

| Field | Type | Description |
|-------|------|-------------|
| `key` | string | Group identifier (see Field Groups below) |
| `label` | string | Localized display label for the group |
| `display` | string | Display mode: `expanded` (open by default) or `collapsed` (closed) |
| `fields` | array | Ordered list of fields to render |

### Field Object

| Field | Type | Description |
|-------|------|-------------|
| `name` | string | Field identifier |
| `type` | string | Input type: `text`, `email`, `tel`, `select` |
| `label` | string | Localized display label |
| `placeholder` | string | Localized placeholder text |
| `required` | bool | Whether the field is required |
| `readonly` | bool | Whether the field is read-only (prefilled, not editable) |
| `value` | mixed | Prefilled value from session/customer data, or `null` |
| `condition` | object\|null | Conditional display rule (see below) |
| `options` | array\|null | For `select` type: list of `{ value, label }` objects |
| `validation` | object\|null | Validation rules (only present if rules exist) |

### Condition Object

Evaluated against sibling values in the same group.

| Field | Type | Description |
|-------|------|-------------|
| `when` | string | Sibling field name whose value controls this field |
| `in` | array | Values of `when` that activate the condition |
| `display` | string | Display when active: `required`, `show`, `readonly`, `hidden` |
| `default` | string | Display when not active |

### Validation Object

| Field | Type | Description |
|-------|------|-------------|
| `max_length` | int | Maximum character length |
| `pattern` | string | Regex pattern the value must match |
| `messages` | object | Localized validation error messages keyed by rule name |

### Field Groups

Groups are ordered by sort priority. Only groups with at least one visible field configured for the merchant are included.

| Key | Fields | Display | Sort |
|-----|--------|---------|------|
| `customer_info` | `email`, `phone`, `full_name` | expanded | 1 |
| `billing_address` | `line1`, `line2`, `city`, `state`, `postal_code`, `country` | expanded | 2 |
| `shipping_address` | `line1`, `line2`, `city`, `state`, `postal_code`, `country` | collapsed | 3 |

Fields with `display: hidden` in merchant configuration are omitted from the response. Select fields (e.g., `country`) may have a restricted set of options configured per merchant.

### Saved Card Object

| Field | Type | Description |
|-------|------|-------------|
| `uuid` | string | Card UUID (use as `card.saved_uuid` in submission) |
| `masked_pan` | string | Masked card number (e.g., `453201******0366`) |
| `card_brand` | string | Card brand (e.g., `visa`, `mastercard`) |
| `expire_month` | string | Expiration month (01-12) |
| `expire_year` | string | Expiration year (YYYY) |
| `cardholder_name` | string | Name on card |

Expired cards are automatically excluded. `saved_cards` is always present and is `[]` when the customer has none.

### Save Card Config Object

| Field | Type | Description |
|-------|------|-------------|
| `usage` | string | Card save usage type (e.g. `card_on_file`) |

### Usage Flow

```
1. Parse JWT from checkout URL → get session_id (sub claim)
2. Fetch /session/{session_id}
3. Render field_groups:
   - For each group, render a section with its label
   - Respect display mode: "expanded" = open by default, "collapsed" = closed
   - For each field, render an input with type, label, placeholder, value, required/readonly
   - For select fields, render a dropdown with field.options
   - Apply validation rules client-side
4. If saved_cards is non-empty:
   - Show card selector UI with card_brand, masked_pan, cardholder_name
   - User selects saved card → submit with card.saved_uuid + cvv + field group values
   - Or user enters new card details + field group values
5. If save_card_config present:
   - Show "Save card" checkbox with usage context
```
