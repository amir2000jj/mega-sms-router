package com.megasmsrouter.domain.routing

import java.security.MessageDigest

/** Stable key used to detect the same SMS being delivered twice. Only a SHA-256 hash is stored. */
object MessageKey {
    fun compute(sender: String, timestamp: Long, body: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest("$sender|$timestamp|$body".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
