package com.megasmsrouter.security

object SecretRedactor {
    private val SENSITIVE = listOf("authorization", "token", "secret", "key", "password", "cookie", "auth")

    fun isSensitiveHeader(name: String): Boolean {
        val n = name.lowercase()
        return SENSITIVE.any { n.contains(it) }
    }

    /** Replaces every occurrence of the given secrets in [text] with ***. */
    fun redact(text: String, secrets: Collection<String>): String {
        var r = text
        for (s in secrets) if (s.length >= 3) r = r.replace(s, "***")
        return r
    }
}
