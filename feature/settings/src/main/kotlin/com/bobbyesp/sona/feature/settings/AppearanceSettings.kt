/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.settings

import android.os.Build
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Wallpaper
import com.bobbyesp.sona.core.model.settings.AppearanceSettings
import com.bobbyesp.sona.core.model.settings.BlurPreference
import com.bobbyesp.sona.core.model.settings.PaletteStyle
import com.bobbyesp.sona.core.model.settings.ThemeMode
import com.bobbyesp.sona.core.ui.contribution.SettingItem
import com.bobbyesp.sona.core.ui.contribution.SettingsAreas
import com.bobbyesp.sona.core.ui.contribution.SettingsContributor

/** How the app looks: the one area this feature owns. */
internal object AppearanceContributor : SettingsContributor {
    override val area = SettingsAreas.Appearance

    override val items: List<SettingItem> =
        listOfNotNull(
            SettingItem.Choice(
                setting = AppearanceSettings.Theme,
                title = R.string.appearance_theme,
                icon = Icons.Rounded.DarkMode,
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
                icon = Icons.Rounded.Wallpaper,
                summary = R.string.appearance_dynamic_summary,
                keywords = R.string.appearance_dynamic_keywords,
            ),
            SettingItem.Switch(
                setting = AppearanceSettings.PureBlack,
                title = R.string.appearance_pure_black,
                icon = Icons.Rounded.Contrast,
                summary = R.string.appearance_pure_black_summary,
                keywords = R.string.appearance_pure_black_keywords,
            ),
            SettingItem.Choice(
                setting = AppearanceSettings.Palette,
                title = R.string.appearance_palette,
                icon = Icons.Rounded.Palette,
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
            // Below Android 12 nothing blurs, so there is nothing to choose.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) blurItem() else null,
        )
}

private fun blurItem(): SettingItem =
    SettingItem.Choice(
        setting = AppearanceSettings.Blur,
        title = R.string.appearance_blur,
        icon = Icons.Rounded.BlurOn,
        summary = R.string.appearance_blur_summary,
        label = { preference ->
            when (preference) {
                BlurPreference.Automatic -> R.string.appearance_blur_automatic
                BlurPreference.Light -> R.string.appearance_blur_light
                BlurPreference.Off -> R.string.appearance_blur_off
            }
        },
        keywords = R.string.appearance_blur_keywords,
    )
