/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.messages.conversation

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bobbyesp.sona.accounts.ui.accent
import com.bobbyesp.sona.core.designsystem.blur.blurSource
import com.bobbyesp.sona.core.designsystem.blur.frosted
import com.bobbyesp.sona.core.designsystem.blur.rememberBlurSource
import com.bobbyesp.sona.core.designsystem.theme.SonaTheme
import com.bobbyesp.sona.core.navigation.Navigator
import com.bobbyesp.sona.core.navigation.pane.LocalPaneContext
import com.bobbyesp.sona.core.ui.contribution.PartyRef
import com.bobbyesp.sona.core.ui.contribution.rememberPartyActions
import com.bobbyesp.sona.core.ui.events.CollectEventsEffect
import com.bobbyesp.sona.feature.messages.MESSAGE_ACTION_ID
import com.bobbyesp.sona.feature.messages.R
import com.bobbyesp.sona.messaging.domain.MessageId
import kotlinx.coroutines.launch

@Composable
internal fun ConversationRoute(
    viewModel: ConversationViewModel,
    navigator: Navigator,
    onBack: () -> Unit,
    onGone: () -> Unit,
    onPicture: (MessageId) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var capture by rememberSaveable { mutableStateOf<String?>(null) }
    var attaching by rememberSaveable { mutableStateOf(false) }

    // On screen while resumed: what arrives is read at once, and not notified.
    LifecycleResumeEffect(viewModel) {
        viewModel.onSendIntent(ConversationIntent.Visible(true))
        onPauseOrDispose { viewModel.onSendIntent(ConversationIntent.Visible(false)) }
    }
    LaunchedEffect(state.gone) { if (state.gone) onGone() }

    val noApp = stringResource(R.string.conversation_no_app)
    val cameraRefused = stringResource(R.string.conversation_camera_refused)
    val cameraUnavailable = stringResource(R.string.conversation_camera_unavailable)
    val copied = stringResource(R.string.conversation_copied)
    fun say(text: String) = scope.launch { snackbar.showSnackbar(text) }

    val media =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let { viewModel.onSendIntent(ConversationIntent.Attach(it.toString())) }
        }
    val document =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { viewModel.onSendIntent(ConversationIntent.Attach(it.toString())) }
        }
    val camera =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
            capture?.let { viewModel.onSendIntent(ConversationIntent.PictureTaken(it, taken)) }
            capture = null
        }
    val cameraPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) viewModel.onSendIntent(ConversationIntent.TakePicture)
            else say(cameraRefused)
        }

    viewModel.effects.CollectEventsEffect { effect ->
        when (effect) {
            is ConversationEffect.OpenCamera -> {
                capture = effect.uri
                try {
                    camera.launch(effect.uri.toUri())
                } catch (_: ActivityNotFoundException) {
                    capture = null
                    say(noApp)
                }
            }
            ConversationEffect.CameraUnavailable -> say(cameraUnavailable)
        }
    }

    val actions =
        remember(viewModel, context) {
            object : BubbleActions {
                override fun intent(intent: ConversationIntent) = viewModel.onSendIntent(intent)

                override fun openPicture(message: MessageId) = onPicture(message)

                override fun openFile(uri: String, mime: String) {
                    if (!context.openWithAnotherApp(uri, mime)) say(noApp)
                }

                override fun copy(text: String) {
                    scope.launch {
                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(null, text)))
                        say(copied)
                    }
                }
            }
        }

    ConversationScreen(
        state = state,
        navigator = navigator,
        actions = actions,
        snackbar = snackbar,
        onBack = onBack,
        onAttach = { attaching = true },
    )

    if (attaching) {
        AttachSheet(
            onDismiss = { attaching = false },
            onChoose = { choice ->
                attaching = false
                when (choice) {
                    AttachChoice.Media ->
                        media.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageAndVideo,
                            ),
                        )
                    AttachChoice.File -> document.launch(arrayOf("*/*"))
                    AttachChoice.Camera ->
                        // The app declares the camera (for video calls), so the camera app may
                        // only be asked for a picture once the permission is given.
                        if (context.hasCamera())
                            viewModel.onSendIntent(ConversationIntent.TakePicture)
                        else cameraPermission.launch(Manifest.permission.CAMERA)
                }
            },
        )
    }
}

private fun Context.hasCamera(): Boolean =
    checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

/** The chosen app may read it for as long as it has it open. */
private fun Context.openWithAnotherApp(uri: String, mime: String): Boolean {
    val view =
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri.toUri(), mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    return try {
        startActivity(
            Intent.createChooser(view, null).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

/** A conversation, with both bars frosted over the messages that pass beneath. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ConversationScreen(
    state: ConversationState,
    navigator: Navigator,
    actions: BubbleActions,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onAttach: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val behind = rememberBlurSource()
    val header = state.header
    val pane = LocalPaneContext.current
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                modifier = Modifier.frosted(behind, MaterialTheme.colorScheme.surfaceContainer),
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                    ),
                navigationIcon = {
                    if (pane.providesOwnBackAffordance) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.messages_back),
                            )
                        }
                    }
                },
                title = { if (header != null) TopBarTitle(header, state.typing) },
                actions = {
                    if (header != null) {
                        val party =
                            PartyRef(
                                address = header.address,
                                name = header.title,
                                account = header.account?.id?.value,
                            )
                        rememberPartyActions(party, navigator)
                            .filterNot { it.id == MESSAGE_ACTION_ID }
                            .forEach { action ->
                                IconButton(onClick = action.onClick, enabled = action.enabled) {
                                    Icon(action.icon, contentDescription = action.label)
                                }
                            }
                    }
                },
            )
        },
        bottomBar = {
            Composer(
                draft = state.draft,
                problem = state.problem,
                preparing = state.preparing,
                onDraft = { actions.intent(ConversationIntent.Draft(it)) },
                onSend = { actions.intent(ConversationIntent.Send) },
                onAttach = onAttach,
                onDismissProblem = { actions.intent(ConversationIntent.DismissProblem) },
                modifier =
                    Modifier.frosted(behind, MaterialTheme.colorScheme.surfaceContainer)
                        .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)),
            )
        },
    ) { padding ->
        if (!state.loaded || header == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }
            return@Scaffold
        }
        Thread(state, header, padding, actions, Modifier.blurSource(behind))
    }

    state.details?.let { message ->
        MessageDetailsDialog(
            message = message,
            subject = state.subject,
            onDismiss = { actions.intent(ConversationIntent.Details(null)) },
        )
    }
}

@Composable
private fun TopBarTitle(header: ConversationHeader, typing: Boolean) {
    Column {
        Text(
            header.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (header.severalAccounts) {
                header.account?.let { account ->
                    Box(Modifier.size(8.dp).clip(CircleShape).background(account.accent))
                    Spacer(Modifier.size(6.dp))
                }
            }
            AnimatedContent(targetState = typing, label = "Typing") { now ->
                if (now) {
                    Text(
                        stringResource(R.string.conversation_typing),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                } else {
                    Text(
                        header.address,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = SonaTheme.typography.monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.StartEllipsis,
                    )
                }
            }
        }
    }
}

/**
 * A reversed list opens at the newest and stays there; stable keys keep a changed message on its
 * row.
 */
@Composable
private fun Thread(
    state: ConversationState,
    header: ConversationHeader,
    padding: PaddingValues,
    actions: BubbleActions,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val newestFirst = remember(state.items) { state.items.asReversed() }
    val newest = newestFirst.firstOrNull()?.key
    // Back to the newest when one arrives or is sent, if the user was near it.
    LaunchedEffect(newest) {
        if (listState.firstVisibleItemIndex <= 2) listState.animateScrollToItem(0)
    }
    val peer = state.subject.peer
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            state = listState,
            reverseLayout = true,
            modifier = Modifier.fillMaxHeight().widthIn(max = 840.dp),
            contentPadding =
                PaddingValues(
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 8.dp,
                ),
        ) {
            items(newestFirst, key = { it.key }, contentType = { it::class }) { item ->
                when (item) {
                    is ThreadItem.Day -> DayHeading(item.at, Modifier.animateItem())
                    is ThreadItem.Bubble ->
                        MessageBubble(
                            item = item,
                            peer = peer,
                            subject = state.subject,
                            actions = actions,
                            modifier = Modifier.animateItem(),
                        )
                }
            }
            if (header.plainPeer) {
                item(key = "plain-peer", contentType = "note") { PlainPeerNote(peer) }
            }
        }
    }
}
