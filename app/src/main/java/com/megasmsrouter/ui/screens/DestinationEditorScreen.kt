package com.megasmsrouter.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.megasmsrouter.R
import com.megasmsrouter.domain.model.*
import com.megasmsrouter.domain.validation.DestinationIssue
import com.megasmsrouter.domain.validation.DestinationValidator
import com.megasmsrouter.domain.validation.HeaderError
import com.megasmsrouter.domain.validation.HeaderValidator
import com.megasmsrouter.domain.validation.UrlValidator
import com.megasmsrouter.network.summary
import com.megasmsrouter.security.SecretRedactor
import com.megasmsrouter.ui.MsrViewModel
import com.megasmsrouter.ui.components.*
import kotlinx.coroutines.launch
import java.util.UUID

class HeaderRow(initialName: String, initialValue: String) {
    var name by mutableStateOf(initialName)
    var value by mutableStateOf(initialValue)
}

class DestinationFormState(d: ApiDestination) {
    val id = d.id
    private val lastStatus = d.lastStatus
    private val lastStatusAt = d.lastStatusAt
    var name by mutableStateOf(d.name)
    var enabled by mutableStateOf(d.enabled)
    var url by mutableStateOf(d.url)
    var method by mutableStateOf(d.method)
    var bodyType by mutableStateOf(d.bodyType)
    var bodyTemplate by mutableStateOf(d.bodyTemplate)
    var authType by mutableStateOf(d.authType)
    var authUser by mutableStateOf(d.authUser)
    var authSecret by mutableStateOf(d.authSecret)
    var apiKeyHeader by mutableStateOf(d.apiKeyHeader)
    var allowHttp by mutableStateOf(d.allowHttp)
    var connectTimeout by mutableStateOf(d.connectTimeoutSec.toString())
    var readTimeout by mutableStateOf(d.readTimeoutSec.toString())
    var maxRetries by mutableStateOf(d.maxRetries.toString())
    var retryNonIdempotent by mutableStateOf(d.retryNonIdempotent)
    var successContains by mutableStateOf(d.successContains)
    var priority by mutableStateOf(d.priority.toString())
    val headers = mutableStateListOf<HeaderRow>().apply {
        d.headers.forEach { add(HeaderRow(it.name, it.value)) }
    }

    fun toDomain() = ApiDestination(
        id = id,
        name = name,
        enabled = enabled,
        url = url,
        method = method,
        headers = headers.map { HeaderEntry(it.name.trim(), it.value.trim()) }
            .filter { it.name.isNotEmpty() || it.value.isNotEmpty() },
        bodyType = bodyType,
        bodyTemplate = bodyTemplate,
        authType = authType,
        authUser = authUser,
        authSecret = authSecret,
        apiKeyHeader = apiKeyHeader,
        allowHttp = allowHttp,
        connectTimeoutSec = connectTimeout.trim().toIntOrNull() ?: -1,
        readTimeoutSec = readTimeout.trim().toIntOrNull() ?: -1,
        maxRetries = maxRetries.trim().toIntOrNull() ?: -1,
        retryNonIdempotent = retryNonIdempotent,
        successContains = successContains,
        priority = priority.trim().toIntOrNull() ?: 0,
        lastStatus = lastStatus,
        lastStatusAt = lastStatusAt
    )
}

@Composable
fun issueText(i: DestinationIssue): String = stringResource(
    when (i) {
        DestinationIssue.NAME_EMPTY -> R.string.issue_name
        DestinationIssue.URL_EMPTY -> R.string.issue_url_empty
        DestinationIssue.URL_MALFORMED -> R.string.issue_url_malformed
        DestinationIssue.URL_SCHEME -> R.string.issue_url_scheme
        DestinationIssue.URL_HTTP_NOT_ALLOWED -> R.string.issue_url_http
        DestinationIssue.HEADER_INVALID -> R.string.issue_header
        DestinationIssue.BODY_INVALID_JSON -> R.string.issue_json
        DestinationIssue.BODY_UNKNOWN_PLACEHOLDER -> R.string.issue_placeholder
        DestinationIssue.GET_BODY_UNSUPPORTED -> R.string.issue_get_body
        DestinationIssue.TIMEOUT_RANGE -> R.string.issue_timeout
        DestinationIssue.RETRIES_RANGE -> R.string.issue_retries
        DestinationIssue.AUTH_SECRET_MISSING -> R.string.issue_auth_secret
        DestinationIssue.AUTH_USER_MISSING -> R.string.issue_auth_user
        DestinationIssue.API_KEY_HEADER_INVALID -> R.string.issue_apikey_header
    }
)

@Composable
fun DestinationEditorScreen(vm: MsrViewModel, id: String, onClose: () -> Unit) {
    val initial by produceState<ApiDestination?>(null, id) {
        value = if (id == "new") ApiDestination(id = UUID.randomUUID().toString()) else vm.getDestination(id)
    }
    val d = initial
    if (d == null) {
        Box(Modifier.fillMaxSize()) { CircularProgressIndicator(Modifier.padding(32.dp)) }
    } else {
        DestinationForm(vm, d, isNew = id == "new", onClose = onClose)
    }
}

@Composable
private fun DestinationForm(vm: MsrViewModel, original: ApiDestination, isNew: Boolean, onClose: () -> Unit) {
    val form = remember(original.id) { DestinationFormState(original) }
    val scope = rememberCoroutineScope()
    var attempted by remember { mutableStateOf(false) }
    var testState by remember { mutableStateOf<String?>(null) }

    val current = form.toDomain()
    val issues = DestinationValidator.validate(current)
    val headerErrors = HeaderValidator.validateAll(current.headers)
    val urlError = UrlValidator.validate(form.url, form.allowHttp)

    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(12.dp)
    ) {
        ScreenHeader(
            title = stringResource(if (isNew) R.string.dest_add else R.string.dest_edit_title),
            onBack = onClose
        )

        SectionCard(stringResource(R.string.sec_general)) {
            AppTextField(form.name, { form.name = it }, stringResource(R.string.field_name))
            SwitchRow(stringResource(R.string.field_enabled), form.enabled) { form.enabled = it }
            AppTextField(
                form.priority, { form.priority = it }, stringResource(R.string.field_priority),
                keyboardType = KeyboardType.Number
            )
        }

        SectionCard(stringResource(R.string.sec_url)) {
            ChipRow(HttpMethod.values().toList(), form.method, { it.name }) {
                form.method = it
                if (it == HttpMethod.GET && form.bodyType != BodyType.NONE && form.bodyType != BodyType.FORM) {
                    form.bodyType = BodyType.FORM
                }
            }
            AppTextField(
                form.url, { form.url = it }, stringResource(R.string.field_url),
                keyboardType = KeyboardType.Uri, ltr = true,
                error = if (form.url.isNotBlank() && urlError != null) {
                    stringResource(
                        when (urlError) {
                            com.megasmsrouter.domain.validation.UrlError.HTTP_NOT_ALLOWED -> R.string.issue_url_http
                            com.megasmsrouter.domain.validation.UrlError.UNSUPPORTED_SCHEME -> R.string.issue_url_scheme
                            else -> R.string.issue_url_malformed
                        }
                    )
                } else null
            )
            SwitchRow(stringResource(R.string.allow_http), form.allowHttp) { form.allowHttp = it }
            if (form.allowHttp) {
                Text(
                    stringResource(R.string.http_warning),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        SectionCard(stringResource(R.string.sec_headers)) {
            form.headers.forEachIndexed { i, row ->
                val err = headerErrors[i]
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AppTextField(
                        row.name, { row.name = it }, stringResource(R.string.header_name),
                        modifier = Modifier.weight(1f), ltr = true,
                        error = if (err == HeaderError.DUPLICATE || err == HeaderError.INVALID_NAME ||
                            err == HeaderError.FORBIDDEN_NAME || err == HeaderError.EMPTY_NAME
                        ) stringResource(R.string.issue_header) else null
                    )
                    AppTextField(
                        row.value, { row.value = it }, stringResource(R.string.header_value),
                        modifier = Modifier.weight(1f), ltr = true,
                        masked = SecretRedactor.isSensitiveHeader(row.name),
                        error = if (err == HeaderError.INVALID_VALUE) stringResource(R.string.issue_header) else null
                    )
                }
                TextButton(onClick = { form.headers.removeAt(i) }) { Text(stringResource(R.string.header_remove)) }
            }
            OutlinedButton(onClick = { form.headers.add(HeaderRow("", "")) }) {
                Text(stringResource(R.string.header_add))
            }
        }

        SectionCard(stringResource(R.string.sec_auth)) {
            ChipRow(
                AuthType.values().toList(), form.authType,
                {
                    stringResource(
                        when (it) {
                            AuthType.NONE -> R.string.auth_none
                            AuthType.BEARER -> R.string.auth_bearer
                            AuthType.BASIC -> R.string.auth_basic
                            AuthType.API_KEY -> R.string.auth_apikey
                        }
                    )
                }
            ) { form.authType = it }
            when (form.authType) {
                AuthType.NONE -> {}
                AuthType.BEARER ->
                    AppTextField(form.authSecret, { form.authSecret = it }, stringResource(R.string.field_token), masked = true)
                AuthType.BASIC -> {
                    AppTextField(form.authUser, { form.authUser = it }, stringResource(R.string.field_username), ltr = true)
                    AppTextField(form.authSecret, { form.authSecret = it }, stringResource(R.string.field_password), masked = true)
                }
                AuthType.API_KEY -> {
                    AppTextField(form.apiKeyHeader, { form.apiKeyHeader = it }, stringResource(R.string.field_apikey_header), ltr = true)
                    AppTextField(form.authSecret, { form.authSecret = it }, stringResource(R.string.field_apikey_value), masked = true)
                }
            }
            if (form.authType != AuthType.NONE) {
                Text(stringResource(R.string.secret_note), style = MaterialTheme.typography.bodySmall)
            }
        }

        SectionCard(stringResource(R.string.sec_body)) {
            val bodyOptions =
                if (form.method == HttpMethod.GET) listOf(BodyType.NONE, BodyType.FORM) else BodyType.values().toList()
            ChipRow(
                bodyOptions, form.bodyType,
                {
                    stringResource(
                        when (it) {
                            BodyType.NONE -> R.string.body_none
                            BodyType.JSON -> R.string.body_json
                            BodyType.FORM -> R.string.body_form
                            BodyType.TEXT -> R.string.body_text
                        }
                    )
                }
            ) { form.bodyType = it }
            if (form.method == HttpMethod.GET) {
                Text(stringResource(R.string.body_get_hint), style = MaterialTheme.typography.bodySmall)
            }
            if (form.bodyType != BodyType.NONE) {
                AppTextField(
                    form.bodyTemplate, { form.bodyTemplate = it }, stringResource(R.string.field_body),
                    singleLine = false, minLines = 4, ltr = true,
                    error = if (issues.contains(DestinationIssue.BODY_INVALID_JSON)) stringResource(R.string.issue_json)
                    else if (issues.contains(DestinationIssue.BODY_UNKNOWN_PLACEHOLDER)) stringResource(R.string.issue_placeholder)
                    else null
                )
            }
        }

        SectionCard(stringResource(R.string.sec_placeholders)) {
            Text(stringResource(R.string.placeholders_help), style = MaterialTheme.typography.bodySmall)
            Placeholders.NAMES.forEach { n ->
                Text("{{" + n + "}}", style = MaterialTheme.typography.bodyMedium)
            }
        }

        SectionCard(stringResource(R.string.sec_timeout)) {
            AppTextField(form.connectTimeout, { form.connectTimeout = it }, stringResource(R.string.field_connect_timeout), keyboardType = KeyboardType.Number)
            AppTextField(form.readTimeout, { form.readTimeout = it }, stringResource(R.string.field_read_timeout), keyboardType = KeyboardType.Number)
            AppTextField(form.maxRetries, { form.maxRetries = it }, stringResource(R.string.field_retries), keyboardType = KeyboardType.Number)
            SwitchRow(stringResource(R.string.retry_non_idempotent), form.retryNonIdempotent) { form.retryNonIdempotent = it }
            Text(stringResource(R.string.retry_non_idempotent_hint), style = MaterialTheme.typography.bodySmall)
            AppTextField(form.successContains, { form.successContains = it }, stringResource(R.string.success_contains))
        }

        SectionCard(stringResource(R.string.sec_test)) {
            Text(stringResource(R.string.test_hint), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = {
                if (issues.isNotEmpty()) {
                    attempted = true
                } else {
                    testState = ""
                    scope.launch { testState = vm.sendTest(current).summary() }
                }
            }) { Text(stringResource(R.string.btn_test_send)) }
            testState?.let {
                Text(if (it.isEmpty()) stringResource(R.string.testing) else stringResource(R.string.test_result, it))
            }
        }

        if (attempted && issues.isNotEmpty()) {
            Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Column(Modifier.padding(12.dp)) {
                    issues.forEach { Text("• " + issueText(it), color = MaterialTheme.colorScheme.error) }
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (issues.isEmpty()) {
                    vm.saveDestination(current)
                    onClose()
                } else {
                    attempted = true
                }
            }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.btn_save)) }
            OutlinedButton(onClick = onClose, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.cancel)) }
        }
    }
}
