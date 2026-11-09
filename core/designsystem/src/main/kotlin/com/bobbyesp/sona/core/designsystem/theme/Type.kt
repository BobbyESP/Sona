/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Material's type scale in [family]. Titles are bold, as in Docucraft, so a name on the call screen
 * or a list row reads from a distance; body text keeps Material's tracking off.
 */
internal fun createTypography(family: FontFamily): Typography {
    val base = Typography()

    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = family, fontWeight = FontWeight.Medium),
        displayMedium =
            base.displayMedium.copy(fontFamily = family, fontWeight = FontWeight.Medium),
        displaySmall = base.displaySmall.copy(fontFamily = family, fontWeight = FontWeight.Medium),
        headlineLarge =
            base.headlineLarge.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
        headlineMedium =
            base.headlineMedium.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
        headlineSmall =
            base.headlineSmall.copy(fontFamily = family, fontWeight = FontWeight.Medium),
        titleLarge = base.titleLarge.copy(fontFamily = family, fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontFamily = family, fontWeight = FontWeight.Bold),
        titleSmall = base.titleSmall.copy(fontFamily = family, fontWeight = FontWeight.Medium),
        bodyLarge =
            base.bodyLarge.copy(
                fontFamily = family,
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.sp,
            ),
        bodyMedium =
            base.bodyMedium.copy(
                fontFamily = family,
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.sp,
            ),
        bodySmall =
            base.bodySmall.copy(
                fontFamily = family,
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.sp,
            ),
        labelLarge = base.labelLarge.copy(fontFamily = family, fontWeight = FontWeight.Medium),
        labelMedium = base.labelMedium.copy(fontFamily = family, fontWeight = FontWeight.Medium),
        labelSmall = base.labelSmall.copy(fontFamily = family, fontWeight = FontWeight.Medium),
    )
}
