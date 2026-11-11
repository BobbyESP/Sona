/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.accounts.domain

import com.bobbyesp.sona.core.model.AccountId
import com.bobbyesp.sona.core.model.settings.Overrides
import com.bobbyesp.sona.sip.api.ServerEndpoint
import com.bobbyesp.sona.sip.api.Transport
import kotlin.time.Duration.Companion.seconds

/**
 * What the account editor holds: the fields as the user typed them. [validate] turns it into an
 * [Account] or says, field by field, what is wrong; nothing is saved before that.
 *
 * Only [server], [username] and the password (kept apart, in the editor) are asked for at first;
 * the rest are under *More options* and have defaults.
 */
data class AccountDraft(
    val id: AccountId? = null,
    val server: String = "",
    val username: String = "",
    val label: String = "",
    val transport: Transport = Transport.Udp,
    val port: String = "",
    val domain: String = "",
    val displayName: String = "",
    val authUsername: String = "",
    val outboundProxy: String = "",
    val expirySeconds: String = "",
    val voicemailNumber: String = "",
    val color: Int? = null,
    val enabled: Boolean = true,
    val overrides: Overrides = Overrides(),
) {
    /** The label shown when the user gives none: `lab.local · 102`. */
    val suggestedLabel: String
        get() =
            listOf(server.trim(), username.trim()).filter { it.isNotEmpty() }.joinToString(" · ")

    companion object {
        /** The draft of an existing account, to edit it. */
        fun of(account: Account): AccountDraft =
            AccountDraft(
                id = account.id,
                server = account.server.host,
                username = account.username,
                label = account.label,
                transport = account.server.transport,
                port =
                    account.server.port
                        ?.takeIf { it != account.server.transport.defaultPort }
                        ?.toString()
                        .orEmpty(),
                domain = account.domain.takeIf { it != account.server.host }.orEmpty(),
                displayName = account.displayName.orEmpty(),
                authUsername = account.authUsername.orEmpty(),
                outboundProxy = account.outboundProxy?.host.orEmpty(),
                expirySeconds =
                    account.expiry
                        .takeIf { it != Account.DefaultExpiry }
                        ?.inWholeSeconds
                        ?.toString()
                        .orEmpty(),
                voicemailNumber =
                    account.voicemailNumber.takeIf { it != Account.DEFAULT_VOICEMAIL }.orEmpty(),
                color = account.color,
                enabled = account.enabled,
                overrides = account.overrides,
            )
    }
}

/** A field of the draft, to put a problem under it. */
enum class DraftField {
    Server,
    Username,
    Port,
    Domain,
    AuthUsername,
    OutboundProxy,
    Expiry,
}

/** What is wrong with a field. */
enum class DraftProblem {
    Required,
    NotAHost,
    NotAUsername,
    OutOfRange,
}

sealed interface DraftValidation {
    data class Valid(val account: Account) : DraftValidation

    data class Invalid(val problems: Map<DraftField, DraftProblem>) : DraftValidation
}

/**
 * The account [this] describes, or every problem it has. Spaces around values are dropped; a server
 * written as `sip:lab.local:5070` is understood as host and port.
 *
 * @param id The id to give a new account.
 * @param color The color to give it when the draft has none.
 */
fun AccountDraft.validate(id: AccountId, color: Int): DraftValidation {
    val problems = mutableMapOf<DraftField, DraftProblem>()

    val (host, portInServer) = splitServer(server)
    if (host.isEmpty()) problems[DraftField.Server] = DraftProblem.Required
    else if (!host.isHost()) problems[DraftField.Server] = DraftProblem.NotAHost

    val user = username.trim()
    if (user.isEmpty()) problems[DraftField.Username] = DraftProblem.Required
    else if (!user.isUsername()) problems[DraftField.Username] = DraftProblem.NotAUsername

    val portValue = port.trim().ifEmpty { null }?.let { it.toIntOrNull() ?: -1 } ?: portInServer
    if (portValue != null && portValue !in 1..65535) {
        problems[DraftField.Port] = DraftProblem.OutOfRange
    }

    val domainValue = domain.trim().ifEmpty { host }
    if (domain.isNotBlank() && !domainValue.isHost()) {
        problems[DraftField.Domain] = DraftProblem.NotAHost
    }

    val auth = authUsername.trim().ifEmpty { null }
    if (auth != null && !auth.isUsername()) {
        problems[DraftField.AuthUsername] = DraftProblem.NotAUsername
    }

    val (proxyHost, proxyPort) = splitServer(outboundProxy)
    if (proxyHost.isNotEmpty() && !proxyHost.isHost()) {
        problems[DraftField.OutboundProxy] = DraftProblem.NotAHost
    } else if (proxyPort != null && proxyPort !in 1..65535) {
        problems[DraftField.OutboundProxy] = DraftProblem.OutOfRange
    }

    val expiry = expirySeconds.trim().ifEmpty { null }?.let { it.toIntOrNull() ?: -1 }
    if (expiry != null && expiry !in MinExpiry..MaxExpiry) {
        problems[DraftField.Expiry] = DraftProblem.OutOfRange
    }

    if (problems.isNotEmpty()) return DraftValidation.Invalid(problems)

    return DraftValidation.Valid(
        Account(
            id = this.id ?: id,
            label = label.trim().ifEmpty { suggestedLabel },
            color = this.color ?: color,
            username = user,
            domain = domainValue,
            server = ServerEndpoint(host, portValue ?: transport.defaultPort, transport),
            displayName = displayName.trim().ifEmpty { null },
            authUsername = auth,
            outboundProxy =
                proxyHost.ifEmpty { null }?.let { ServerEndpoint(it, proxyPort, transport) },
            expiry = expiry?.seconds ?: Account.DefaultExpiry,
            voicemailNumber = voicemailNumber.trim().ifEmpty { Account.DEFAULT_VOICEMAIL },
            enabled = enabled,
            overrides = overrides,
        ),
    )
}

private const val MinExpiry = 60
private const val MaxExpiry = 86_400

/** `sip:host:port`, `host:port` or `host` as host and port; IPv6 hosts keep their brackets. */
private fun splitServer(text: String): Pair<String, Int?> {
    val bare =
        text.trim().removePrefix("sips:").removePrefix("sip:").substringBefore(';').trimEnd('/')
    return if (bare.startsWith("[")) {
        val end = bare.indexOf(']')
        if (end < 0) bare to null
        else bare.substring(0, end + 1) to bare.substring(end + 1).removePrefix(":").toIntOrNull()
    } else if (bare.count { it == ':' } == 1) {
        bare.substringBefore(':') to (bare.substringAfter(':').toIntOrNull() ?: -1)
    } else {
        bare to null
    }
}

private val HostLabel = Regex("^[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?$")

/** A host name, an IPv4 address, or a bracketed IPv6 address. */
private fun String.isHost(): Boolean =
    when {
        startsWith("[") && endsWith("]") ->
            length > 2 && drop(1).dropLast(1).all { it.isLetterOrDigit() || it == ':' || it == '.' }
        else -> split('.').all { HostLabel.matches(it) } && length <= 253
    }

/** What can be the user part of a SIP address without escaping. */
private fun String.isUsername(): Boolean = none {
    it.isWhitespace() || it == '@' || it == ':' || it == ';' || it == '<' || it == '>'
}
