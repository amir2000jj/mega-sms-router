package com.megasmsrouter.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.megasmsrouter.R
import com.megasmsrouter.domain.model.RoutingRule
import com.megasmsrouter.ui.MsrViewModel
import com.megasmsrouter.ui.components.ScreenHeader

@Composable
fun RulesScreen(vm: MsrViewModel, onEdit: (String) -> Unit) {
    val list by vm.rules.collectAsState()
    val dests by vm.destinations.collectAsState()
    var toDelete by remember { mutableStateOf<RoutingRule?>(null) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = stringResource(R.string.nav_rules),
            actions = { Button(onClick = { onEdit("new") }) { Text(stringResource(R.string.rule_add)) } }
        )
        if (list.isEmpty()) {
            Text(stringResource(R.string.rule_empty), Modifier.padding(16.dp))
        } else {
            LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list, key = { it.id }) { r ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(r.name, style = MaterialTheme.typography.titleMedium)
                                    Text(stringResource(R.string.rule_summary_senders, r.senders.joinToString("، ")))
                                    Text(
                                        stringResource(
                                            R.string.rule_summary_dests,
                                            r.destinationIds.mapNotNull { id -> dests.firstOrNull { it.id == id }?.name }
                                                .joinToString("، ")
                                        ),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Switch(checked = r.enabled, onCheckedChange = { vm.setRuleEnabled(r.id, it) })
                            }
                            Row {
                                TextButton(onClick = { onEdit(r.id) }) { Text(stringResource(R.string.edit)) }
                                TextButton(onClick = { toDelete = r }) { Text(stringResource(R.string.delete)) }
                            }
                        }
                    }
                }
            }
        }
    }

    toDelete?.let { r ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(stringResource(R.string.delete)) },
            text = { Text(stringResource(R.string.delete_rule_confirm, r.name)) },
            confirmButton = {
                TextButton(onClick = { vm.deleteRule(r.id); toDelete = null }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}
