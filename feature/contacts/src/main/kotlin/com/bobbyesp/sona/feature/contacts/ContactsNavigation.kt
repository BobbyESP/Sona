/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.contacts

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.bobbyesp.sona.core.navigation.Navigator
import com.bobbyesp.sona.core.navigation.keys.ContactEditor
import com.bobbyesp.sona.core.navigation.motion.TopLevelMotion
import com.bobbyesp.sona.core.ui.contribution.NavSection
import com.bobbyesp.sona.core.ui.contribution.TopLevelDestination
import com.bobbyesp.sona.feature.contacts.editor.ContactEditorRoute
import com.bobbyesp.sona.feature.contacts.editor.ContactEditorViewModel
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** The Contacts tab. Only this feature opens it, through its [TopLevelDestination]. */
@Serializable data object Contacts : NavKey

internal object ContactsSection : NavSection {
    override fun EntryProviderScope<NavKey>.register(navigator: Navigator) {
        entry<Contacts>(metadata = TopLevelMotion.metadata()) {
            ContactsRoute(
                onNew = { navigator.goTo(ContactEditor()) },
                onEdit = { contact -> navigator.goTo(ContactEditor(contactId = contact.id)) },
            )
        }
        entry<ContactEditor> { key ->
            ContactEditorRoute(
                viewModel =
                    koinViewModel<ContactEditorViewModel>(key = key.toString()) {
                        parametersOf(key)
                    },
                onBack = navigator::goBack,
            )
        }
    }
}

internal object ContactsTab : TopLevelDestination {
    override val key: NavKey = Contacts
    override val order: Int = 10
    override val label: Int = R.string.contacts_title
    override val icon: ImageVector = Icons.Outlined.Contacts
    override val selectedIcon: ImageVector = Icons.Rounded.Contacts
}
