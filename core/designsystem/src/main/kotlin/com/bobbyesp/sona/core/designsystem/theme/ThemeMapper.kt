/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import com.bobbyesp.sona.core.model.settings.PaletteStyle
import com.bobbyesp.sona.core.model.settings.ThemeMode
import com.materialkolor.PaletteStyle as KolorPaletteStyle

/** Whether [this] mode means the dark theme right now. */
@Composable
fun ThemeMode.isDarkTheme(): Boolean =
    when (this) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

/** Maps the domain [PaletteStyle] to MaterialKolor's. */
fun PaletteStyle.toPaletteStyle(): KolorPaletteStyle =
    when (this) {
        PaletteStyle.TonalSpot -> KolorPaletteStyle.TonalSpot
        PaletteStyle.Vibrant -> KolorPaletteStyle.Vibrant
        PaletteStyle.Expressive -> KolorPaletteStyle.Expressive
        PaletteStyle.FruitSalad -> KolorPaletteStyle.FruitSalad
        PaletteStyle.Rainbow -> KolorPaletteStyle.Rainbow
        PaletteStyle.Monochrome -> KolorPaletteStyle.Monochrome
    }
