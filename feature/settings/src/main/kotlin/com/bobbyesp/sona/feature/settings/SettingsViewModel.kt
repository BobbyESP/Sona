/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.settings

import com.bobbyesp.sona.core.common.StringProvider
import com.bobbyesp.sona.core.model.settings.Setting
import com.bobbyesp.sona.core.model.settings.SettingsStore
import com.bobbyesp.sona.core.ui.contribution.SettingItem
import com.bobbyesp.sona.core.ui.contribution.SettingsArea
import com.bobbyesp.sona.core.ui.contribution.SettingsContributor
import com.bobbyesp.sona.core.ui.viewmodel.BaseViewModel

/** An area with every item the features contributed to it. */
internal data class AreaItems(val area: SettingsArea, val items: List<SettingItem>)

internal data class SettingsState(
    /** Every area that has items, in order. */
    val areas: List<AreaItems> = emptyList(),
    val query: String = "",
    /** The items whose title, summary or keywords hold the query; empty while nothing is typed. */
    val results: List<AreaItems> = emptyList(),
)

internal sealed interface SettingsIntent {
    data class Query(val text: String) : SettingsIntent

    data class Write<T : Any>(val setting: Setting<T>, val value: T) : SettingsIntent

    data class Run(val action: SettingItem.Action) : SettingsIntent
}

/**
 * The settings screens' state: what the features contributed, grouped by area, and the search over
 * it. It never names a feature; reading and writing a value goes through the [SettingsStore] by the
 * contributed declaration.
 */
internal class SettingsViewModel(
    contributors: List<SettingsContributor>,
    val store: SettingsStore,
    private val strings: StringProvider,
) : BaseViewModel<SettingsIntent, SettingsState, Nothing>(SettingsState()) {

    private val areas: List<AreaItems> =
        contributors
            .groupBy { it.area.id }
            .values
            .map { group -> AreaItems(group.first().area, group.flatMap { it.items }) }
            .filter { it.items.isNotEmpty() }
            .sortedBy { it.area.order }

    init {
        setState { copy(areas = this@SettingsViewModel.areas) }
    }

    override fun onHandleIntent(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.Query ->
                setState { copy(query = intent.text, results = search(intent.text)) }
            is SettingsIntent.Write<*> -> launch { write(intent) }
            is SettingsIntent.Run -> launch { intent.action.run() }
        }
    }

    private suspend fun <T : Any> write(intent: SettingsIntent.Write<T>) =
        store.set(intent.setting, intent.value)

    private fun search(text: String): List<AreaItems> {
        val words = text.trim().lowercase().split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        return areas
            .map { entry ->
                entry.copy(
                    items =
                        entry.items.filter { item ->
                            val haystack = item.searchText().lowercase()
                            words.all { it in haystack }
                        },
                )
            }
            .filter { it.items.isNotEmpty() }
    }

    private fun SettingItem.searchText(): String =
        listOfNotNull(title, summary, keywords).joinToString(" ") { strings.get(it) }
}
