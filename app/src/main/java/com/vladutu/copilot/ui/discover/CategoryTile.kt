package com.vladutu.copilot.ui.discover

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * One Discover category as a split tile: the name zone opens the Browse results
 * (long-press deletes), the ▶ zone instantly starts a radio mix. Each zone is its
 * own knob stop — [nameFocus] then [playFocus], matching KnobPagedGrid's item-major
 * stop order.
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
    Row(modifier = modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val nameInteraction = remember { MutableInteractionSource() }
        val nameFocused by nameInteraction.collectIsFocusedAsState()
        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .let { if (nameFocus != null) it.focusRequester(nameFocus) else it }
                .combinedClickable(
                    interactionSource = nameInteraction,
                    indication = LocalIndication.current,
                    onClick = onBrowse,
                    onLongClick = onLongPress,
                ),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            border = focusBorder(nameFocused),
        ) {
            Box(
                modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = keyword,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 26.sp, lineHeight = 32.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        val playInteraction = remember { MutableInteractionSource() }
        val playFocused by playInteraction.collectIsFocusedAsState()
        Surface(
            modifier = Modifier
                .width(72.dp)
                .fillMaxHeight()
                .let { if (playFocus != null) it.focusRequester(playFocus) else it }
                .clickable(
                    interactionSource = playInteraction,
                    indication = LocalIndication.current,
                    enabled = !busy,
                    onClick = onPlayMix,
                ),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
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
                        modifier = Modifier.size(40.dp),
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
