/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.core.model.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** What an account says about an option: nothing (the app's value applies), or its own value. */
sealed interface Override<out T> {
    data object Inherit : Override<Nothing>

    data class Value<T>(val value: T) : Override<T>
}

/** The value that applies: the account's own, or [app]'s. */
fun <T> Override<T>.over(app: T): T =
    when (this) {
        Override.Inherit -> app
        is Override.Value -> value
    }

/**
 * An account's own values, for the options declared `overridable`. Kept as a storage keeps them (by
 * key, as a [StoredValue]), so they are read with the same rule as the app's: a value that means
 * nothing anymore is no override.
 */
data class Overrides(val stored: Map<String, StoredValue> = emptyMap()) {

    operator fun <T : Any> get(setting: Setting<T>): Override<T> =
        stored[setting.key]?.let(setting::decode)?.let { Override.Value(it) } ?: Override.Inherit

    /** These overrides with [setting] set to [override]. Only an overridable option can be. */
    fun <T : Any> with(setting: Setting<T>, override: Override<T>): Overrides {
        require(setting.overridable) { "$setting cannot be overridden by an account" }
        return when (override) {
            Override.Inherit -> Overrides(stored - setting.key)
            is Override.Value -> {
                require(setting.accepts(override.value)) { "$setting refuses ${override.value}" }
                Overrides(stored + (setting.key to setting.encode(override.value)))
            }
        }
    }

    val isEmpty: Boolean
        get() = stored.isEmpty()
}

/** Resolves an option for an account: the account's value, over the app's, over the default. */
class EffectiveSettings(private val settings: SettingsStore) {
    fun <T : Any> observe(setting: Setting<T>, overrides: Overrides): Flow<T> =
        when (val own = overrides[setting]) {
            Override.Inherit -> settings.observe(setting)
            is Override.Value -> flowOf(own.value)
        }
}

/**
 * A stored value as one line of text, with its kind (`b:true`, `i:5`, `l:600000`, `s:opus,-gsm`):
 * how a storage of text only (a column, a saved state) keeps any option.
 */
fun StoredValue.toText(): String =
    when (this) {
        is StoredValue.Bool -> "b:$value"
        is StoredValue.Int32 -> "i:$value"
        is StoredValue.Int64 -> "l:$value"
        is StoredValue.Str -> "s:$value"
    }

/** The value [text] was written from by [toText], or `null` when it is not one. */
fun storedValueOf(text: String): StoredValue? {
    val body = text.substringAfter(':', missingDelimiterValue = "")
    return when (text.substringBefore(':')) {
        "b" -> body.toBooleanStrictOrNull()?.let(StoredValue::Bool)
        "i" -> body.toIntOrNull()?.let(StoredValue::Int32)
        "l" -> body.toLongOrNull()?.let(StoredValue::Int64)
        "s" -> if (text.startsWith("s:")) StoredValue.Str(body) else null
        else -> null
    }
}
