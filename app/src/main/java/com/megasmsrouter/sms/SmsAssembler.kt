package com.megasmsrouter.sms

import com.megasmsrouter.domain.model.IncomingSms

data class RawSmsPart(val address: String?, val body: String?, val timestamp: Long)

object SmsAssembler {
    /** Joins the parts of a multipart SMS (same sender, in arrival order) into one message. */
    fun assemble(parts: List<RawSmsPart>): List<IncomingSms> {
        val bySender = LinkedHashMap<String, MutableList<RawSmsPart>>()
        for (p in parts) {
            val addr = p.address?.trim().orEmpty()
            if (addr.isEmpty()) continue
            bySender.getOrPut(addr) { mutableListOf() }.add(p)
        }
        return bySender.map { (addr, ps) ->
            IncomingSms(addr, ps.joinToString("") { it.body.orEmpty() }, ps.first().timestamp)
        }
    }
}
