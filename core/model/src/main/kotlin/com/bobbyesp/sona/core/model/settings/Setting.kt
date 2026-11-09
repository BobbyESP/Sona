/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.core.model.settings

import kotlin.enums.enumEntries
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * An option the user can change, declared once: its key, its default and how it is stored.
 *
 * Reading, writing, defaults, export and the settings screens' items all come from the declaration,
 * so adding an option is one line in its area's object (see [AppearanceSettings]) and nothing in a
 * repository. A storage only knows the four kinds of [StoredValue].
 *
 * @property key Where it is stored. Never renamed once released: a new key loses the user's choice.
 * @property overridable Whether an account may override it (codecs, transport, DTMF mode…).
 */
sealed class Setting<T : Any>(val key: String, val default: T, val overridable: Boolean) {

    /** What a storage keeps for [value]. */
    abstract fun encode(value: T): StoredValue

    /**
     * The value [stored] means, or `null` when it means nothing any more: a choice that was
     * removed, a number out of today's range, another kind of value under the same key. A `null`
     * reads as [default], so an old or damaged value never breaks a screen.
     */
    abstract fun decode(stored: StoredValue): T?

    /** Whether [value] may be written; the same rule [decode] applies to what is read. */
    open fun accepts(value: T): Boolean = true

    class Toggle(key: String, default: Boolean, overridable: Boolean = false) :
        Setting<Boolean>(key, default, overridable) {
        override fun encode(value: Boolean) = StoredValue.Bool(value)

        override fun decode(stored: StoredValue) = (stored as? StoredValue.Bool)?.value
    }

    class Number(
        key: String,
        default: Int,
        val range: IntRange = Int.MIN_VALUE..Int.MAX_VALUE,
        overridable: Boolean = false,
    ) : Setting<Int>(key, default, overridable) {
        init {
            require(default in range) { "$key: default $default is outside $range" }
        }

        override fun encode(value: Int) = StoredValue.Int32(value)

        override fun decode(stored: StoredValue) =
            (stored as? StoredValue.Int32)?.value?.takeIf { it in range }

        override fun accepts(value: Int) = value in range
    }

    class Text(key: String, default: String, overridable: Boolean = false) :
        Setting<String>(key, default, overridable) {
        override fun encode(value: String) = StoredValue.Str(value)

        override fun decode(stored: StoredValue) = (stored as? StoredValue.Str)?.value
    }

    /** A length of time, stored in milliseconds. */
    class Span(
        key: String,
        default: Duration,
        val range: ClosedRange<Duration> = Duration.ZERO..Duration.INFINITE,
        overridable: Boolean = false,
    ) : Setting<Duration>(key, default, overridable) {
        init {
            require(default in range) { "$key: default $default is outside $range" }
        }

        override fun encode(value: Duration) = StoredValue.Int64(value.inWholeMilliseconds)

        override fun decode(stored: StoredValue) =
            (stored as? StoredValue.Int64)?.value?.milliseconds?.takeIf { it in range }

        override fun accepts(value: Duration) = value in range
    }

    /** One of an enum's values, stored by name so reordering the enum changes nothing. */
    class Choice<E : Enum<E>>(
        key: String,
        default: E,
        val options: List<E>,
        overridable: Boolean = false,
    ) : Setting<E>(key, default, overridable) {
        init {
            require(default in options) { "$key: default $default is not an option" }
        }

        override fun encode(value: E) = StoredValue.Str(value.name)

        override fun decode(stored: StoredValue): E? {
            val name = (stored as? StoredValue.Str)?.value ?: return null
            return options.firstOrNull { it.name == name }
        }

        override fun accepts(value: E) = value in options
    }

    override fun toString(): String = "Setting($key)"

    companion object {
        /** A [Choice] among every value of [E]. */
        inline fun <reified E : Enum<E>> choice(
            key: String,
            default: E,
            overridable: Boolean = false,
        ): Choice<E> = Choice(key, default, enumEntries<E>(), overridable)
    }
}

/** The four kinds of value a storage keeps; every [Setting] is written as one of them. */
sealed interface StoredValue {
    data class Bool(val value: Boolean) : StoredValue

    data class Int32(val value: Int) : StoredValue

    data class Int64(val value: Long) : StoredValue

    data class Str(val value: String) : StoredValue
}

/** What a stored value reads as: its meaning, or the default when it has none. */
fun <T : Any> Setting<T>.read(stored: StoredValue?): T = stored?.let(::decode) ?: default
