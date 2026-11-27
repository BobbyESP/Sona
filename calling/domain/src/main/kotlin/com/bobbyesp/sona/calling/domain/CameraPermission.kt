/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.calling.domain

/**
 * Whether Sona may use the camera now. A port: the platform asks Android. The screen asks the user
 * for the permission before turning the camera on; this is what the coordinator checks, so a camera
 * the user did not allow is never asked of the engine, and the call goes on as audio.
 */
fun interface CameraPermission {
    fun granted(): Boolean

    companion object {
        /** Always allowed: tests, previews. */
        val Granted = CameraPermission { true }
    }
}
