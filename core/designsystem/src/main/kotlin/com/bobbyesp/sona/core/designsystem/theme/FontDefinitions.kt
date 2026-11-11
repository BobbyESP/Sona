/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.core.designsystem.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import com.bobbyesp.sona.core.designsystem.R

/**
 * Fonts packaged in `res/font`. They load synchronously on the first frame, with no provider and no
 * network, so the typography never swaps its font after the app starts.
 */
internal object BundledFonts {
    /** For what is read character by character: SIP addresses, traces, logs. */
    val JetBrainsMono: FontFamily =
        FontFamily(
            Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
            Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
            Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold),
            Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
        )
}

/**
 * How a style sets Google Sans Flex's axes besides its weight: how round its strokes end, how wide
 * it runs, and the size its shapes are drawn for.
 *
 * @property roundness The `ROND` axis, 0 (crisp) to 100 (fully rounded terminals).
 * @property width The `wdth` axis, 25 (condensed) to 151 (expanded); 100 is normal.
 * @property opticalSize The size the glyphs are drawn for (`opsz`): a style's own font size, so
 *   large text is tight and small text open. Unspecified leaves the font's default.
 */
internal data class FlexVoice(
    val roundness: Float = 0f,
    val width: Float = 100f,
    val opticalSize: TextUnit = TextUnit.Unspecified,
)

/**
 * Google Sans Flex, one variable font, as font families. A family is made per [FlexVoice] and holds
 * every weight from 100 to 900, upright and slanted, so a `fontWeight` set on top of a style picks
 * a real instance of the font instead of a synthesized bold.
 *
 * Families are kept once made: the platform caches a typeface per font, and a family that is the
 * same object keeps that cache warm.
 */
internal object GoogleSansFlex {
    private val families = HashMap<FlexVoice, FontFamily>()

    fun family(voice: FlexVoice): FontFamily =
        synchronized(families) { families.getOrPut(voice) { build(voice) } }

    private fun build(voice: FlexVoice): FontFamily =
        FontFamily(
            Weights.flatMap { weight ->
                listOf(FontStyle.Normal, FontStyle.Italic).map { style ->
                    Font(
                        resId = R.font.google_sans_flex,
                        weight = weight,
                        style = style,
                        variationSettings = voice.settings(weight, style),
                    )
                }
            },
        )

    private fun FlexVoice.settings(weight: FontWeight, style: FontStyle): FontVariation.Settings {
        val axes = buildList {
            add(FontVariation.Setting(RoundnessAxis, roundness))
            add(FontVariation.width(width))
            if (style == FontStyle.Italic) add(FontVariation.Setting(SlantAxis, MaxSlant))
            if (opticalSize.isSpecified) add(FontVariation.opticalSizing(opticalSize))
        }
        return FontVariation.Settings(weight, style, *axes.toTypedArray())
    }

    private val Weights = (100..900 step 100).map(::FontWeight)
    private const val RoundnessAxis = "ROND"
    private const val SlantAxis = "slnt"
    private const val MaxSlant = -10f
}
