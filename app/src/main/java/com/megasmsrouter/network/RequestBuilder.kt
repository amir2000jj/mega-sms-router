package com.megasmsrouter.network

import com.megasmsrouter.domain.model.*
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

object RequestBuilder {
    const val USER_AGENT = "MegaSmsRouter/1.0"

    /**
     * Builds an OkHttp request. GET never carries a body: with FORM type the template becomes
     * query parameters. Throws IllegalArgumentException for an invalid URL/header (validate first).
     */
    fun build(dest: ApiDestination, payload: MessagePayload, idempotencyKey: String? = null): Request {
        val values = Placeholders.valuesOf(payload)
        var url = dest.url.trim().toHttpUrl()
        val template = dest.bodyTemplate
        var body: RequestBody? = null

        if (dest.method == HttpMethod.GET) {
            if (dest.bodyType == BodyType.FORM && template.isNotBlank()) {
                val b = url.newBuilder()
                BodyRenderer.pairs(template, values).forEach { (k, v) -> b.addQueryParameter(k, v) }
                url = b.build()
            }
        } else {
            val customType = dest.headers
                .firstOrNull { it.name.equals("Content-Type", ignoreCase = true) }
                ?.value?.toMediaTypeOrNull()
            val media = customType ?: dest.bodyType.mediaType?.toMediaType()
            val text: String? = when (dest.bodyType) {
                BodyType.NONE -> null
                BodyType.JSON -> BodyRenderer.renderJson(template, values)
                BodyType.FORM -> BodyRenderer.renderForm(template, values)
                BodyType.TEXT -> Placeholders.substitute(template, values)
            }
            body = when {
                text != null -> text.toRequestBody(media)
                dest.method == HttpMethod.DELETE -> null
                else -> ByteArray(0).toRequestBody(null)
            }
        }

        val rb = Request.Builder().url(url).header("User-Agent", USER_AGENT)
        dest.headers.forEach { h ->
            if (!h.name.equals("Content-Type", ignoreCase = true)) rb.addHeader(h.name.trim(), h.value.trim())
        }
        when (dest.authType) {
            AuthType.NONE -> {}
            AuthType.BEARER -> rb.header("Authorization", "Bearer ${dest.authSecret}")
            AuthType.BASIC -> rb.header("Authorization", Credentials.basic(dest.authUser, dest.authSecret))
            AuthType.API_KEY -> rb.header(dest.apiKeyHeader.trim(), dest.authSecret)
        }
        if (idempotencyKey != null) rb.header("Idempotency-Key", idempotencyKey)
        rb.method(dest.method.name, body)
        return rb.build()
    }
}
