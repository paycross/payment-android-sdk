# Localization

The payment sheet ships English and French. This page lists every string it
draws, says what each one paints, and describes the two ways a merchant changes
what a shopper reads.

## Languages

| Language | Tag | Resource folder |
|---|---|---|
| English | `en` | `values/` |
| French | `fr` | `values-fr/` |

A language the SDK does not ship is not an error. It falls back rather than
failing, and the sheet paints English.

## How the language is picked

Four candidates, in order. The first that names a language the SDK ships wins.

1. **The merchant's override** — `PayCross.init(locale = "fr")`.
2. **The payment session's `locale`**, minted by your backend with the session.
3. **The device's language.**
4. **English.**

Each candidate is matched on its own: the whole tag first, then its primary
subtag, so `fr-CA` reaches French. One that matches nothing falls through to the
next rather than ending the ladder, so `init(locale = "de")` over a session whose
locale is `fr` still draws French, and a blank or malformed tag costs nothing.

This is the hosted checkout page's rule, unchanged, so a shopper who moves
between the page and either native sheet reads one language. iOS resolves
identically.

The merchant override applies before the sheet's window is built. The session's
locale arrives with the payment session, after the form is on screen, and is
applied without recreating the activity, so a half-filled card form survives it.

The sheet's language never touches the host app's. Only the payment sheet's own
window is affected.

## Amounts

The amount does **not** go through that ladder. It is formatted with the first
locale anyone actually named — the merchant's override, else the session's
`locale`, else the device — kept whole and never narrowed to `en` or `fr`.

The strings are clamped because the SDK either has the words or it does not.
Numbers are different: the platform can format them for every locale it knows,
so there is nothing to gain by taking a shopper's own grouping away.

| Situation | Words | Amount |
|---|---|---|
| Device `de`, no override, no session locale | English | `12,34 €` |
| Session `fr-CH` | French | Swiss-French grouping |
| Session `fr-CA`, override `fr` | French | France's French grouping, because the override named it |

So a German shopper reads an English sheet over an amount written the way they
expect, and a Swiss session keeps Switzerland's conventions rather than being
moved onto France's on the way to the French strings.

## Overriding a string

Every `paycross_*` key below is public API. Declare the same name in your own
app's `res/values/strings.xml` and Android's resource merging gives yours to the
sheet:

```xml
<!-- app/src/main/res/values/strings.xml -->
<string name="paycross_pay_amount">Donate %1$s</string>

<!-- app/src/main/res/values-fr/strings.xml -->
<string name="paycross_pay_amount">Faire un don de %1$s</string>
```

**Setting `locale` does not disable your overrides.** The two work together: the
locale picks which of your `values-*` folders is read, and your resource still
beats ours inside it. Some SDKs make an explicit locale turn string overrides
off; this one does not.

Keep the format arguments. `paycross_pay_amount` without its `%1$s` draws a
button with no amount on it, and `paycross_saved_card_expires` carries two.

## The keys

`%1$s` and `%2$s` are positional so a translation can reorder them.

### Card form

| Key | English | Paints |
|---|---|---|
| `paycross_card_number` | Card Number | Card number field label |
| `paycross_expiry_label` | MM/YY | Expiry field label. The typed format stays MM/YY in every language; only the label is translated |
| `paycross_cvv` | CVV | CVV field label |
| `paycross_cardholder_name` | Cardholder Name | Cardholder field label |
| `paycross_save_this_card` | Save card for future use | Save-card checkbox, shown when the session allows saving |
| `paycross_pay_amount` | Pay `%1$s` | The Pay button. `%1$s` is the formatted amount |
| `paycross_or_pay_with_card` | Or pay with card | Divider under the Google Pay button |
| `paycross_total` | Total | Names the total line **inside Google Pay's own sheet**. The card form draws no Total caption of its own; iOS uses the same key for its amount caption |
| `paycross_processing` | Processing payment... | Overlay while a payment is in flight |

### Saved cards

| Key | English | Paints |
|---|---|---|
| `paycross_use_a_new_card` | Use a new card | The last row of the card picker |
| `paycross_saved_card_expires` | Expires `%1$s`/`%2$s` | Each stored card's second line. `%1$s` is the month, `%2$s` the last two digits of the year |
| `paycross_saved_card_cvv_prompt` | Enter CVV for `%1$s` | Above the CVV box when a stored card is picked. `%1$s` is the masked number |
| `paycross_remove_card` | Remove card, `%1$s` | Spoken label on a card's delete button. `%1$s` is the row's title, e.g. `Visa •••• 0366` |
| `paycross_remove_card_title` | Remove this card? | Removal dialog title |
| `paycross_remove_card_message` | `%1$s` will no longer be offered for future payments. | Removal dialog body |
| `paycross_remove_card_confirm` | Remove | Removal dialog, confirm |
| `paycross_remove_card_keep` | Keep | Removal dialog, dismiss |
| `paycross_remove_card_failed` | Could not remove the card. Try again. | Banner when a removal did not go through |

### Cancelling

| Key | English | Paints |
|---|---|---|
| `paycross_cancel_payment_title` | Cancel Payment? | Back-press dialog title |
| `paycross_cancel_payment_message` | Are you sure you want to cancel this payment? | Back-press dialog body |
| `paycross_cancel_payment_yes` | Yes, Cancel | Back-press dialog, confirm |
| `paycross_cancel_payment_continue` | Continue Payment | Back-press dialog, dismiss |

### Errors

| Key | English | Paints |
|---|---|---|
| `paycross_error_invalid_token` | Invalid session token | The session token could not be read. An integration error, not a shopper one |
| `paycross_session_expired` | This payment session has expired. Start again. | The session token is past its expiry |
| `paycross_error_payment_failed` | Payment failed. Please try again. | A decline the shopper can retry, and a wallet sheet that failed |
| `paycross_error_network` | Network error. Please try again. | The submit request never reached the server |
| `paycross_error_submission_failed` | Payment submission failed | The server refused the submit and sent no message of its own |
| `paycross_field_required` | `%1$s` is required | A merchant-configured field left empty. `%1$s` is the field's label |
| `paycross_field_invalid` | `%1$s` is invalid | A merchant-configured field that failed its pattern |

Two of these are last resorts. When the server sends a message of its own — a
rejected submit, or a field group's own `validation.messages` — that message is
shown instead, exactly as the server wrote it. The SDK never translates it,
because it has no way to know what language it is in.

### Spoken labels

Read by TalkBack, not drawn on screen.

| Key | English | Describes |
|---|---|---|
| `paycross_card_number_field` | Card number input | The card number field |
| `paycross_expiry_field` | Expiry date input | The expiry field |
| `paycross_cvv_field` | CVV input | The CVV field |
| `paycross_cardholder_name_field` | Cardholder name input | The cardholder field |

## What is not translated here

- **The invalid-field announcement** on a card field is Material's own
  `default_error_message`, borrowed deliberately. Compose ships it in about forty
  languages, which is more than this SDK does, so a shopper outside `en` and `fr`
  still hears it in their own. Do not declare a `paycross_` key for it.
- **Merchant-configured field groups** — their labels, placeholders and option
  names come from the payment session and are the merchant's own text.
- **The Google Pay button.** Google's brand guidelines own its label, and Google
  localizes it from the device.
- **The 3-D Secure challenge**, which is the issuing bank's own page.
- **Card brand names**, which are trademarks.

## Adding a language

1. Add `sdk/src/main/res/values-<tag>/strings.xml` with every key from
   `values/strings.xml`. Lint fails the build on a missing one.
2. Add the tag to `SUPPORTED_TAGS` in
   `sdk/src/main/kotlin/com/paycross/sdk/internal/util/LocaleResolution.kt`.
   Without it the resolver will not select the folder.
3. Add a row to the Languages table above and to the README's.
