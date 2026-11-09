/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.telecom.jetpack

import android.content.Context
import android.net.Uri
import android.telecom.DisconnectCause as PlatformCause
import android.util.Log
import androidx.core.telecom.CallAttributesCompat
import androidx.core.telecom.CallControlScope
import androidx.core.telecom.CallEndpointCompat
import androidx.core.telecom.CallsManager
import com.bobbyesp.sona.telecom.api.AudioRoute
import com.bobbyesp.sona.telecom.api.AudioRouteKind
import com.bobbyesp.sona.telecom.api.DisconnectCause
import com.bobbyesp.sona.telecom.api.SystemCall
import com.bobbyesp.sona.telecom.api.SystemCallEvents
import com.bobbyesp.sona.telecom.api.SystemCallRequest
import com.bobbyesp.sona.telecom.api.SystemCalls
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** The app's system calls: Android's Telecom through Core-Telecom, which stays a detail. */
fun Context.createSystemCalls(scope: CoroutineScope): SystemCalls =
    JetpackSystemCalls(applicationContext, scope)

/**
 * Calls registered with Android's Telecom as the app's own (self-managed) calls, through
 * Core-Telecom: Telecom from Android 14, a `ConnectionService` below, the same API either way.
 *
 * Each call lives in a coroutine of [scope] for as long as Telecom has it: `addCall` runs its
 * block, and the call stays until it is disconnected. Telecom owns the audio route; the engine's
 * voice streams follow whatever device it chooses.
 */
internal class JetpackSystemCalls(context: Context, private val scope: CoroutineScope) :
    SystemCalls {

    private val manager = CallsManager(context)
    private var registered = false

    override suspend fun add(request: SystemCallRequest, events: SystemCallEvents): SystemCall? {
        if (!registered) {
            try {
                manager.registerAppWithTelecom(CallsManager.CAPABILITY_BASELINE)
                registered = true
            } catch (error: Exception) {
                Log.w(TAG, "Telecom refused to register the app", error)
                return null
            }
        }

        val ready = CompletableDeferred<SystemCall?>()
        val job = scope.launch {
            try {
                manager.addCall(
                    callAttributes = request.toAttributes(),
                    onAnswer = { _ -> events.onAnswer() },
                    onDisconnect = { _ -> events.onDisconnect() },
                    onSetActive = { events.onSetActive() },
                    onSetInactive = { events.onSetInactive() },
                ) {
                    val call = JetpackSystemCall(this)
                    launch { currentCallEndpoint.collect { call.onRoute(it) } }
                    launch { availableEndpoints.collect { call.onRoutes(it) } }
                    ready.complete(call)
                }
            } catch (error: Exception) {
                Log.w(TAG, "Telecom refused call ${request.id}", error)
                ready.complete(null)
            }
        }
        // Telecom answers within a second or two; beyond that the call goes on without it.
        return withTimeoutOrNull(5.seconds) { ready.await() }
            ?: run {
                job.cancel()
                null
            }
    }

    private fun SystemCallRequest.toAttributes() =
        CallAttributesCompat(
            displayName = displayName,
            address = Uri.parse(address),
            direction =
                if (incoming) CallAttributesCompat.DIRECTION_INCOMING
                else CallAttributesCompat.DIRECTION_OUTGOING,
            callType = CallAttributesCompat.CALL_TYPE_AUDIO_CALL,
            callCapabilities = CallAttributesCompat.SUPPORTS_SET_INACTIVE,
        )

    private companion object {
        const val TAG = "SonaTelecom"
    }
}

private class JetpackSystemCall(private val control: CallControlScope) : SystemCall {
    private val _route = MutableStateFlow<AudioRoute?>(null)
    override val route: StateFlow<AudioRoute?> = _route.asStateFlow()

    private val _routes = MutableStateFlow<List<AudioRoute>>(emptyList())
    override val routes: StateFlow<List<AudioRoute>> = _routes.asStateFlow()

    private var endpoints: Map<String, CallEndpointCompat> = emptyMap()

    fun onRoute(endpoint: CallEndpointCompat) {
        _route.value = endpoint.toRoute()
    }

    fun onRoutes(available: List<CallEndpointCompat>) {
        endpoints = available.associateBy { it.identifier.toString() }
        _routes.value = available.map { it.toRoute() }
    }

    override suspend fun answer() {
        guarded("answer") { control.answer(CallAttributesCompat.CALL_TYPE_AUDIO_CALL) }
    }

    override suspend fun setActive() {
        guarded("setActive") { control.setActive() }
    }

    override suspend fun setInactive() {
        guarded("setInactive") { control.setInactive() }
    }

    override suspend fun disconnect(cause: DisconnectCause) {
        guarded("disconnect") { control.disconnect(PlatformCause(cause.toPlatform())) }
    }

    override suspend fun requestRoute(route: AudioRoute) {
        endpoints[route.id]?.let { guarded("route") { control.requestEndpointChange(it) } }
    }
}

/** Telecom refusing a request is logged, never a crash: the call goes on without it. */
private suspend fun guarded(what: String, request: suspend () -> Any?) {
    try {
        request()
    } catch (error: Exception) {
        Log.w("SonaTelecom", "Telecom refused $what", error)
    }
}

private fun CallEndpointCompat.toRoute() =
    AudioRoute(
        id = identifier.toString(),
        kind =
            when (type) {
                CallEndpointCompat.TYPE_EARPIECE -> AudioRouteKind.Earpiece
                CallEndpointCompat.TYPE_SPEAKER -> AudioRouteKind.Speaker
                CallEndpointCompat.TYPE_WIRED_HEADSET -> AudioRouteKind.WiredHeadset
                CallEndpointCompat.TYPE_BLUETOOTH -> AudioRouteKind.Bluetooth
                else -> AudioRouteKind.Other
            },
        name = name.toString(),
    )

/**
 * Telecom's causes. Self-managed calls may only end as local, remote, missed or rejected: any other
 * code throws (found on a device), so a busy extension is a remote end, and an error a local one.
 */
private fun DisconnectCause.toPlatform(): Int =
    when (this) {
        DisconnectCause.Local,
        DisconnectCause.Error -> PlatformCause.LOCAL
        DisconnectCause.Remote,
        DisconnectCause.Busy -> PlatformCause.REMOTE
        DisconnectCause.Rejected -> PlatformCause.REJECTED
        DisconnectCause.Missed -> PlatformCause.MISSED
    }
