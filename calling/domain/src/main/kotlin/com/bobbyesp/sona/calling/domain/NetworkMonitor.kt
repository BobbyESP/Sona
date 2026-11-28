/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.calling.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NetworkKind {
    /** Wi-Fi, or a cable. */
    Wifi,
    Cellular,
    None,
}

/**
 * The network in use. [metered] is the system's word for "costs by the byte": mobile data, and a
 * Wi-Fi that is a phone's hotspot.
 */
data class NetworkStatus(val kind: NetworkKind, val metered: Boolean) {
    /** A metered Wi-Fi counts too: it is mobile data a step away. */
    val constrained: Boolean
        get() = kind == NetworkKind.Cellular || (kind == NetworkKind.Wifi && metered)

    companion object {
        val Wifi = NetworkStatus(NetworkKind.Wifi, metered = false)
        val Cellular = NetworkStatus(NetworkKind.Cellular, metered = true)
        val None = NetworkStatus(NetworkKind.None, metered = false)
    }
}

/** Which network the phone uses, as it changes: calls on mobile data send within its limits. */
interface NetworkMonitor {
    val status: StateFlow<NetworkStatus>
}

/** For tests and previews: changes only when told. */
class FakeNetworkMonitor(initial: NetworkStatus = NetworkStatus.Wifi) : NetworkMonitor {
    private val _status = MutableStateFlow(initial)
    override val status: StateFlow<NetworkStatus> = _status.asStateFlow()

    fun set(status: NetworkStatus) {
        _status.value = status
    }
}
