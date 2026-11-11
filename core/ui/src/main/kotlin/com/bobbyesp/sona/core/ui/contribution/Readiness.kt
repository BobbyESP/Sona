/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.core.ui.contribution

import android.content.Intent
import androidx.annotation.StringRes
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

/**
 * Something outside Sona's screens that silently breaks calls when it is wrong: a permission, a
 * system setting, an account that is off. Each is a contribution, shown in *Call readiness* (in
 * onboarding and in the settings) with what it breaks and a button that fixes it; the account chip
 * warns while any is a problem. See docs/calling.md, Call readiness.
 */
interface ReadinessCheck {
    val id: String

    /** Lower first: what breaks the most comes first. */
    val order: Int

    /** What is checked, as a name: *Microphone*. */
    @get:StringRes val title: Int

    /** What does not work while it is a problem: *The other side will not hear you*. */
    @get:StringRes val problem: Int

    /** The button that fixes it: *Allow*. */
    @get:StringRes val fixLabel: Int

    val fix: ReadinessFix

    /** How it is now. Asked again whenever the app comes back, and whenever [changes] says so. */
    suspend fun check(): ReadinessState

    /** Emits when the answer of [check] may have changed without the app leaving the screen. */
    val changes: Flow<*>
        get() = emptyFlow<Unit>()
}

enum class ReadinessState {
    Ready,
    Problem,

    /** Not a question on this phone (an Android version without that setting): not shown. */
    NotApplicable,
}

/** What the fix button does. The screen carries it out; a check never touches an activity. */
sealed interface ReadinessFix {
    /**
     * Asks for runtime permissions; once refused for good, their place in the system's settings.
     */
    data class AskPermissions(val permissions: List<String>) : ReadinessFix

    /** Opens a screen of the system's settings. */
    data class SystemSettings(val intent: Intent) : ReadinessFix

    /** Opens a destination of the app. */
    data class Open(val key: NavKey) : ReadinessFix
}

data class ReadinessResult(val check: ReadinessCheck, val state: ReadinessState)

/** What keeps calls from working, most serious first. */
val List<ReadinessResult>.problems: List<ReadinessResult>
    get() = filter { it.state == ReadinessState.Problem }

/**
 * Every [ReadinessCheck], kept answered: at start, at each [refresh] (the app coming back from the
 * system's settings, a permission just answered) and whenever a check says it changed. One for the
 * app, so the readiness screen and the account chip never disagree.
 */
class ReadinessMonitor(checks: List<ReadinessCheck>, scope: CoroutineScope) {
    private val checks = checks.sortedBy { it.order }
    private val refreshes =
        MutableSharedFlow<Unit>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    private val _results = MutableStateFlow<List<ReadinessResult>?>(null)

    /** `null` until the checks were first answered. Without [ReadinessState.NotApplicable] ones. */
    val results: StateFlow<List<ReadinessResult>?> = _results.asStateFlow()

    init {
        scope.launch {
            val triggers =
                this@ReadinessMonitor.checks.map { check -> check.changes.map {} } + refreshes
            merge(*triggers.toTypedArray())
                .onStart { emit(Unit) }
                .collectLatest {
                    _results.value =
                        this@ReadinessMonitor.checks
                            .map { ReadinessResult(it, it.check()) }
                            .filter { it.state != ReadinessState.NotApplicable }
                }
        }
    }

    fun refresh() {
        refreshes.tryEmit(Unit)
    }
}
