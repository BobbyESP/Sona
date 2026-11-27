/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.call

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bobbyesp.sona.calling.domain.CallCoordinator
import com.bobbyesp.sona.calling.domain.DialOutcome
import com.bobbyesp.sona.calling.ui.problemText
import com.bobbyesp.sona.core.model.AccountId
import com.bobbyesp.sona.core.model.settings.SettingsStore
import com.bobbyesp.sona.core.model.settings.VideoSettings
import com.bobbyesp.sona.core.navigation.Navigator
import com.bobbyesp.sona.core.ui.contribution.LocalCallScreen
import com.bobbyesp.sona.core.ui.contribution.PartyAction
import com.bobbyesp.sona.core.ui.contribution.PartyActionProvider
import com.bobbyesp.sona.core.ui.contribution.PartyRef
import com.bobbyesp.sona.sip.api.Capability
import com.bobbyesp.sona.sip.api.SipEngine
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * *Video call*, wherever a party is shown: dials with the camera on, after asking for the
 * microphone and the camera, and opens the call screen. Not offered on an engine without video or
 * with video calls off in the settings; a call that could not start says why.
 */
internal object VideoCallAction : PartyActionProvider {
    override val order: Int = 10

    @Composable
    override fun rememberAction(party: PartyRef, navigator: Navigator): PartyAction? {
        val engine: SipEngine = koinInject()
        if (Capability.VideoCalls !in engine.capabilities) return null
        val settings: SettingsStore = koinInject()
        val enabled by
            remember(settings) { settings.observe(VideoSettings.Enabled) }
                .collectAsStateWithLifecycle(initialValue = false)
        if (!enabled) return null

        val coordinator: CallCoordinator = koinInject()
        val context = LocalContext.current
        val openCall by rememberUpdatedState(LocalCallScreen.current)
        val scope = rememberCoroutineScope()
        var problem by remember { mutableStateOf<DialOutcome?>(null) }
        val dial = {
            scope.launch {
                val outcome =
                    coordinator.dial(
                        party.address,
                        party.account?.let(::AccountId),
                        video = true,
                    )
                if (outcome is DialOutcome.Started) openCall() else problem = outcome
            }
            Unit
        }
        // Whatever the user answers, the call goes: without the camera it is audio, and says why.
        val asked =
            rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions(),
            ) {
                dial()
            }
        problem?.problemText()?.let { text ->
            LaunchedEffect(problem) {
                Toast.makeText(context, text, Toast.LENGTH_LONG).show()
                problem = null
            }
        }
        val label = stringResource(R.string.call_video_call)
        return remember(party, label) {
            PartyAction(
                id = ID,
                label = label,
                icon = Icons.Rounded.Videocam,
                onClick = {
                    val granted =
                        listOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA).all {
                            context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
                        }
                    if (granted) dial() else asked.launch(videoCallPermissions())
                },
            )
        }
    }

    const val ID = "call.video"
}
