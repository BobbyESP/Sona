/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Material's type scale in Google Sans Flex, each role with its own voice. Large text, read at a
 * glance, is fully round; titles a little less; body and label text, which is read, keep only a
 * hint of it, so the app is friendly where it speaks up and plain where it explains. Every role's
 * optical size is its own font size.
 *
 * The emphasized roles (Material 3 Expressive) are the same voices a weight step or two heavier:
 * for what the eye should land on first, as the number on the keypad, or a list item's headline.
 *
 * Weight follows importance, never the other way round: a list item's name (`bodyLargeEmphasized`)
 * outweighs what explains it (body) and what is said about it (a time, a state: the medium and
 * small labels, which stay light). Large labels speak for actions, and keep their weight.
 */
internal fun createTypography(): Typography {
    val base = Typography()

    fun TextStyle.voiced(weight: FontWeight, roundness: Float, width: Float = 100f): TextStyle =
        copy(
            fontFamily = GoogleSansFlex.family(FlexVoice(roundness, width, fontSize)),
            fontWeight = weight,
        )

    fun TextStyle.reading(weight: FontWeight, roundness: Float): TextStyle =
        voiced(weight, roundness).copy(letterSpacing = 0.sp)

    return Typography(
        displayLarge = base.displayLarge.voiced(FontWeight.Medium, Round),
        displayMedium = base.displayMedium.voiced(FontWeight.Medium, Round),
        displaySmall = base.displaySmall.voiced(FontWeight.Medium, Round),
        headlineLarge = base.headlineLarge.voiced(FontWeight.SemiBold, Round),
        headlineMedium = base.headlineMedium.voiced(FontWeight.SemiBold, Round),
        headlineSmall = base.headlineSmall.voiced(FontWeight.SemiBold, Round),
        titleLarge = base.titleLarge.voiced(FontWeight.SemiBold, Rounded),
        titleMedium = base.titleMedium.voiced(FontWeight.SemiBold, Rounded),
        titleSmall = base.titleSmall.voiced(FontWeight.SemiBold, Rounded),
        bodyLarge = base.bodyLarge.reading(FontWeight.Normal, Soft),
        bodyMedium = base.bodyMedium.reading(FontWeight.Normal, Soft),
        bodySmall = base.bodySmall.reading(FontWeight.Normal, Soft),
        labelLarge = base.labelLarge.voiced(FontWeight.SemiBold, Rounded),
        labelMedium = base.labelMedium.voiced(FontWeight.Medium, Rounded),
        labelSmall = base.labelSmall.voiced(FontWeight.Medium, Rounded),
        displayLargeEmphasized = base.displayLargeEmphasized.voiced(FontWeight.Bold, Round),
        displayMediumEmphasized = base.displayMediumEmphasized.voiced(FontWeight.Bold, Round),
        displaySmallEmphasized = base.displaySmallEmphasized.voiced(FontWeight.Bold, Round),
        headlineLargeEmphasized = base.headlineLargeEmphasized.voiced(FontWeight.Bold, Round),
        headlineMediumEmphasized = base.headlineMediumEmphasized.voiced(FontWeight.Bold, Round),
        headlineSmallEmphasized = base.headlineSmallEmphasized.voiced(FontWeight.Bold, Round),
        titleLargeEmphasized = base.titleLargeEmphasized.voiced(FontWeight.ExtraBold, Rounded),
        titleMediumEmphasized = base.titleMediumEmphasized.voiced(FontWeight.ExtraBold, Rounded),
        titleSmallEmphasized = base.titleSmallEmphasized.voiced(FontWeight.Bold, Rounded),
        bodyLargeEmphasized = base.bodyLargeEmphasized.reading(FontWeight.SemiBold, Soft),
        bodyMediumEmphasized = base.bodyMediumEmphasized.reading(FontWeight.SemiBold, Soft),
        bodySmallEmphasized = base.bodySmallEmphasized.reading(FontWeight.SemiBold, Soft),
        labelLargeEmphasized = base.labelLargeEmphasized.voiced(FontWeight.Bold, Rounded),
        labelMediumEmphasized = base.labelMediumEmphasized.voiced(FontWeight.Bold, Rounded),
        labelSmallEmphasized = base.labelSmallEmphasized.voiced(FontWeight.Bold, Rounded),
    )
}

/**
 * Numerals: what is typed on the keypad, the keys' digits, durations. Round and a little wide, with
 * figures of one width, so a number does not shift as it grows and reads like the keys it came
 * from.
 */
internal fun createNumerals(typography: Typography): SonaNumerals {
    fun TextStyle.numeral(weight: FontWeight): TextStyle =
        copy(
            fontFamily = GoogleSansFlex.family(FlexVoice(Round, NumeralWidth, fontSize)),
            fontWeight = weight,
            fontFeatureSettings = "tnum",
            letterSpacing = 0.sp,
        )

    return SonaNumerals(
        large =
            typography.displayLarge
                .copy(fontSize = 52.sp, lineHeight = 1.15.em)
                .numeral(FontWeight.Medium),
        medium = typography.headlineLarge.numeral(FontWeight.Medium),
        small = typography.titleLarge.numeral(FontWeight.SemiBold),
    )
}

/** Fully rounded terminals: display and headline text. */
private const val Round = 100f

/** Titles and labels: round enough to be friendly, crisp enough to scan. */
private const val Rounded = 60f

/** Text that is read: a hint of the round, never at the cost of legibility. */
private const val Soft = 20f

private const val NumeralWidth = 110f
