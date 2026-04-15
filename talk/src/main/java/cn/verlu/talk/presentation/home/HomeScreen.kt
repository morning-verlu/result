package cn.verlu.talk.presentation.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cn.verlu.talk.presentation.contacts.ContactsScreen
import cn.verlu.talk.presentation.contacts.ContactsViewModel
import cn.verlu.talk.presentation.conversations.ConversationListScreen

/**
 * 主 Tab 内容区；顶栏与底栏由 [cn.verlu.talk.presentation.navigation.TalkNavApp] 各路由 [Scaffold] 提供。
 */
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    selectedTab: Int,
    contactsViewModel: ContactsViewModel,
    onNavigateToChat: (roomId: String) -> Unit = {},
    onNavigateToContacts: () -> Unit = {},
) {
    Box(modifier = modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = selectedTab,
            label = "TalkHomeTabSwitch",
            transitionSpec = {
                val forward = targetState > initialState
                slideInHorizontally(
                    animationSpec = tween(220),
                    initialOffsetX = { if (forward) it else -it },
                ) togetherWith slideOutHorizontally(
                    animationSpec = tween(220),
                    targetOffsetX = { if (forward) -it else it },
                )
            },
        ) { tab ->
            when (tab) {
                0 -> ConversationListScreen(
                    modifier = Modifier.fillMaxSize(),
                    onNavigateToChat = onNavigateToChat,
                    onNavigateToContacts = onNavigateToContacts,
                )

                else -> ContactsScreen(
                    modifier = Modifier.fillMaxSize(),
                    onNavigateToChat = onNavigateToChat,
                    viewModel = contactsViewModel,
                )
            }
        }
    }
}
