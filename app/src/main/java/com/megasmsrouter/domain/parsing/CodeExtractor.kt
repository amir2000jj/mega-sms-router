package com.megasmsrouter.domain.parsing

import java.util.regex.PatternSyntaxException

class RegexTimeoutException : RuntimeException()

/** CharSequence that aborts regex matching after a deadline (protects against catastrophic backtracking). */
class DeadlineCharSequence(
    private val inner: CharSequence,
    private val deadlineNanos: Long
) : CharSequence {
    override val length: Int get() = inner.length
    override fun get(index: Int): Char {
        if (System.nanoTime() > deadlineNanos) throw RegexTimeoutException()
        return inner[index]
    }
    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
        DeadlineCharSequence(inner.subSequence(startIndex, endIndex), deadlineNanos)
    override fun toString(): String = inner.toString()
}

data class ExtractionConfig(
    val pattern: String = "",
    val group: Int = 0,
    val minDigits: Int = 4,
    val maxDigits: Int = 8
)

object CodeExtractor {
    const val MAX_BODY_CHARS = 2000
    private const val REGEX_BUDGET_NANOS = 200_000_000L

    /**
     * Returns the first verification code or null.
     * Default: a standalone run of minDigits..maxDigits digits (not part of a longer number).
     * Custom: the user's regex; [ExtractionConfig.group] selects the capture group.
     * Persian/Arabic digits are normalised first.
     */
    fun extract(body: String, cfg: ExtractionConfig = ExtractionConfig()): String? {
        val text = DigitNormalizer.normalize(body).take(MAX_BODY_CHARS)
        return try {
            val regex = if (cfg.pattern.isBlank()) {
                val lo = cfg.minDigits.coerceAtLeast(1)
                val hi = cfg.maxDigits.coerceAtLeast(lo)
                Regex("(?<!\\d)\\d{$lo,$hi}(?!\\d)")
            } else {
                Regex(cfg.pattern)
            }
            val seq = DeadlineCharSequence(text, System.nanoTime() + REGEX_BUDGET_NANOS)
            val match = regex.find(seq)
            if (match == null || cfg.group < 0 || cfg.group >= match.groups.size) {
                null
            } else {
                match.groups[cfg.group]?.value?.takeIf { it.isNotEmpty() }
            }
        } catch (e: PatternSyntaxException) {
            null
        } catch (e: RegexTimeoutException) {
            null
        }
    }

    /** Number of capture groups of a valid pattern, or -1 if the pattern is invalid. */
    fun groupCount(pattern: String): Int = try {
        Regex(pattern).toPattern().matcher("").groupCount()
    } catch (e: PatternSyntaxException) {
        -1
    }
}
