/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.accounts.editor

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Voicemail
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bobbyesp.sona.accounts.domain.AccountDraft
import com.bobbyesp.sona.accounts.domain.DraftField
import com.bobbyesp.sona.accounts.domain.DraftProblem
import com.bobbyesp.sona.accounts.ui.explain
import com.bobbyesp.sona.accounts.ui.stateText
import com.bobbyesp.sona.core.designsystem.components.ConnectedChoice
import com.bobbyesp.sona.core.designsystem.components.ListHeadline
import com.bobbyesp.sona.core.designsystem.components.RankedList
import com.bobbyesp.sona.core.designsystem.components.SectionHeader
import com.bobbyesp.sona.core.designsystem.components.SegmentedChoiceList
import com.bobbyesp.sona.core.designsystem.components.SonaTextField
import com.bobbyesp.sona.core.designsystem.components.SonaTextFieldDefaults
import com.bobbyesp.sona.core.designsystem.components.SonaTextFieldShapes
import com.bobbyesp.sona.core.designsystem.components.segmentedListColors
import com.bobbyesp.sona.core.designsystem.theme.SonaTheme
import com.bobbyesp.sona.core.model.settings.AudioCodec
import com.bobbyesp.sona.core.model.settings.AudioSettings
import com.bobbyesp.sona.core.model.settings.CallSettings
import com.bobbyesp.sona.core.model.settings.DtmfMethod
import com.bobbyesp.sona.core.model.settings.MessageSettings
import com.bobbyesp.sona.core.model.settings.MessageSizeLimit
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

/**
 * Adding or editing an account: a form in groups, the sign-in first and what has a default behind
 * *More options*, then the server's answer once it is saved.
 *
 * Built like the keypad, the reference for Sona's look: [SonaTextField]s for what is typed,
 * segmented list items for what is chosen or switched, a connected group for a choice of three,
 * section headers that TalkBack reads as headings, and one primary action at the bottom.
 */
@Composable
internal fun AccountEditorScreen(
    state: EditorState,
    onIntent: (EditorIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val localNetwork = rememberLocalNetworkAccess()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val motion = MaterialTheme.motionScheme
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isNew) R.string.editor_title_new
                            else R.string.editor_title_edit,
                        ),
                    )
                },
                subtitle = state.status?.account?.label?.let { { Text(it) } },
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
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        AnimatedContent(
            targetState = state.phase is EditorPhase.Editing,
            transitionSpec = {
                fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec())
            },
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
    val motion = MaterialTheme.motionScheme
    fun edit(change: AccountDraft.() -> AccountDraft) = onIntent(EditorIntent.Edit(change))

    Column(Modifier.fillMaxSize().imePadding()) {
        Column(
            modifier =
                Modifier.weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = ScreenMargin)
                    .widthIn(max = FormMaxWidth),
            verticalArrangement = Arrangement.spacedBy(FieldGap),
        ) {
            state.status?.let { LiveStatus(state) }
            if (state.isNew) {
                Text(
                    text = stringResource(R.string.editor_intro),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }

            Header(R.string.editor_section_sign_in)
            FieldGroup {
                EditorField(
                    shapes = SonaTextFieldDefaults.segmentedShapes(0, 3),
                    value = draft.server,
                    onValueChange = { edit { copy(server = it) } },
                    label = R.string.field_server,
                    icon = Icons.Rounded.Dns,
                    placeholder = stringResource(R.string.field_server_hint),
                    problem = state.problems[DraftField.Server],
                    keyboardType = KeyboardType.Uri,
                )
                EditorField(
                    shapes = SonaTextFieldDefaults.segmentedShapes(1, 3),
                    value = draft.username,
                    onValueChange = { edit { copy(username = it) } },
                    label = R.string.field_username,
                    icon = Icons.Rounded.Dialpad,
                    problem = state.problems[DraftField.Username],
                    keyboardType = KeyboardType.Ascii,
                )
                PasswordField(state, onIntent, SonaTextFieldDefaults.segmentedShapes(2, 3))
            }

            Header(R.string.editor_section_account)
            AccountSwitches(state, ::edit, onIntent)

            Spacer(Modifier.size(4.dp))
            MoreOptionsToggle(
                expanded = state.showMore,
                onToggle = { onIntent(EditorIntent.ToggleMore) },
            )
            AnimatedVisibility(
                visible = state.showMore,
                enter =
                    expandVertically(motion.defaultSpatialSpec()) +
                        fadeIn(motion.defaultEffectsSpec()),
                exit =
                    shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
            ) {
                MoreOptions(state, ::edit)
            }
            Spacer(Modifier.size(16.dp))
        }
        SaveButton(
            enabled = draft.enabled,
            // A lab's server is on the local network: ask for it now, in context. Refused, the
            // account is still saved, and the result explains why it cannot register.
            onClick = { localNetwork.request { onIntent(EditorIntent.Save) } },
        )
    }
}

/** A group's heading, lined up with the fields' text. */
@Composable
private fun Header(text: Int) {
    SectionHeader(
        text = stringResource(text),
        contentPadding = PaddingValues(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 0.dp),
    )
}

/**
 * The one action of the form, at its foot and above the keyboard: a medium button that squares
 * under the finger, saying what saving does (it checks the account with the server when it is on).
 */
@Composable
private fun SaveButton(enabled: Boolean, onClick: () -> Unit) {
    val height = ButtonDefaults.MediumContainerHeight
    Button(
        onClick = onClick,
        shapes = ButtonDefaults.shapes(),
        contentPadding = ButtonDefaults.contentPaddingFor(height),
        modifier =
            Modifier.fillMaxWidth()
                .padding(horizontal = ScreenMargin, vertical = 12.dp)
                .navigationBarsPadding()
                .heightIn(min = height),
    ) {
        Icon(
            imageVector = Icons.Rounded.Check,
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.iconSizeFor(height)),
        )
        Spacer(Modifier.size(ButtonDefaults.iconSpacingFor(height)))
        Text(
            text = stringResource(if (enabled) R.string.check_and_save else R.string.save),
            style = ButtonDefaults.textStyleFor(height),
        )
    }
}

/**
 * What has a sensible default: a row that opens and closes the group, its arrow turning on the
 * spatial spring, and a line saying what is inside, so nobody has to open it to find out.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MoreOptionsToggle(expanded: Boolean, onToggle: () -> Unit) {
    val turn =
        animateFloatAsState(
            targetValue = if (expanded) 180f else 0f,
            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
            label = "MoreOptionsArrow",
        )
    SegmentedListItem(
        onClick = onToggle,
        shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
        colors = segmentedListColors(),
        leadingContent = { Icon(Icons.Rounded.Tune, contentDescription = null) },
        supportingContent = { Text(stringResource(R.string.more_options_summary)) },
        trailingContent = {
            Icon(
                imageVector = Icons.Rounded.ExpandMore,
                contentDescription = null,
                modifier = Modifier.graphicsLayer { rotationZ = turn.value },
            )
        },
    ) {
        ListHeadline(
            stringResource(if (expanded) R.string.fewer_options else R.string.more_options),
        )
    }
}

@Composable
private fun MoreOptions(state: EditorState, edit: (AccountDraft.() -> AccountDraft) -> Unit) {
    val draft = state.draft
    Column(verticalArrangement = Arrangement.spacedBy(FieldGap)) {
        Header(R.string.editor_section_connection)
        TransportChoice(selected = draft.transport, onSelect = { edit { copy(transport = it) } })
        FieldGroup {
            EditorField(
                shapes = SonaTextFieldDefaults.segmentedShapes(0, 4),
                value = draft.port,
                onValueChange = { edit { copy(port = it.filter(Char::isDigit)) } },
                label = R.string.field_port,
                icon = Icons.Rounded.Tag,
                placeholder = if (draft.transport == Transport.Tls) "5061" else "5060",
                supporting = R.string.field_port_supporting,
                problem = state.problems[DraftField.Port],
                keyboardType = KeyboardType.Number,
            )
            EditorField(
                shapes = SonaTextFieldDefaults.segmentedShapes(1, 4),
                value = draft.domain,
                onValueChange = { edit { copy(domain = it) } },
                label = R.string.field_domain,
                icon = Icons.Rounded.Language,
                supporting = R.string.field_domain_hint,
                problem = state.problems[DraftField.Domain],
                keyboardType = KeyboardType.Uri,
            )
            EditorField(
                shapes = SonaTextFieldDefaults.segmentedShapes(2, 4),
                value = draft.outboundProxy,
                onValueChange = { edit { copy(outboundProxy = it) } },
                label = R.string.field_outbound_proxy,
                icon = Icons.Rounded.Router,
                problem = state.problems[DraftField.OutboundProxy],
                keyboardType = KeyboardType.Uri,
            )
            EditorField(
                shapes = SonaTextFieldDefaults.segmentedShapes(3, 4),
                value = draft.expirySeconds,
                onValueChange = { edit { copy(expirySeconds = it.filter(Char::isDigit)) } },
                label = R.string.field_expiry,
                icon = Icons.Rounded.Timer,
                placeholder = "600",
                supporting = R.string.field_expiry_supporting,
                problem = state.problems[DraftField.Expiry],
                keyboardType = KeyboardType.Number,
            )
        }

        Header(R.string.editor_section_identity)
        FieldGroup {
            EditorField(
                shapes = SonaTextFieldDefaults.segmentedShapes(0, 4),
                value = draft.label,
                onValueChange = { edit { copy(label = it) } },
                label = R.string.field_label,
                icon = Icons.AutoMirrored.Rounded.Label,
                placeholder = draft.suggestedLabel,
                capitalization = KeyboardCapitalization.Sentences,
            )
            EditorField(
                shapes = SonaTextFieldDefaults.segmentedShapes(1, 4),
                value = draft.displayName,
                onValueChange = { edit { copy(displayName = it) } },
                label = R.string.field_display_name,
                icon = Icons.Rounded.Person,
                capitalization = KeyboardCapitalization.Words,
            )
            EditorField(
                shapes = SonaTextFieldDefaults.segmentedShapes(2, 4),
                value = draft.authUsername,
                onValueChange = { edit { copy(authUsername = it) } },
                label = R.string.field_auth_username,
                icon = Icons.Rounded.Badge,
                supporting = R.string.field_auth_username_hint,
                problem = state.problems[DraftField.AuthUsername],
                keyboardType = KeyboardType.Ascii,
            )
            EditorField(
                shapes = SonaTextFieldDefaults.segmentedShapes(3, 4),
                value = draft.voicemailNumber,
                onValueChange = { edit { copy(voicemailNumber = it) } },
                label = R.string.field_voicemail,
                icon = Icons.Rounded.Voicemail,
                placeholder = "*97",
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Done,
            )
        }

        Header(R.string.overrides_title)
        CallOverrides(state, edit)

        Header(R.string.overrides_messages_title)
        MessageOverrides(state, edit)
    }
}

/** UDP, TCP or TLS, as a connected group under its name. */
@Composable
private fun TransportChoice(selected: Transport, onSelect: (Transport) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.field_transport),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp),
        )
        ConnectedChoice(
            options = Transport.entries,
            selected = selected,
            onSelect = onSelect,
            label = { it.name.uppercase() },
        )
    }
}

/** Whether the account is used, and whether it is the one calls go out on. */
@Composable
private fun AccountSwitches(
    state: EditorState,
    edit: (AccountDraft.() -> AccountDraft) -> Unit,
    onIntent: (EditorIntent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        SwitchItem(
            index = 0,
            count = 2,
            icon = Icons.Rounded.PowerSettingsNew,
            title = R.string.field_enabled,
            description = R.string.field_enabled_description,
            checked = state.draft.enabled,
            onCheckedChange = { edit { copy(enabled = it) } },
        )
        SwitchItem(
            index = 1,
            count = 2,
            icon = Icons.Rounded.Star,
            title = R.string.field_default,
            checked = state.isDefault,
            onCheckedChange = { onIntent(EditorIntent.Default(it)) },
        )
    }
}

/** What calls on this account do differently from the app: one group of rows, each a dialog. */
@Composable
private fun CallOverrides(state: EditorState, edit: (AccountDraft.() -> AccountDraft) -> Unit) {
    val draft = state.draft
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        OverrideItem(
            index = 0,
            count = 2,
            icon = Icons.Rounded.GraphicEq,
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
        OverrideItem(
            index = 1,
            count = 2,
            icon = Icons.Rounded.Dialpad,
            title = R.string.override_dtmf,
            override = draft.overrides[CallSettings.Dtmf],
            app = state.appDtmf,
            describe = { stringResource(it.label) },
            onChange = { edit { copy(overrides = overrides.with(CallSettings.Dtmf, it)) } },
        ) { method, change ->
            SegmentedChoiceList(
                options = DtmfMethod.entries,
                selected = method,
                onSelect = change,
                label = { stringResource(it.label) },
            )
        }
    }
}

/** This account's own message size: the one its server relays. */
@Composable
private fun MessageOverrides(state: EditorState, edit: (AccountDraft.() -> AccountDraft) -> Unit) {
    OverrideItem(
        index = 0,
        count = 1,
        icon = Icons.Rounded.Straighten,
        title = R.string.override_largest_message,
        override = state.draft.overrides[MessageSettings.LargestMessage],
        app = state.appLargestMessage,
        describe = { stringResource(it.label) },
        onChange = {
            edit { copy(overrides = overrides.with(MessageSettings.LargestMessage, it)) }
        },
    ) { limit, change ->
        SegmentedChoiceList(
            options = MessageSizeLimit.entries,
            selected = limit,
            onSelect = change,
            label = { stringResource(it.label) },
        )
    }
}

private val MessageSizeLimit.label: Int
    get() =
        when (this) {
            MessageSizeLimit.B700 -> R.string.size_700_b
            MessageSizeLimit.Kb1 -> R.string.size_1_kb
            MessageSizeLimit.Kb4 -> R.string.size_4_kb
            MessageSizeLimit.Kb16 -> R.string.size_16_kb
            MessageSizeLimit.Kb60 -> R.string.size_60_kb
        }

/**
 * Fields that belong together as one segmented group: the segmented gap between them, and each with
 * its place's shapes ([SonaTextFieldDefaults.segmentedShapes]).
 */
@Composable
private fun FieldGroup(content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(SonaTextFieldDefaults.SegmentedGap)) {
        content()
    }
}

/**
 * A field of the form: [SonaTextField] with the editor's words, icon and keyboard. Its supporting
 * line opens only while it is focused, so its group stays one piece at rest.
 */
@Composable
private fun EditorField(
    value: String,
    onValueChange: (String) -> Unit,
    label: Int,
    icon: ImageVector,
    shapes: SonaTextFieldShapes,
    placeholder: String? = null,
    supporting: Int? = null,
    problem: DraftProblem? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    imeAction: ImeAction = ImeAction.Next,
) {
    SonaTextField(
        value = value,
        onValueChange = onValueChange,
        label = stringResource(label),
        leadingIcon = icon,
        placeholder = placeholder,
        supportingText = supporting?.let { stringResource(it) },
        supportingOnFocus = true,
        error = problem?.let { stringResource(it.text) },
        shapes = shapes,
        keyboardOptions =
            KeyboardOptions(
                capitalization = capitalization,
                autoCorrectEnabled = false,
                keyboardType = keyboardType,
                imeAction = imeAction,
            ),
    )
}

/** The password, hidden unless asked; for an existing account, empty keeps the one in the vault. */
@Composable
private fun PasswordField(
    state: EditorState,
    onIntent: (EditorIntent) -> Unit,
    shapes: SonaTextFieldShapes,
) {
    val visible = state.passwordVisible
    SonaTextField(
        value = state.password,
        onValueChange = { onIntent(EditorIntent.Password(it)) },
        label =
            stringResource(
                if (state.isNew && state.draft.id == null) R.string.field_password
                else R.string.field_password_keep,
            ),
        leadingIcon = Icons.Rounded.Key,
        shapes = shapes,
        error =
            if (state.passwordMissing) stringResource(R.string.problem_password_required) else null,
        visualTransformation =
            if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions =
            KeyboardOptions(
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
        trailingContent = {
            IconToggleButton(
                checked = visible,
                onCheckedChange = { onIntent(EditorIntent.TogglePasswordVisible) },
            ) {
                Icon(
                    if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription =
                        stringResource(
                            if (visible) R.string.field_password_hide
                            else R.string.field_password_show,
                        ),
                )
            }
        },
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

/** A switch as a segmented list item: the whole row is the switch, its thumb marked when on. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SwitchItem(
    index: Int,
    count: Int,
    icon: ImageVector,
    title: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    description: Int? = null,
) {
    SegmentedListItem(
        checked = checked,
        onCheckedChange = onCheckedChange,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = segmentedListColors(),
        leadingContent = { Icon(icon, contentDescription = null) },
        supportingContent = description?.let { { Text(stringResource(it)) } },
        trailingContent = { ThumbSwitch(checked) },
    ) {
        ListHeadline(stringResource(title))
    }
}

@Composable
private fun ThumbSwitch(checked: Boolean) {
    Switch(
        checked = checked,
        onCheckedChange = null,
        thumbContent =
            if (checked) {
                {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize),
                    )
                }
            } else null,
    )
}

/**
 * An option this account may have its own value for. The row says which applies, *Uses the app's
 * setting (Opus, G.722)* or the account's own; it opens a dialog with the switch between the two
 * and, for the account's own, [editor] to change it. Turning the app's setting off starts from the
 * app's value, so the account's own differs only where the user changes it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun <T> OverrideItem(
    index: Int,
    count: Int,
    icon: ImageVector,
    title: Int,
    override: Override<T>,
    app: T,
    describe: @Composable (T) -> String,
    onChange: (Override<T>) -> Unit,
    editor: @Composable (value: T, change: (T) -> Unit) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    SegmentedListItem(
        onClick = { open = true },
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = segmentedListColors(),
        leadingContent = { Icon(icon, contentDescription = null) },
        supportingContent = {
            Text(
                when (override) {
                    Override.Inherit -> stringResource(R.string.override_inherit, describe(app))
                    is Override.Value ->
                        stringResource(R.string.override_own, describe(override.value))
                },
            )
        },
        trailingContent = {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
        },
    ) {
        ListHeadline(stringResource(title))
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    DialogSwitchRow(
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

/**
 * A switch inside a dialog, where the dialog is the container: a plain row, all of it the switch.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DialogSwitchRow(title: Int, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    ListItem(
        checked = checked,
        onCheckedChange = onCheckedChange,
        trailingContent = { ThumbSwitch(checked) },
        shapes = ListItemDefaults.shapes(shape = MaterialTheme.shapes.large),
        colors =
            ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                selectedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
    ) {
        ListHeadline(stringResource(title))
    }
}

/** How the account's state looks: a mark on a shape, and the container colors around it. */
@Immutable
private data class StatusLook(
    val icon: ImageVector?,
    val container: Color,
    val content: Color,
    val mark: Color,
    val onMark: Color,
)

@Composable
private fun statusLook(registration: RegistrationState, enabled: Boolean): StatusLook {
    val colors = MaterialTheme.colorScheme
    return when {
        !enabled ->
            StatusLook(
                Icons.Rounded.PowerSettingsNew,
                colors.surfaceContainerHigh,
                colors.onSurface,
                colors.secondaryContainer,
                colors.onSecondaryContainer,
            )
        registration == RegistrationState.Registered ->
            StatusLook(
                Icons.Rounded.CheckCircle,
                colors.primaryContainer,
                colors.onPrimaryContainer,
                colors.primary,
                colors.onPrimary,
            )
        registration is RegistrationState.Failed ->
            StatusLook(
                Icons.Rounded.ErrorOutline,
                colors.errorContainer,
                colors.onErrorContainer,
                colors.error,
                colors.onError,
            )
        registration == RegistrationState.Offline ->
            StatusLook(
                Icons.Rounded.CloudOff,
                colors.surfaceContainerHigh,
                colors.onSurface,
                colors.secondaryContainer,
                colors.onSecondaryContainer,
            )
        // Registering: the mark is a loading indicator.
        else ->
            StatusLook(
                null,
                colors.secondaryContainer,
                colors.onSecondaryContainer,
                colors.secondary,
                colors.onSecondary,
            )
    }
}

/**
 * An existing account's state at the top of its editor: a mark on Material's cookie shape and the
 * state in a word, in the colors of how it is going; when it failed, why, and the SIP line under
 * it. Spoken by TalkBack as it changes.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LiveStatus(state: EditorState) {
    val status = state.status ?: return
    val enabled = status.account.enabled
    val failed = (status.registration as? RegistrationState.Failed)?.takeIf { enabled }
    val look = statusLook(status.registration, enabled)
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = look.container,
        contentColor = look.content,
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier.size(StatusMarkSize)
                        .background(look.mark, MaterialShapes.Cookie9Sided.toShape()),
                contentAlignment = Alignment.Center,
            ) {
                if (look.icon != null) {
                    Icon(look.icon, contentDescription = null, tint = look.onMark)
                } else {
                    LoadingIndicator(Modifier.size(32.dp), color = look.onMark)
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(status.stateText(), style = MaterialTheme.typography.titleMediumEmphasized)
                if (failed != null) {
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
}

/**
 * Waiting for the server, then what it answered: a large mark on one of Material's shapes, the
 * answer in a headline, why in a sentence, and the SIP line in a pill under it.
 */
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
    val colors = MaterialTheme.colorScheme

    Column(
        modifier =
            Modifier.fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .navigationBarsPadding()
                .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        when {
            phase is EditorPhase.Checking -> {
                ContainedLoadingIndicator(Modifier.size(ResultMarkSize))
                ResultTitle(stringResource(R.string.checking, user, server))
            }
            account != null && !account.enabled -> {
                ResultMark(
                    Icons.Rounded.CheckCircle,
                    colors.secondaryContainer,
                    colors.onSecondaryContainer,
                )
                ResultTitle(stringResource(R.string.checked_off))
            }
            registration == RegistrationState.Registered -> {
                ResultMark(
                    Icons.Rounded.CheckCircle,
                    colors.primaryContainer,
                    colors.onPrimaryContainer,
                )
                ResultTitle(stringResource(R.string.checked_ok, user, server))
                ResultBody(stringResource(R.string.checked_ok_description))
            }
            registration is RegistrationState.Failed &&
                account != null &&
                !localNetwork.granted -> {
                ResultMark(Icons.Rounded.Lan, colors.errorContainer, colors.onErrorContainer)
                ResultTitle(stringResource(R.string.checked_failed))
                ResultBody(stringResource(R.string.local_network_needed))
                Button(
                    onClick = {
                        localNetwork.request { granted ->
                            if (granted) onIntent(EditorIntent.Retry)
                        }
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.local_network_allow))
                }
            }
            registration is RegistrationState.Failed && account != null -> {
                val problem = registration.explain(account)
                ResultMark(
                    Icons.Rounded.ErrorOutline,
                    colors.errorContainer,
                    colors.onErrorContainer,
                )
                ResultTitle(stringResource(R.string.checked_failed))
                ResultBody(problem.sentence)
                problem.technical?.let {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = colors.surfaceContainerHigh,
                    ) {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = SonaTheme.typography.monospace,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
            }
            else -> {
                ResultMark(
                    Icons.Rounded.Schedule,
                    colors.surfaceContainerHigh,
                    colors.onSurfaceVariant,
                )
                ResultTitle(stringResource(R.string.checked_slow))
            }
        }
        if (phase is EditorPhase.Checked) {
            Spacer(Modifier.size(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val height = ButtonDefaults.MediumContainerHeight
                OutlinedButton(
                    onClick = { onIntent(EditorIntent.EditAgain) },
                    shapes = ButtonDefaults.shapes(),
                    contentPadding = ButtonDefaults.contentPaddingFor(height),
                    modifier = Modifier.heightIn(min = height),
                ) {
                    Text(
                        stringResource(R.string.edit_again),
                        style = ButtonDefaults.textStyleFor(height),
                    )
                }
                Button(
                    onClick = { onIntent(EditorIntent.Done) },
                    shapes = ButtonDefaults.shapes(),
                    contentPadding = ButtonDefaults.contentPaddingFor(height),
                    modifier = Modifier.heightIn(min = height),
                ) {
                    Text(stringResource(R.string.done), style = ButtonDefaults.textStyleFor(height))
                }
            }
        }
    }
}

/** The answer's mark: an icon on Material's sunny shape, arriving on the spatial spring. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ResultMark(icon: ImageVector, container: Color, content: Color) {
    val motion = MaterialTheme.motionScheme
    // Shown from its first frame on, so it grows in once, when the answer arrives.
    val appeared = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = appeared,
        enter =
            scaleIn(motion.slowSpatialSpec(), initialScale = 0.4f) +
                fadeIn(motion.defaultEffectsSpec()),
    ) {
        Box(
            modifier =
                Modifier.size(ResultMarkSize).background(container, MaterialShapes.Sunny.toShape()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(48.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ResultTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.headlineSmallEmphasized,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun ResultBody(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

private val ScreenMargin = 16.dp
private val FormMaxWidth = 640.dp
private val FieldGap = 10.dp
private val StatusMarkSize = 52.dp
private val ResultMarkSize = 112.dp

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
