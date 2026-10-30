package com.megasmsrouter.domain.validation

import com.megasmsrouter.domain.model.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener

enum class UrlError { EMPTY, MALFORMED, UNSUPPORTED_SCHEME, HTTP_NOT_ALLOWED }

object UrlValidator {
    /** HTTPS is required unless the user explicitly enabled the HTTP exception for this destination. */
    fun validate(url: String, allowHttp: Boolean): UrlError? {
        val t = url.trim()
        if (t.isEmpty()) return UrlError.EMPTY
        val parsed = t.toHttpUrlOrNull()
            ?: return if (t.contains("://")) UrlError.UNSUPPORTED_SCHEME else UrlError.MALFORMED
        if (parsed.host.isEmpty()) return UrlError.MALFORMED
        if (parsed.scheme == "http" && !allowHttp) return UrlError.HTTP_NOT_ALLOWED
        return null
    }
}

enum class HeaderError { EMPTY_NAME, INVALID_NAME, INVALID_VALUE, FORBIDDEN_NAME, DUPLICATE }

object HeaderValidator {
    private val TOKEN = Regex("^[!#$%&'*+\\-.^_`|~0-9A-Za-z]+$")
    private val FORBIDDEN = setOf("host", "content-length", "transfer-encoding", "connection", "upgrade")

    fun validate(name: String, value: String): HeaderError? {
        val n = name.trim()
        if (n.isEmpty()) return HeaderError.EMPTY_NAME
        if (!TOKEN.matches(n)) return HeaderError.INVALID_NAME
        if (n.lowercase() in FORBIDDEN) return HeaderError.FORBIDDEN_NAME
        // visible ASCII + space + tab only (no CR/LF/control/non-ASCII => no header injection)
        if (value.any { !(it == '\t' || it.code in 0x20..0x7E) }) return HeaderError.INVALID_VALUE
        return null
    }

    fun isValidName(name: String): Boolean = TOKEN.matches(name.trim())

    /** Map of row index -> error. */
    fun validateAll(headers: List<HeaderEntry>): Map<Int, HeaderError> {
        val errors = LinkedHashMap<Int, HeaderError>()
        val seen = HashSet<String>()
        headers.forEachIndexed { i, h ->
            val e = validate(h.name, h.value)
            if (e != null) errors[i] = e
            else if (!seen.add(h.name.trim().lowercase())) errors[i] = HeaderError.DUPLICATE
        }
        return errors
    }
}

object JsonTemplate {
    /** A valid JSON object/array with nothing after it. */
    fun isValid(t: String): Boolean = try {
        val tk = JSONTokener(t)
        val v = tk.nextValue()
        (v is JSONObject || v is JSONArray) && tk.nextClean() == '\u0000'
    } catch (e: JSONException) {
        false
    }
}

enum class DestinationIssue {
    NAME_EMPTY, URL_EMPTY, URL_MALFORMED, URL_SCHEME, URL_HTTP_NOT_ALLOWED,
    HEADER_INVALID, BODY_INVALID_JSON, BODY_UNKNOWN_PLACEHOLDER, GET_BODY_UNSUPPORTED,
    TIMEOUT_RANGE, RETRIES_RANGE, AUTH_SECRET_MISSING, AUTH_USER_MISSING, API_KEY_HEADER_INVALID
}

object DestinationValidator {
    fun validate(d: ApiDestination): List<DestinationIssue> {
        val out = mutableListOf<DestinationIssue>()
        if (d.name.isBlank()) out += DestinationIssue.NAME_EMPTY

        UrlValidator.validate(d.url, d.allowHttp)?.let {
            out += when (it) {
                UrlError.EMPTY -> DestinationIssue.URL_EMPTY
                UrlError.MALFORMED -> DestinationIssue.URL_MALFORMED
                UrlError.UNSUPPORTED_SCHEME -> DestinationIssue.URL_SCHEME
                UrlError.HTTP_NOT_ALLOWED -> DestinationIssue.URL_HTTP_NOT_ALLOWED
            }
        }

        if (HeaderValidator.validateAll(d.headers).isNotEmpty()) out += DestinationIssue.HEADER_INVALID

        if (d.method == HttpMethod.GET && (d.bodyType == BodyType.JSON || d.bodyType == BodyType.TEXT)) {
            out += DestinationIssue.GET_BODY_UNSUPPORTED
        } else if (d.bodyType != BodyType.NONE) {
            if (d.bodyType == BodyType.JSON && !JsonTemplate.isValid(d.bodyTemplate)) {
                out += DestinationIssue.BODY_INVALID_JSON
            }
            if (Placeholders.unknown(d.bodyTemplate).isNotEmpty()) out += DestinationIssue.BODY_UNKNOWN_PLACEHOLDER
        }

        if (d.connectTimeoutSec !in 1..120 || d.readTimeoutSec !in 1..120) out += DestinationIssue.TIMEOUT_RANGE
        if (d.maxRetries !in 0..10) out += DestinationIssue.RETRIES_RANGE

        when (d.authType) {
            AuthType.NONE -> {}
            AuthType.BEARER -> if (d.authSecret.isBlank()) out += DestinationIssue.AUTH_SECRET_MISSING
            AuthType.BASIC -> {
                if (d.authUser.isBlank()) out += DestinationIssue.AUTH_USER_MISSING
                if (d.authSecret.isBlank()) out += DestinationIssue.AUTH_SECRET_MISSING
            }
            AuthType.API_KEY -> {
                if (d.authSecret.isBlank()) out += DestinationIssue.AUTH_SECRET_MISSING
                if (HeaderValidator.validate(d.apiKeyHeader, d.authSecret) != null) {
                    out += DestinationIssue.API_KEY_HEADER_INVALID
                }
            }
        }
        return out
    }
}

enum class RuleIssue { NAME_EMPTY, NO_SENDERS, NO_DESTINATIONS, PATTERN_INVALID, GROUP_RANGE, DIGITS_RANGE }

object RuleValidator {
    fun validate(r: RoutingRule): List<RuleIssue> {
        val out = mutableListOf<RuleIssue>()
        if (r.name.isBlank()) out += RuleIssue.NAME_EMPTY
        if (r.senders.none { it.isNotBlank() }) out += RuleIssue.NO_SENDERS
        if (r.destinationIds.isEmpty()) out += RuleIssue.NO_DESTINATIONS
        if (r.pattern.isNotBlank()) {
            val groups = com.megasmsrouter.domain.parsing.CodeExtractor.groupCount(r.pattern)
            if (groups < 0) out += RuleIssue.PATTERN_INVALID
            else if (r.captureGroup !in 0..groups) out += RuleIssue.GROUP_RANGE
        } else {
            if (r.minDigits !in 1..12 || r.maxDigits !in 1..12 || r.minDigits > r.maxDigits) {
                out += RuleIssue.DIGITS_RANGE
            }
        }
        return out
    }
}
