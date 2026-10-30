package com.megasmsrouter.data.repository

import com.megasmsrouter.data.entities.ApiDestinationEntity
import com.megasmsrouter.data.entities.RoutingRuleEntity
import com.megasmsrouter.domain.model.*
import com.megasmsrouter.security.SecretCipher
import org.json.JSONArray
import org.json.JSONObject

inline fun <reified T : Enum<T>> enumOr(name: String, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default

object HeaderCodec {
    fun encode(list: List<HeaderEntry>): String {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("n", it.name).put("v", it.value)) }
        return arr.toString()
    }

    fun decode(json: String): List<HeaderEntry> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                HeaderEntry(o.getString("n"), o.getString("v"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}

object PayloadCodec {
    fun encode(p: MessagePayload): String = JSONObject()
        .put("code", p.code).put("sender", p.sender).put("message", p.message).put("timestamp", p.timestamp)
        .toString()

    fun decode(json: String, destinationId: String, messageId: String): MessagePayload? = try {
        val o = JSONObject(json)
        MessagePayload(
            code = o.optString("code"),
            sender = o.optString("sender"),
            message = o.optString("message"),
            timestamp = o.optLong("timestamp"),
            destinationId = destinationId,
            messageId = messageId
        )
    } catch (e: Exception) {
        null
    }
}

fun ApiDestinationEntity.toDomain(c: SecretCipher) = ApiDestination(
    id = id,
    name = name,
    enabled = enabled,
    url = url,
    method = enumOr(method, HttpMethod.POST),
    headers = HeaderCodec.decode(c.decrypt(headersEnc)),
    bodyType = enumOr(bodyType, BodyType.JSON),
    bodyTemplate = bodyTemplate,
    authType = enumOr(authType, AuthType.NONE),
    authUser = authUser,
    authSecret = c.decrypt(authSecretEnc),
    apiKeyHeader = apiKeyHeader,
    allowHttp = allowHttp,
    connectTimeoutSec = connectTimeoutSec,
    readTimeoutSec = readTimeoutSec,
    maxRetries = maxRetries,
    retryNonIdempotent = retryNonIdempotent,
    successContains = successContains,
    priority = priority,
    lastStatus = lastStatus,
    lastStatusAt = lastStatusAt
)

fun ApiDestination.toEntity(c: SecretCipher) = ApiDestinationEntity(
    id = id,
    name = name.trim(),
    enabled = enabled,
    url = url.trim(),
    method = method.name,
    headersEnc = c.encrypt(HeaderCodec.encode(headers)),
    bodyType = bodyType.name,
    bodyTemplate = bodyTemplate,
    authType = authType.name,
    authUser = authUser,
    authSecretEnc = c.encrypt(authSecret),
    apiKeyHeader = apiKeyHeader.trim(),
    allowHttp = allowHttp,
    connectTimeoutSec = connectTimeoutSec,
    readTimeoutSec = readTimeoutSec,
    maxRetries = maxRetries,
    retryNonIdempotent = retryNonIdempotent,
    successContains = successContains,
    priority = priority,
    lastStatus = lastStatus,
    lastStatusAt = lastStatusAt
)

private fun String.toLines(): List<String> = lines().map { it.trim() }.filter { it.isNotEmpty() }

fun RoutingRuleEntity.toDomain(destinationIds: List<String>) = RoutingRule(
    id = id,
    name = name,
    enabled = enabled,
    priority = priority,
    senders = senders.toLines(),
    normalizePhone = normalizePhone,
    keywords = keywords.toLines(),
    pattern = pattern,
    captureGroup = captureGroup,
    minDigits = minDigits,
    maxDigits = maxDigits,
    destinationIds = destinationIds
)

fun RoutingRule.toEntity() = RoutingRuleEntity(
    id = id,
    name = name.trim(),
    enabled = enabled,
    priority = priority,
    senders = senders.map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n"),
    normalizePhone = normalizePhone,
    keywords = keywords.map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n"),
    pattern = pattern,
    captureGroup = captureGroup,
    minDigits = minDigits,
    maxDigits = maxDigits
)
