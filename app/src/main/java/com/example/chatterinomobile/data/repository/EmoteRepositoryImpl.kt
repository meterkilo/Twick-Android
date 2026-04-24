package com.example.chatterinomobile.data.repository

import com.example.chatterinomobile.data.model.Emote
import com.example.chatterinomobile.data.remote.api.BttvApi
import com.example.chatterinomobile.data.remote.api.FfzApi
import com.example.chatterinomobile.data.remote.api.SevenTvApi
import com.example.chatterinomobile.data.remote.mapper.toDomain
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Loads emotes from 7TV, BTTV, and FFZ and keeps them in in-memory lookup
 * tables keyed by emote name. A single [Mutex] guards writes so concurrent
 * channel switches don't race.
 *
 * There are two caches:
 * - [globalEmotesByName] for provider-wide sets loaded once on startup/join
 * - [channelEmotesByChannelId] for broadcaster-specific sets keyed by Twitch ID
 *
 * Reads stay lock-free because the hot path is every inbound message. We
 * tolerate the tiny race where one message arrives while a channel cache is
 * swapping in; worst case, that frame misses a just-loaded emote once.
 */
class EmoteRepositoryImpl(
    private val sevenTvApi: SevenTvApi,
    private val bttvApi: BttvApi,
    private val ffzApi: FfzApi
) : EmoteRepository {

    private val globalEmotesByName = HashMap<String, Emote>()
    private val channelEmotesByChannelId = HashMap<String, HashMap<String, Emote>>()
    private var globalLoadedAtMillis = 0L
    private val channelLoadedAtMillis = HashMap<String, Long>()
    private val inFlightLoads = HashMap<String, Deferred<Map<String, Emote>>>()
    private val mutex = Mutex()

    override suspend fun loadEmotesForChannel(channelId: String?) {
        withContext(Dispatchers.IO) {
            coroutineScope {
                if (channelId == null) {
                    if (isGlobalFresh()) return@coroutineScope
                    val globalLoaded = loadGlobalEmotes()
                    mutex.withLock {
                        globalEmotesByName.clear()
                        globalEmotesByName.putAll(globalLoaded)
                        globalLoadedAtMillis = System.currentTimeMillis()
                    }
                    return@coroutineScope
                }

                if (isChannelFresh(channelId)) return@coroutineScope

                val channelLoaded = mutex.withLock {
                    inFlightLoads[channelId]
                        ?: async {
                            runCatching { fetchChannelEmotes(channelId) }
                                .getOrElse { emptyMap() }
                        }.also { inFlightLoads[channelId] = it }
                }.await()

                mutex.withLock {
                    channelEmotesByChannelId[channelId] = HashMap(channelLoaded)
                    channelLoadedAtMillis[channelId] = System.currentTimeMillis()
                    inFlightLoads.remove(channelId)
                }
            }
        }
    }

    override fun findEmote(name: String, channelId: String?): Emote? {
        if (channelId != null) {
            channelEmotesByChannelId[channelId]?.get(name)?.let { return it }
        }
        return globalEmotesByName[name]
    }

    override fun clearCache(channelId: String?) {
        if (channelId == null) {
            globalEmotesByName.clear()
            channelEmotesByChannelId.clear()
            channelLoadedAtMillis.clear()
            globalLoadedAtMillis = 0L
            return
        }

        channelEmotesByChannelId.remove(channelId)
        channelLoadedAtMillis.remove(channelId)
    }

    /**
     * Chatterino desktop resolves name collisions as BTTV > FFZ > 7TV.
     * We reproduce that by inserting lower-priority providers first and letting
     * later puts overwrite earlier ones.
     */
    private fun mergeWithPrecedence(
        sevenTv: List<Emote>,
        ffz: List<Emote>,
        bttv: List<Emote>
    ): HashMap<String, Emote> {
        val merged = HashMap<String, Emote>(sevenTv.size + ffz.size + bttv.size)
        for (emote in sevenTv) merged[emote.name] = emote
        for (emote in ffz) merged[emote.name] = emote
        for (emote in bttv) merged[emote.name] = emote
        return merged
    }

    private suspend fun loadGlobalEmotes(): HashMap<String, Emote> = coroutineScope {
        val globalSevenTv: Deferred<List<Emote>> = async {
            runCatching { sevenTvApi.getGlobalEmoteSet().emotes.map { it.toDomain() } }
                .getOrElse { emptyList() }
        }
        val globalFfz: Deferred<List<Emote>> = async {
            runCatching { ffzApi.getGlobalEmotes().map { it.toDomain() } }
                .getOrElse { emptyList() }
        }
        val globalBttv: Deferred<List<Emote>> = async {
            runCatching { bttvApi.getGlobalEmotes().map { it.toDomain() } }
                .getOrElse { emptyList() }
        }

        mergeWithPrecedence(
            sevenTv = globalSevenTv.await(),
            ffz = globalFfz.await(),
            bttv = globalBttv.await()
        )
    }

    private suspend fun fetchChannelEmotes(channelId: String): Map<String, Emote> = coroutineScope {
        val channelSevenTv: Deferred<List<Emote>> = async {
            runCatching { sevenTvApi.getChannelEmotes(channelId).map { it.toDomain() } }
                .getOrElse { emptyList() }
        }
        val channelFfz: Deferred<List<Emote>> = async {
            runCatching { ffzApi.getChannelEmotes(channelId).map { it.toDomain() } }
                .getOrElse { emptyList() }
        }
        val channelBttv: Deferred<List<Emote>> = async {
            runCatching { bttvApi.getChannelEmotes(channelId).map { it.toDomain() } }
                .getOrElse { emptyList() }
        }

        mergeWithPrecedence(
            sevenTv = channelSevenTv.await(),
            ffz = channelFfz.await(),
            bttv = channelBttv.await()
        )
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
