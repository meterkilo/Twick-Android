package com.example.chatterinomobile.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Palette matching the Twick reference mockups (the JSX prototype Franz wants the
 * onboarding flow to look like). Kept as plain Color constants instead of feeding
 * Material3's color scheme because onboarding is a one-off bespoke surface — we
 * don't want these tokens leaking into the rest of the app's M3 theming.
 */
object Twick {
    val Bg = Color(0xFF0A0A0B)
    val Ink = Color(0xFFF6F6F7)
    val Ink2 = Color(0xFFC4C4CB)
    val Ink3 = Color(0xFF8A8A93)
    val Ink4 = Color(0xFF5A5A63)

    // Surfaces (s1 = closest to bg, s3 = highest elevation)
    val S1 = Color(0xFF141416)
    val S2 = Color(0xFF1C1C20)
    val S3 = Color(0xFF26262C)

    val Hairline = Color(0x1AFFFFFF)

    // Brand purple — what the user explicitly asked for as the primary CTA color.
    val Accent = Color(0xFF9146FF)
    val AccentDim = Color(0xFF6F2CD9)
    val AccentSoft = Color(0x339146FF)

    val Twitch = Color(0xFF9146FF)
    val Success = Color(0xFF4ADE80)

    val PurpleGradient = Brush.linearGradient(
        colors = listOf(Accent, AccentDim)
    )
}
