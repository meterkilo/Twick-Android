package com.example.chatterinomobile.di

import com.example.chatterinomobile.BuildConfig
import com.example.chatterinomobile.data.local.TokenStore
import com.example.chatterinomobile.data.repository.AnonymousAuthRepository
import com.example.chatterinomobile.data.repository.AuthRepository
import com.example.chatterinomobile.data.repository.BadgeRepository
import com.example.chatterinomobile.data.repository.BadgeRepositoryImpl
import com.example.chatterinomobile.data.repository.ChannelRepository
import com.example.chatterinomobile.data.repository.ChannelRepositoryImpl
import com.example.chatterinomobile.data.repository.ChatRepository
import com.example.chatterinomobile.data.repository.ChatRepositoryImpl
import com.example.chatterinomobile.data.repository.EmoteRepository
import com.example.chatterinomobile.data.repository.EmoteRepositoryImpl
import com.example.chatterinomobile.data.repository.PaintRepository
import com.example.chatterinomobile.data.repository.PaintRepositoryImpl
import com.example.chatterinomobile.data.repository.TwitchOAuthRepository
import org.koin.dsl.bind
import org.koin.dsl.module

val repositoryModule = module {
    single { TokenStore(get()) }

    // OAuth is optional for local/CI builds. When the client ID is missing we
    // intentionally keep the app in anonymous read-only mode instead of
    // crashing at startup or hardcoding someone else's client ID.
    single {
        if (BuildConfig.TWITCH_CLIENT_ID.isBlank()) {
            AnonymousAuthRepository(BuildConfig.TWITCH_CLIENT_ID)
        } else {
            TwitchOAuthRepository(get(), get())
        }
    } bind AuthRepository::class

    // Emotes / cosmetics / channels
    single { EmoteRepositoryImpl(get(), get(), get()) } bind EmoteRepository::class
    single { BadgeRepositoryImpl(get(), get()) } bind BadgeRepository::class
    single { PaintRepositoryImpl(get()) } bind PaintRepository::class
    single { ChannelRepositoryImpl(get()) } bind ChannelRepository::class

    // Chat — depends on TwitchIrcClient + IrcMessageMapper + ModerationEventMapper
    // + MessageEnricher from networkModule. The enricher pulls Emote/Paint
    // repositories from this module, so load order matters only in that
    // repositoryModule must be listed alongside (Koin resolves lazily).
    single {
        ChatRepositoryImpl(
            ircClient = get(),
            mapper = get(),
            moderationMapper = get(),
            roomStateMapper = get(),
            userStateMapper = get(),
            enricher = get(),
            channelRepository = get(),
            badgeRepository = get(),
            emoteRepository = get()
        )
    } bind ChatRepository::class
}
