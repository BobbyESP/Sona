/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.history

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.rounded.History
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.bobbyesp.sona.core.navigation.Navigator
import com.bobbyesp.sona.core.navigation.motion.TopLevelMotion
import com.bobbyesp.sona.core.ui.contribution.NavSection
import com.bobbyesp.sona.core.ui.contribution.TopLevelDestination
import kotlinx.serialization.Serializable

/** The Recents tab. Only this feature opens it, through its [TopLevelDestination]. */
@Serializable data object Recents : NavKey

internal object RecentsSection : NavSection {
    override fun EntryProviderScope<NavKey>.register(navigator: Navigator) {
        entry<Recents>(metadata = TopLevelMotion.metadata()) { RecentsScreen() }
    }
}

internal object RecentsTab : TopLevelDestination {
    override val key: NavKey = Recents
    override val order: Int = 0
    override val label: Int = R.string.history_title
    override val icon: ImageVector = Icons.Outlined.History
    override val selectedIcon: ImageVector = Icons.Rounded.History
}
