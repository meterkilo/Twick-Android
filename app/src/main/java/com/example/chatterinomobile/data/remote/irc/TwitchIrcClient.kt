package com.example.chatterinomobile.data.remote.irc

import com.example.chatterinomobile.data.repository.AuthRepository
import com.example.chatterinomobile.data.model.SendMessageResult
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

/**
 * Minimal Twitch IRC-over-WebSocket client.
 *
 * Lifecycle:
 *  - [connect] opens `wss://irc-ws.chat.twitch.tv:443`, requests the tags,
 *    commands, and membership capabilities, and logs in. If [AuthRepository]
 *    returns no token we fall back to an anonymous `justinfan{N}` login — we
 *    can read chat but can't send.
 *  - [joinChannel] / [partChannel] adjust the active channel set. Joins sent
 *    before [connect] are queued and flushed on connect.
 *  - [disconnect] cancels the reader coroutine and closes the socket. Safe
 *    to call multiple times.
 *
 * Outbound frames: this class exposes [sendMessage] for PRIVMSG but will
 * silently drop sends when anonymous, since the Twitch server would close
 * the connection for unauthenticated PRIVMSGs.
 *
 * Inbound frames: every non-empty line received is parsed via [IrcParser]
 * and emitted on [incoming]. The WebSocket frame may bundle several lines
 * (they're \r\n-separated), so we split before parsing.
 *
 * Reconnection is *not* handled here — that's the ChatRepository's concern.
 */
class TwitchIrcClient(
    private val httpClient: HttpClient,
    private val authRepository: AuthRepository
) {

    // buffer large enough to survive a brief render-thread stall without
    // dropping frames. DROP_OLDEST keeps the newest chat visible if we do
    // fall behind — stale messages aren't useful.
    private val _incoming = MutableSharedFlow<IrcMessage>(
        replay = 0,
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val incoming: Flow<IrcMessage> = _incoming.asSharedFlow()

    private val _disconnects = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val disconnects: Flow<Unit> = _disconnects.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stateMutex = Mutex()

    private var session: DefaultClientWebSocketSession? = null
    private var readerJob: Job? = null
    private val joinedChannels = HashSet<String>() // channel logins, no '#'

    suspend fun connect() {
        stateMutex.withLock {
            if (session != null) return // already connected
            val token = authRepository.getAccessToken()
            val nick = if (token != null) {
                authRepository.getUserId()?.let { "user_$it" } ?: anonymousNick()
            } else {
                anonymousNick()
            }

            val ws = httpClient.webSocketSession(urlString = WS_URL)
            session = ws

            // Capabilities: we need tags for badges/colors/emote ranges,
            // commands for USERNOTICE/CLEARCHAT, membership for JOIN/PART.
            ws.send("CAP REQ :twitch.tv/tags twitch.tv/commands twitch.tv/membership")
            if (token != null) ws.send("PASS oauth:$token")
            ws.send("NICK $nick")

            // Rejoin any previously-tracked channels.
            for (channel in joinedChannels) {
                ws.send("JOIN #$channel")
            }

            readerJob = scope.launch { readLoop(ws) }
        }
    }

    private suspend fun readLoop(ws: DefaultClientWebSocketSession) {
        try {
            while (scope.isActive) {
                val frame = ws.incoming.receive()
                if (frame !is Frame.Text) continue
                val text = frame.readText()
                // A single WS frame can carry multiple IRC lines.
                for (line in text.split("\r\n")) {
                    if (line.isEmpty()) continue
                    val parsed = IrcParser.parse(line) ?: continue
                    if (parsed.command == "PING") {
                        // Twitch pings about every 5 min — we must pong to stay alive.
                        val pongTarget = parsed.trailing ?: "tmi.twitch.tv"
                        ws.send("PONG :$pongTarget")
                        continue
                    }
                    _incoming.emit(parsed)
                }
            }
        } catch (t: CancellationException) {
            // Normal shutdown path — `disconnect()` cancels us. Let it propagate.
            throw t
        } catch (_: Throwable) {
            // Connection dropped. Clear session state so `connect()` can be
            // safely retried; reconnection policy lives at the repo layer.
            // Not taking the mutex: `disconnect()` may be holding it, and this
            // reference-equality check is fine without it since only this
            // coroutine and `disconnect` ever mutate `session`.
            if (session === ws) {
                session = null
                _disconnects.tryEmit(Unit)
            }
        }
    }

    suspend fun joinChannel(login: String) {
        val normalized = login.lowercase().removePrefix("#")
        stateMutex.withLock {
            if (!joinedChannels.add(normalized)) return
            session?.send("JOIN #$normalized")
        }
    }

    suspend fun partChannel(login: String) {
        val normalized = login.lowercase().removePrefix("#")
        stateMutex.withLock {
            if (!joinedChannels.remove(normalized)) return
            session?.send("PART #$normalized")
        }
    }

    /**
     * Sends a PRIVMSG. Silently drops when anonymous — the server would kick
     * us otherwise. Returns true if a frame was actually written.
     */
    suspend fun sendMessage(channelLogin: String, text: String): SendMessageResult {
        if (text.isBlank()) return SendMessageResult.EmptyMessage
        if (authRepository.getAccessToken() == null) return SendMessageResult.Anonymous
        val channel = channelLogin.lowercase().removePrefix("#")
        val ws = stateMutex.withLock { session } ?: return SendMessageResult.Disconnected
        return runCatching {
            ws.send("PRIVMSG #$channel :$text")
            SendMessageResult.Sent
        }.getOrElse { throwable ->
            SendMessageResult.Failed(throwable.message ?: "Failed to send message")
        }
    }

    suspend fun disconnect() {
        stateMutex.withLock {
            readerJob?.cancel()
            readerJob = null
            try {
                session?.close()
            } catch (_: Throwable) {
                // best-effort close
            }
            session = null
        }
    }

    private fun anonymousNick(): String =
        "justinfan${Random.nextInt(10_000, 99_999)}"

    companion object {
        private const val WS_URL = "wss://irc-ws.chat.twitch.tv:443"
    }
}
