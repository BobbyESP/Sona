/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.messages

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.bobbyesp.sona.core.model.settings.AttachmentLimit
import com.bobbyesp.sona.core.model.settings.AutoDownloadLimit
import com.bobbyesp.sona.core.model.settings.MessageSettings
import com.bobbyesp.sona.core.model.settings.MessageSizeLimit
import com.bobbyesp.sona.core.navigation.Navigator
import com.bobbyesp.sona.core.navigation.keys.NewConversation
import com.bobbyesp.sona.core.ui.contribution.PartyAction
import com.bobbyesp.sona.core.ui.contribution.PartyActionProvider
import com.bobbyesp.sona.core.ui.contribution.PartyRef
import com.bobbyesp.sona.core.ui.contribution.SettingItem
import com.bobbyesp.sona.core.ui.contribution.SettingsAreas
import com.bobbyesp.sona.core.ui.contribution.SettingsContributor

/** The id of *Message*, so the conversation's own top bar can leave it out. */
internal const val MESSAGE_ACTION_ID = "messages.write"

/**
 * *Message*, wherever a party is shown (a contact, a history entry): opens the conversation with
 * them, on the entry's account when it has one, through *New message* (which asks for the account
 * only when there is a choice to make).
 */
internal object MessagePartyAction : PartyActionProvider {
    override val order: Int = 100

    @Composable
    override fun rememberAction(party: PartyRef, navigator: Navigator): PartyAction? {
        if (party.address.isBlank()) return null
        val label = stringResource(R.string.action_message)
        return remember(party, navigator, label) {
            PartyAction(
                id = MESSAGE_ACTION_ID,
                label = label,
                icon = Icons.AutoMirrored.Rounded.Chat,
                onClick = {
                    navigator.goTo(
                        NewConversation(address = party.address, accountId = party.account),
                    )
                },
            )
        }
    }
}

/** *Messages* in the settings: what other Sona phones are told, files, and the server's size. */
internal object MessagesSettingsContributor : SettingsContributor {
    override val area = SettingsAreas.Messages

    override val items: List<SettingItem> =
        listOf(
            SettingItem.Switch(
                setting = MessageSettings.ReadReceipts,
                title = R.string.settings_read_receipts,
                summary = R.string.settings_read_receipts_summary,
                keywords = R.string.settings_read_receipts_keywords,
                icon = Icons.Rounded.DoneAll,
            ),
            SettingItem.Switch(
                setting = MessageSettings.TypingIndicators,
                title = R.string.settings_typing,
                summary = R.string.settings_typing_summary,
                keywords = R.string.settings_typing_keywords,
                icon = Icons.Rounded.Edit,
            ),
            SettingItem.Choice(
                setting = MessageSettings.AutoDownload,
                title = R.string.settings_auto_download,
                summary = R.string.settings_auto_download_summary,
                keywords = R.string.settings_auto_download_keywords,
                icon = Icons.Rounded.Download,
                label = { limit ->
                    when (limit) {
                        AutoDownloadLimit.Never -> R.string.limit_never
                        AutoDownloadLimit.Kb500 -> R.string.limit_500_kb
                        AutoDownloadLimit.Mb2 -> R.string.limit_2_mb
                        AutoDownloadLimit.Mb10 -> R.string.limit_10_mb
                    }
                },
            ),
            SettingItem.Switch(
                setting = MessageSettings.OriginalQualityPictures,
                title = R.string.settings_original,
                summary = R.string.settings_original_summary,
                keywords = R.string.settings_original_keywords,
                icon = Icons.Rounded.HighQuality,
            ),
            SettingItem.Choice(
                setting = MessageSettings.LargestAttachment,
                title = R.string.settings_largest_attachment,
                summary = R.string.settings_largest_attachment_summary,
                keywords = R.string.settings_largest_attachment_keywords,
                icon = Icons.Rounded.AttachFile,
                label = { limit ->
                    when (limit) {
                        AttachmentLimit.Mb2 -> R.string.limit_2_mb
                        AttachmentLimit.Mb5 -> R.string.limit_5_mb
                        AttachmentLimit.Mb10 -> R.string.limit_10_mb
                        AttachmentLimit.Mb20 -> R.string.limit_20_mb
                    }
                },
            ),
            SettingItem.Choice(
                setting = MessageSettings.LargestMessage,
                title = R.string.settings_largest_message,
                summary = R.string.settings_largest_message_summary,
                keywords = R.string.settings_largest_message_keywords,
                icon = Icons.Rounded.Straighten,
                label = MessageSizeLimit::label,
            ),
        )
}

/** How a message size limit is named in the settings. */
internal val MessageSizeLimit.label: Int
    get() =
        when (this) {
            MessageSizeLimit.B700 -> R.string.limit_700_b
            MessageSizeLimit.Kb1 -> R.string.limit_1_kb
            MessageSizeLimit.Kb4 -> R.string.limit_4_kb
            MessageSizeLimit.Kb16 -> R.string.limit_16_kb
            MessageSizeLimit.Kb60 -> R.string.limit_60_kb
        }
