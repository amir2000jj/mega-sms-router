package com.megasmsrouter.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.megasmsrouter.R
import com.megasmsrouter.domain.model.RoutingRule
import com.megasmsrouter.domain.routing.Router
import com.megasmsrouter.domain.validation.RuleIssue
import com.megasmsrouter.domain.validation.RuleValidator
import com.megasmsrouter.ui.MsrViewModel
import com.megasmsrouter.ui.components.*
import java.util.UUID

@Composable
fun RuleEditorScreen(vm: MsrViewModel, id: String, onClose: () -> Unit) {
    val initial by produceState<RoutingRule?>(null, id) {
        value = if (id == "new") RoutingRule(id = UUID.randomUUID().toString()) else vm.getRule(id)
    }
    val r = initial
    if (r == null) {
        Box(Modifier.fillMaxSize()) { CircularProgressIndicator(Modifier.padding(32.dp)) }
    } else {
        RuleForm(vm, r, isNew = id == "new", onClose = onClose)
    }
}

@Composable
private fun ruleIssueText(i: RuleIssue): String = stringResource(
    when (i) {
        RuleIssue.NAME_EMPTY -> R.string.issue_name
        RuleIssue.NO_SENDERS -> R.string.issue_rule_senders
        RuleIssue.NO_DESTINATIONS -> R.string.issue_rule_dests
        RuleIssue.PATTERN_INVALID -> R.string.issue_rule_pattern
        RuleIssue.GROUP_RANGE -> R.string.issue_rule_group
        RuleIssue.DIGITS_RANGE -> R.string.issue_rule_digits
    }
)

@Composable
private fun RuleForm(vm: MsrViewModel, original: RoutingRule, isNew: Boolean, onClose: () -> Unit) {
    val dests by vm.destinations.collectAsState()
    var name by remember { mutableStateOf(original.name) }
    var enabled by remember { mutableStateOf(original.enabled) }
    var priority by remember { mutableStateOf(original.priority.toString()) }
    var senders by remember { mutableStateOf(original.senders.joinToString("\n")) }
    var normalize by remember { mutableStateOf(original.normalizePhone) }
    var keywords by remember { mutableStateOf(original.keywords.joinToString("\n")) }
    var pattern by remember { mutableStateOf(original.pattern) }
    var group by remember { mutableStateOf(original.captureGroup.toString()) }
    var minD by remember { mutableStateOf(original.minDigits.toString()) }
    var maxD by remember { mutableStateOf(original.maxDigits.toString()) }
    val selected = remember { mutableStateListOf<String>().apply { addAll(original.destinationIds) } }
    var sampleSender by remember { mutableStateOf("") }
    var sampleBody by remember { mutableStateOf("") }
    var sampleResult by remember { mutableStateOf<String?>(null) }
    var attempted by remember { mutableStateOf(false) }

    val current = RoutingRule(
        id = original.id,
        name = name,
        enabled = enabled,
        priority = priority.trim().toIntOrNull() ?: 0,
        senders = senders.lines().map { it.trim() }.filter { it.isNotEmpty() },
        normalizePhone = normalize,
        keywords = keywords.lines().map { it.trim() }.filter { it.isNotEmpty() },
        pattern = pattern.trim(),
        captureGroup = group.trim().toIntOrNull() ?: -1,
        minDigits = minD.trim().toIntOrNull() ?: -1,
        maxDigits = maxD.trim().toIntOrNull() ?: -1,
        destinationIds = selected.toList()
    )
    val issues = RuleValidator.validate(current)
    val matchedText = stringResource(R.string.sample_match)
    val noMatchText = stringResource(R.string.sample_nomatch)

    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(12.dp)) {
        ScreenHeader(
            title = stringResource(if (isNew) R.string.rule_add else R.string.rule_edit_title),
            onBack = onClose
        )

        SectionCard(stringResource(R.string.sec_general)) {
            AppTextField(name, { name = it }, stringResource(R.string.field_name))
            SwitchRow(stringResource(R.string.field_enabled), enabled) { enabled = it }
            AppTextField(priority, { priority = it }, stringResource(R.string.field_priority), keyboardType = KeyboardType.Number)
        }

        SectionCard(stringResource(R.string.sec_senders)) {
            AppTextField(
                senders, { senders = it }, stringResource(R.string.rule_senders),
                singleLine = false, minLines = 3, ltr = true
            )
            Text(stringResource(R.string.rule_senders_hint), style = MaterialTheme.typography.bodySmall)
            SwitchRow(stringResource(R.string.rule_normalize), normalize) { normalize = it }
            AppTextField(
                keywords, { keywords = it }, stringResource(R.string.rule_keywords),
                singleLine = false, minLines = 2
            )
        }

        SectionCard(stringResource(R.string.sec_extraction)) {
            AppTextField(pattern, { pattern = it }, stringResource(R.string.rule_pattern), ltr = true)
            if (pattern.isBlank()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppTextField(minD, { minD = it }, stringResource(R.string.rule_min), Modifier.weight(1f), keyboardType = KeyboardType.Number)
                    AppTextField(maxD, { maxD = it }, stringResource(R.string.rule_max), Modifier.weight(1f), keyboardType = KeyboardType.Number)
                }
            } else {
                AppTextField(group, { group = it }, stringResource(R.string.rule_group), keyboardType = KeyboardType.Number)
            }
        }

        SectionCard(stringResource(R.string.rule_destinations)) {
            if (dests.isEmpty()) Text(stringResource(R.string.rule_no_dest))
            dests.forEach { d ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = d.id in selected,
                        onCheckedChange = { on -> if (on) { if (d.id !in selected) selected.add(d.id) } else selected.remove(d.id) }
                    )
                    Text(d.name + (if (d.enabled) "" else " (" + stringResource(R.string.disabled) + ")"))
                }
            }
        }

        SectionCard(stringResource(R.string.rule_sample)) {
            AppTextField(sampleSender, { sampleSender = it }, stringResource(R.string.sample_sender), ltr = true)
            AppTextField(sampleBody, { sampleBody = it }, stringResource(R.string.sample_body), singleLine = false, minLines = 3)
            OutlinedButton(onClick = {
                val code = Router.matchRule(current.copy(enabled = true), sampleSender, sampleBody)
                sampleResult = if (code != null) matchedText.replace("%s", code) else noMatchText
            }) { Text(stringResource(R.string.btn_run_test)) }
            sampleResult?.let { Text(it) }
            Text(stringResource(R.string.sample_note), style = MaterialTheme.typography.bodySmall)
        }

        if (attempted && issues.isNotEmpty()) {
            Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Column(Modifier.padding(12.dp)) {
                    issues.forEach { Text("• " + ruleIssueText(it), color = MaterialTheme.colorScheme.error) }
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (issues.isEmpty()) {
                    vm.saveRule(current)
                    onClose()
                } else {
                    attempted = true
                }
            }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.btn_save)) }
            OutlinedButton(onClick = onClose, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.cancel)) }
        }
    }
}
