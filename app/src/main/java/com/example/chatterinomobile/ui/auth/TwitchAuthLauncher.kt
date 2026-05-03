package com.example.chatterinomobile.ui.auth

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsServiceConnection
import androidx.browser.customtabs.CustomTabsSession
import androidx.browser.customtabs.CustomTabsClient
import androidx.core.net.toUri

object TwitchAuthLauncher {
    private var serviceConnection: CustomTabsServiceConnection? = null
    private var customTabsClient: CustomTabsClient? = null
    private var customTabsSession: CustomTabsSession? = null
    private var providerPackage: String? = null

    fun bind(activity: Activity) {
        if (customTabsClient != null || serviceConnection != null) return

        val resolvedPackage = CustomTabsClient.getPackageName(activity, null) ?: return
        providerPackage = resolvedPackage

        serviceConnection = object : CustomTabsServiceConnection() {
            override fun onCustomTabsServiceConnected(
                name: android.content.ComponentName,
                client: CustomTabsClient
            ) {
                customTabsClient = client
                client.warmup(0L)
                customTabsSession = client.newSession(null)
            }

            override fun onServiceDisconnected(name: android.content.ComponentName) {
                customTabsSession = null
                customTabsClient = null
            }
        }.also { connection ->
            if (!CustomTabsClient.bindCustomTabsService(activity, resolvedPackage, connection)) {
                serviceConnection = null
                providerPackage = null
            }
        }
    }

    fun unbind(activity: Activity) {
        serviceConnection?.let(activity::unbindService)
        serviceConnection = null
        customTabsSession = null
        customTabsClient = null
        providerPackage = null
    }

    fun warmupUrl(url: String) {
        customTabsSession?.mayLaunchUrl(Uri.parse(url), null, null)
    }

    fun launch(context: Context, authorizeUrl: String) {
        warmupUrl(authorizeUrl)

        CustomTabsIntent.Builder(customTabsSession)
            .setShowTitle(true)
            .setUrlBarHidingEnabled(false)
            .build()
            .apply {
                intent.`package` = providerPackage
            }
            .launchUrl(context, authorizeUrl.toUri())
    }
}
