/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.calling.domain

import com.bobbyesp.sona.core.model.SipAddress

/**
 * Who an address is, for a call to say "Ana" instead of "102". A port: the calling domain does not
 * know contacts; the directory answers it.
 */
fun interface CallerNames {
    /** The name of whoever [address] belongs to, or `null` when it is nobody known. */
    suspend fun nameFor(address: SipAddress): String?

    companion object {
        /** Nobody is known: calls show what the server says, or the user part. */
        val None = CallerNames { null }
    }
}
