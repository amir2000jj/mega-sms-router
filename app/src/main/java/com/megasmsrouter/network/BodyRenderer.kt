package com.megasmsrouter.network

import com.megasmsrouter.domain.model.Placeholders
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.URLEncoder

/**
 * Builds bodies WITHOUT string concatenation of JSON: the template is parsed, placeholders are
 * replaced only inside string values, and the tree is serialised again, so quotes, newlines and
 * unicode in an SMS can never break the JSON.
 */
object BodyRenderer {

    fun renderJson(template: String, values: Map<String, String>): String {
        val parsed = JSONTokener(template).nextValue()
        return substituteNode(parsed, values).toString()
    }

    private fun substituteNode(node: Any?, values: Map<String, String>): Any? = when (node) {
        is JSONObject -> {
            val out = JSONObject()
            val keys = node.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                out.put(k, substituteNode(node.get(k), values))
            }
            out
        }
        is JSONArray -> {
            val out = JSONArray()
            for (i in 0 until node.length()) out.put(substituteNode(node.get(i), values))
            out
        }
        is String -> Placeholders.substitute(node, values)
        else -> node
    }

    /** "a={{code}}&b=x" -> [(a, 123456), (b, x)] (values substituted, not yet encoded). */
    fun pairs(template: String, values: Map<String, String>): List<Pair<String, String>> =
        template.split("&").map { it.trim() }.filter { it.isNotEmpty() }.map { p ->
            val i = p.indexOf('=')
            if (i < 0) Pair(p, "") else Pair(p.substring(0, i), Placeholders.substitute(p.substring(i + 1), values))
        }

    fun renderForm(template: String, values: Map<String, String>): String =
        pairs(template, values).joinToString("&") { (k, v) -> "${enc(k)}=${enc(v)}" }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}
