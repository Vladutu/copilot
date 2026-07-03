package com.vladutu.copilot.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vladutu.copilot.R
import com.vladutu.copilot.nowplaying.NowPlaying

/**
 * Persistent now-playing footer shown at the bottom of every browsing screen. It is
 * ALWAYS present — even when nothing is playing it renders a placeholder — so the
 * layout never reflows as playback starts/stops and the strip is a stable anchor that
 * also soaks up the rail's spare vertical height (keeping tiles from looking hollow).
 *
 * The Like heart lives only on Home; that screen passes it in via [likeControl] and
 * keeps ownership of its knob focus. Every other screen leaves [likeControl] null, so
 * the strip has no focusable child and never interferes with their knob navigation.
 */
@Composable
fun NowPlayingStrip(
    nowPlaying: NowPlaying?,
    modifier: Modifier = Modifier,
    likeControl: (@Composable () -> Unit)? = null,
) {
    Column(modifier = modifier) {
        // Top divider (redesign-spec §3a).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outline),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Album-art slot: the music model carries no artwork, so a music-note glyph
            // stands in (per product decision 2026-07-03).
            Image(
                painter = painterResource(R.drawable.ic_music_note),
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(10.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                if (nowPlaying != null) {
                    Text(
                        text = nowPlaying.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val artist = nowPlaying.artist
                    if (!artist.isNullOrBlank()) {
                        Text(
                            text = artist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else {
                    Text(
                        text = stringResource(R.string.now_playing_idle),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            likeControl?.invoke()
        }
    }
}
