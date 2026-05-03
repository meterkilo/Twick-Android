package com.example.chatterinomobile.ui.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chatterinomobile.data.model.Channel
import com.example.chatterinomobile.ui.theme.Twick
import org.koin.androidx.compose.koinViewModel

@Composable
fun DiscoveryScreen(
    onJoinChannel: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiscoveryViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var activeTab by remember { mutableIntStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Twick.Bg)
    ) {
        DiscoveryTopBar(onRefresh = viewModel::refresh)
        DiscoveryTabs(activeTab = activeTab, onTabSelected = { activeTab = it })

        when {
            state.isLoading -> LoadingBody()
            state.error != null && state.followedLive.isEmpty() && state.recommendedStreams.isEmpty() ->
                ErrorBody(message = state.error!!, onRetry = viewModel::refresh)
            else -> when (activeTab) {
                0 -> ForYouBody(state = state, onJoinChannel = onJoinChannel)
                1 -> FollowingBody(state = state, onJoinChannel = onJoinChannel)
                2 -> BrowseBody(state = state, onJoinChannel = onJoinChannel)
                else -> ForYouBody(state = state, onJoinChannel = onJoinChannel)
            }
        }
    }
}

@Composable
private fun LoadingBody() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Twick.Accent, modifier = Modifier.size(40.dp))
    }
}

@Composable
private fun ErrorBody(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Couldn't load streams", color = Twick.Ink2, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(text = message, color = Twick.Ink3, fontSize = 12.sp)
        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Twick.S2)
                .clickable(onClick = onRetry)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = null, tint = Twick.Accent, modifier = Modifier.size(16.dp))
            Text(text = "Try again", color = Twick.Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ForYouBody(state: DiscoveryUiState, onJoinChannel: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        if (state.followedLive.isNotEmpty()) {
            SectionHeader(title = "Live now · following", action = "See all")
            LiveFollowingRow(channels = state.followedLive, onClick = onJoinChannel)
            Spacer(Modifier.height(8.dp))
        }

        if (state.recommendedStreams.isNotEmpty()) {
            SectionHeader(title = "Recommended")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                state.recommendedStreams.forEach { channel ->
                    StreamCard(channel = channel, onClick = { onJoinChannel(channel.login) })
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun FollowingBody(state: DiscoveryUiState, onJoinChannel: (String) -> Unit) {
    val liveLogins = state.followedLive.map { it.login }.toSet()

    if (state.followedLogins.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("You're not following anyone yet", color = Twick.Ink3, fontSize = 14.sp)
        }
        return
    }

    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {

        state.followedLive.forEach { channel ->
            FollowingRow(channel = channel, isLive = true, onClick = { onJoinChannel(channel.login) })
        }

        state.followedLogins
            .filter { it !in liveLogins }
            .take(30)
            .forEach { login ->
                val offlineChannel = Channel(id = "", login = login, displayName = login)
                FollowingRow(channel = offlineChannel, isLive = false, onClick = { onJoinChannel(login) })
            }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun BrowseBody(state: DiscoveryUiState, onJoinChannel: (String) -> Unit) {
    if (state.recommendedStreams.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No streams available", color = Twick.Ink3, fontSize = 14.sp)
        }
        return
    }


    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(8.dp))
        val chunked = (state.followedLive + state.recommendedStreams)
            .distinctBy { it.login }
            .chunked(2)
        chunked.forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                pair.forEach { channel ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(bottom = 12.dp)
                            .clickable { onJoinChannel(channel.login) }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(thumbnailGradient(channel.login))
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(4.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Twick.Live)
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "● ${formatViewers(channel.viewerCount)}",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = channel.displayName,
                            color = Twick.Ink,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (channel.gameName != null) {
                            Text(
                                text = channel.gameName,
                                color = Twick.Ink3,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun DiscoveryTopBar(onRefresh: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Twick.PurpleGradient),
                contentAlignment = Alignment.Center
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(14.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(Color.White)
                    )
                    Box(
                        modifier = Modifier
                            .width(6.dp)
                            .height(14.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(Color.White.copy(alpha = 0.92f))
                    )
                }
            }
            Text(
                text = "twick",
                color = Twick.Ink,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Row {
            TopBarIconButton(icon = Icons.Filled.Search, onClick = {})
            TopBarIconButton(icon = Icons.Filled.Notifications, onClick = {})
        }
    }
}

@Composable
private fun TopBarIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = Twick.Ink, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun DiscoveryTabs(activeTab: Int, onTabSelected: (Int) -> Unit) {
    val tabs = listOf("For you", "Following", "Browse")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        tabs.forEachIndexed { idx, label ->
            val isActive = idx == activeTab
            Column(
                modifier = Modifier
                    .padding(bottom = 10.dp)
                    .clickable { onTabSelected(idx) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = label,
                    color = if (isActive) Twick.Ink else Twick.Ink3,
                    fontSize = 14.sp,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .height(2.dp)
                        .width(if (isActive) 36.dp else 0.dp)
                        .background(if (isActive) Twick.Accent else Color.Transparent)
                )
            }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Twick.Hairline)
    )
}

@Composable
private fun SectionHeader(title: String, action: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, color = Twick.Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        if (action != null) {
            Text(
                text = action,
                color = Twick.Accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable {}
            )
        }
    }
}

@Composable
private fun LiveFollowingRow(channels: List<Channel>, onClick: (String) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        items(channels, key = { it.login }) { channel ->
            Column(
                modifier = Modifier
                    .width(64.dp)
                    .clickable { onClick(channel.login) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(Twick.Live)
                        .padding(2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AvatarCircle(name = channel.displayName, size = 56.dp)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = channel.displayName,
                    color = Twick.Ink2,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatViewers(channel.viewerCount),
                    color = Twick.Ink3,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun FollowingRow(channel: Channel, isLive: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box {
            AvatarCircle(name = channel.displayName, size = 44.dp)
            if (isLive) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Twick.Live)
                        .border(2.dp, Twick.Bg, CircleShape)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = channel.displayName,
                color = if (isLive) Twick.Ink else Twick.Ink3,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (isLive && channel.gameName != null) {
                Text(
                    text = channel.gameName,
                    color = Twick.Ink3,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else if (!isLive) {
                Text(text = "Offline", color = Twick.Ink4, fontSize = 11.sp)
            }
        }
        if (isLive) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatViewers(channel.viewerCount),
                    color = Twick.Live,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun StreamCard(channel: Channel, onClick: () -> Unit) {
    Column(modifier = Modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(thumbnailGradient(channel.login))
        ) {

            Row(
                modifier = Modifier
                    .padding(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Twick.Live)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "● LIVE",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = formatViewers(channel.viewerCount),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                )
            }


            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Twick.Twitch)
                )
                Text(text = "TWITCH", color = Twick.Ink, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AvatarCircle(name = channel.displayName, size = 32.dp)
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = channel.displayName.lowercase(),
                        color = Twick.Ink,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (channel.gameName != null) {
                        Text(
                            text = " · ${channel.gameName}",
                            color = Twick.Ink3,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }
                if (channel.title != null) {
                    Text(
                        text = channel.title,
                        color = Twick.Ink3,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = null,
                tint = Twick.Ink3,
                modifier = Modifier
                    .size(18.dp)
                    .clickable {}
            )
        }
    }
}

@Composable
private fun AvatarCircle(name: String, size: Dp) {
    val seed = name.hashCode()
    val hue = ((seed % 360) + 360) % 360
    val brush = Brush.linearGradient(
        colors = listOf(
            hsvColor(hue.toFloat(), 0.55f, 0.65f),
            hsvColor(((hue + 40) % 360).toFloat(), 0.55f, 0.40f)
        )
    )
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(brush)
            .border(2.dp, Twick.Bg, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name.take(1).uppercase(),
            color = Color.White,
            fontSize = (size.value * 0.4f).sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun thumbnailGradient(seed: String): Brush {
    val hue = ((seed.hashCode() % 360) + 360) % 360
    return Brush.linearGradient(
        colors = listOf(
            hsvColor(hue.toFloat(), 0.40f, 0.35f),
            hsvColor(hue.toFloat(), 0.30f, 0.18f)
        )
    )
}

private fun hsvColor(hue: Float, saturation: Float, value: Float): Color {
    val c = value * saturation
    val hh = hue / 60f
    val x = c * (1f - kotlin.math.abs((hh % 2f) - 1f))
    val (r1, g1, b1) = when {
        hh < 1f -> Triple(c, x, 0f)
        hh < 2f -> Triple(x, c, 0f)
        hh < 3f -> Triple(0f, c, x)
        hh < 4f -> Triple(0f, x, c)
        hh < 5f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val m = value - c
    return Color(r1 + m, g1 + m, b1 + m)
}

private fun formatViewers(count: Int): String = when {
    count >= 1_000_000 -> "${count / 1_000_000}.${(count % 1_000_000) / 100_000}M"
    count >= 1_000 -> "${count / 1_000}.${(count % 1_000) / 100}K"
    else -> count.toString()
}
