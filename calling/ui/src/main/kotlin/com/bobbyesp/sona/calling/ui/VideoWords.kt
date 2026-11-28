/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.calling.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.bobbyesp.sona.calling.domain.CallQuality
import com.bobbyesp.sona.calling.domain.CameraBlocked

@Composable
fun CameraBlocked.text(): String =
    when (this) {
        CameraBlocked.NoPermission -> stringResource(R.string.camera_blocked_permission)
    }

/** The technical line under [text], for *Show SIP details*: what Android answered. */
val CameraBlocked.detail: String
    get() =
        when (this) {
            CameraBlocked.NoPermission -> "android.permission.CAMERA: not granted"
        }

@Composable
fun CallQuality.label(): String =
    stringResource(
        when (this) {
            CallQuality.Good -> R.string.quality_good
            CallQuality.Fair -> R.string.quality_fair
            CallQuality.Poor -> R.string.quality_poor
        },
    )
