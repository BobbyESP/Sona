/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.core.ui.contribution

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Troubleshoot
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import com.bobbyesp.sona.core.model.settings.Setting
import com.bobbyesp.sona.core.ui.R

/**
 * A group of options on the settings root: *Calls*, *Appearance*. Several features may add items to
 * the same area (each names it by [id]); the settings screens list the areas that have any.
 */
data class SettingsArea(
    val id: String,
    /** Lower first. */
    val order: Int,
    @StringRes val title: Int,
    @StringRes val summary: Int,
    val icon: ImageVector,
)

/** The areas, declared once so every feature that contributes to one names the same. */
object SettingsAreas {
    val Calls =
        SettingsArea(
            "calls",
            10,
            R.string.settings_area_calls,
            R.string.settings_area_calls_summary,
            Icons.Rounded.Call,
        )
    val Directory =
        SettingsArea(
            "directory",
            20,
            R.string.settings_area_directory,
            R.string.settings_area_directory_summary,
            Icons.Rounded.Contacts,
        )
    val Appearance =
        SettingsArea(
            "appearance",
            30,
            R.string.settings_area_appearance,
            R.string.settings_area_appearance_summary,
            Icons.Rounded.Palette,
        )
    val Diagnostics =
        SettingsArea(
            "diagnostics",
            90,
            R.string.settings_area_diagnostics,
            R.string.settings_area_diagnostics_summary,
            Icons.Rounded.Troubleshoot,
        )
}

/**
 * One option, declared as data: the settings screens draw it (and search it) without knowing the
 * feature it belongs to. [keywords] is a string resource of space separated words the search also
 * matches ("ring busy 486").
 */
sealed interface SettingItem {
    @get:StringRes val title: Int

    @get:StringRes val summary: Int?

    @get:StringRes val keywords: Int?

    class Switch(
        val setting: Setting.Toggle,
        @StringRes override val title: Int,
        @StringRes override val summary: Int? = null,
        @StringRes override val keywords: Int? = null,
    ) : SettingItem

    /** One of an enum's values, [label] naming each. */
    class Choice<E : Enum<E>>(
        val setting: Setting.Choice<E>,
        @StringRes override val title: Int,
        val label: (E) -> Int,
        @StringRes override val summary: Int? = null,
        @StringRes override val keywords: Int? = null,
    ) : SettingItem

    /** Something that is done, not stored (*Clear history*), asked about first when [confirm]. */
    class Action(
        @StringRes override val title: Int,
        @StringRes override val summary: Int? = null,
        @StringRes override val keywords: Int? = null,
        val destructive: Boolean = false,
        @StringRes val confirm: Int? = null,
        val run: suspend () -> Unit,
    ) : SettingItem

    /** A screen of its own, opened through its key. */
    class Link(
        @StringRes override val title: Int,
        val key: NavKey,
        @StringRes override val summary: Int? = null,
        @StringRes override val keywords: Int? = null,
    ) : SettingItem
}

/** What a feature adds to the settings: items in an [area]. */
interface SettingsContributor {
    val area: SettingsArea

    /** In the order they are shown. */
    val items: List<SettingItem>
}

/**
 * Opens the settings. The shell provides it; a top bar only shows its button, so no screen knows
 * where settings live. `null` where there is nowhere to open them.
 */
val LocalOpenSettings = androidx.compose.runtime.staticCompositionLocalOf<(() -> Unit)?> { null }
