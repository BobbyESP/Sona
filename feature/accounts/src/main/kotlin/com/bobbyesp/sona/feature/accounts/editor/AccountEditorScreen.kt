/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.accounts.editor

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bobbyesp.sona.accounts.domain.AccountDraft
import com.bobbyesp.sona.accounts.domain.DraftField
import com.bobbyesp.sona.accounts.domain.DraftProblem
import com.bobbyesp.sona.accounts.ui.explain
import com.bobbyesp.sona.accounts.ui.stateText
import com.bobbyesp.sona.core.designsystem.components.RankedList
import com.bobbyesp.sona.core.designsystem.theme.SonaTheme
import com.bobbyesp.sona.core.model.settings.AudioCodec
import com.bobbyesp.sona.core.model.settings.AudioSettings
import com.bobbyesp.sona.core.model.settings.CallSettings
import com.bobbyesp.sona.core.model.settings.DtmfMethod
import com.bobbyesp.sona.core.model.settings.Override
import com.bobbyesp.sona.core.ui.LocalNetworkAccess
import com.bobbyesp.sona.core.ui.events.CollectEventsEffect
import com.bobbyesp.sona.core.ui.rememberLocalNetworkAccess
import com.bobbyesp.sona.feature.accounts.R
import com.bobbyesp.sona.sip.api.RegistrationState
import com.bobbyesp.sona.sip.api.Transport

@Composable
internal fun AccountEditorRoute(
    viewModel: AccountEditorViewModel,
    onBack: () -> Unit,
    onLeave: () -> Unit,
    onConfirmDelete: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    viewModel.effects.CollectEventsEffect { effect ->
        when (effect) {
            EditorEffect.Leave -> onLeave()
            is EditorEffect.ConfirmDelete -> onConfirmDelete(effect.id.value)
        }
    }
    AccountEditorScreen(state = state, onIntent = viewModel::onSendIntent, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountEditorScreen(
    state: EditorState,
    onIntent: (EditorIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val localNetwork = rememberLocalNetworkAccess()
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isNew) R.string.editor_title_new
                            else R.string.editor_title_edit,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.editor_back),
                        )
                    }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = { onIntent(EditorIntent.Delete) }) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = stringResource(R.string.editor_delete),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        AnimatedContent(
            targetState = state.phase is EditorPhase.Editing,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            modifier = Modifier.padding(padding).fillMaxSize(),
            label = "EditorPhase",
        ) { editing ->
            if (editing) EditorForm(state, onIntent, localNetwork)
            else CheckResult(state, onIntent, localNetwork)
        }
    }
}

@Composable
private fun EditorForm(
    state: EditorState,
    onIntent: (EditorIntent) -> Unit,
    localNetwork: LocalNetworkAccess,
) {
    val draft = state.draft
    fun edit(change: AccountDraft.() -> AccountDraft) = onIntent(EditorIntent.Edit(change))

    Column(Modifier.fillMaxSize().imePadding()) {
        Column(
            modifier =
                Modifier.weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .widthIn(max = 640.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.status?.let { LiveStatus(state) }
            if (state.isNew) {
                Text(
                    text = stringResource(R.string.editor_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Field(
                value = draft.server,
                onValueChange = { edit { copy(server = it) } },
                label = R.string.field_server,
                placeholder = R.string.field_server_hint,
                problem = state.problems[DraftField.Server],
                keyboardType = KeyboardType.Uri,
            )
            Field(
                value = draft.username,
                onValueChange = { edit { copy(username = it) } },
                label = R.string.field_username,
                problem = state.problems[DraftField.Username],
                keyboardType = KeyboardType.Ascii,
            )
            PasswordField(state, onIntent)

            TextButton(onClick = { onIntent(EditorIntent.ToggleMore) }) {
                Icon(
                    if (state.showMore) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    stringResource(
                        if (state.showMore) R.string.fewer_options else R.string.more_options,
                    ),
                )
            }
            AnimatedVisibility(
                visible = state.showMore,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                MoreOptions(state, ::edit, onIntent)
            }
            Spacer(Modifier.size(8.dp))
        }
        Button(
            // A lab's server is on the local network: ask for it now, in context. Refused, the
            // account is still saved, and the result explains why it cannot register.
            onClick = { localNetwork.request { onIntent(EditorIntent.Save) } },
            modifier =
                Modifier.fillMaxWidth()
                    .padding(16.dp)
                    .navigationBarsPadding()
                    .heightIn(min = 56.dp),
        ) {
            Text(
                stringResource(
                    if (draft.enabled) R.string.check_and_save else R.string.save,
                ),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun MoreOptions(
    state: EditorState,
    edit: (AccountDraft.() -> AccountDraft) -> Unit,
    onIntent: (EditorIntent) -> Unit,
) {
    val draft = state.draft
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Field(
            value = draft.label,
            onValueChange = { edit { copy(label = it) } },
            label = R.string.field_label,
            placeholderText = draft.suggestedLabel,
        )
        Text(stringResource(R.string.field_transport), style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            Transport.entries.forEachIndexed { index, transport ->
                SegmentedButton(
                    selected = draft.transport == transport,
                    onClick = { edit { copy(transport = transport) } },
                    shape = SegmentedButtonDefaults.itemShape(index, Transport.entries.size),
                ) {
                    Text(transport.name.uppercase())
                }
            }
        }
        Field(
            value = draft.port,
            onValueChange = { edit { copy(port = it.filter(Char::isDigit)) } },
            label = R.string.field_port,
            placeholderText = if (draft.transport == Transport.Tls) "5061" else "5060",
            problem = state.problems[DraftField.Port],
            keyboardType = KeyboardType.Number,
        )
        Field(
            value = draft.domain,
            onValueChange = { edit { copy(domain = it) } },
            label = R.string.field_domain,
            placeholder = R.string.field_domain_hint,
            problem = state.problems[DraftField.Domain],
            keyboardType = KeyboardType.Uri,
        )
        Field(
            value = draft.displayName,
            onValueChange = { edit { copy(displayName = it) } },
            label = R.string.field_display_name,
        )
        Field(
            value = draft.authUsername,
            onValueChange = { edit { copy(authUsername = it) } },
            label = R.string.field_auth_username,
            placeholder = R.string.field_auth_username_hint,
            problem = state.problems[DraftField.AuthUsername],
            keyboardType = KeyboardType.Ascii,
        )
        Field(
            value = draft.outboundProxy,
            onValueChange = { edit { copy(outboundProxy = it) } },
            label = R.string.field_outbound_proxy,
            problem = state.problems[DraftField.OutboundProxy],
            keyboardType = KeyboardType.Uri,
        )
        Field(
            value = draft.expirySeconds,
            onValueChange = { edit { copy(expirySeconds = it.filter(Char::isDigit)) } },
            label = R.string.field_expiry,
            placeholderText = "600",
            problem = state.problems[DraftField.Expiry],
            keyboardType = KeyboardType.Number,
        )
        Field(
            value = draft.voicemailNumber,
            onValueChange = { edit { copy(voicemailNumber = it) } },
            label = R.string.field_voicemail,
            placeholderText = "*97",
            keyboardType = KeyboardType.Phone,
        )
        Text(
            stringResource(R.string.overrides_title),
            style = MaterialTheme.typography.labelLarge,
        )
        OverrideRow(
            title = R.string.override_codecs,
            override = draft.overrides[AudioSettings.Codecs],
            app = state.appCodecs,
            describe = { ranking ->
                ranking.enabled.joinToString(", ", transform = AudioCodec::labelOf).ifEmpty {
                    stringResource(R.string.override_none)
                }
            },
            onChange = { edit { copy(overrides = overrides.with(AudioSettings.Codecs, it)) } },
        ) { ranking, change ->
            RankedList(ranking = ranking, label = AudioCodec::labelOf, onChange = change)
        }
        OverrideRow(
            title = R.string.override_dtmf,
            override = draft.overrides[CallSettings.Dtmf],
            app = state.appDtmf,
            describe = { stringResource(it.label) },
            onChange = { edit { copy(overrides = overrides.with(CallSettings.Dtmf, it)) } },
        ) { method, change ->
            DtmfMethod.entries.forEach { option ->
                Row(
                    Modifier.fillMaxWidth()
                        .selectable(
                            selected = option == method,
                            role = Role.RadioButton,
                            onClick = { change(option) },
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = option == method, onClick = null)
                    Spacer(Modifier.size(12.dp))
                    Text(stringResource(option.label))
                }
            }
        }
        SwitchRow(
            title = R.string.field_enabled,
            description = R.string.field_enabled_description,
            checked = draft.enabled,
            onCheckedChange = { edit { copy(enabled = it) } },
        )
        SwitchRow(
            title = R.string.field_default,
            checked = state.isDefault,
            onCheckedChange = { onIntent(EditorIntent.Default(it)) },
        )
    }
}

@Composable
private fun PasswordField(state: EditorState, onIntent: (EditorIntent) -> Unit) {
    OutlinedTextField(
        value = state.password,
        onValueChange = { onIntent(EditorIntent.Password(it)) },
        modifier = Modifier.fillMaxWidth(),
        label = {
            Text(
                stringResource(
                    if (state.isNew && state.draft.id == null) R.string.field_password
                    else R.string.field_password_keep,
                ),
            )
        },
        singleLine = true,
        isError = state.passwordMissing,
        supportingText =
            if (state.passwordMissing) {
                { Text(stringResource(R.string.problem_password_required)) }
            } else null,
        visualTransformation =
            if (state.passwordVisible) VisualTransformation.None
            else PasswordVisualTransformation(),
        keyboardOptions =
            KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        trailingIcon = {
            IconButton(onClick = { onIntent(EditorIntent.TogglePasswordVisible) }) {
                Icon(
                    if (state.passwordVisible) Icons.Rounded.VisibilityOff
                    else Icons.Rounded.Visibility,
                    contentDescription =
                        stringResource(
                            if (state.passwordVisible) R.string.field_password_hide
                            else R.string.field_password_show,
                        ),
                )
            }
        },
    )
}

@Composable
private fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    label: Int,
    modifier: Modifier = Modifier,
    placeholder: Int? = null,
    placeholderText: String? = null,
    problem: DraftProblem? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val hint = placeholderText ?: placeholder?.let { stringResource(it) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(stringResource(label)) },
        placeholder = hint?.let { { Text(it) } },
        singleLine = true,
        isError = problem != null,
        supportingText = problem?.let { { Text(stringResource(it.text)) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
    )
}

private val DraftProblem.text: Int
    get() =
        when (this) {
            DraftProblem.Required -> R.string.problem_required
            DraftProblem.NotAHost -> R.string.problem_not_a_host
            DraftProblem.NotAUsername -> R.string.problem_not_a_username
            DraftProblem.OutOfRange -> R.string.problem_out_of_range
        }

private val DtmfMethod.label: Int
    get() =
        when (this) {
            DtmfMethod.Rfc4733 -> R.string.dtmf_rfc4733
            DtmfMethod.SipInfo -> R.string.dtmf_info
            DtmfMethod.InBand -> R.string.dtmf_inband
        }

/**
 * An option this account may have its own value for. The row says which applies, *Uses the app's
 * setting (Opus, G.722)* or the account's own; it opens a dialog with the switch between the two
 * and, for the account's own, [editor] to change it. Turning the app's setting off starts from the
 * app's value, so the account's own differs only where the user changes it.
 */
@Composable
private fun <T> OverrideRow(
    title: Int,
    override: Override<T>,
    app: T,
    describe: @Composable (T) -> String,
    onChange: (Override<T>) -> Unit,
    editor: @Composable (value: T, change: (T) -> Unit) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = {
            Text(
                when (override) {
                    Override.Inherit -> stringResource(R.string.override_inherit, describe(app))
                    is Override.Value ->
                        stringResource(R.string.override_own, describe(override.value))
                },
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { open = true },
    )
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    SwitchRow(
                        title = R.string.override_use_app,
                        checked = override is Override.Inherit,
                        onCheckedChange = { useApp ->
                            onChange(if (useApp) Override.Inherit else Override.Value(app))
                        },
                    )
                    when (override) {
                        Override.Inherit ->
                            Text(
                                describe(app),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        is Override.Value -> editor(override.value) { onChange(Override.Value(it)) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { open = false }) { Text(stringResource(R.string.done)) }
            },
        )
    }
}

@Composable
private fun SwitchRow(
    title: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    description: Int? = null,
) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = description?.let { { Text(stringResource(it)) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        // The whole row is the switch: seen on a phone, where a touch on the words did nothing.
        modifier =
            Modifier.toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
    )
}

/** An existing account's state at the top of its editor, with why it failed when it did. */
@Composable
private fun LiveStatus(state: EditorState) {
    val status = state.status ?: return
    val failed = status.registration as? RegistrationState.Failed
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (failed != null && status.account.enabled)
                        MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(status.stateText(), style = MaterialTheme.typography.titleMedium)
            if (failed != null && status.account.enabled) {
                val problem = failed.explain(status.account)
                Text(problem.sentence, style = MaterialTheme.typography.bodyMedium)
                problem.technical?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = SonaTheme.typography.monospace,
                    )
                }
            }
        }
    }
}

/** Waiting for the server, then what it answered, in words, with the SIP status under it. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CheckResult(
    state: EditorState,
    onIntent: (EditorIntent) -> Unit,
    localNetwork: LocalNetworkAccess,
) {
    val status = state.status
    val account = status?.account
    val user = account?.username ?: state.draft.username
    val server = account?.server?.host ?: state.draft.server
    val registration = status?.registration
    val phase = state.phase

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        when {
            phase is EditorPhase.Checking -> {
                LoadingIndicator(Modifier.size(72.dp))
                Text(
                    stringResource(R.string.checking, user, server),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            account != null && !account.enabled -> {
                ResultIcon(Icons.Rounded.CheckCircle, MaterialTheme.colorScheme.primary)
                Text(
                    stringResource(R.string.checked_off),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            registration == RegistrationState.Registered -> {
                ResultIcon(Icons.Rounded.CheckCircle, MaterialTheme.colorScheme.primary)
                Text(
                    stringResource(R.string.checked_ok, user, server),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    stringResource(R.string.checked_ok_description),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            registration is RegistrationState.Failed &&
                account != null &&
                !localNetwork.granted -> {
                ResultIcon(Icons.Rounded.Lan, MaterialTheme.colorScheme.error)
                Text(
                    stringResource(R.string.checked_failed),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    stringResource(R.string.local_network_needed),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Button(
                    onClick = {
                        localNetwork.request { granted ->
                            if (granted) onIntent(EditorIntent.Retry)
                        }
                    },
                ) {
                    Text(stringResource(R.string.local_network_allow))
                }
            }
            registration is RegistrationState.Failed && account != null -> {
                val problem = registration.explain(account)
                ResultIcon(Icons.Rounded.ErrorOutline, MaterialTheme.colorScheme.error)
                Text(
                    stringResource(R.string.checked_failed),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(problem.sentence, style = MaterialTheme.typography.bodyLarge)
                problem.technical?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = SonaTheme.typography.monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> {
                ResultIcon(Icons.Rounded.Schedule, MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    stringResource(R.string.checked_slow),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        if (phase is EditorPhase.Checked) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { onIntent(EditorIntent.EditAgain) }) {
                    Text(stringResource(R.string.edit_again))
                }
                Button(
                    onClick = { onIntent(EditorIntent.Done) },
                    contentPadding = ButtonDefaults.ContentPadding,
                ) {
                    Text(stringResource(R.string.done))
                }
            }
        }
    }
}

@Composable
private fun ResultIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color) {
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(72.dp))
}

@PreviewLightDark
@Composable
private fun AccountEditorPreview() {
    SonaTheme {
        AccountEditorScreen(
            state =
                EditorState(
                    isNew = true,
                    draft = AccountDraft(server = "192.168.1.10", username = "102"),
                    showMore = true,
                ),
            onIntent = {},
            onBack = {},
        )
    }
}
