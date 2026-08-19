package com.paycross.sdk.internal.util

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

internal object Amounts {

    // ISO 4217 currencies with no minor unit — amount minor units equal major units.
    private val ZERO_DECIMAL_CURRENCIES = setOf(
        "BIF", "CLP", "GNF", "JPY", "KMF", "KRW", "MGA",
        "PYG", "RWF", "UGX", "VND", "VUV", "XAF", "XOF", "XPF"
    )

    fun fractionDigits(currencyCode: String): Int =
        if (currencyCode.uppercase() in ZERO_DECIMAL_CURRENCIES) 0 else 2

    /**
     * Plain decimal string in major units for gateway/wallet amount fields —
     * no symbol, no grouping (e.g. 12345 EUR -> "123.45", 5000 JPY -> "5000").
     * Mirrors the checkout page's minorToAmountString.
     */
    fun toMajorString(amountMinor: Long, currencyCode: String): String =
        BigDecimal.valueOf(amountMinor)
            .movePointLeft(fractionDigits(currencyCode))
            .setScale(fractionDigits(currencyCode))
            .toPlainString()

    /**
     * Formats a minor-unit amount (e.g. cents) as a localized currency string.
     */
    fun formatMinor(amountMinor: Long, currencyCode: String, locale: Locale = Locale.getDefault()): String {
        val digits = fractionDigits(currencyCode)
        val major = amountMinor / Math.pow(10.0, digits.toDouble())
        val format = NumberFormat.getCurrencyInstance(locale).apply {
            currency = Currency.getInstance(currencyCode.uppercase())
            minimumFractionDigits = digits
            maximumFractionDigits = digits
        }
        return format.format(major)
    }
}
