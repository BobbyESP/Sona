/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.bobbyesp.sona.calling.domain.CallCoordinator
import com.bobbyesp.sona.calling.domain.DialOutcome
import com.bobbyesp.sona.calling.platform.CallScreenIntents
import com.bobbyesp.sona.core.designsystem.theme.SonaTheme
import com.bobbyesp.sona.core.model.AccountId
import com.bobbyesp.sona.core.model.CallId
import com.bobbyesp.sona.feature.call.CallRoute
import com.bobbyesp.sona.shell.ShellViewModel
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * The call screen's own activity. It shows over the lock screen and turns the screen on, it is the
 * incoming call's full-screen intent, and it runs in its own task: ending a call leaves the user
 * where they were, and recents show the call, not a second copy of the app. See docs/calling.md.
 */
class CallActivity : ComponentActivity() {

    private val coordinator: CallCoordinator by inject()
    private val shell: ShellViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        enableEdgeToEdge()
        answerIfAsked(intent)
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
            SonaTheme(config = theme) { CallRoute(onClosed = ::finishAndRemoveTask) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        answerIfAsked(intent)
    }

    /**
     * The notification's *Answer* opens this screen, answering as it does: with the camera for a
     * call that offered video, whose *Answer* the system draws as one.
     */
    private fun answerIfAsked(intent: Intent?) {
        val call = intent?.getStringExtra(EXTRA_ANSWER) ?: return
        coordinator.answer(CallId(call), video = intent.getBooleanExtra(EXTRA_ANSWER_VIDEO, false))
        intent.removeExtra(EXTRA_ANSWER)
        intent.removeExtra(EXTRA_ANSWER_VIDEO)
    }

    companion object {
        private const val EXTRA_ANSWER = "answer"
        private const val EXTRA_ANSWER_VIDEO = "answer_video"
        private const val EXTRA_CALL_BACK = "call_back"
        private const val EXTRA_ACCOUNT = "account"
        private val CALL_APPEARS_WITHIN = 3.seconds

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
