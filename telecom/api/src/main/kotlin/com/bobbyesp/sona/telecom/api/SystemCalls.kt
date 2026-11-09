/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.telecom.api

import com.bobbyesp.sona.core.model.CallId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Android's own call system (Telecom), as the calling domain needs it: telling it a call exists,
 * moving the call through its states, and learning what it decides (the audio route, a headset's
 * answer, a watch's hang-up). No Android type: Core-Telecom stays in `:telecom:jetpack`.
 */
interface SystemCalls {
    /**
     * Tells the system about a call and returns its handle, or `null` when the system refused it:
     * the call then goes on without system integration (no headset buttons), rather than failing.
     */
    suspend fun add(request: SystemCallRequest, events: SystemCallEvents): SystemCall?
}

data class SystemCallRequest(
    val id: CallId,
    val displayName: String,
    /** The remote address, as the system shows it and logs it: `sip:101@lab.local`. */
    val address: String,
    val incoming: Boolean,
)

/**
 * What the system asks of a call: a headset, a watch, a car, or the system itself (another app's
 * call taking over). Each must be carried out within 5 seconds, or the system may end the call.
 */
interface SystemCallEvents {
    suspend fun onAnswer()

    suspend fun onDisconnect()

    suspend fun onSetActive()

    suspend fun onSetInactive()
}

/** A call the system knows. */
interface SystemCall {
    /** Where the audio goes now; `null` until the system says. */
    val route: StateFlow<AudioRoute?>

    /** Where it could go. */
    val routes: StateFlow<List<AudioRoute>>

    suspend fun answer()

    suspend fun setActive()

    suspend fun setInactive()

    suspend fun disconnect(cause: DisconnectCause)

    suspend fun requestRoute(route: AudioRoute)
}

enum class DisconnectCause {
    Local,
    Remote,
    Rejected,
    Missed,
    Busy,
    Error,
}

enum class AudioRouteKind {
    Earpiece,
    Speaker,
    WiredHeadset,
    Bluetooth,
    Other,
}

/** A place the audio can go, as the system names it ("Pixel Buds"). */
data class AudioRoute(val id: String, val kind: AudioRouteKind, val name: String)

/**
 * A system that accepts every call and records what was asked of it; [events] are the calls'
 * listeners, to play a headset's part in a test.
 */
class FakeSystemCalls(private val refuse: Boolean = false) : SystemCalls {
    val calls = mutableMapOf<CallId, FakeSystemCall>()
    val events = mutableMapOf<CallId, SystemCallEvents>()

    override suspend fun add(request: SystemCallRequest, events: SystemCallEvents): SystemCall? {
        if (refuse) return null
        this.events[request.id] = events
        return FakeSystemCall(request).also { calls[request.id] = it }
    }
}

class FakeSystemCall(val request: SystemCallRequest) : SystemCall {
    /**
     * Every request made of the system, in order: `answer`, `active`, `inactive`,
     * `disconnect:Busy`…
     */
    val log = mutableListOf<String>()

    override val route = MutableStateFlow<AudioRoute?>(Earpiece)
    override val routes = MutableStateFlow(listOf(Earpiece, Speaker))

    override suspend fun answer() {
        log += "answer"
    }

    override suspend fun setActive() {
        log += "active"
    }

    override suspend fun setInactive() {
        log += "inactive"
    }

    override suspend fun disconnect(cause: DisconnectCause) {
        log += "disconnect:$cause"
    }

    override suspend fun requestRoute(route: AudioRoute) {
        log += "route:${route.kind}"
        this.route.value = route
    }

    companion object {
        val Earpiece = AudioRoute("earpiece", AudioRouteKind.Earpiece, "Phone")
        val Speaker = AudioRoute("speaker", AudioRouteKind.Speaker, "Speaker")
    }
}
