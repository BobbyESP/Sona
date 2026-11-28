/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.core.navigation.keys

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** A conversation, by its id in the messages' store. */
@Serializable data class Conversation(val conversationId: Long) : NavKey

/**
 * Writing to someone new. A party's *Message* opens it with [address] chosen, and with [accountId]
 * for a history entry; with neither, it starts at the search.
 */
@Serializable
data class NewConversation(val address: String? = null, val accountId: String? = null) : NavKey
