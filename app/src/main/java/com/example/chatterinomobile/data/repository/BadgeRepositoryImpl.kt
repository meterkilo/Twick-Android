package com.example.chatterinomobile.data.repository

import com.example.chatterinomobile.data.model.Badge
import com.example.chatterinomobile.data.remote.api.SevenTvCosmeticsApi
import com.example.chatterinomobile.data.remote.api.TwitchHelixApi
import com.example.chatterinomobile.data.remote.mapper.toDomain
import com.example.chatterinomobile.data.remote.mapper.twitchBadgeKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * In-memory badge cache. Reads go through unsynchronized `HashMap`s because
 * badges are looked up from the render loop on every message — taking a
 * mutex there would be disastrous. Writes are serialized by [writeMutex] so
 * concurrent channel switches don't trample each other.
 *
 * The slight read-side race (a channel badge appearing mid-write) is fine:
 * the worst case is one frame with an older badge image.
 */
class BadgeRepositoryImpl(
    private val helixApi: TwitchHelixApi,
    private val sevenTvCosmeticsApi: SevenTvCosmeticsApi
) : BadgeRepository {

    // key = "$setId/$version"
    private val globalTwitchBadges = HashMap<String, Badge>()

    // channelId -> (key -> Badge)
    private val channelTwitchBadges = HashMap<String, HashMap<String, Badge>>()

    // twitchUserId -> list of SEVENTV badges
    private val sevenTvBadgesByUser = HashMap<String, MutableList<Badge>>()

    private var globalLoadedAtMillis = 0L
    private val channelLoadedAtMillis = HashMap<String, Long>()
    private val writeMutex = Mutex()

    override suspend fun loadGlobalBadges() {
        if (isGlobalFresh()) return
        withContext(Dispatchers.IO) {
            coroutineScope {
                val twitchDeferred = async {
                    runCatching { helixApi.getGlobalBadges() }.getOrElse { emptyList() }
                }
                val cosmeticsDeferred = async {
                    runCatching { sevenTvCosmeticsApi.getCosmetics() }.getOrNull()
                }
                val twitchSets = twitchDeferred.await()
                val cosmetics = cosmeticsDeferred.await()

                writeMutex.withLock {
                    globalTwitchBadges.clear()
                    for (set in twitchSets) {
                        for (version in set.versions) {
                            globalTwitchBadges[twitchBadgeKey(set.setId, version.id)] =
                                version.toDomain(set.setId)
                        }
                    }

                    sevenTvBadgesByUser.clear()
                    if (cosmetics != null) {
                        for (badgeDto in cosmetics.badges) {
                            val badge = badgeDto.toDomain()
                            for (userId in badgeDto.users) {
                                sevenTvBadgesByUser
                                    .getOrPut(userId) { mutableListOf() }
                                    .add(badge)
                            }
                        }
                    }
                    globalLoadedAtMillis = System.currentTimeMillis()
                }
            }
        }
    }

    override suspend fun loadChannelBadges(channelId: String) {
        if (isChannelFresh(channelId)) return
        val sets = withContext(Dispatchers.IO) {
            runCatching { helixApi.getChannelBadges(channelId) }.getOrElse { emptyList() }
        }
        writeMutex.withLock {
            val bucket = HashMap<String, Badge>()
            for (set in sets) {
                for (version in set.versions) {
                    bucket[twitchBadgeKey(set.setId, version.id)] = version.toDomain(set.setId)
                }
            }
            channelTwitchBadges[channelId] = bucket
            channelLoadedAtMillis[channelId] = System.currentTimeMillis()
        }
    }

    override fun findTwitchBadge(setId: String, version: String, channelId: String?): Badge? {
        val key = twitchBadgeKey(setId, version)
        // Channel overrides win (e.g. subscriber badges are per-channel).
        if (channelId != null) {
            channelTwitchBadges[channelId]?.get(key)?.let { return it }
        }
        return globalTwitchBadges[key]
    }

    override fun findThirdPartyBadges(twitchUserId: String): List<Badge> =
        sevenTvBadgesByUser[twitchUserId].orEmpty()

    override fun clearCache(channelId: String?) {
        if (channelId == null) {
            globalTwitchBadges.clear()
            sevenTvBadgesByUser.clear()
            channelTwitchBadges.clear()
            channelLoadedAtMillis.clear()
            globalLoadedAtMillis = 0L
            return
        }

        channelTwitchBadges.remove(channelId)
        channelLoadedAtMillis.remove(channelId)
    }

    private fun isGlobalFresh(): Boolean =
        globalLoadedAtMillis != 0L &&
            System.currentTimeMillis() - globalLoadedAtMillis < GLOBAL_CACHE_TTL_MILLIS

    private fun isChannelFresh(channelId: String): Boolean {
        val loadedAt = channelLoadedAtMillis[channelId] ?: return false
        return System.currentTimeMillis() - loadedAt < CHANNEL_CACHE_TTL_MILLIS
    }

    companion object {
        private const val GLOBAL_CACHE_TTL_MILLIS = 6L * 60L * 60L * 1000L
        private const val CHANNEL_CACHE_TTL_MILLIS = 30L * 60L * 1000L
    }
}
