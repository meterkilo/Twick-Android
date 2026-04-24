# Chatterino Mobile — Codex context

Drop-in AGENTS.md. Contains everything a fresh Codex session needs to be productive without re-deriving decisions from the codebase.

## What this project is

A lightweight, low-latency Twitch + Kick chat client for Android. Think Chatterino / DankChat, but mobile-first with no bloat. Name was undecided between Swiftchat / Pogchat / Refined; settled on **Chatterino Mobile** for now.

Owner: Franz. Solo developer, ships Android first, then KMP + iOS + desktop.

No backend — connects direct to Twitch and Kick APIs. Only cost is the $25 Play Store fee and (later) $99/yr Apple fee.

## Tech stack

- **Language**: Kotlin (Android-first, KMP migration later — keep networking + business logic portable).
- **Networking**: Ktor (`ktor-client-okhttp` + `WebSockets` + `ContentNegotiation` + JSON + Logging + HttpTimeout).
- **UI**: Jetpack Compose + Material 3.
- **DI**: Koin (`koin-android`, `koin-androidx-compose`).
- **Image loading**: Coil (not yet wired — UI doesn't exist).
- **Local storage** (planned): SQLite via SQLDelight for message history.
- **Auth**: Twitch OAuth **Device Code Flow** (not PKCE). Tokens stored in `EncryptedSharedPreferences`.
- **Caching**: LRU in-memory for emotes/badges; no disk cache yet.
- **Serialization**: kotlinx-serialization.
- **Compile SDK 36, min SDK 24, Java 11 target.**

Gradle uses the version catalog at `gradle/libs.versions.toml` + direct coordinates for Ktor / Koin / security-crypto in `app/build.gradle.kts`.

## Architecture

```
app/src/main/java/com/example/chatterinomobile/
├── ChatterinoApp.kt            # Application subclass; starts Koin
├── MainActivity.kt             # Compose host (currently just a Hello Android smoke test)
├── data/
│   ├── model/                  # Domain types: ChatMessage, ChatUser, MessageFragment,
│   │                           #   Emote, Badge, Paint, Channel, ModerationEvent
│   ├── remote/
│   │   ├── api/                # Ktor wrappers: BttvApi, FfzApi, SevenTvApi,
│   │   │                       #   SevenTvCosmeticsApi, TwitchHelixApi, TwitchOAuthApi (WIP)
│   │   ├── dto/                # kotlinx-serialization DTOs, one file per provider
│   │   ├── irc/                # TwitchIrcClient (WebSocket), IrcParser, IrcMessage,
│   │   │                       #   IrcMessageMapper, ModerationEventMapper, MessageEnricher
│   │   └── mapper/             # DTO → domain mappers
│   └── repository/             # interface + impl for Chat / Emote / Badge / Paint /
│                               #   Channel / Auth
├── di/
│   ├── NetworkModule.kt        # HttpClient, APIs, IRC client, mappers, enricher
│   └── RepositoryModule.kt     # Repositories; currently binds AnonymousAuthRepository
└── ui/theme/                   # Color, Theme, Type (scaffolding only)
```

Flow from the wire to the UI:

```
WebSocket frame
  ↓  (IrcParser)
IrcMessage
  ↓  (TwitchIrcClient.incoming: SharedFlow, DROP_OLDEST, capacity 256)
  ├─→ IrcMessageMapper.map(raw)           → PRIVMSG / USERNOTICE / NOTICE → ChatMessage
  │     ↓  (MessageEnricher.enrich)
  │     ChatMessage (paint + 3rd-party emotes applied)
  │     ↓
  │   ChatRepository.messages : Flow<ChatMessage>
  └─→ ModerationEventMapper.map(raw)      → CLEARCHAT / CLEARMSG → ModerationEvent
        ↓
      ChatRepository.moderationEvents : Flow<ModerationEvent>
```

`ircClient.incoming` is a `SharedFlow`, so two downstream collectors share the emissions without re-parsing. Both mappers return null for frames they don't handle; `mapNotNull` filters those out.

## Key decisions + rationale

Read these before making changes to the relevant area — they're not obvious from the code alone.

- **Enrichment is a decorator, not inline in `IrcMessageMapper`.** Paint lookup and BTTV/FFZ/7TV emote word-swap live in `MessageEnricher`, applied as a map step in `ChatRepositoryImpl`. This keeps `IrcMessageMapper` pure-IRC and lets future enrichment passes (NER mentions, link previews, etc.) stack without touching the mapper.
- **Enricher short-circuits when nothing changes.** If the author has no paint and no Text fragment contains a candidate word, `enrich()` returns the input by reference so the ChatMessage isn't copied. Matters on the render hot path.
- **Emote precedence is BTTV > FFZ > 7TV on name collision.** Matches Chatterino desktop. Implemented by insertion order in `EmoteRepositoryImpl` (later providers overwrite earlier ones — 7TV is loaded first).
- **Anonymous fallback for read-only chat.** When `AuthRepository.getAccessToken()` is null, the IRC client logs in as `justinfan{random}`. We can read chat; PRIVMSG sends are silently dropped so Twitch doesn't kick us.
- **Badge lookups are unsynchronized on the read side.** Render happens every message; taking a mutex there would stall. A single `writeMutex` guards refresh. Slight read-side race during refresh is acceptable — worst case is one frame with a stale badge.
- **`ChatMessage.author` is non-null.** System notices (NOTICE) use a synthetic `SYSTEM_AUTHOR` with id `"system"`, login `"tmi.twitch.tv"`. Avoids nullable author everywhere downstream.
- **Moderation events are a separate flow, not a message variant.** `CLEARCHAT` / `CLEARMSG` *mutate* the existing log; they can't be modeled as appended `ChatMessage`s. UI reaches back into its rendered list when one arrives.
- **IrcMessage.trailing gotcha.** `trailing` is `params.lastOrNull()` — when a frame has only the channel param (e.g. `CLEARCHAT #dallas` for a full wipe, or `USERNOTICE #dallas` with no body), trailing returns the channel name, not null. Guard with `if (raw.params.size > 1) raw.trailing else null`. `TwitchIrcClient`'s PING handler depends on the current behavior, so don't change `trailing` itself.
- **Reconnection is not in `TwitchIrcClient`.** Explicitly layered at the repository. Keeps the client dumb and testable.
- **Rate limiting isn't done either.** Twitch drops the connection at 20 PRIVMSGs / 30s for a plain user. Send queue with token buckets is a known TODO.
- **OAuth Device Code Flow, not PKCE.** User enters a code at `twitch.tv/activate` on any device. Simpler mobile UX than PKCE, no custom URI scheme needed. No client secret — public mobile clients shouldn't have one; PKCE and device flow are designed for that.
- **Client ID via local.properties → BuildConfig, never hardcoded.** `local.properties` is gitignored. `app/build.gradle.kts` reads `twitchClientId` from it and exposes `BuildConfig.TWITCH_CLIENT_ID`. Missing value falls through to anonymous mode so CI builds don't break.
- **Render unit is the `MessageFragment` list.** UI composes each fragment separately — Text runs get a single TextView-equivalent, Emote fragments become images, Mention fragments get tap targets and pill styling. Don't shred plain text into per-word fragments; it inflates the render cost for no visual difference.

## Build & run

Local dev:

```bash
./gradlew :app:compileDebugKotlin    # fast syntax check
./gradlew :app:assembleDebug         # full debug APK
./gradlew test                       # JVM unit tests
```

There's one JVM test (`ExampleUnitTest`) and one instrumented test (`ExampleInstrumentedTest`) — both placeholders from the Android Studio template. No real test coverage yet.

## Required local setup

Create `local.properties` (auto-created by Android Studio, gitignored) with at least:

```
sdk.dir=<your Android SDK path>
twitchClientId=<your Twitch app client ID>
```

Without `twitchClientId`, auth stays anonymous.

## Work in progress

**Currently mid-build: Twitch OAuth Device Code Flow.** State as of this context:

- [x] `local.properties` holds `twitchClientId`.
- [x] `BuildConfig.TWITCH_CLIENT_ID` wired via `buildConfigField` + `buildFeatures { buildConfig = true }`.
- [x] `androidx.security:security-crypto:1.1.0-alpha06` added as a dep (for `EncryptedSharedPreferences`).
- [ ] `TwitchOAuthApi` — Ktor wrapper for `https://id.twitch.tv/oauth2/{device,token,validate}`. Must treat `authorization_pending` / `slow_down` / `expired_token` / `access_denied` as domain states, not exceptions.
- [ ] `TokenStore` — EncryptedSharedPreferences wrapper holding access_token, refresh_token, user_id, login, expires_at, scopes.
- [ ] `TwitchOAuthRepository implements AuthRepository` — `getAccessToken()` auto-refreshes when expired; exposes `startDeviceFlow()` and `awaitDeviceAuthorization(state)` for the UI. Needs to extend poll interval on `slow_down`.
- [ ] Koin swap from `AnonymousAuthRepository` to `TwitchOAuthRepository` (keep anonymous as a test fallback).

OAuth scope list for MVP: `chat:read chat:edit user:read:follows`.

## Backend punch-list (not yet started, roughly by blast radius)

1. **Real OAuth** (above — in progress).
2. **Channel-scoped emote loading.** `EmoteRepositoryImpl.loadEmotesForChannel(channelId)` currently ignores the arg — only globals. Need 7TV user emote set + BTTV channel + FFZ room endpoints, keyed per-channel cache.
3. **On-join orchestration.** `BadgeRepository.loadChannelBadges` exists but nothing calls it. Need a "resolve channel via Helix → fire loadChannelBadges + loadChannelEmotes in parallel" step wired into `ChatRepository.joinChannel`.
4. **Non-PRIVMSG state frames.** `ROOMSTATE` (slow/sub/emote-only mode), `USERSTATE` / `GLOBALUSERSTATE` (your own badges / color for sending). Should be `StateFlow<Map<channel, RoomState>>`, not messages.
5. **Reconnection policy.** Exponential backoff + network-state observer + re-auth on reconnect. Belongs in `ChatRepositoryImpl`, not `TwitchIrcClient`.
6. **Outbound rate limiting.** Per-channel token buckets wrapping `sendMessage`. 20 msgs / 30s for users, 100 / 30s for mods/broadcaster.
7. **Local persistence.** SQLDelight schema for message history. `data/local` package doesn't exist yet.
8. **Kick support.** Project spec covers Twitch + Kick; only Twitch is wired. Kick uses a Pusher WebSocket, separate emote/badge sources.
9. **Error surfacing.** All repositories do `runCatching{}.getOrElse { emptyList() }` — failures vanish silently. UI will need a loading/error state flow per repo.
10. **Reply body cleanup.** Twitch prefixes reply message bodies with `@parentAuthor `. Strip it; attach parent context from the tag.
11. **Already done this session:** paint + 3rd-party emote enrichment decorator (`MessageEnricher`); USERNOTICE + NOTICE → `MessageType.System`; CLEARCHAT + CLEARMSG → `ModerationEvent` on a separate flow.

## Working with Franz — preferences

- **Terse answers.** Punch-list format when listing things; skip trailing summaries of what was just done.
- **Architectural rationale in comments.** The existing code uses long, explanatory doc-comments on every non-trivial type. Match that density — explain *why*, not *what*.
- **Propose approaches, don't demand specs.** Default to a sensible choice and explain the tradeoff; only ask clarifying questions when the answer could reasonably go two different ways.
- **Security-conscious.** Never bake secrets into source. Client secret should never appear anywhere — mobile apps don't use one.
- **Single-concern PRs.** Bundle refactors in one pass rather than splintering into micro-PRs.
- **Gradle can't build in Codex's sandbox** (no outbound to `services.gradle.org`). Manual review is the best we can do from here; Franz runs `./gradlew` locally to confirm.

## External references

- Twitch IRC: <https://dev.twitch.tv/docs/irc/>
- Twitch Helix: <https://dev.twitch.tv/docs/api/reference/>
- Twitch Device Code Flow: <https://dev.twitch.tv/docs/authentication/getting-tokens-device-code-flow/>
- 7TV API: <https://7tv.io/docs>
- BTTV API: <https://api.betterttv.net/3/cached/emotes/global> (undocumented; reverse-engineered from web client)
- FFZ API: <https://api.frankerfacez.com/docs/>
