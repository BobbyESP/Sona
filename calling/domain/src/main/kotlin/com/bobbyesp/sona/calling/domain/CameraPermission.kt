/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.calling.domain

/**
 * Whether Sona may use the camera now. The coordinator checks it, so the camera is never asked of
 * the engine without the permission.
 */
fun interface CameraPermission {
    fun granted(): Boolean

    companion object {
        /** Always allowed: tests, previews. */
        val Granted = CameraPermission { true }
    }
}
