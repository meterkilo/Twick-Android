package com.example.chatterinomobile

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.chatterinomobile.data.repository.AuthRepository
import com.example.chatterinomobile.ui.auth.AuthViewModel
import com.example.chatterinomobile.ui.auth.TwitchAuthLauncher
import com.example.chatterinomobile.ui.channels.ChannelTabsViewModel
import com.example.chatterinomobile.ui.chat.ChatViewModel
import com.example.chatterinomobile.ui.discovery.DiscoveryScreen
import com.example.chatterinomobile.ui.onboarding.OnboardingFlow
import com.example.chatterinomobile.ui.settings.SettingsViewModel
import com.example.chatterinomobile.ui.theme.ChatterinoMobileTheme
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : ComponentActivity() {





    private val authViewModel: AuthViewModel by viewModel()
    private val tabsViewModel: ChannelTabsViewModel by viewModel()
    private val chatViewModel: ChatViewModel by viewModel()
    @Suppress("unused")
    private val settingsViewModel: SettingsViewModel by viewModel()

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthRedirect(intent)
    }

    private fun handleAuthRedirect(intent: Intent?) {
        val data = intent?.data ?: return
        val url = data.toString()
        if (url.startsWith(AuthRepository.REDIRECT_URI)) {
            authViewModel.onRedirectIntercepted(url)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleAuthRedirect(intent)

        setContent {
            val authState by authViewModel.uiState.collectAsState()
            val activeChannel by tabsViewModel.activeChannel.collectAsState()





            var onboardingComplete by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(authState.isLoggedIn, authState.isLoading) {
                if (authState.isLoggedIn && !authState.isLoading && !onboardingComplete) {






                    if (savedInstanceState == null) onboardingComplete = true
                }
            }

            LaunchedEffect(activeChannel.channelLogin, activeChannel.hydration?.channelId) {
                chatViewModel.setActiveChannel(
                    channelLogin = activeChannel.channelLogin,
                    channelId = activeChannel.hydration?.channelId
                )
            }

            val activity = LocalActivity.current
            LaunchedEffect(authState.authorizeUrl) {
                val url = authState.authorizeUrl ?: return@LaunchedEffect
                val ctx = activity ?: return@LaunchedEffect
                TwitchAuthLauncher.launch(ctx, url)
                authViewModel.onAuthorizeUrlConsumed()
            }

            ChatterinoMobileTheme {
                when {
                    !onboardingComplete -> OnboardingFlow(
                        isLoggedIn = authState.isLoggedIn,
                        onConnectTwitch = authViewModel::startLogin,
                        onFinish = { onboardingComplete = true }
                    )




                    else -> Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        DiscoveryScreen(
                            onJoinChannel = tabsViewModel::joinChannel,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}
