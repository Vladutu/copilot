package com.vladutu.copilot.ui.discover

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * One Discover category. Visual sibling of SavedTile/PlaylistTile: same card,
 * the Discover compass as artwork and a bottom title — plus a round ▶ button overlaid on the
 * right edge that instantly starts a radio mix. The card opens Browse results
 * (long-press deletes). Card and ▶ are separate knob stops — [nameFocus] then
 * [playFocus], matching KnobPagedGrid's item-major stop order.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CategoryTile(
    keyword: String,
    busy: Boolean,
    nameFocus: FocusRequester?,
    playFocus: FocusRequester?,
    onBrowse: () -> Unit,
    onPlayMix: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        val nameInteraction = remember { MutableInteractionSource() }
        val nameFocused by nameInteraction.collectIsFocusedAsState()
        Card(
            modifier = Modifier
                .fillMaxSize()
                .let { if (nameFocus != null) it.focusRequester(nameFocus) else it }
                .combinedClickable(
                    interactionSource = nameInteraction,
                    indication = LocalIndication.current,
                    onClick = onBrowse,
                    onLongClick = onLongPress,
                ),
            shape = RoundedCornerShape(16.dp),
            border = focusBorder(nameFocused),
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                Image(
                    painter = rememberVectorPainter(Icons.Filled.Explore),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
                Text(
                    text = keyword,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 26.sp, lineHeight = 32.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // End padding keeps the title clear of the overlaid ▶ button.
                    modifier = Modifier.padding(top = 6.dp, end = 72.dp),
                )
            }
        }

        val playInteraction = remember { MutableInteractionSource() }
        val playFocused by playInteraction.collectIsFocusedAsState()
        Surface(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp)
                .size(56.dp)
                .let { if (playFocus != null) it.focusRequester(playFocus) else it }
                // Always enabled: disabling would make the node non-focusable and
                // release knob focus mid-launch (focus then strands on the back
                // arrow after returning from YT Music). Reentry while busy is
                // guarded in playMix instead.
                .clickable(
                    interactionSource = playInteraction,
                    indication = LocalIndication.current,
                    onClick = onPlayMix,
                ),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = focusBorder(playFocused),
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                } else {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Play $keyword mix",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun focusBorder(focused: Boolean): BorderStroke =
    if (focused) BorderStroke(4.dp, MaterialTheme.colorScheme.primary)
    else BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
