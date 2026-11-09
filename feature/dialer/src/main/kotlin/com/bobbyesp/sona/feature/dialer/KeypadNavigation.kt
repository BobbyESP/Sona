/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.dialer

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.bobbyesp.sona.core.navigation.Navigator
import com.bobbyesp.sona.core.navigation.motion.TopLevelMotion
import com.bobbyesp.sona.core.ui.contribution.NavSection
import com.bobbyesp.sona.core.ui.contribution.TopLevelDestination
import kotlinx.serialization.Serializable

/** The Keypad tab. Only this feature opens it, through its [TopLevelDestination]. */
@Serializable data object Keypad : NavKey

internal object KeypadSection : NavSection {
    override fun EntryProviderScope<NavKey>.register(navigator: Navigator) {
        entry<Keypad>(metadata = TopLevelMotion.metadata()) { KeypadRoute() }
    }
}

internal object KeypadTab : TopLevelDestination {
    override val key: NavKey = Keypad
    override val order: Int = 20
    override val label: Int = R.string.dialer_title
    override val icon: ImageVector = Icons.Outlined.Dialpad
    override val selectedIcon: ImageVector = Icons.Rounded.Dialpad
}
