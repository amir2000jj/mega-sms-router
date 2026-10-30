package com.megasmsrouter.domain.parsing

/** Converts Persian (U+06F0..) and Arabic-Indic (U+0660..) digits to ASCII 0-9. */
object DigitNormalizer {
    fun normalize(input: String): String {
        val sb = StringBuilder(input.length)
        for (c in input) {
            when (c) {
                in '\u06F0'..'\u06F9' -> sb.append('0' + (c - '\u06F0'))
                in '\u0660'..'\u0669' -> sb.append('0' + (c - '\u0660'))
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }
}
