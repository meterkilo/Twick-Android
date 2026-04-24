package com.example.chatterinomobile.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatterinomobile.data.model.ChannelHydrationState
import com.example.chatterinomobile.data.model.ChatMessage
import com.example.chatterinomobile.data.model.ReplyMetadata
import com.example.chatterinomobile.data.model.RoomState
import com.example.chatterinomobile.data.model.SendMessageResult
import com.example.chatterinomobile.data.model.TwitchDeviceAuthorization
import com.example.chatterinomobile.data.model.TwitchDeviceFlowState
import com.example.chatterinomobile.data.model.UserChatState
import com.example.chatterinomobile.data.repository.AuthRepository
import com.example.chatterinomobile.data.repository.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * First real screen-facing ViewModel for the app.
 *
 * It intentionally depends on the stable repository contracts rather than raw
 * IRC/API implementation details, which keeps the UI layer insulated while the
 * backend continues to evolve underneath it.
 */
class ChatViewModel(
    private val authRepository: AuthRepository,
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState(isAuthLoading = true))
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { chatRepository.connect() }
                .onFailure { updateState { copy(chatErrorMessage = it.message ?: "Failed to connect chat") } }
        }

        viewModelScope.launch { refreshAuthSession() }

        viewModelScope.launch {
            chatRepository.messages.collect { message ->
                val activeChannelLogin = _uiState.value.activeChannelLogin ?: return@collect
                val hydration = _uiState.value.channelHydration
                val activeChannelId = hydration?.channelId
                val belongsToActiveChannel =
                    message.channelId == activeChannelLogin ||
                        (activeChannelId != null && message.channelId == activeChannelId)

                if (!belongsToActiveChannel) return@collect

                updateState {
                    copy(
                        recentMessages = (recentMessages + message).takeLast(MAX_RECENT_MESSAGES)
                    )
                }
            }
        }

        viewModelScope.launch {
            chatRepository.roomStates.collect { states ->
                updateState {
                    copy(roomState = selectRoomState(states, activeChannelLogin, channelHydration))
                }
            }
        }

        viewModelScope.launch {
            chatRepository.channelUserStates.collect { states ->
                updateState {
                    copy(currentUserState = selectUserState(states, activeChannelLogin, channelHydration))
                }
            }
        }

        viewModelScope.launch {
            chatRepository.channelHydrationStates.collect { states ->
                updateState {
                    val hydration = activeChannelLogin?.let { states[it] }
                    copy(
                        channelHydration = hydration,
                        roomState = selectRoomState(chatRepository.roomStates.value, activeChannelLogin, hydration),
                        currentUserState = selectUserState(chatRepository.channelUserStates.value, activeChannelLogin, hydration)
                    )
                }
            }
        }
    }

    fun startLogin() {
        viewModelScope.launch {
            runCatching {
                updateState {
                    copy(
                        isAuthLoading = true,
                        authErrorMessage = null,
                        authSuccessMessage = null
                    )
                }

                val deviceFlow = authRepository.startDeviceFlow()
                if (deviceFlow == null) {
                    updateState {
                        copy(
                            isAuthLoading = false,
                            authErrorMessage = "No Twitch client ID is configured in local.properties."
                        )
                    }
                    return@launch
                }

                updateState {
                    copy(
                        isAuthLoading = false,
                        isAwaitingAuthorization = true,
                        authDeviceFlow = deviceFlow,
                        authErrorMessage = null,
                        authSuccessMessage = null
                    )
                }

                when (val result = authRepository.awaitDeviceAuthorization(deviceFlow)) {
                    is TwitchDeviceAuthorization.Authorized -> {
                        refreshAuthSession(successMessage = "Twitch login complete.")
                    }
                    TwitchDeviceAuthorization.Denied -> {
                        updateState {
                            copy(
                                isAwaitingAuthorization = false,
                                authErrorMessage = "Authorization was denied in Twitch."
                            )
                        }
                    }
                    TwitchDeviceAuthorization.Expired -> {
                        updateState {
                            copy(
                                isAwaitingAuthorization = false,
                                authErrorMessage = "The device code expired before authorization completed."
                            )
                        }
                    }
                    is TwitchDeviceAuthorization.Failed -> {
                        updateState {
                            copy(
                                isAwaitingAuthorization = false,
                                authErrorMessage = result.message
                            )
                        }
                    }
                }
            }.onFailure {
                updateState {
                    copy(
                        isAuthLoading = false,
                        isAwaitingAuthorization = false,
                        authErrorMessage = it.message ?: "Twitch auth failed"
                    )
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            runCatching {
                authRepository.clearSession()
                refreshAuthSession()
            }.onFailure {
                updateState {
                    copy(authErrorMessage = it.message ?: "Logout failed")
                }
            }
        }
    }

    fun joinChannel(channelLogin: String) {
        val normalizedLogin = channelLogin.lowercase().removePrefix("#").trim()
        if (normalizedLogin.isBlank()) return

        updateState {
            copy(
                activeChannelLogin = normalizedLogin,
                recentMessages = emptyList(),
                sendStatusMessage = null,
                sendErrorMessage = null,
                chatErrorMessage = null
            )
        }

        viewModelScope.launch {
            runCatching { chatRepository.joinChannel(normalizedLogin) }
                .onFailure {
                    updateState {
                        copy(chatErrorMessage = it.message ?: "Failed to join channel")
                    }
                }
        }
    }

    fun sendMessage(text: String) {
        val activeChannel = _uiState.value.activeChannelLogin ?: run {
            updateState { copy(sendErrorMessage = "Join a channel first.") }
            return
        }

        viewModelScope.launch {
            when (val result = chatRepository.sendMessage(activeChannel, text)) {
                SendMessageResult.Sent -> {
                    updateState {
                        copy(sendStatusMessage = "Message sent.", sendErrorMessage = null)
                    }
                }
                SendMessageResult.EmptyMessage -> {
                    updateState { copy(sendErrorMessage = "Message cannot be empty.") }
                }
                SendMessageResult.Anonymous -> {
                    updateState { copy(sendErrorMessage = "Sign in to send chat messages.") }
                }
                SendMessageResult.Disconnected -> {
                    updateState { copy(sendErrorMessage = "Chat socket is disconnected.") }
                }
                is SendMessageResult.Failed -> {
                    updateState { copy(sendErrorMessage = result.message) }
                }
            }
        }
    }

    private suspend fun refreshAuthSession(successMessage: String? = null) {
        updateState { copy(isAuthLoading = true, authErrorMessage = null) }
        val token = authRepository.getAccessToken()
        val login = authRepository.getLogin()
        val userId = authRepository.getUserId()
        updateState {
            copy(
                isAuthLoading = false,
                isLoggedIn = token != null,
                login = login,
                userId = userId,
                isAwaitingAuthorization = false,
                authDeviceFlow = null,
                authSuccessMessage = successMessage
            )
        }
    }

    private inline fun updateState(transform: ChatUiState.() -> ChatUiState) {
        _uiState.value = _uiState.value.transform()
    }

    private fun selectRoomState(
        states: Map<String, RoomState>,
        channelLogin: String?,
        hydration: ChannelHydrationState?
    ): RoomState? {
        if (channelLogin == null) return null
        return hydration?.channelId?.let(states::get) ?: states[channelLogin]
    }

    private fun selectUserState(
        states: Map<String, UserChatState>,
        channelLogin: String?,
        hydration: ChannelHydrationState?
    ): UserChatState? {
        if (channelLogin == null) return null
        return hydration?.channelId?.let(states::get) ?: states[channelLogin]
    }

    companion object {
        private const val MAX_RECENT_MESSAGES = 100
    }
}

data class ChatUiState(
    val isAuthLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val isAwaitingAuthorization: Boolean = false,
    val login: String? = null,
    val userId: String? = null,
    val authDeviceFlow: TwitchDeviceFlowState? = null,
    val authErrorMessage: String? = null,
    val authSuccessMessage: String? = null,
    val activeChannelLogin: String? = null,
    val channelHydration: ChannelHydrationState? = null,
    val roomState: RoomState? = null,
    val currentUserState: UserChatState? = null,
    val recentMessages: List<ChatMessage> = emptyList(),
    val sendStatusMessage: String? = null,
    val sendErrorMessage: String? = null,
    val chatErrorMessage: String? = null
)

fun ReplyMetadata.describeParent(): String =
    parentDisplayName ?: parentUserLogin ?: parentMessageId
