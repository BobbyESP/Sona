/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.calling.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The kind of network the phone uses now. */
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
    /**
     * Video is sent within the mobile data limits: on mobile data, and on a metered Wi-Fi, which is
     * mobile data a step away.
     */
    val constrained: Boolean
        get() = kind == NetworkKind.Cellular || (kind == NetworkKind.Wifi && metered)

    companion object {
        val Wifi = NetworkStatus(NetworkKind.Wifi, metered = false)
        val Cellular = NetworkStatus(NetworkKind.Cellular, metered = true)
        val None = NetworkStatus(NetworkKind.None, metered = false)
    }
}

/**
 * Which network the phone uses, as it changes. A port: the platform listens to Android's
 * connectivity (`:calling:platform`), and the engine's configuration is rebuilt from it, so a call
 * on mobile data sends within the mobile data limits.
 */
interface NetworkMonitor {
    val status: StateFlow<NetworkStatus>
}

/** A network that changes only when told: for tests and previews. */
class FakeNetworkMonitor(initial: NetworkStatus = NetworkStatus.Wifi) : NetworkMonitor {
    private val _status = MutableStateFlow(initial)
    override val status: StateFlow<NetworkStatus> = _status.asStateFlow()

    fun set(status: NetworkStatus) {
        _status.value = status
    }
}
