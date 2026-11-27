/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.messaging.domain.envelope

/**
 * File names as another phone sends them, made safe to write and to show: a name is only ever a
 * name, never a path (`../../shared_prefs/x.xml` is `x.xml`), without control characters, and not
 * longer than a file system takes.
 */
object FileNames {
    private const val MAX_LENGTH = 120
    private const val FALLBACK = "file"

    fun safe(name: String): String {
        val last = name.substringAfterLast('/').substringAfterLast('\\')
        val cleaned =
            last
                .filterNot { it.isISOControl() || it in Forbidden }
                .trim()
                .trimStart('.')
                .ifBlank { FALLBACK }
        if (cleaned.length <= MAX_LENGTH) return cleaned
        val extension = cleaned.substringAfterLast('.', "").take(MAX_EXTENSION)
        val stem = cleaned.take(MAX_LENGTH - extension.length - 1)
        return if (extension.isEmpty()) cleaned.take(MAX_LENGTH) else "$stem.$extension"
    }

    private const val MAX_EXTENSION = 10
    private const val Forbidden = "<>:\"|?*"
}
