/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.core.testing

import com.bobbyesp.sona.core.common.DispatcherProvider
import com.bobbyesp.sona.core.model.settings.Setting
import com.bobbyesp.sona.core.model.settings.SettingsStore
import com.bobbyesp.sona.core.model.settings.StoredValue
import com.bobbyesp.sona.core.model.settings.read
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * A [SettingsStore] over a map, encoding and decoding exactly as the DataStore one does, so what a
 * test reads is what the app would read.
 */
class InMemorySettingsStore(initial: Map<String, StoredValue> = emptyMap()) : SettingsStore {
    private val values = MutableStateFlow(initial)

    /** What is stored, as a storage would see it; for asserting what was written. */
    val stored: Map<String, StoredValue>
        get() = values.value

    override fun <T : Any> observe(setting: Setting<T>): Flow<T> =
        values.map { setting.read(it[setting.key]) }.distinctUntilChanged()

    override suspend fun <T : Any> set(setting: Setting<T>, value: T) {
        require(setting.accepts(value)) { "$setting refuses $value" }
        values.update { it + (setting.key to setting.encode(value)) }
    }

    override suspend fun reset(setting: Setting<*>) {
        values.update { it - setting.key }
    }
}

/** Every dispatcher is [dispatcher], so a test controls all the time there is. */
class TestDispatchers(val dispatcher: TestDispatcher = StandardTestDispatcher()) :
    DispatcherProvider {
    override val main: CoroutineDispatcher = dispatcher
    override val default: CoroutineDispatcher = dispatcher
    override val io: CoroutineDispatcher = dispatcher
}

/** Replaces `Dispatchers.Main`, which a ViewModel's scope uses, for the length of a test. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = StandardTestDispatcher()) :
    TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)

    override fun finished(description: Description) = Dispatchers.resetMain()
}
