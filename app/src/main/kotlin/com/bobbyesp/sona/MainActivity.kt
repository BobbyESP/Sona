/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bobbyesp.sona.calling.domain.CallCoordinator
import com.bobbyesp.sona.calling.domain.CallState
import com.bobbyesp.sona.calling.platform.ServiceController
import com.bobbyesp.sona.core.designsystem.theme.SonaTheme
import com.bobbyesp.sona.core.ui.contribution.ReadinessMonitor
import com.bobbyesp.sona.core.ui.contribution.problems
import com.bobbyesp.sona.shell.ShellViewModel
import com.bobbyesp.sona.shell.SonaApp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : ComponentActivity() {

    private val shell: ShellViewModel by viewModel()
    private val coordinator: CallCoordinator by inject()
    private val readiness: ReadinessMonitor by inject()
    private val services: ServiceController by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // The user's theme from the first frame: never the default one swapped for it.
        splash.setKeepOnScreenCondition { shell.startup.value == null }

        // A call that rings while the app is in use opens its screen at once, rather than only a
        // heads-up notification.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                coordinator.calls
                    .map { calls -> calls.any { it.state == CallState.Incoming } }
                    .distinctUntilChanged()
                    .collect { ringing ->
                        if (ringing) startActivity(CallActivity.intent(this@MainActivity))
                    }
            }
        }

        setContent {
            val startup by shell.startup.collectAsStateWithLifecycle()
            val accountChip by shell.accountChip.collectAsStateWithLifecycle()
            val checks by readiness.results.collectAsStateWithLifecycle()
            val ready = startup ?: return@setContent
            SonaTheme(config = ready.theme) {
                SonaApp(
                    accountChip = accountChip,
                    needsOnboarding = ready.needsOnboarding,
                    readinessWarning = checks?.problems?.isNotEmpty() == true,
                )
            }
        }
    }

    /**
     * Back in front, possibly from the system's settings: what calls need is asked again, and the
     * keep-alive notification is posted again in case it could not be shown before.
     */
    override fun onResume() {
        super.onResume()
        readiness.refresh()
        services.refresh()
    }
}
