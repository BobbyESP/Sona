/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona

import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Rational
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.bobbyesp.sona.calling.domain.CallCoordinator
import com.bobbyesp.sona.calling.domain.DialOutcome
import com.bobbyesp.sona.calling.platform.CallActionReceiver
import com.bobbyesp.sona.calling.platform.CallScreenIntents
import com.bobbyesp.sona.core.designsystem.theme.SonaTheme
import com.bobbyesp.sona.core.model.AccountId
import com.bobbyesp.sona.core.model.CallId
import com.bobbyesp.sona.feature.call.CallRoute
import com.bobbyesp.sona.shell.ShellViewModel
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * The call screen's own activity. It shows over the lock screen and turns the screen on, it is the
 * incoming call's full-screen intent, and it runs in its own task: ending a call leaves the user
 * where they were, and recents show the call, not a second copy of the app. See docs/calling.md.
 *
 * **Picture-in-Picture** (docs/video.md): an answered video call goes on in a small window when the
 * user leaves, with *Mute* and *Hang up*. An audio call never does: its notification is the way
 * back.
 */
class CallActivity : ComponentActivity() {

    private val coordinator: CallCoordinator by inject()
    private val shell: ShellViewModel by viewModel()

    private var pip: PipTarget? = null
    private var inPictureInPicture by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        enableEdgeToEdge()
        answerIfAsked(intent)
        if (supportsPictureInPicture()) {
            lifecycleScope.launch {
                coordinator.calls.map(::pipTarget).distinctUntilChanged().collect {
                    pip = it
                    updatePictureInPicture()
                }
            }
        }
        // A missed call's *Call back* dials first, and shows the call once it exists: a screen
        // that opens with no call would close at once.
        val callBack = intent.getStringExtra(EXTRA_CALL_BACK)
        if (callBack != null && savedInstanceState == null) {
            intent.removeExtra(EXTRA_CALL_BACK)
            lifecycleScope.launch {
                val outcome =
                    coordinator.dial(
                        callBack,
                        intent.getStringExtra(EXTRA_ACCOUNT)?.let(::AccountId),
                    )
                if (outcome is DialOutcome.Started) {
                    withTimeoutOrNull(CALL_APPEARS_WITHIN) {
                        coordinator.calls.first { calls -> calls.any { !it.isOver } }
                    }
                    show()
                } else {
                    Toast.makeText(this@CallActivity, R.string.call_back_failed, Toast.LENGTH_LONG)
                        .show()
                    finishAndRemoveTask()
                }
            }
        } else {
            show()
        }
    }

    private fun show() {
        setContent {
            val startup by shell.startup.collectAsStateWithLifecycle()
            val theme = startup?.theme ?: return@setContent
            SonaTheme(config = theme) {
                CallRoute(onClosed = ::finishAndRemoveTask, pictureInPicture = inPictureInPicture)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        answerIfAsked(intent)
    }

    /** Android 12+ enters Picture-in-Picture by itself. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return
        val target = pip ?: return
        if (!supportsPictureInPicture()) return
        try {
            enterPictureInPictureMode(pictureInPictureParams(target))
        } catch (error: IllegalStateException) {
            Log.w(TAG, "Picture-in-Picture refused", error)
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPictureInPicture = isInPictureInPictureMode
    }

    /**
     * The notification's *Answer* opens this screen, with the camera for a call that offered video.
     */
    private fun answerIfAsked(intent: Intent?) {
        val call = intent?.getStringExtra(EXTRA_ANSWER) ?: return
        coordinator.answer(CallId(call), video = intent.getBooleanExtra(EXTRA_ANSWER_VIDEO, false))
        intent.removeExtra(EXTRA_ANSWER)
        intent.removeExtra(EXTRA_ANSWER_VIDEO)
    }

    private fun supportsPictureInPicture(): Boolean =
        packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    /** It enters by itself (Android 12+) only while there is a video call. */
    private fun updatePictureInPicture() {
        try {
            setPictureInPictureParams(pictureInPictureParams(pip))
        } catch (error: IllegalArgumentException) {
            Log.w(TAG, "Picture-in-Picture parameters refused", error)
        } catch (error: IllegalStateException) {
            Log.w(TAG, "Picture-in-Picture parameters refused", error)
        }
    }

    private fun pictureInPictureParams(target: PipTarget?): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
        if (target != null) {
            builder.setAspectRatio(Rational(target.aspect.width, target.aspect.height))
            builder.setActions(pictureInPictureActions(target))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(target != null)
            builder.setSeamlessResizeEnabled(false)
        }
        return builder.build()
    }

    private fun pictureInPictureActions(target: PipTarget): List<RemoteAction> {
        val mute = getString(if (target.muted) R.string.pip_unmute else R.string.pip_mute)
        val hangUp = getString(R.string.pip_hang_up)
        return listOf(
            RemoteAction(
                Icon.createWithResource(
                    this,
                    if (target.muted) R.drawable.ic_pip_mic_off else R.drawable.ic_pip_mic,
                ),
                mute,
                mute,
                CallActionReceiver.pendingIntent(
                    this,
                    CallActionReceiver.ACTION_TOGGLE_MUTE,
                    target.call,
                    REQUEST_PIP_MUTE,
                ),
            ),
            RemoteAction(
                Icon.createWithResource(this, R.drawable.ic_pip_call_end),
                hangUp,
                hangUp,
                CallActionReceiver.pendingIntent(
                    this,
                    CallActionReceiver.ACTION_HANG_UP,
                    target.call,
                    REQUEST_PIP_HANG_UP,
                ),
            ),
        )
    }

    companion object {
        private const val TAG = "CallActivity"
        private const val EXTRA_ANSWER = "answer"
        private const val EXTRA_ANSWER_VIDEO = "answer_video"
        private const val EXTRA_CALL_BACK = "call_back"
        private const val EXTRA_ACCOUNT = "account"
        private val CALL_APPEARS_WITHIN = 3.seconds
        private const val REQUEST_PIP_MUTE = 101
        private const val REQUEST_PIP_HANG_UP = 102

        fun intent(context: Context): Intent =
            Intent(context, CallActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

    /** How the platform's notifications open this screen. */
    object Intents : CallScreenIntents {
        override fun screen(context: Context): Intent = intent(context)

        override fun answer(context: Context, call: CallId, video: Boolean): Intent =
            intent(context).putExtra(EXTRA_ANSWER, call.value).putExtra(EXTRA_ANSWER_VIDEO, video)

        override fun callBack(context: Context, address: String, account: String?): Intent =
            intent(context).putExtra(EXTRA_CALL_BACK, address).putExtra(EXTRA_ACCOUNT, account)
    }
}
