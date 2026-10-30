package com.megasmsrouter.domain.model

import java.util.UUID

enum class HttpMethod {
    GET, POST, PUT, PATCH, DELETE;

    /** Safe to repeat without changing server state (RFC 9110). */
    val isIdempotent: Boolean get() = this == GET || this == PUT || this == DELETE
}

enum class BodyType(val mediaType: String?) {
    NONE(null),
    JSON("application/json; charset=utf-8"),
    FORM("application/x-www-form-urlencoded"),
    TEXT("text/plain; charset=utf-8")
}

enum class AuthType { NONE, BEARER, BASIC, API_KEY }

data class HeaderEntry(val name: String, val value: String)

data class ApiDestination(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val enabled: Boolean = true,
    val url: String = "https://",
    val method: HttpMethod = HttpMethod.POST,
    val headers: List<HeaderEntry> = emptyList(),
    val bodyType: BodyType = BodyType.JSON,
    val bodyTemplate: String = "{\"code\": \"{{code}}\"}",
    val authType: AuthType = AuthType.NONE,
    val authUser: String = "",
    /** Bearer token, API key or Basic password. Stored encrypted. */
    val authSecret: String = "",
    val apiKeyHeader: String = "X-API-Key",
    /** Explicit, per-destination exception that allows plain http:// (local testing only). */
    val allowHttp: Boolean = false,
    val connectTimeoutSec: Int = 10,
    val readTimeoutSec: Int = 15,
    val maxRetries: Int = 3,
    /** Allow retry of POST/PATCH. Then an Idempotency-Key header is sent. */
    val retryNonIdempotent: Boolean = false,
    /** Optional response handling: a 2xx response only counts as success if the body contains this text. */
    val successContains: String = "",
    val priority: Int = 0,
    val lastStatus: String? = null,
    val lastStatusAt: Long? = null
)

data class RoutingRule(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val enabled: Boolean = true,
    val priority: Int = 0,
    val senders: List<String> = emptyList(),
    val normalizePhone: Boolean = false,
    val keywords: List<String> = emptyList(),
    /** Empty = default numeric code (minDigits..maxDigits). */
    val pattern: String = "",
    val captureGroup: Int = 0,
    val minDigits: Int = 4,
    val maxDigits: Int = 8,
    val destinationIds: List<String> = emptyList()
)

data class IncomingSms(val sender: String, val body: String, val timestamp: Long)

data class MessagePayload(
    val code: String,
    val sender: String,
    val message: String,
    val timestamp: Long,
    val destinationId: String,
    val messageId: String
)
