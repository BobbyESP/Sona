/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.core.navigation.keys

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * A conversation, by its id in the messages' store. Opened from the Messages list, a message's
 * notification, and the *New message* screen once it knows who to write to.
 */
@Serializable data class Conversation(val conversationId: Long) : NavKey

/**
 * Writing to someone new. A party's *Message* opens it with the [address] already chosen (`102`,
 * `sip:ana@lab.local`) and, from a history entry, the [accountId] the call was on; with neither, it
 * starts at the search for a contact or a number.
 */
@Serializable
data class NewConversation(val address: String? = null, val accountId: String? = null) : NavKey
