package com.megasmsrouter

import com.megasmsrouter.domain.model.RoutingRule
import com.megasmsrouter.domain.routing.MessageKey
import com.megasmsrouter.domain.routing.Router
import com.megasmsrouter.domain.routing.SenderMatcher
import com.megasmsrouter.sms.RawSmsPart
import com.megasmsrouter.sms.SmsAssembler
import org.junit.Assert.*
import org.junit.Test

class RoutingTest {
    private fun rule(
        id: String, senders: List<String>, dests: List<String>, priority: Int = 0,
        keywords: List<String> = emptyList(), pattern: String = "", group: Int = 0,
        enabled: Boolean = true, normalize: Boolean = false
    ) = RoutingRule(
        id = id, name = id, enabled = enabled, priority = priority, senders = senders,
        keywords = keywords, pattern = pattern, captureGroup = group,
        normalizePhone = normalize, destinationIds = dests
    )

    @Test fun exactSenderMatchIsCaseInsensitive() {
        assertTrue(SenderMatcher.matches("MyService", listOf("myservice"), false))
        assertFalse(SenderMatcher.matches("Other", listOf("myservice"), false))
        assertFalse(SenderMatcher.matches("", listOf(""), false))
    }

    @Test fun phoneNormalization() {
        assertTrue(SenderMatcher.matches("+989121234567", listOf("09121234567"), true))
        assertTrue(SenderMatcher.matches("۰۹۱۲۱۲۳۴۵۶۷", listOf("+98 912 123 4567"), true))
        assertFalse(SenderMatcher.matches("+989121234567", listOf("09121234567"), false))
        assertFalse(SenderMatcher.matches("+989121234567", listOf("09129999999"), true))
    }

    @Test fun ruleWithoutSendersNeverMatches() {
        val r = rule("a", emptyList(), listOf("d1"))
        assertTrue(Router.route("anyone", "code 123456", listOf(r), setOf("d1")).isEmpty())
    }

    @Test fun unapprovedSenderIsIgnored() {
        val r = rule("a", listOf("Bank"), listOf("d1"))
        assertTrue(Router.route("Stranger", "code 123456", listOf(r), setOf("d1")).isEmpty())
    }

    @Test fun messageWithoutCodeIsNotForwarded() {
        val r = rule("a", listOf("Bank"), listOf("d1"))
        assertTrue(Router.route("Bank", "hello there", listOf(r), setOf("d1")).isEmpty())
    }

    @Test fun keywordFilter() {
        val r = rule("a", listOf("Bank"), listOf("d1"), keywords = listOf("login"))
        assertTrue(Router.route("Bank", "promo 123456", listOf(r), setOf("d1")).isEmpty())
        assertEquals(1, Router.route("Bank", "Your LOGIN code 123456", listOf(r), setOf("d1")).size)
    }

    @Test fun disabledRuleAndDisabledDestinationAreSkipped() {
        val off = rule("a", listOf("Bank"), listOf("d1"), enabled = false)
        assertTrue(Router.route("Bank", "code 123456", listOf(off), setOf("d1")).isEmpty())
        val on = rule("b", listOf("Bank"), listOf("d1"))
        assertTrue(Router.route("Bank", "code 123456", listOf(on), emptySet()).isEmpty())
    }

    @Test fun modeA_differentRulesGoToDifferentDestinations() {
        val a = rule("A", listOf("ServiceA"), listOf("dA"))
        val b = rule("B", listOf("ServiceB"), listOf("dB"), pattern = "PIN (\\d{5})", group = 1)
        val rules = listOf(a, b)
        val ra = Router.route("ServiceA", "code 111222", rules, setOf("dA", "dB"))
        assertEquals(listOf("dA"), ra.map { it.destinationId })
        assertEquals("111222", ra[0].code)
        val rb = Router.route("ServiceB", "PIN 98765", rules, setOf("dA", "dB"))
        assertEquals(listOf("dB"), rb.map { it.destinationId })
        assertEquals("98765", rb[0].code)
    }

    @Test fun modeB_oneMessageToMultipleDestinations() {
        val r = rule("A", listOf("Bank"), listOf("d1", "d2", "d3"))
        val p = Router.route("Bank", "code 123456", listOf(r), setOf("d1", "d2", "d3"))
        assertEquals(listOf("d1", "d2", "d3"), p.map { it.destinationId })
    }

    @Test fun overlappingRulesDoNotCreateDuplicateRequests() {
        val low = rule("low", listOf("Bank"), listOf("d1", "d2"), priority = 5)
        val high = rule("high", listOf("Bank"), listOf("d1"), priority = 1)
        val p = Router.route("Bank", "code 123456", listOf(low, high), setOf("d1", "d2"))
        assertEquals(2, p.size)
        assertEquals("high", p.first { it.destinationId == "d1" }.ruleId) // best priority wins
        assertEquals("low", p.first { it.destinationId == "d2" }.ruleId)
    }

    @Test fun messageKeyIsStableAndSensitive() {
        val k = MessageKey.compute("A", 1L, "body")
        assertEquals(k, MessageKey.compute("A", 1L, "body"))
        assertNotEquals(k, MessageKey.compute("A", 2L, "body"))
        assertNotEquals(k, MessageKey.compute("B", 1L, "body"))
        assertNotEquals(k, MessageKey.compute("A", 1L, "body2"))
        assertEquals(64, k.length)
    }

    @Test fun multipartMessagesAreJoinedPerSender() {
        val out = SmsAssembler.assemble(
            listOf(
                RawSmsPart("Bank", "Your code ", 10L),
                RawSmsPart("Bank", "is 123456", 10L),
                RawSmsPart("Other", "hi", 11L),
                RawSmsPart(null, "ignored", 12L)
            )
        )
        assertEquals(2, out.size)
        assertEquals("Your code is 123456", out[0].body)
        assertEquals("Bank", out[0].sender)
        assertEquals(10L, out[0].timestamp)
        assertEquals("Other", out[1].sender)
    }
}
