package com.paycross.sdk.internal.validation

import java.util.Calendar

internal object CardValidator {

    fun isValidCardNumber(number: String): Boolean {
        val cleaned = number.replace("\\s".toRegex(), "")
        if (cleaned.length < 13 || cleaned.length > 19) return false
        if (!cleaned.all { it.isDigit() }) return false
        return luhnCheck(cleaned)
    }

    fun isValidExpiry(month: String, year: String): Boolean {
        val m = month.toIntOrNull() ?: return false
        val y = year.toIntOrNull() ?: return false

        if (m < 1 || m > 12) return false

        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR)
        val currentMonth = calendar.get(Calendar.MONTH) + 1

        val fullYear = if (y < 100) 2000 + y else y

        if (fullYear < currentYear) return false
        if (fullYear == currentYear && m < currentMonth) return false

        return true
    }

    fun isValidCvv(cvv: String, cardType: CardType): Boolean {
        if (!cvv.all { it.isDigit() }) return false
        return cvv.length == cardType.cvvLength
    }

    fun isValidCardholderName(name: String): Boolean {
        return name.isNotBlank()
    }

    private fun luhnCheck(number: String): Boolean {
        var sum = 0
        var alternate = false

        for (i in number.length - 1 downTo 0) {
            var digit = number[i].digitToInt()

            if (alternate) {
                digit *= 2
                if (digit > 9) digit -= 9
            }

            sum += digit
            alternate = !alternate
        }

        return sum % 10 == 0
    }
}
