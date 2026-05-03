package com.example.chatterinomobile.ui.auth

import android.content.Context
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri

object TwitchAuthLauncher {
    fun launch(context: Context, authorizeUrl: String) {
        CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setUrlBarHidingEnabled(false)
            .build()
            .launchUrl(context, authorizeUrl.toUri())
    }
}
