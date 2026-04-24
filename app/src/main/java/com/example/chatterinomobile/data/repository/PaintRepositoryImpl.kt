package com.example.chatterinomobile.data.repository

import com.example.chatterinomobile.data.model.Paint
import com.example.chatterinomobile.data.remote.api.SevenTvCosmeticsApi
import com.example.chatterinomobile.data.remote.mapper.toDomain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Holds the paint table in a `Map<twitchUserId, Paint>`. Populated by one
 * network call on startup (and on manual refresh). Reads are lock-free and
 * happen on every message render.
 *
 * Note: a given user only has one active paint at a time. If the 7TV API
 * returns a user under multiple paints (shouldn't happen, but the `users`
 * field is a free-form list), the *last* one wins — consistent with how the
 * 7TV browser extension resolves the same case.
 */
class PaintRepositoryImpl(
    private val sevenTvCosmeticsApi: SevenTvCosmeticsApi
) : PaintRepository {

    private val paintByUserId = HashMap<String, Paint>()
    private val writeMutex = Mutex()

    override suspend fun loadPaints() {
        val cosmetics = withContext(Dispatchers.IO) {
            runCatching { sevenTvCosmeticsApi.getCosmetics() }.getOrNull()
        } ?: return

        writeMutex.withLock {
            paintByUserId.clear()
            for (paintDto in cosmetics.paints) {
                val paint = paintDto.toDomain()
                for (userId in paintDto.users) {
                    paintByUserId[userId] = paint
                }
            }
        }
    }

    override fun findPaintForUser(twitchUserId: String): Paint? =
        paintByUserId[twitchUserId]
}
