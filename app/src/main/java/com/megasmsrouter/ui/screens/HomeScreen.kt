package com.megasmsrouter.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.megasmsrouter.R
import com.megasmsrouter.data.entities.RequestHistoryEntity
import com.megasmsrouter.sms.SmsPermissionManager
import com.megasmsrouter.ui.MsrViewModel
import com.megasmsrouter.ui.components.ScreenHeader
import com.megasmsrouter.ui.components.SectionCard
import com.megasmsrouter.util.formatTime
import com.megasmsrouter.util.hostOf

@Composable
fun HomeScreen(vm: MsrViewModel, onAbout: () -> Unit) {
    val ctx = LocalContext.current
    val dests by vm.destinations.collectAsState()
    val rules by vm.rules.collectAsState()
    val forwarding by vm.forwarding.collectAsState()
    val history by vm.history.collectAsState()

    var hasPerm by remember { mutableStateOf(SmsPermissionManager.hasReceiveSms(ctx)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPerm = it }
    LifecycleResumeEffect(Unit) {
        hasPerm = SmsPermissionManager.hasReceiveSms(ctx)
        onPauseOrDispose { }
    }
    var confirm by remember { mutableStateOf(false) }

    val enabledDests = dests.filter { it.enabled }
    val enabledRules = rules.filter { r ->
        r.enabled && r.senders.isNotEmpty() && r.destinationIds.any { id -> enabledDests.any { it.id == id } }
    }
    val reason: Int? = when {
        !hasPerm -> R.string.reason_no_perm
        enabledRules.isEmpty() -> R.string.reason_no_rules
        enabledDests.isEmpty() -> R.string.reason_no_dest
        !forwarding -> R.string.reason_paused
        else -> null
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ScreenHeader(
            title = stringResource(R.string.app_name) + " (" + stringResource(R.string.app_short) + ")",
            actions = { TextButton(onClick = onAbout) { Text(stringResource(R.string.docs)) } }
        )

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (reason == null) {
                    Text(stringResource(R.string.status_ready), style = MaterialTheme.typography.titleLarge)
                } else {
                    Text(
                        stringResource(R.string.status_not_ready),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(stringResource(reason))
                }
                Text(stringResource(if (forwarding) R.string.forwarding_on else R.string.forwarding_off))
                Text(stringResource(if (hasPerm) R.string.perm_granted else R.string.perm_missing))
                Text(stringResource(R.string.cfg_rules_count, enabledRules.size))
                Text(stringResource(R.string.cfg_dest_count, enabledDests.size))
            }
        }

        Button(
            onClick = { if (forwarding) vm.setForwarding(false) else confirm = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(if (forwarding) R.string.btn_pause else R.string.btn_resume))
        }

        if (!hasPerm) {
            Button(
                onClick = { launcher.launch(Manifest.permission.RECEIVE_SMS) },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.btn_grant)) }
            OutlinedButton(
                onClick = {
                    ctx.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.btn_app_settings)) }
        }

        SectionCard(stringResource(R.string.recent_activity)) {
            if (history.isEmpty()) {
                Text(stringResource(R.string.no_activity))
            } else {
                history.take(5).forEach { HistoryRow(it) }
            }
        }

        SectionCard(stringResource(R.string.reliability_title)) {
            Text(stringResource(R.string.reliability_body))
            OutlinedButton(onClick = {
                try {
                    ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                } catch (e: Exception) {
                    // some devices do not expose this screen
                }
            }) { Text(stringResource(R.string.battery_btn)) }
        }

        Text(stringResource(R.string.privacy_note), style = MaterialTheme.typography.bodySmall)
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.confirm_body))
                    if (enabledDests.isEmpty()) Text(stringResource(R.string.confirm_none))
                    enabledDests.forEach { Text("• " + it.name + " — " + hostOf(it.url)) }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.setForwarding(true); confirm = false }) {
                    Text(stringResource(R.string.confirm_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
fun HistoryRow(h: RequestHistoryEntity) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            (if (h.success) "✓ " else "✗ ") + h.destinationName + " — " + h.method + " " + h.host,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            formatTime(h.timestamp) + " · " + (h.statusCode?.toString() ?: h.note) + " · " +
                stringResource(R.string.hist_details, h.durationMs, h.retryCount),
            style = MaterialTheme.typography.bodySmall
        )
    }
}
