/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.sona.feature.messages

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.bobbyesp.sona.core.navigation.Navigator
import com.bobbyesp.sona.core.navigation.keys.Conversation
import com.bobbyesp.sona.core.navigation.keys.NewConversation
import com.bobbyesp.sona.core.navigation.motion.TopLevelMotion
import com.bobbyesp.sona.core.ui.contribution.NavSection
import com.bobbyesp.sona.core.ui.contribution.TopLevelDestination
import com.bobbyesp.sona.feature.messages.conversation.ConversationRoute
import com.bobbyesp.sona.feature.messages.conversation.ConversationViewModel
import com.bobbyesp.sona.feature.messages.list.ConversationsRoute
import com.bobbyesp.sona.feature.messages.list.NoConversationOpen
import com.bobbyesp.sona.feature.messages.picture.PictureRoute
import com.bobbyesp.sona.feature.messages.picture.PictureViewModel
import com.bobbyesp.sona.feature.messages.start.NewConversationRoute
import com.bobbyesp.sona.feature.messages.start.NewConversationViewModel
import com.bobbyesp.sona.messaging.domain.Messenger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** The Messages tab. Only this feature opens it, through its [TopLevelDestination]. */
@Serializable data object Messages : NavKey

/** A picture of a conversation, on the whole screen. Only this feature opens it. */
@Serializable
internal data class MessagePicture(val conversationId: Long, val messageId: Long) : NavKey

/** The list and its detail (a conversation or *New message*): side by side on a wide window. */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
internal object MessagesSection : NavSection {
    override fun EntryProviderScope<NavKey>.register(navigator: Navigator) {
        entry<Messages>(
            metadata =
                TopLevelMotion.metadata() +
                    ListDetailSceneStrategy.listPane(detailPlaceholder = { NoConversationOpen() }),
        ) {
            ConversationsRoute(
                onOpen = { id -> navigator.showDetail(Conversation(id.value)) },
                onNew = { navigator.showDetail(NewConversation()) },
            )
        }
        entry<Conversation>(metadata = ListDetailSceneStrategy.detailPane()) { key ->
            ConversationRoute(
                viewModel =
                    koinViewModel<ConversationViewModel>(key = key.toString()) {
                        parametersOf(key.conversationId)
                    },
                navigator = navigator,
                onBack = navigator::goBack,
                onGone = { navigator.removeDestination(key) },
                onPicture = { message ->
                    navigator.goTo(MessagePicture(key.conversationId, message.value))
                },
            )
        }
        entry<NewConversation>(metadata = ListDetailSceneStrategy.detailPane()) { key ->
            NewConversationRoute(
                viewModel =
                    koinViewModel<NewConversationViewModel>(key = key.toString()) {
                        parametersOf(key)
                    },
                onBack = navigator::goBack,
                onOpened = { id ->
                    // *New message* gives way to the conversation: back leaves both.
                    navigator.removeDestination(key)
                    navigator.goTo(Conversation(id.value))
                },
            )
        }
        entry<MessagePicture> { key ->
            PictureRoute(
                viewModel =
                    koinViewModel<PictureViewModel>(key = key.toString()) {
                        parametersOf(key.conversationId, key.messageId)
                    },
                onBack = navigator::goBack,
            )
        }
    }
}

/** Replaces the open detail, so moving between conversations never piles them up behind back. */
private fun Navigator.showDetail(key: NavKey) {
    goBackWhile { it is Conversation || it is NewConversation || it is MessagePicture }
    goTo(key)
}

internal class MessagesTab(messenger: Messenger) : TopLevelDestination {
    override val badge: Flow<Int> = messenger.totalUnread.distinctUntilChanged()

    override val key: NavKey = Messages
    override val order: Int = 15
    override val label: Int = R.string.messages_title
    override val icon: ImageVector = Icons.AutoMirrored.Outlined.Chat
    override val selectedIcon: ImageVector = Icons.AutoMirrored.Rounded.Chat
}
