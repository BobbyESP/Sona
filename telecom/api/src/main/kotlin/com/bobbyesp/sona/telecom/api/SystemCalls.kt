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
    /**
     * A video call to the system: dialed with the camera, or ringing with video offered. A watch or
     * a car shows it as one, and the system prefers the speaker for it.
     */
    val video: Boolean = false,
)

/**
 * What the system asks of a call: a headset, a watch, a car, or the system itself (another app's
 * call taking over). Each must be carried out within 5 seconds, or the system may end the call.
 */
interface SystemCallEvents {
    /** An answer from another device: the call is answered without the camera, whatever it asks. */
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

    /** Tells the system the call was answered, as a video call when [video]. */
    suspend fun answer(video: Boolean = false)

    suspend fun setActive()

    suspend fun setInactive()

    suspend fun disconnect(cause: DisconnectCause)

    suspend fun requestRoute(route: AudioRoute)

    /**
     * Tells the system the call shows video now, or no longer does: its type follows the picture on
     * screen, either side's.
     */
    suspend fun setVideo(video: Boolean)
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
 * listeners, to play a headset's part in a test. Each call starts on [route], with [routes] to
 * choose from: a headset plugged in is `route = FakeSystemCall.Headset`.
 */
class FakeSystemCalls(
    private val refuse: Boolean = false,
    private val route: AudioRoute = FakeSystemCall.Earpiece,
    private val routes: List<AudioRoute> = listOf(FakeSystemCall.Earpiece, FakeSystemCall.Speaker),
) : SystemCalls {
    val calls = mutableMapOf<CallId, FakeSystemCall>()
    val events = mutableMapOf<CallId, SystemCallEvents>()

    override suspend fun add(request: SystemCallRequest, events: SystemCallEvents): SystemCall? {
        if (refuse) return null
        this.events[request.id] = events
        return FakeSystemCall(request, route, routes).also { calls[request.id] = it }
    }
}

class FakeSystemCall(
    val request: SystemCallRequest,
    route: AudioRoute = Earpiece,
    routes: List<AudioRoute> = listOf(Earpiece, Speaker),
) : SystemCall {
    /**
     * Every request made of the system, in order: `answer`, `answer:video`, `active`, `inactive`,
     * `disconnect:Busy`, `route:Speaker`, `video:true`…
     */
    val log = mutableListOf<String>()

    override val route = MutableStateFlow<AudioRoute?>(route)
    override val routes = MutableStateFlow(routes)

    override suspend fun answer(video: Boolean) {
        log += if (video) "answer:video" else "answer"
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

    override suspend fun setVideo(video: Boolean) {
        log += "video:$video"
    }

    companion object {
        val Earpiece = AudioRoute("earpiece", AudioRouteKind.Earpiece, "Phone")
        val Speaker = AudioRoute("speaker", AudioRouteKind.Speaker, "Speaker")
        val Headset = AudioRoute("wired", AudioRouteKind.WiredHeadset, "Headset")
    }
}
