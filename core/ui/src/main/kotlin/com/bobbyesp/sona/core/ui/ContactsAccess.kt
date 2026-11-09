/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.core.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember

/**
 * The way to ask for the phone's contacts (`READ_CONTACTS`), in context: from a card in Contacts
 * the user chooses to press, never at start. Whether it was granted is the directory's to say (it
 * reads the permission itself); [onResult] only tells the screen to look again.
 */
@Stable
class ContactsAccess internal constructor() {
    internal var launch: () -> Unit = {}

    fun request() = launch()
}

@Composable
fun rememberContactsAccess(onResult: (granted: Boolean) -> Unit): ContactsAccess {
    val access = remember { ContactsAccess() }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), onResult)
    access.launch = { launcher.launch(Manifest.permission.READ_CONTACTS) }
    return access
}
