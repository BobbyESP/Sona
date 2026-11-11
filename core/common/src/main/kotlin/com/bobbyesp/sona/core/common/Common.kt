/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.core.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * The dispatchers a class may switch to, given rather than reached for, so tests can replace them.
 */
interface DispatcherProvider {
    val main: CoroutineDispatcher
    val default: CoroutineDispatcher
    val io: CoroutineDispatcher
}

object DefaultDispatchers : DispatcherProvider {
    override val main: CoroutineDispatcher = Dispatchers.Main
    override val default: CoroutineDispatcher = Dispatchers.Default
    override val io: CoroutineDispatcher = Dispatchers.IO
}

/** Text for a ViewModel, which never holds a `Context`. [id] is an Android string resource. */
interface StringProvider {
    fun get(id: Int, vararg args: Any): String
}

/**
 * What this build of the app is, for *About*: only the app module knows its own version.
 *
 * @property sourceUrl Where the source of this app is published, which the GPLv3 owes its users.
 */
data class AppInfo(val version: String, val sourceUrl: String)
