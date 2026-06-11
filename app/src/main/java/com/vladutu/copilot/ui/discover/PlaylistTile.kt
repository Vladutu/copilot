package com.vladutu.copilot.ui.discover

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vladutu.copilot.R
import com.vladutu.copilot.discover.FoundPlaylist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * One Browse result. Visual sibling of SavedTile, but the artwork comes straight
 * from the search result's thumbnail URL (no Coil in this app, and no ArtworkCache
 * entry — Discover results are ephemeral and never saved).
 */
@Composable
fun PlaylistTile(
    playlist: FoundPlaylist,
    okHttp: OkHttpClient,
    focus: FocusRequester?,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var bitmap by remember(playlist.playlistId) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(playlist.playlistId) {
        val url = playlist.thumbnailUrl ?: return@LaunchedEffect
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                okHttp.newCall(okhttp3.Request.Builder().url(url).build()).execute().use { resp ->
                    if (!resp.isSuccessful) return@runCatching null
                    resp.body?.bytes()?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                }
            }.getOrNull()
        }?.asImageBitmap()
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    Card(
        modifier = modifier
            .fillMaxSize()
            .let { if (focus != null) it.focusRequester(focus) else it }
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onTap,
            ),
        shape = RoundedCornerShape(16.dp),
        border = focusBorder(isFocused),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                val bm = bitmap
                if (bm != null) {
                    Image(
                        bitmap = bm,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.ic_music_note),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Text(
                text = playlist.title,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 26.sp, lineHeight = 32.sp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
