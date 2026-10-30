package com.megasmsrouter.domain.routing

import com.megasmsrouter.domain.parsing.DigitNormalizer

object SenderMatcher {
    /**
     * Normalised phone number (last 10 digits), or null for alphanumeric sender IDs / short numbers.
     * Makes "+98 912 123 4567" equal to "09121234567".
     */
    fun normalizePhone(raw: String): String? {
        if (raw.any { it.isLetter() && it.code < 0x600 }) return null
        val digits = DigitNormalizer.normalize(raw).filter { it in '0'..'9' }
        return if (digits.length >= 10) digits.takeLast(10) else null
    }

    fun matches(sender: String, approved: List<String>, useNormalization: Boolean): Boolean {
        val s = sender.trim()
        if (s.isEmpty()) return false
        return approved.any { a ->
            val ap = a.trim()
            when {
                ap.isEmpty() -> false
                ap.equals(s, ignoreCase = true) -> true
                useNormalization -> {
                    val x = normalizePhone(s)
                    val y = normalizePhone(ap)
                    x != null && y != null && x == y
                }
                else -> false
            }
        }
    }
}
