/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.contacts

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContactPhone
import com.bobbyesp.sona.core.model.settings.ContactsSettings
import com.bobbyesp.sona.core.ui.contribution.SettingItem
import com.bobbyesp.sona.core.ui.contribution.SettingsAreas
import com.bobbyesp.sona.core.ui.contribution.SettingsContributor

/** Whether the phone contacts are used, in *Contacts and history*. */
internal object ContactsSettingsContributor : SettingsContributor {
    override val area = SettingsAreas.Directory

    override val section = R.string.settings_section_contacts

    override val items: List<SettingItem> =
        listOf(
            SettingItem.Switch(
                setting = ContactsSettings.UseDeviceContacts,
                title = R.string.settings_device_contacts,
                icon = Icons.Rounded.ContactPhone,
                summary = R.string.settings_device_contacts_summary,
                keywords = R.string.settings_device_contacts_keywords,
            ),
        )
}
