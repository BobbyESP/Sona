/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.core.navigation.keys

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** The settings root: the areas and the search. */
@Serializable data object Settings : NavKey

/** One area of the settings, by its id. */
@Serializable data class SettingsPage(val areaId: String) : NavKey

/** What Sona sees of the servers: registrations, network and engine. */
@Serializable data object Diagnostics : NavKey
