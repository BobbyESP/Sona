/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.accounts.domain

import com.bobbyesp.sona.core.model.AccountId
import com.bobbyesp.sona.core.model.SipAddress
import com.bobbyesp.sona.core.model.settings.Overrides
import com.bobbyesp.sona.sip.api.ServerEndpoint
import com.bobbyesp.sona.sip.api.Transport
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * A SIP account on the user's own server: an extension on their Asterisk. Stored on the phone and
 * nowhere else; its password is in the vault, not here.
 *
 * @property domain The SIP domain of the identity (`102@lab.local`); for Asterisk usually the
 *   server's own address.
 * @property revision Bumped on every save, so a change of password alone (which is not in this
 *   class) still reaches the engine.
 * @property overrides Its own values for the options an account may override (codecs, DTMF).
 */
data class Account(
    val id: AccountId,
    val label: String,
    val color: Int,
    val username: String,
    val domain: String,
    val server: ServerEndpoint,
    val displayName: String? = null,
    val authUsername: String? = null,
    val outboundProxy: ServerEndpoint? = null,
    val expiry: Duration = DefaultExpiry,
    val voicemailNumber: String = DEFAULT_VOICEMAIL,
    val enabled: Boolean = true,
    val isDefault: Boolean = false,
    val revision: Long = 0,
    val overrides: Overrides = Overrides(),
) {
    val identity: SipAddress
        get() = SipAddress(user = username, host = domain)

    companion object {
        val DefaultExpiry: Duration = 600.seconds
        const val DEFAULT_VOICEMAIL = "*97"
        const val DEFAULT_PORT = 5060
        const val DEFAULT_TLS_PORT = 5061
    }
}

/** The port a transport uses when none is given. */
val Transport.defaultPort: Int
    get() = if (this == Transport.Tls) Account.DEFAULT_TLS_PORT else Account.DEFAULT_PORT

/**
 * The colors an account can take, as hues the theme harmonizes (see `SonaAccentTheme`). A new
 * account takes the first one no other account has.
 */
object AccountColors {
    val palette: List<Int> =
        listOf(
                0xFF00696B, // teal
                0xFF3F5AA8, // blue
                0xFF8E4585, // plum
                0xFFB3261E, // red
                0xFF8B5000, // amber
                0xFF386A20, // green
                0xFF6750A4, // violet
                0xFF006874, // cyan
            )
            .map { it.toInt() }

    fun next(taken: Collection<Int>): Int =
        palette.firstOrNull { it !in taken } ?: palette[taken.size % palette.size]
}
