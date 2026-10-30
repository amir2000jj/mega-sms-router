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
import com.megasmsrouter.domain.model.ApiDestination
import com.megasmsrouter.network.FailureKind
import com.megasmsrouter.network.NetworkResult
import com.megasmsrouter.network.summary
import com.megasmsrouter.ui.MsrViewModel
import com.megasmsrouter.ui.components.ScreenHeader
import com.megasmsrouter.util.formatTime
import com.megasmsrouter.util.hostOf
import kotlinx.coroutines.launch

@Composable
fun DestinationsScreen(vm: MsrViewModel, onEdit: (String) -> Unit) {
    val list by vm.destinations.collectAsState()
    val scope = rememberCoroutineScope()
    val copySuffix = stringResource(R.string.copy_suffix)
    var toDelete by remember { mutableStateOf<ApiDestination?>(null) }
    var toTest by remember { mutableStateOf<ApiDestination?>(null) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = stringResource(R.string.nav_destinations),
            actions = { Button(onClick = { onEdit("new") }) { Text(stringResource(R.string.dest_add)) } }
        )
        if (list.isEmpty()) {
            Text(stringResource(R.string.dest_empty), Modifier.padding(16.dp))
        } else {
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(list, key = { it.id }) { d ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(d.name, style = MaterialTheme.typography.titleMedium)
                                    Text(d.method.name + " · " + hostOf(d.url))
                                    Text(
                                        stringResource(if (d.enabled) R.string.enabled else R.string.disabled),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    d.lastStatus?.let {
                                        Text(
                                            stringResource(R.string.last_status, it, formatTime(d.lastStatusAt ?: 0L)),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                                Switch(checked = d.enabled, onCheckedChange = { vm.setDestinationEnabled(d.id, it) })
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { onEdit(d.id) }) { Text(stringResource(R.string.edit)) }
                                TextButton(onClick = { toTest = d }) { Text(stringResource(R.string.test)) }
                                TextButton(onClick = { vm.duplicateDestination(d.id, copySuffix) }) {
                                    Text(stringResource(R.string.duplicate))
                                }
                                TextButton(onClick = { toDelete = d }) { Text(stringResource(R.string.delete)) }
                            }
                        }
                    }
                }
            }
        }
    }

    toDelete?.let { d ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(stringResource(R.string.delete)) },
            text = { Text(stringResource(R.string.delete_dest_confirm, d.name)) },
            confirmButton = {
                TextButton(onClick = { vm.deleteDestination(d.id); toDelete = null }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    toTest?.let { d ->
        AlertDialog(
            onDismissRequest = { if (!testing) toTest = null },
            title = { Text(stringResource(R.string.test)) },
            text = {
                Text(
                    when {
                        testing -> stringResource(R.string.testing)
                        testResult != null -> stringResource(R.string.test_result, testResult ?: "")
                        else -> stringResource(R.string.test_confirm, d.name)
                    }
                )
            },
            confirmButton = {
                if (testResult == null && !testing) {
                    TextButton(onClick = {
                        testing = true
                        scope.launch {
                            val r = vm.sendTest(d)
                            testResult = r.summary()
                            testing = false
                        }
                    }) { Text(stringResource(R.string.btn_test_send)) }
                }
            },
            dismissButton = {
                TextButton(onClick = { toTest = null; testResult = null }, enabled = !testing) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }
}
