package com.vladutu.copilot.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vladutu.copilot.R
import com.vladutu.copilot.launch.AppLauncher
import com.vladutu.copilot.nowplaying.NowPlaying
import com.vladutu.copilot.service.UiState
import com.vladutu.copilot.ui.KnobPagedGrid
import com.vladutu.copilot.ui.MTricolor
import com.vladutu.copilot.ui.MediaRowTile
import com.vladutu.copilot.ui.NowPlayingStrip
import com.vladutu.copilot.ui.TrailingStop
import com.vladutu.copilot.ui.theme.LocalPageSizes
import com.vladutu.copilot.ui.theme.LocalThemeSpec
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date

/** One of the four fixed Home entries, in knob reading order. */
private data class HomeTile(
    val labelRes: Int,
    val onClick: () -> Unit,
    val packageName: String? = null,
    val fallbackIcon: ImageVector? = null,
    val fallbackRes: Int? = null,
)

@Composable
fun HomeScreen(
    state: UiState,
    nowPlaying: NowPlaying?,
    isLiked: Boolean,
    likedCount: Int,
    onLike: () -> Unit,
    onOpenWaze: () -> Unit,
    onOpenMaps: () -> Unit,
    onOpenDestinations: () -> Unit,
    onOpenMusic: () -> Unit,
    onOpenStatus: () -> Unit,
    onOpenSettings: () -> Unit,
    onBackFromHome: () -> Unit,
) {
    BackHandler(onBack = onBackFromHome)

    val songPlaying = nowPlaying != null
    val heartFocus = remember { FocusRequester() }

    // Four fixed tiles: Waze, Maps, Places, Music. The Like heart is the trailing knob
    // stop while a song plays — LAST overall, so default focus (Waze) never lands on it
    // and a left twist from Waze never reaches it.
    val tiles = listOf(
        HomeTile(R.string.home_waze, onOpenWaze, packageName = AppLauncher.WAZE_PKG, fallbackRes = R.drawable.ic_map_pin),
        HomeTile(R.string.home_maps, onOpenMaps, packageName = AppLauncher.MAPS_PKG, fallbackRes = R.drawable.ic_map_pin),
        HomeTile(R.string.home_destinations, onOpenDestinations, fallbackIcon = Icons.Filled.Place),
        HomeTile(R.string.home_music, onOpenMusic, fallbackIcon = Icons.Filled.LibraryMusic),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            // bottom = 0: the now-playing strip is always the last child and sits flush
            // against the screen edge.
            .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 0.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TopBar(state = state, onOpenStatus = onOpenStatus, onOpenSettings = onOpenSettings)

        // Rail (redesign-spec §3a) on the shared knob grid, paged at the menu size.
        // The now-playing strip rides in the grid's bottom slot so the heart — Home-only,
        // rendered by the strip's likeControl, focused as the grid's trailing stop —
        // stays inside the grid's knob key-handler scope. Home keeps ownership of the
        // heart's FocusRequester.
        KnobPagedGrid(
            items = tiles,
            resetKey = "home",
            pageSize = LocalPageSizes.current.menuTiles,
            perItemDots = true,
            trailingStop = if (songPlaying) TrailingStop(heartFocus, heartFilled = isLiked) else null,
            // Flush inside the screen's own 24dp padding, as before the grid.
            horizontalPadding = 0.dp,
            modifier = Modifier.weight(1f),
            bottom = {
                Box(modifier = Modifier.padding(top = 12.dp)) {
                    NowPlayingStrip(
                        nowPlaying = nowPlaying,
                        likeControl = if (songPlaying) {
                            { LikeHeart(isLiked = isLiked, likedCount = likedCount, heartFocus = heartFocus, onLike = onLike) }
                        } else {
                            null
                        },
                    )
                }
            },
        ) { tile, requesters ->
            MediaRowTile(
                modifier = Modifier.fillMaxSize(),
                focusRequester = requesters?.get(0),
                label = stringResource(tile.labelRes),
                onClick = tile.onClick,
                packageName = tile.packageName,
                fallbackIcon = tile.fallbackIcon,
                fallbackRes = tile.fallbackRes,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun TopBar(
    state: UiState,
    onOpenStatus: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(60.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Inner Row keeps the outer SpaceBetween at exactly two children, so on themes
        // without stripes (Default) the bar lays out identically to before.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LocalThemeSpec.current.accents.stripes?.let { MTricolor(it) }
            ConnectionStatus(state = state, onClick = onOpenStatus)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = currentTime(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Tap-only gear (see BackHomeButton): never a knob stop.
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.focusProperties { canFocus = false },
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.settings_title),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

/**
 * Home-only Like control that slots into the now-playing strip: the trailing knob stop
 * while a song plays — amber ring + liked-count pill badge. Home owns [heartFocus].
 */
@Composable
private fun LikeHeart(
    isLiked: Boolean,
    likedCount: Int,
    heartFocus: FocusRequester,
    onLike: () -> Unit,
) {
    val heartInteraction = remember { MutableInteractionSource() }
    val heartFocused by heartInteraction.collectIsFocusedAsState()
    val primary = MaterialTheme.colorScheme.primary

    Box(contentAlignment = Alignment.TopEnd) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .then(
                    if (heartFocused) {
                        Modifier.shadow(16.dp, CircleShape, spotColor = primary, ambientColor = primary)
                    } else {
                        Modifier
                    },
                )
                .clip(CircleShape)
                .border(
                    width = if (heartFocused) 4.dp else 2.dp,
                    color = primary,
                    shape = CircleShape,
                )
                .focusRequester(heartFocus)
                .clickable(
                    interactionSource = heartInteraction,
                    indication = LocalIndication.current,
                    onClick = onLike,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = stringResource(R.string.like_song),
                tint = primary,
                modifier = Modifier.size(30.dp),
            )
        }
        // Liked-songs count badge (pill so 2–3 digits fit).
        Box(
            modifier = Modifier
                .heightIn(min = 20.dp)
                .widthIn(min = 20.dp)
                .clip(CircleShape)
                .background(primary)
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (likedCount > 99) "99+" else likedCount.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

/** Ticking wall-clock HH:mm for the top bar (redesign-spec §3a). */
@Composable
private fun currentTime(): String {
    val locale = LocalConfiguration.current.locales[0]
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(10_000)
        }
    }
    val formatter = remember(locale) { SimpleDateFormat("HH:mm", locale) }
    return remember(now, formatter) { formatter.format(Date(now)) }
}
