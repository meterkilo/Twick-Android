package com.example.chatterinomobile.ui.discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatterinomobile.data.local.FollowListCache
import com.example.chatterinomobile.data.model.Channel
import com.example.chatterinomobile.data.remote.api.TwitchHelixApi
import com.example.chatterinomobile.data.repository.AuthRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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
    val error: String? = null,
    val searchQuery: String = "",
    val searchResults: List<Channel> = emptyList(),
    val isSearching: Boolean = false,
    val searchActive: Boolean = false
)

class DiscoveryViewModel(
    private val helixApi: TwitchHelixApi,
    private val authRepository: AuthRepository,
    private val followListCache: FollowListCache
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiscoveryUiState())
    val uiState: StateFlow<DiscoveryUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        load()
    }

    fun refresh() = load()

    fun openSearch() {
        _uiState.update { it.copy(searchActive = true, searchQuery = "", searchResults = emptyList()) }
    }

    fun closeSearch() {
        searchJob?.cancel()
        _uiState.update { it.copy(searchActive = false, searchQuery = "", searchResults = emptyList(), isSearching = false) }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            _uiState.update { it.copy(isSearching = true) }
            val results = runCatching { helixApi.searchChannels(query) }.getOrElse { emptyList() }
            val channels = results
                .map { dto ->
                    Channel(
                        id = dto.id,
                        login = dto.broadcasterLogin,
                        displayName = dto.displayName,
                        isLive = dto.isLive,
                        gameName = dto.gameName,
                        title = dto.title,
                        profileImageUrl = dto.thumbnailUrl
                    )
                }
                .sortedWith(
                    compareByDescending<Channel> { it.isLive }
                        .thenBy { it.displayName.lowercase() }
                )
            _uiState.update { it.copy(searchResults = channels, isSearching = false) }
        }
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val userId = authRepository.getUserId()

                val cachedLogins = if (userId != null) followListCache.read(userId)?.logins else null

                if (cachedLogins != null) {
                    _uiState.update { it.copy(followedLogins = cachedLogins) }
                    loadLiveAndRecommended(userId, cachedLogins, refreshFollows = true)
                } else {
                    loadLiveAndRecommended(userId, logins = null, refreshFollows = true)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private suspend fun loadLiveAndRecommended(
        userId: String?,
        logins: List<String>?,
        refreshFollows: Boolean
    ) {
        coroutineScope {
            val freshFollowsDeferred = if (refreshFollows && userId != null) {
                async { fetchAllFollows(userId) }
            } else null

            val topStreamsDeferred = async {
                runCatching { helixApi.getTopStreams(limit = 20) }.getOrElse { emptyList() }
            }

            val followedLogins = if (freshFollowsDeferred != null) {
                val fresh = freshFollowsDeferred.await()
                if (userId != null && fresh.isNotEmpty()) followListCache.write(userId, fresh)
                fresh
            } else {
                logins ?: emptyList()
            }

            val topStreams = topStreamsDeferred.await()

            val followedLive: List<Channel> = if (followedLogins.isNotEmpty()) {
                val liveStreams = followedLogins
                    .chunked(100)
                    .map { batch ->
                        async {
                            runCatching { helixApi.getStreamsByLogin(batch) }.getOrElse { emptyList() }
                        }
                    }
                    .awaitAll()
                    .flatten()

                val liveLogins = liveStreams.map { it.userLogin }
                val usersByLogin = if (liveLogins.isNotEmpty()) {
                    liveLogins
                        .chunked(100)
                        .map { batch ->
                            async {
                                runCatching { helixApi.getUsersByLogin(batch) }.getOrElse { emptyList() }
                            }
                        }
                        .awaitAll()
                        .flatten()
                        .associateBy { it.login }
                } else emptyMap()

                val streamsByLogin = liveStreams.associateBy { it.userLogin }
                followedLogins
                    .mapNotNull { login -> streamsByLogin[login] }
                    .map { stream ->
                        val user = usersByLogin[stream.userLogin]
                        Channel(
                            id = stream.userId,
                            login = stream.userLogin,
                            displayName = stream.userName,
                            isLive = true,
                            viewerCount = stream.viewerCount,
                            gameName = stream.gameName,
                            title = stream.title,
                            thumbnailUrl = thumbnailUrl(stream.thumbnailUrl),
                            profileImageUrl = user?.profileImageUrl
                        )
                    }
            } else emptyList()

            val followedLoginSet = followedLogins.toSet()
            val recommendedStreams = topStreams.filter { it.userLogin !in followedLoginSet }.take(10)

            val recommendedUsers = recommendedStreams
                .map { it.userLogin }
                .chunked(100)
                .map { batch ->
                    async {
                        runCatching { helixApi.getUsersByLogin(batch) }.getOrElse { emptyList() }
                    }
                }
                .awaitAll()
                .flatten()
                .associateBy { it.login }

            val recommended = recommendedStreams.map { stream ->
                Channel(
                    id = stream.userId,
                    login = stream.userLogin,
                    displayName = stream.userName,
                    isLive = true,
                    viewerCount = stream.viewerCount,
                    gameName = stream.gameName,
                    title = stream.title,
                    thumbnailUrl = thumbnailUrl(stream.thumbnailUrl),
                    profileImageUrl = recommendedUsers[stream.userLogin]?.profileImageUrl
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
    }

    private suspend fun fetchAllFollows(userId: String): List<String> {
        val all = mutableListOf<String>()
        var cursor: String? = null
        do {
            val page = runCatching {
                helixApi.getFollowedChannelsPaged(userId, after = cursor)
            }.getOrNull() ?: break
            all.addAll(page.logins)
            cursor = page.nextCursor
        } while (cursor != null && all.size < MAX_FOLLOWS)
        return all
    }

    private fun thumbnailUrl(raw: String?): String? {
        if (raw == null) return null
        val bucket = System.currentTimeMillis() / 1000 / 300
        return raw.replace("{width}", "440").replace("{height}", "248") + "?cb=$bucket"
    }

    companion object {
        private const val MAX_FOLLOWS = 1000
    }
}
