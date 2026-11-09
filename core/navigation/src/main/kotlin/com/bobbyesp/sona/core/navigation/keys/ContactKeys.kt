/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.core.navigation.keys

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Adding a contact, or editing one ([contactId], from the directory). Recents opens it to save a
 * number it shows, so it comes with that [number] and [name] already filled in.
 */
@Serializable
data class ContactEditor(
    val contactId: String? = null,
    val number: String? = null,
    val name: String? = null,
) : NavKey
