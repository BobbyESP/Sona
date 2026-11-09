/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.history

import com.bobbyesp.sona.calling.domain.CallCoordinator
import com.bobbyesp.sona.calling.domain.DialOutcome
import com.bobbyesp.sona.core.model.settings.HistorySettings
import com.bobbyesp.sona.core.model.settings.RecentTapAction
import com.bobbyesp.sona.core.model.settings.Setting
import com.bobbyesp.sona.core.model.settings.SettingsStore
import com.bobbyesp.sona.core.ui.viewmodel.BaseViewModel
import com.bobbyesp.sona.directory.domain.CallHistory
import com.bobbyesp.sona.directory.domain.CallRecord
import com.bobbyesp.sona.directory.domain.RecentGroup
import com.bobbyesp.sona.directory.domain.groupedConsecutive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

internal enum class RecentsFilter {
    All,
    Missed,
}

internal data class RecentsState(
    /** False until the history was read: the screen shows nothing rather than "no calls". */
    val loaded: Boolean = false,
    val filter: RecentsFilter = RecentsFilter.All,
    /** What the list shows, filtered and (when on) grouped, newest first. */
    val groups: List<RecentGroup> = emptyList(),
    /** Every call there is, so *no calls* can be told from *no missed calls*. */
    val total: Int = 0,
    /** The entry whose details are open, by its newest call. */
    val detailsOf: Long? = null,
    /** An entry waiting for the user to confirm calling it back. */
    val confirm: CallRecord? = null,
    val tapAction: RecentTapAction = RecentTapAction.Details,
    /** Why the last call could not be made, until the user does something else. */
    val problem: DialOutcome? = null,
    /** Calls just deleted, kept for *Undo* until the message goes. */
    val deleted: List<CallRecord> = emptyList(),
) {
    val details: RecentGroup?
        get() = detailsOf?.let { id -> groups.firstOrNull { it.latest.id == id } }
}

internal sealed interface RecentsIntent {
    data class Filter(val filter: RecentsFilter) : RecentsIntent

    /** A row was tapped: opens its details or calls back, as the setting says. */
    data class Tap(val group: RecentGroup) : RecentsIntent

    data class Details(val group: RecentGroup) : RecentsIntent

    data object CloseDetails : RecentsIntent

    /** The call button: calls the person back, asking first when the setting says so. */
    data class CallBack(val record: CallRecord, val confirmed: Boolean = false) : RecentsIntent

    data object DismissConfirm : RecentsIntent

    data class Delete(val group: RecentGroup) : RecentsIntent

    /** *Undo* was pressed: the calls deleted last come back. */
    data object Undo : RecentsIntent

    /** The message offering *Undo* went away: the deletion stands. */
    data object UndoExpired : RecentsIntent

    /** Recents is on screen: the missed calls are seen. */
    data object Seen : RecentsIntent
}

internal sealed interface RecentsEffect {
    data object OpenCall : RecentsEffect
}

internal class RecentsViewModel(
    private val history: CallHistory,
    private val coordinator: CallCoordinator,
    private val settings: SettingsStore,
) : BaseViewModel<RecentsIntent, RecentsState, RecentsEffect>(RecentsState()) {

    private val filter = MutableStateFlow(RecentsFilter.All)

    init {
        launch {
            combine(
                    history.records,
                    filter,
                    settings.observe(HistorySettings.GroupCalls),
                    settings.observe(HistorySettings.TapAction),
                ) { records, filter, group, tap ->
                    val shown =
                        if (filter == RecentsFilter.Missed) records.filter { it.isMissed }
                        else records
                    val groups =
                        if (group) shown.groupedConsecutive()
                        else shown.map { RecentGroup(listOf(it)) }
                    Quad(groups, records.size, filter, tap)
                }
                .collect { (groups, total, filter, tap) ->
                    setState {
                        copy(
                            loaded = true,
                            groups = groups,
                            total = total,
                            filter = filter,
                            tapAction = tap,
                        )
                    }
                }
        }
    }

    override fun onHandleIntent(intent: RecentsIntent) {
        when (intent) {
            is RecentsIntent.Filter -> filter.value = intent.filter
            is RecentsIntent.Tap ->
                when (currentState.tapAction) {
                    RecentTapAction.Details -> setState { copy(detailsOf = intent.group.latest.id) }
                    RecentTapAction.CallBack -> callBack(intent.group.latest, confirmed = false)
                }
            is RecentsIntent.Details -> setState { copy(detailsOf = intent.group.latest.id) }
            RecentsIntent.CloseDetails -> setState { copy(detailsOf = null) }
            is RecentsIntent.CallBack -> callBack(intent.record, intent.confirmed)
            RecentsIntent.DismissConfirm -> setState { copy(confirm = null) }
            RecentsIntent.Undo -> {
                val back = currentState.deleted
                setState { copy(deleted = emptyList()) }
                launch { back.forEach { history.add(it) } }
            }
            RecentsIntent.UndoExpired -> setState { copy(deleted = emptyList()) }
            is RecentsIntent.Delete -> {
                setState { copy(detailsOf = null) }
                setState { copy(deleted = intent.group.records) }
                launch { history.delete(intent.group.records.map { it.id }.toSet()) }
            }
            RecentsIntent.Seen -> launch { history.markSeen() }
        }
    }

    private fun callBack(record: CallRecord, confirmed: Boolean) {
        launch {
            if (!confirmed && settings.current(HistorySettings.ConfirmCallBack)) {
                setState { copy(confirm = record, detailsOf = null) }
                return@launch
            }
            setState { copy(confirm = null, detailsOf = null) }
            when (val outcome = coordinator.dial(record.address.uri, record.account)) {
                is DialOutcome.Started -> {
                    setState { copy(problem = null) }
                    sendEffect(RecentsEffect.OpenCall)
                }
                else -> setState { copy(problem = outcome) }
            }
        }
    }

    private data class Quad(
        val groups: List<RecentGroup>,
        val total: Int,
        val filter: RecentsFilter,
        val tap: RecentTapAction,
    )
}

private suspend fun <T : Any> SettingsStore.current(setting: Setting<T>): T =
    observe(setting).first()
