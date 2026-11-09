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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bobbyesp.sona.core.designsystem.theme.SonaTheme
import com.bobbyesp.sona.shell.ShellViewModel
import com.bobbyesp.sona.shell.SonaApp
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : ComponentActivity() {

    private val shell: ShellViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // The user's theme from the first frame: never the default one swapped for it.
        splash.setKeepOnScreenCondition { shell.startup.value == null }

        setContent {
            val startup by shell.startup.collectAsStateWithLifecycle()
            val accountChip by shell.accountChip.collectAsStateWithLifecycle()
            val ready = startup ?: return@setContent
            SonaTheme(config = ready.theme) {
                SonaApp(accountChip = accountChip, needsOnboarding = ready.needsOnboarding)
            }
        }
    }
}
