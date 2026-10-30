package com.megasmsrouter.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.megasmsrouter.R
import com.megasmsrouter.ui.MsrViewModel
import com.megasmsrouter.ui.components.ScreenHeader

@Composable
fun HistoryScreen(vm: MsrViewModel) {
    val list by vm.history.collectAsState()
    var confirm by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = stringResource(R.string.nav_history),
            actions = {
                if (list.isNotEmpty()) TextButton(onClick = { confirm = true }) {
                    Text(stringResource(R.string.hist_clear))
                }
            }
        )
        if (list.isEmpty()) {
            Text(stringResource(R.string.hist_empty), Modifier.padding(16.dp))
        } else {
            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp)) {
                items(list, key = { it.id }) { h ->
                    HistoryRow(h)
                    HorizontalDivider()
                }
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.hist_clear)) },
            text = { Text(stringResource(R.string.hist_clear_confirm)) },
            confirmButton = {
                TextButton(onClick = { vm.clearHistory(); confirm = false }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}
