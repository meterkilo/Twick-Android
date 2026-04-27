package com.example.chatterinomobile.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * Stateless coordinator for the onboarding sequence. Kept deliberately simple —
 * onboarding is linear (Splash → Welcome → Connect → Sync → app), with one back
 * affordance on Connect/Sync to step backwards. We persist the step in
 * `rememberSaveable` so a config change mid-flow doesn't bounce the user back to
 * the splash.
 *
 * The flow is wired with two callbacks rather than owning auth/sync state:
 *   - `onConnectTwitch`: handed off to AuthViewModel.startLogin so the platform
 *     OAuth WebView takes over. Onboarding doesn't care how that resolves; the
 *     parent decides whether to advance to the sync screen.
 *   - `onFinish`: emitted once the user dismisses the sync screen, signalling
 *     the parent to swap onboarding out for the main app shell.
 */
@Composable
fun OnboardingFlow(
    isLoggedIn: Boolean,
    onConnectTwitch: () -> Unit,
    onFinish: () -> Unit
) {
    var step by rememberSaveable { mutableStateOf(OnboardingStep.Splash) }

    // Auto-advance the splash on first composition. Pure UX delay — the real
    // session check happens in AuthViewModel and is already in flight before
    // we render. 1200ms is long enough for the gradient + dot blink to land.
    LaunchedEffect(Unit) {
        if (step == OnboardingStep.Splash) {
            delay(1200)
            // If the user is already authed (returning user, token still valid),
            // skip straight past the welcome+connect screens to the sync stage.
            step = if (isLoggedIn) OnboardingStep.EmoteSync else OnboardingStep.Welcome
        }
    }

    // If auth completes mid-flow (user returns from the OAuth WebView), advance
    // automatically. We only do this from Connect → Sync; we don't want to skip
    // the Welcome screen for first-time users just because the token survived
    // a process restart.
    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn && step == OnboardingStep.ConnectTwitch) {
            step = OnboardingStep.EmoteSync
        }
    }

    AnimatedContent(
        targetState = step,
        transitionSpec = {
            val forward = targetState.ordinal > initialState.ordinal
            val direction = if (forward) 1 else -1
            (slideInHorizontally(tween(280)) { full -> direction * full } + fadeIn(tween(220)))
                .togetherWith(
                    slideOutHorizontally(tween(280)) { full -> -direction * full } + fadeOut(tween(180))
                )
        },
        label = "onboarding"
    ) { current ->
        when (current) {
            OnboardingStep.Splash -> SplashScreen()
            OnboardingStep.Welcome -> WelcomeScreen(
                onGetStarted = { step = OnboardingStep.ConnectTwitch }
            )
            OnboardingStep.ConnectTwitch -> ConnectTwitchScreen(
                onBack = { step = OnboardingStep.Welcome },
                onConnect = onConnectTwitch
            )
            OnboardingStep.EmoteSync -> EmoteSyncScreen(
                onBack = { step = OnboardingStep.ConnectTwitch },
                onFinish = onFinish
            )
        }
    }
}

enum class OnboardingStep { Splash, Welcome, ConnectTwitch, EmoteSync }
