/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.core.model.settings

import kotlinx.coroutines.flow.Flow

/**
 * Where the user's options live. One implementation for every option, whatever its kind: DataStore
 * in the app, a map in tests.
 */
interface SettingsStore {
    /**
     * The current value of [setting], then every change. Never fails: unreadable is the default.
     */
    fun <T : Any> observe(setting: Setting<T>): Flow<T>

    /** Writes [value]. A value [Setting.accepts] refuses throws: it is a programming error. */
    suspend fun <T : Any> set(setting: Setting<T>, value: T)

    /** Back to the default, by forgetting the stored value. */
    suspend fun reset(setting: Setting<*>)
}
