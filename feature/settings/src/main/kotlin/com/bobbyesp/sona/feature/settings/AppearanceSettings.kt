/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.settings

import com.bobbyesp.sona.core.model.settings.AppearanceSettings
import com.bobbyesp.sona.core.model.settings.PaletteStyle
import com.bobbyesp.sona.core.model.settings.ThemeMode
import com.bobbyesp.sona.core.ui.contribution.SettingItem
import com.bobbyesp.sona.core.ui.contribution.SettingsAreas
import com.bobbyesp.sona.core.ui.contribution.SettingsContributor

/** How the app looks: the one area this feature owns. */
internal object AppearanceContributor : SettingsContributor {
    override val area = SettingsAreas.Appearance

    override val items: List<SettingItem> =
        listOf(
            SettingItem.Choice(
                setting = AppearanceSettings.Theme,
                title = R.string.appearance_theme,
                label = { mode ->
                    when (mode) {
                        ThemeMode.System -> R.string.appearance_theme_system
                        ThemeMode.Light -> R.string.appearance_theme_light
                        ThemeMode.Dark -> R.string.appearance_theme_dark
                    }
                },
                keywords = R.string.appearance_theme_keywords,
            ),
            SettingItem.Switch(
                setting = AppearanceSettings.DynamicColor,
                title = R.string.appearance_dynamic,
                summary = R.string.appearance_dynamic_summary,
                keywords = R.string.appearance_dynamic_keywords,
            ),
            SettingItem.Switch(
                setting = AppearanceSettings.PureBlack,
                title = R.string.appearance_pure_black,
                summary = R.string.appearance_pure_black_summary,
                keywords = R.string.appearance_pure_black_keywords,
            ),
            SettingItem.Choice(
                setting = AppearanceSettings.Palette,
                title = R.string.appearance_palette,
                summary = R.string.appearance_palette_summary,
                label = { style ->
                    when (style) {
                        PaletteStyle.TonalSpot -> R.string.appearance_palette_tonal_spot
                        PaletteStyle.Vibrant -> R.string.appearance_palette_vibrant
                        PaletteStyle.Expressive -> R.string.appearance_palette_expressive
                        PaletteStyle.FruitSalad -> R.string.appearance_palette_fruit_salad
                        PaletteStyle.Rainbow -> R.string.appearance_palette_rainbow
                        PaletteStyle.Monochrome -> R.string.appearance_palette_monochrome
                    }
                },
            ),
        )
}
