package com.megasmsrouter

import com.megasmsrouter.domain.parsing.CodeExtractor
import com.megasmsrouter.domain.parsing.DigitNormalizer
import com.megasmsrouter.domain.parsing.ExtractionConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParsingTest {
    @Test fun normalizesPersianAndArabicDigits() {
        assertEquals("0123456789", DigitNormalizer.normalize("۰۱۲۳۴۵۶۷۸۹"))
        assertEquals("0123456789", DigitNormalizer.normalize("٠١٢٣٤٥٦٧٨٩"))
        assertEquals("ab12", DigitNormalizer.normalize("ab۱۲"))
    }

    @Test fun defaultExtractsFirstStandaloneCode() {
        assertEquals("123456", CodeExtractor.extract("Your code is 123456. Valid 5 min"))
    }

    @Test fun defaultIgnoresShortAndTooLongNumbers() {
        assertNull(CodeExtractor.extract("Call 123 or ref 123456789012"))
        assertNull(CodeExtractor.extract("no digits here"))
    }

    @Test fun doesNotAssumeFirstNumberIsCode() {
        assertEquals("4821", CodeExtractor.extract("Order 12 total 7: code 4821"))
    }

    @Test fun persianDigitsInMessage() {
        assertEquals("482913", CodeExtractor.extract("کد تایید شما: ۴۸۲۹۱۳"))
    }

    @Test fun arabicDigitsInMessage() {
        assertEquals("5599", CodeExtractor.extract("رمز: ٥٥٩٩"))
    }

    @Test fun customRegexWithGroup() {
        val cfg = ExtractionConfig(pattern = "code[:=]\\s*([A-Z0-9]{6})", group = 1)
        assertEquals("AB12CD", CodeExtractor.extract("Login code: AB12CD now", cfg))
    }

    @Test fun customRegexGroupZeroIsWholeMatch() {
        val cfg = ExtractionConfig(pattern = "G-\\d{4}", group = 0)
        assertEquals("G-1234", CodeExtractor.extract("use G-1234", cfg))
    }

    @Test fun customRegexNoMatchOrBadGroupIsNull() {
        assertNull(CodeExtractor.extract("hello", ExtractionConfig(pattern = "x(\\d+)", group = 1)))
        assertNull(CodeExtractor.extract("x12", ExtractionConfig(pattern = "x(\\d+)", group = 5)))
    }

    @Test fun invalidRegexReturnsNullInsteadOfCrashing() {
        assertNull(CodeExtractor.extract("123456", ExtractionConfig(pattern = "([unclosed")))
        assertEquals(-1, CodeExtractor.groupCount("([unclosed"))
        assertEquals(1, CodeExtractor.groupCount("a(b)"))
    }

    @Test fun catastrophicRegexIsAbortedQuickly() {
        val body = "a".repeat(40) + "!"
        val start = System.nanoTime()
        val r = CodeExtractor.extract(body, ExtractionConfig(pattern = "(a+)+b"))
        val ms = (System.nanoTime() - start) / 1_000_000
        assertNull(r)
        assert(ms < 3000) { "regex took $ms ms" }
    }

    @Test fun customDigitRange() {
        val cfg = ExtractionConfig(minDigits = 6, maxDigits = 6)
        assertNull(CodeExtractor.extract("code 1234", cfg))
        assertEquals("123456", CodeExtractor.extract("code 1234 and 123456", cfg))
    }
}
