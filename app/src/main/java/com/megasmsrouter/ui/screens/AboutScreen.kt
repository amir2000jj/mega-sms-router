package com.megasmsrouter.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.megasmsrouter.R
import com.megasmsrouter.ui.MsrViewModel
import com.megasmsrouter.ui.components.ScreenHeader
import com.megasmsrouter.ui.components.SectionCard

@Composable
fun AboutScreen(vm: MsrViewModel, onBack: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        ScreenHeader(title = stringResource(R.string.docs), onBack = onBack)
        SectionCard(stringResource(R.string.about_placeholders_title)) { Text(stringResource(R.string.about_placeholders)) }
        SectionCard(stringResource(R.string.about_routing_title)) { Text(stringResource(R.string.about_routing)) }
        SectionCard(stringResource(R.string.about_delivery_title)) { Text(stringResource(R.string.about_delivery)) }
        SectionCard(stringResource(R.string.about_limits_title)) { Text(stringResource(R.string.about_limits)) }
        SectionCard(stringResource(R.string.about_privacy_title)) {
            Text(stringResource(R.string.about_privacy))
            OutlinedButton(onClick = { confirm = true }) { Text(stringResource(R.string.delete_all)) }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.delete_all)) },
            text = { Text(stringResource(R.string.delete_all_confirm)) },
            confirmButton = {
                TextButton(onClick = { vm.deleteAllData(); confirm = false }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}
