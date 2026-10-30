package com.megasmsrouter.domain.routing

import com.megasmsrouter.domain.model.RoutingRule
import com.megasmsrouter.domain.parsing.CodeExtractor
import com.megasmsrouter.domain.parsing.ExtractionConfig

data class PlannedDelivery(val destinationId: String, val ruleId: String, val code: String)

object Router {

    fun extractionOf(rule: RoutingRule) = ExtractionConfig(
        pattern = rule.pattern,
        group = rule.captureGroup,
        minDigits = rule.minDigits,
        maxDigits = rule.maxDigits
    )

    /**
     * Returns the extracted code if [rule] matches this message, otherwise null.
     * A rule matches only if: it is enabled, it has explicit senders, the sender is approved,
     * keywords (if any) are present, AND a code is found. There is no "match everything" rule.
     */
    fun matchRule(rule: RoutingRule, sender: String, body: String): String? {
        if (!rule.enabled || rule.senders.isEmpty()) return null
        if (!SenderMatcher.matches(sender, rule.senders, rule.normalizePhone)) return null
        if (rule.keywords.isNotEmpty() && rule.keywords.none { body.contains(it, ignoreCase = true) }) return null
        return CodeExtractor.extract(body, extractionOf(rule))
    }

    /**
     * Decides which destinations receive this message.
     * Rules are evaluated by ascending priority (then name). Each destination is planned at most
     * once per message: if several rules point to the same destination, the first rule wins.
     */
    fun route(
        sender: String,
        body: String,
        rules: List<RoutingRule>,
        enabledDestinationIds: Set<String>
    ): List<PlannedDelivery> {
        val planned = LinkedHashMap<String, PlannedDelivery>()
        for (rule in rules.sortedWith(compareBy({ it.priority }, { it.name }))) {
            val code = matchRule(rule, sender, body) ?: continue
            for (destId in rule.destinationIds) {
                if (destId in enabledDestinationIds && destId !in planned) {
                    planned[destId] = PlannedDelivery(destId, rule.id, code)
                }
            }
        }
        return planned.values.toList()
    }
}
