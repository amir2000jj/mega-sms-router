package com.megasmsrouter

import com.megasmsrouter.domain.model.*
import com.megasmsrouter.domain.validation.*
import com.megasmsrouter.network.*
import com.megasmsrouter.security.SecretRedactor
import org.junit.Assert.*
import org.junit.Test

class ValidationTest {
    @Test fun urlRequiresHttpsByDefault() {
        assertNull(UrlValidator.validate("https://example.com/hook", false))
        assertEquals(UrlError.HTTP_NOT_ALLOWED, UrlValidator.validate("http://example.com", false))
        assertNull(UrlValidator.validate("http://192.168.1.5:8080/x", true))
        assertEquals(UrlError.EMPTY, UrlValidator.validate("  ", false))
        assertEquals(UrlError.UNSUPPORTED_SCHEME, UrlValidator.validate("ftp://example.com", true))
        assertEquals(UrlError.MALFORMED, UrlValidator.validate("not a url", false))
    }

    @Test fun headerValidation() {
        assertNull(HeaderValidator.validate("Authorization", "Bearer abc"))
        assertNull(HeaderValidator.validate("X-Client", "MSR"))
        assertEquals(HeaderError.EMPTY_NAME, HeaderValidator.validate(" ", "x"))
        assertEquals(HeaderError.INVALID_NAME, HeaderValidator.validate("Bad Name", "x"))
        assertEquals(HeaderError.INVALID_NAME, HeaderValidator.validate("X:Y", "x"))
        assertEquals(HeaderError.INVALID_VALUE, HeaderValidator.validate("X-A", "a\r\nInjected: 1"))
        assertEquals(HeaderError.INVALID_VALUE, HeaderValidator.validate("X-A", "سلام"))
        assertEquals(HeaderError.FORBIDDEN_NAME, HeaderValidator.validate("Host", "evil"))
    }

    @Test fun duplicateHeadersAreFlagged() {
        val errs = HeaderValidator.validateAll(listOf(HeaderEntry("X-A", "1"), HeaderEntry("x-a", "2")))
        assertEquals(setOf(1), errs.keys)
        assertEquals(HeaderError.DUPLICATE, errs[1])
    }

    @Test fun jsonTemplateValidation() {
        assertTrue(JsonTemplate.isValid("{\"code\":\"{{code}}\"}"))
        assertTrue(JsonTemplate.isValid("[1,2]"))
        assertFalse(JsonTemplate.isValid("{\"code\":"))
        assertFalse(JsonTemplate.isValid("{\"a\":1} trailing"))
        assertFalse(JsonTemplate.isValid("\"just a string\""))
    }

    @Test fun destinationValidation() {
        val ok = ApiDestination(name = "n", url = "https://example.com/api")
        assertTrue(DestinationValidator.validate(ok).isEmpty())
        assertTrue(DestinationIssue.NAME_EMPTY in DestinationValidator.validate(ok.copy(name = "")))
        assertTrue(DestinationIssue.URL_HTTP_NOT_ALLOWED in DestinationValidator.validate(ok.copy(url = "http://x.com")))
        assertTrue(DestinationIssue.BODY_INVALID_JSON in DestinationValidator.validate(ok.copy(bodyTemplate = "{oops")))
        assertTrue(DestinationIssue.BODY_UNKNOWN_PLACEHOLDER in DestinationValidator.validate(ok.copy(bodyTemplate = "{\"a\":\"{{nope}}\"}")))
        assertTrue(DestinationIssue.GET_BODY_UNSUPPORTED in DestinationValidator.validate(ok.copy(method = HttpMethod.GET)))
        assertTrue(DestinationIssue.TIMEOUT_RANGE in DestinationValidator.validate(ok.copy(readTimeoutSec = 0)))
        assertTrue(DestinationIssue.RETRIES_RANGE in DestinationValidator.validate(ok.copy(maxRetries = 99)))
        assertTrue(DestinationIssue.AUTH_SECRET_MISSING in DestinationValidator.validate(ok.copy(authType = AuthType.BEARER)))
        assertTrue(DestinationIssue.AUTH_USER_MISSING in DestinationValidator.validate(ok.copy(authType = AuthType.BASIC, authSecret = "p")))
    }

    @Test fun ruleValidation() {
        val ok = RoutingRule(name = "r", senders = listOf("Bank"), destinationIds = listOf("d"))
        assertTrue(RuleValidator.validate(ok).isEmpty())
        assertTrue(RuleIssue.NO_SENDERS in RuleValidator.validate(ok.copy(senders = emptyList())))
        assertTrue(RuleIssue.NO_DESTINATIONS in RuleValidator.validate(ok.copy(destinationIds = emptyList())))
        assertTrue(RuleIssue.PATTERN_INVALID in RuleValidator.validate(ok.copy(pattern = "([x")))
        assertTrue(RuleIssue.GROUP_RANGE in RuleValidator.validate(ok.copy(pattern = "a(b)", captureGroup = 3)))
        assertTrue(RuleIssue.DIGITS_RANGE in RuleValidator.validate(ok.copy(minDigits = 9, maxDigits = 4)))
    }

    @Test fun retryEligibility() {
        val http500 = NetworkResult.HttpError(500, 1)
        assertTrue(RetryPolicy.shouldRetry(http500, HttpMethod.PUT, false, 0, 3))
        assertFalse(RetryPolicy.shouldRetry(http500, HttpMethod.POST, false, 0, 3)) // non-idempotent
        assertTrue(RetryPolicy.shouldRetry(http500, HttpMethod.POST, true, 0, 3))
        assertFalse(RetryPolicy.shouldRetry(http500, HttpMethod.PUT, false, 3, 3)) // bounded
        assertTrue(RetryPolicy.shouldRetry(NetworkResult.HttpError(429, 1), HttpMethod.GET, false, 1, 3))
        for (c in listOf(400, 401, 403, 404)) {
            assertFalse(RetryPolicy.shouldRetry(NetworkResult.HttpError(c, 1), HttpMethod.GET, true, 0, 3))
        }
        assertFalse(RetryPolicy.shouldRetry(NetworkResult.Success(200, 1), HttpMethod.GET, true, 0, 3))
        assertTrue(RetryPolicy.shouldRetry(NetworkResult.Failure(FailureKind.OFFLINE, "x", 1), HttpMethod.POST, false, 0, 3))
        assertFalse(RetryPolicy.shouldRetry(NetworkResult.Failure(FailureKind.TIMEOUT, "x", 1), HttpMethod.POST, false, 0, 3))
        assertTrue(RetryPolicy.shouldRetry(NetworkResult.Failure(FailureKind.TIMEOUT, "x", 1), HttpMethod.PUT, false, 0, 3))
        assertFalse(RetryPolicy.shouldRetry(NetworkResult.Failure(FailureKind.TLS, "x", 1), HttpMethod.GET, true, 0, 3))
        assertFalse(RetryPolicy.shouldRetry(NetworkResult.HttpError(200, 1, bodyMismatch = true), HttpMethod.GET, true, 0, 3))
    }

    @Test fun secretRedaction() {
        assertTrue(SecretRedactor.isSensitiveHeader("Authorization"))
        assertTrue(SecretRedactor.isSensitiveHeader("X-API-Key"))
        assertFalse(SecretRedactor.isSensitiveHeader("X-Client"))
        assertEquals("token=*** end", SecretRedactor.redact("token=abc123 end", listOf("abc123")))
    }

    @Test fun placeholderHelpers() {
        assertEquals(setOf("code", "sender"), Placeholders.used("{{code}} {{ sender }}"))
        assertEquals(setOf("bogus"), Placeholders.unknown("{{code}}{{bogus}}"))
        val v = mapOf("code" to "1\$2")
        assertEquals("a 1\$2 b", Placeholders.substitute("a {{code}} b", v)) // '$' kept literally
        assertEquals("{{unknown}}", Placeholders.substitute("{{unknown}}", v))
    }
}
