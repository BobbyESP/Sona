/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bobbyesp.sona.calling.domain.CallCoordinator
import com.bobbyesp.sona.calling.platform.CallScreenIntents
import com.bobbyesp.sona.core.designsystem.theme.SonaTheme
import com.bobbyesp.sona.core.model.CallId
import com.bobbyesp.sona.feature.call.CallRoute
import com.bobbyesp.sona.shell.ShellViewModel
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

    /** The notification's *Answer* opens this screen, answering as it does. */
    private fun answerIfAsked(intent: Intent?) {
        val call = intent?.getStringExtra(EXTRA_ANSWER) ?: return
        coordinator.answer(CallId(call))
        intent.removeExtra(EXTRA_ANSWER)
    }

    companion object {
        private const val EXTRA_ANSWER = "answer"

        fun intent(context: Context): Intent =
            Intent(context, CallActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

    /** How the platform's notifications open this screen. */
    object Intents : CallScreenIntents {
        override fun screen(context: Context): Intent = intent(context)

        override fun answer(context: Context, call: CallId): Intent =
            intent(context).putExtra(EXTRA_ANSWER, call.value)
    }
}
