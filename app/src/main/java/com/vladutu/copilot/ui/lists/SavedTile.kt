package com.vladutu.copilot.ui.lists

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vladutu.copilot.R
import com.vladutu.copilot.history.Form
import com.vladutu.copilot.history.SavedItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SavedTile(
    item: SavedItem,
    artworkFile: File,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var bitmap by remember(item.id) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(item.id) {
        if (artworkFile.exists()) {
            val bmp = withContext(Dispatchers.IO) {
                runCatching { BitmapFactory.decodeFile(artworkFile.absolutePath) }.getOrNull()
            }
            bitmap = bmp?.asImageBitmap()
        }
    }
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    Card(
        modifier = modifier.combinedClickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            onClick = onTap,
            onLongClick = onLongPress,
        ),
        shape = RoundedCornerShape(16.dp),
        border = if (isFocused) BorderStroke(4.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
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
                        painter = painterResource(
                            id = if (item.form == Form.DESTINATION) R.drawable.ic_map_pin else R.drawable.ic_music_note
                        ),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Text(
                text = item.title ?: "Untitled · ${item.id.take(8)}",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
