package com.example.chatterinomobile.ui.discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatterinomobile.data.model.Channel
import com.example.chatterinomobile.data.remote.api.TwitchHelixApi
import com.example.chatterinomobile.data.repository.AuthRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DiscoveryUiState(
    val isLoading: Boolean = true,
    val followedLive: List<Channel> = emptyList(),
    val followedLogins: List<String> = emptyList(),
    val recommendedStreams: List<Channel> = emptyList(),
    val error: String? = null
)

class DiscoveryViewModel(
    private val helixApi: TwitchHelixApi,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiscoveryUiState())
    val uiState: StateFlow<DiscoveryUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun refresh() = load()

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val userId = authRepository.getUserId()
                coroutineScope {
                    val followsDeferred = if (userId != null) {
                        async { runCatching { helixApi.getFollowedChannels(userId) }.getOrElse { emptyList() } }
                    } else null

                    val topStreamsDeferred = async {
                        runCatching { helixApi.getTopStreams(limit = 20) }.getOrElse { emptyList() }
                    }

                    val followedChannels = followsDeferred?.await() ?: emptyList()
                    val topStreams = topStreamsDeferred.await()

                    val followedLogins = followedChannels.map { it.broadcasterLogin }


                    val followedLive: List<Channel> = if (followedLogins.isNotEmpty()) {
                        val streamsByLogin = runCatching {
                            helixApi.getStreamsByLogin(followedLogins.take(20))
                        }.getOrElse { emptyList() }.associateBy { it.userLogin }

                        val userDtos = runCatching {
                            helixApi.getUsersByLogin(followedLogins.take(20))
                        }.getOrElse { emptyList() }

                        userDtos
                            .filter { streamsByLogin.containsKey(it.login) }
                            .map { user ->
                                val stream = streamsByLogin[user.login]!!
                                Channel(
                                    id = user.id,
                                    login = user.login,
                                    displayName = user.displayName,
                                    isLive = true,
                                    viewerCount = stream.viewerCount,
                                    gameName = stream.gameName,
                                    title = stream.title,
                                    thumbnailUrl = stream.thumbnailUrl
                                        ?.replace("{width}", "440")
                                        ?.replace("{height}", "248")
                                )
                            }
                            .sortedByDescending { it.viewerCount }
                    } else emptyList()


                    val followedLoginSet = followedLogins.toSet()
                    val recommended = topStreams
                        .filter { it.userLogin !in followedLoginSet }
                        .take(10)
                        .map { stream ->
                            Channel(
                                id = stream.userId,
                                login = stream.userLogin,
                                displayName = stream.userName,
                                isLive = true,
                                viewerCount = stream.viewerCount,
                                gameName = stream.gameName,
                                title = stream.title,
                                thumbnailUrl = stream.thumbnailUrl
                                    ?.replace("{width}", "440")
                                    ?.replace("{height}", "248")
                            )
                        }

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            followedLive = followedLive,
                            followedLogins = followedLogins,
                            recommendedStreams = recommended,
                            error = null
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}
