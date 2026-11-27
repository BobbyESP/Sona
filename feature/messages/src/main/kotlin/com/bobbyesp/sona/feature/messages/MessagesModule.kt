/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.messages

import com.bobbyesp.sona.core.navigation.keys.NewConversation
import com.bobbyesp.sona.core.ui.contribution.NavSection
import com.bobbyesp.sona.core.ui.contribution.PartyActionProvider
import com.bobbyesp.sona.core.ui.contribution.SettingsContributor
import com.bobbyesp.sona.core.ui.contribution.TopLevelDestination
import com.bobbyesp.sona.feature.messages.conversation.ConversationViewModel
import com.bobbyesp.sona.feature.messages.list.ConversationsViewModel
import com.bobbyesp.sona.feature.messages.picture.PictureViewModel
import com.bobbyesp.sona.feature.messages.start.NewConversationViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

val messagesModule = module {
    single { MessagesSection } bind NavSection::class
    single { MessagesTab(get()) } bind TopLevelDestination::class
    single { MessagePartyAction } bind PartyActionProvider::class
    single { MessagesSettingsContributor } bind SettingsContributor::class
    viewModelOf(::ConversationsViewModel)
    viewModel { params ->
        NewConversationViewModel(
            key = params.get<NewConversation>(),
            messenger = get(),
            directory = get(),
            accounts = get(),
        )
    }
    viewModel { params ->
        ConversationViewModel(
            conversationId = params.get(),
            messenger = get(),
            accounts = get(),
            captures = get(),
            dispatchers = get(),
        )
    }
    viewModel { params ->
        PictureViewModel(
            conversationId = params[0],
            messageId = params[1],
            messenger = get(),
        )
    }
}
