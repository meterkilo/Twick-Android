package com.example.chatterinomobile.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chatterinomobile.ui.theme.Twick

// ─── Brand mark ──────────────────────────────────────────────────────────────

/**
 * Purple-gradient square with the app glyph (two white bars). Shared between
 * SplashScreen (84dp) and WelcomeScreen (48dp).
 */
@Composable
internal fun BrandMark(
    size: Dp,
    cornerRadius: Dp,
    glyphSize: Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(Twick.PurpleGradient),
        contentAlignment = Alignment.Center
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(glyphSize / 8)) {
            Box(
                modifier = Modifier
                    .width(glyphSize / 4)
                    .height(glyphSize)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White)
            )
            Box(
                modifier = Modifier
                    .width(glyphSize / 2.5f)
                    .height(glyphSize)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.92f))
            )
        }
    }
}

// ─── App bar ─────────────────────────────────────────────────────────────────

/** Back-arrow bar used on Connect and Sync screens. Title is optional. */
@Composable
internal fun OnboardingAppBar(
    onBack: () -> Unit,
    title: String = "",
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .padding(top = 56.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Twick.Ink,
                modifier = Modifier.size(20.dp)
            )
        }

        if (title.isNotEmpty()) {
            Text(
                text = title,
                color = Twick.Ink,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

// ─── Buttons ─────────────────────────────────────────────────────────────────

/**
 * Inverted (white-on-bg) primary CTA — used on Welcome and EmoteSync screens
 * where we don't want to compete with the Twitch brand colour.
 */
@Composable
internal fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Twick.Ink)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Twick.Bg,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Purple-gradient CTA — exclusively for Twitch OAuth actions so the brand
 * colour is reserved for that single entry point.
 */
@Composable
internal fun TwitchPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Twick.PurpleGradient)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TwitchLogo(size = 20.dp)
            Text(
                text = text,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
internal fun SecondaryTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Twick.Ink2,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(contentPadding)
        )
    }
}
