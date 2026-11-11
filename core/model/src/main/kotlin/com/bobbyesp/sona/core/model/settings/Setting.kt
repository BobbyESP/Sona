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

    /**
     * Named things in the order the user wants them, each on or off (the codecs). Stored as text,
     * `opus,g722,-gsm`: a `-` marks what is off. What is read always holds every one of [default]'s
     * names once: a name no longer known is dropped, one added since takes its default place at the
     * end.
     */
    class Ranked(key: String, default: Ranking, overridable: Boolean = false) :
        Setting<Ranking>(key, default, overridable) {
        private val names = default.entries.map { it.id }

        init {
            require(names.distinct().size == names.size) { "$key: a name is there twice" }
            require(names.none { ',' in it || it.startsWith('-') || it.isBlank() }) {
                "$key: a name cannot be stored"
            }
        }

        override fun encode(value: Ranking) =
            StoredValue.Str(
                value.entries.joinToString(",") { if (it.enabled) it.id else "-${it.id}" },
            )

        override fun decode(stored: StoredValue): Ranking? {
            val text = (stored as? StoredValue.Str)?.value ?: return null
            val read =
                text
                    .split(',')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .map { Ranking.Entry(it.removePrefix("-"), enabled = !it.startsWith('-')) }
                    .filter { it.id in names }
                    .distinctBy { it.id }
            val missing = default.entries.filter { known -> read.none { it.id == known.id } }
            return Ranking(read + missing)
        }

        override fun accepts(value: Ranking): Boolean {
            val given = value.entries.map { it.id }
            return given.size == names.size && given.toSet() == names.toSet()
        }
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

/** Named things in an order, each on or off. The value of a [Setting.Ranked]. */
data class Ranking(val entries: List<Entry>) {
    data class Entry(val id: String, val enabled: Boolean = true)

    /** What is on, first choice first. */
    val enabled: List<String>
        get() = entries.filter { it.enabled }.map { it.id }

    /** [id] one place earlier ([up]) or later; the same ranking when it is already at that end. */
    fun moved(id: String, up: Boolean): Ranking {
        val from = entries.indexOfFirst { it.id == id }
        val to = if (up) from - 1 else from + 1
        if (from < 0 || to !in entries.indices) return this
        val next = entries.toMutableList()
        next[from] = entries[to]
        next[to] = entries[from]
        return Ranking(next)
    }

    fun switched(id: String, enabled: Boolean): Ranking =
        Ranking(entries.map { if (it.id == id) it.copy(enabled = enabled) else it })
}

/** What a stored value reads as: its meaning, or the default when it has none. */
fun <T : Any> Setting<T>.read(stored: StoredValue?): T = stored?.let(::decode) ?: default
