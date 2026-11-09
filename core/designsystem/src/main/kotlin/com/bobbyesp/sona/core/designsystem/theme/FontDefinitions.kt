/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.core.designsystem.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.bobbyesp.sona.core.designsystem.R

/**
 * Fonts packaged in `res/font`. They load synchronously on the first frame, with no provider and no
 * network, so the typography never swaps its font after the app starts.
 */
internal object BundledFonts {
    val DMSans: FontFamily =
        FontFamily(
            Font(R.font.dm_sans_regular, FontWeight.Normal),
            Font(R.font.dm_sans_medium, FontWeight.Medium),
            Font(R.font.dm_sans_semibold, FontWeight.SemiBold),
            Font(R.font.dm_sans_bold, FontWeight.Bold),
        )

    /** For what is read character by character: SIP addresses, traces, logs. */
    val JetBrainsMono: FontFamily =
        FontFamily(
            Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
            Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
            Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold),
            Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
        )
}
