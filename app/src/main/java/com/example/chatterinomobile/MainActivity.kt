package com.example.chatterinomobile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.chatterinomobile.data.model.ChannelHydrationState
import com.example.chatterinomobile.data.model.ChatMessage
import com.example.chatterinomobile.data.model.MessageFragment
import com.example.chatterinomobile.data.model.MessageType
import com.example.chatterinomobile.data.model.TwitchDeviceFlowState
import com.example.chatterinomobile.data.model.UserChatState
import com.example.chatterinomobile.ui.chat.ChatUiState
import com.example.chatterinomobile.ui.chat.ChatViewModel
import com.example.chatterinomobile.ui.chat.describeParent
import com.example.chatterinomobile.ui.theme.ChatterinoMobileTheme
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : ComponentActivity() {
    private val chatViewModel: ChatViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val uiState by chatViewModel.uiState.collectAsState()

            ChatterinoMobileTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ChatScreen(
                        state = uiState,
                        onStartLogin = chatViewModel::startLogin,
                        onLogout = chatViewModel::logout,
                        onJoinChannel = chatViewModel::joinChannel,
                        onSendMessage = chatViewModel::sendMessage,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatScreen(
    state: ChatUiState,
    onStartLogin: () -> Unit,
    onLogout: () -> Unit,
    onJoinChannel: (String) -> Unit,
    onSendMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var channelInput by rememberSaveable { mutableStateOf(state.activeChannelLogin ?: "") }
    var messageInput by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Chatterino Mobile",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        AuthCard(
            state = state,
            onStartLogin = onStartLogin,
            onLogout = onLogout
        )

        CardBlock(title = "Channel") {
            OutlinedTextField(
                value = channelInput,
                onValueChange = { channelInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Channel login") },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { onJoinChannel(channelInput) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Join Channel")
            }

            if (state.activeChannelLogin != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text("Active channel: ${state.activeChannelLogin}")
            }

            state.channelHydration?.let { hydration ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = buildString {
                        append("Hydration: ")
                        append(
                            when {
                                hydration.isLoading -> "loading"
                                hydration.isReady -> "ready"
                                hydration.errorMessage != null -> "failed"
                                else -> "idle"
                            }
                        )
                    }
                )
                hydration.errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }

            state.roomState?.let { roomState ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Room modes: slow=${roomState.slowModeSeconds}s, " +
                        "subs=${roomState.subscribersOnly}, " +
                        "emote-only=${roomState.emoteOnly}, " +
                        "followers=${roomState.followersOnlyMinutes ?: "off"}"
                )
            }

            state.currentUserState?.let { userState ->
                Spacer(modifier = Modifier.height(8.dp))
                UserStateSummary(userState)
            }

            state.chatErrorMessage?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }

        CardBlock(title = "Send") {
            OutlinedTextField(
                value = messageInput,
                onValueChange = { messageInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Message") }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    onSendMessage(messageInput)
                    messageInput = ""
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Send Message")
            }

            state.sendStatusMessage?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.primary)
            }

            state.sendErrorMessage?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }

        CardBlock(title = "Messages") {
            if (state.recentMessages.isEmpty()) {
                Text(
                    text = "No messages yet. Join a channel and wait for chat.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                state.recentMessages.takeLast(30).forEach { message ->
                    MessageRow(message)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun AuthCard(
    state: ChatUiState,
    onStartLogin: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current

    CardBlock(title = "Auth") {
        val status = when {
            state.isAuthLoading -> "Checking session..."
            state.isAwaitingAuthorization -> "Waiting for Twitch authorization..."
            state.isLoggedIn -> "Signed in as ${state.login ?: state.userId ?: "unknown"}"
            else -> "Not signed in"
        }
        Text(status)

        if (state.isAuthLoading || state.isAwaitingAuthorization) {
            Spacer(modifier = Modifier.height(8.dp))
            CircularProgressIndicator()
        }

        state.authSuccessMessage?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.primary)
        }

        state.authErrorMessage?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        state.authDeviceFlow?.let { deviceFlow ->
            Spacer(modifier = Modifier.height(12.dp))
            DeviceCodeCard(
                state = deviceFlow,
                onOpenActivation = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(deviceFlow.verificationUri))
                    )
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (state.isLoggedIn) {
            OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
                Text("Log Out")
            }
        } else {
            Button(
                onClick = onStartLogin,
                enabled = !state.isAuthLoading && !state.isAwaitingAuthorization,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Sign In With Twitch")
            }
        }
    }
}

@Composable
private fun DeviceCodeCard(
    state: TwitchDeviceFlowState,
    onOpenActivation: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Code: ${state.userCode}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            SelectionContainer {
                Text("Activate at: ${state.verificationUri}")
            }
            OutlinedButton(onClick = onOpenActivation) {
                Text("Open Activation Page")
            }
        }
    }
}

@Composable
private fun CardBlock(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun UserStateSummary(userState: UserChatState) {
    Text(
        "You in chat: ${userState.displayName ?: userState.login ?: userState.userId ?: "unknown"}"
    )
    if (userState.badges.isNotEmpty()) {
        Text(
            "Badges: ${userState.badges.joinToString { it.description }}"
        )
    }
}

@Composable
private fun MessageRow(message: ChatMessage) {
    val body = remember(message) { renderFragments(message.fragment) }
    val systemText = (message.Type as? MessageType.System)?.text

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = message.author.displayName,
                fontWeight = FontWeight.SemiBold
            )
            if (message.reply != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "replying to ${message.reply.describeParent()}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (systemText != null) {
            Text(
                text = systemText,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (body.isNotBlank()) {
            Text(body)
        }
    }
}

private fun renderFragments(fragments: List<MessageFragment>): String =
    buildString {
        for (fragment in fragments) {
            when (fragment) {
                is MessageFragment.Text -> append(fragment.content)
                is MessageFragment.Emote -> append(":${fragment.name}:")
                is MessageFragment.Mention -> append("@${fragment.username}")
            }
        }
    }

@Preview(showBackground = true)
@Composable
private fun ChatScreenPreview() {
    ChatterinoMobileTheme {
        ChatScreen(
            state = ChatUiState(
                isLoggedIn = true,
                login = "franz",
                activeChannelLogin = "xqc",
                channelHydration = ChannelHydrationState(
                    channelLogin = "xqc",
                    channelId = "71092938",
                    isReady = true
                ),
                recentMessages = listOf(
                    ChatMessage(
                        id = "1",
                        channelId = "71092938",
                        author = com.example.chatterinomobile.data.model.ChatUser(
                            id = "1",
                            login = "viewer",
                            displayName = "viewer"
                        ),
                        fragment = listOf(MessageFragment.Text("hello chat")),
                        timestamp = 0L
                    )
                )
            ),
            onStartLogin = {},
            onLogout = {},
            onJoinChannel = {},
            onSendMessage = {}
        )
    }
}
