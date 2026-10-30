package com.megasmsrouter.domain.model

/**
 * Placeholder syntax: {{name}} (optional spaces inside the braces are allowed).
 * Names: code, sender, message, timestamp (epoch millis), destination_id, message_id.
 * Unknown names are left untouched and rejected by validation.
 */
object Placeholders {
    val NAMES = listOf("code", "sender", "message", "timestamp", "destination_id", "message_id")
    private val REGEX = Regex("\\{\\{\\s*([a-z_]+)\\s*\\}\\}")

    fun valuesOf(p: MessagePayload): Map<String, String> = mapOf(
        "code" to p.code,
        "sender" to p.sender,
        "message" to p.message,
        "timestamp" to p.timestamp.toString(),
        "destination_id" to p.destinationId,
        "message_id" to p.messageId
    )

    /** Replacement text is inserted literally (no regex group syntax). */
    fun substitute(text: String, values: Map<String, String>): String =
        REGEX.replace(text) { m -> values[m.groupValues[1]] ?: m.value }

    fun used(text: String): Set<String> =
        REGEX.findAll(text).map { it.groupValues[1] }.toSet()

    fun unknown(text: String): Set<String> = used(text) - NAMES.toSet()

    /** Fields a destination really needs (data minimisation: only these are stored for delivery). */
    fun usedBy(d: ApiDestination): Set<String> =
        if (d.bodyType == BodyType.NONE) emptySet() else used(d.bodyTemplate)
}
