/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.messages.picture

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Share
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.bobbyesp.sona.core.ui.viewmodel.BaseViewModel
import com.bobbyesp.sona.feature.messages.R
import com.bobbyesp.sona.messaging.domain.Attachment
import com.bobbyesp.sona.messaging.domain.ConversationId
import com.bobbyesp.sona.messaging.domain.MessageId
import com.bobbyesp.sona.messaging.domain.Messenger
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class PictureState(
    val loaded: Boolean = false,
    val file: Attachment? = null,
    val uri: String? = null,
    /** The message is gone (deleted): the viewer leaves. */
    val gone: Boolean = false,
)

internal class PictureViewModel(conversationId: Long, messageId: Long, messenger: Messenger) :
    BaseViewModel<Nothing, PictureState, Nothing>(PictureState()) {

    init {
        launch {
            messenger
                .messages(ConversationId(conversationId))
                .map { messages -> messages.firstOrNull { it.id == MessageId(messageId) } }
                .collect { message ->
                    val file = message?.attachment
                    val uri = file?.takeIf { it.file != null }?.let(messenger::uriOf)
                    setState {
                        PictureState(loaded = true, file = file, uri = uri, gone = uri == null)
                    }
                }
        }
    }

    override fun onHandleIntent(intent: Nothing) = Unit
}

@Composable
internal fun PictureRoute(viewModel: PictureViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.gone) { if (state.gone) onBack() }
    PictureScreen(state, onBack)
}

/**
 * A picture on the whole screen, zoomable. *Share* lets another app read it by a grant; *Save*
 * writes a copy where the user picks.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PictureScreen(state: PictureState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val saved = stringResource(R.string.picture_saved)
    val notSaved = stringResource(R.string.picture_not_saved)
    val file = state.file
    val uri = state.uri
    val save =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument(file?.mime ?: "image/*"),
        ) { target ->
            if (target == null || uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                val done = context.copy(uri.toUri(), target)
                snackbar.showSnackbar(if (done) saved else notSaved)
            }
        }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(file?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.messages_back),
                        )
                    }
                },
                actions = {
                    if (uri != null && file != null) {
                        IconButton(onClick = { context.share(uri, file.mime) }) {
                            Icon(
                                Icons.Rounded.Share,
                                contentDescription = stringResource(R.string.picture_share),
                            )
                        }
                        IconButton(onClick = { save.launch(file.name) }) {
                            Icon(
                                Icons.Rounded.Download,
                                contentDescription = stringResource(R.string.picture_save),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            if (uri == null) {
                LoadingIndicator()
            } else {
                ZoomablePicture(uri, file?.name)
            }
        }
    }
}

@Composable
private fun ZoomablePicture(uri: String, description: String?) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    AsyncImage(
        model = uri,
        contentDescription = description,
        contentScale = ContentScale.Fit,
        modifier =
            Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            scale = 1f
                            offset = Offset.Zero
                        },
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 6f)
                        offset = if (scale == 1f) Offset.Zero else offset + pan
                    }
                }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    // At most as far as the picture reaches past the screen's edge.
                    val maxX = size.width * (scale - 1) / 2
                    val maxY = size.height * (scale - 1) / 2
                    translationX = offset.x.coerceIn(-maxX, maxX)
                    translationY = offset.y.coerceIn(-maxY, maxY)
                },
    )
}

private fun Context.share(uri: String, mime: String) {
    val parsed = uri.toUri()
    val send =
        Intent(Intent.ACTION_SEND)
            .setType(mime)
            .putExtra(Intent.EXTRA_STREAM, parsed)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    send.clipData = ClipData.newRawUri(null, parsed)
    startActivity(Intent.createChooser(send, null))
}

/** Off the main thread: a picture can be megabytes. */
private suspend fun Context.copy(from: Uri, to: Uri): Boolean =
    withContext(Dispatchers.IO) {
        try {
            val input = contentResolver.openInputStream(from) ?: return@withContext false
            input.use { source ->
                val output = contentResolver.openOutputStream(to) ?: return@withContext false
                output.use { source.copyTo(it) }
            }
            true
        } catch (e: IOException) {
            Log.w("PictureViewer", "Could not save the picture", e)
            false
        } catch (e: SecurityException) {
            Log.w("PictureViewer", "Could not save the picture", e)
            false
        }
    }
