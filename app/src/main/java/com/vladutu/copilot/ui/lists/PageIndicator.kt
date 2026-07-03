package com.vladutu.copilot.ui.lists

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The shared bottom dot strip (redesign-spec §2c). The current stop renders as a short
 * amber pill; the rest are muted outline dots. On Home the last stop can render as a
 * heart glyph (the Like stop) via [heartAtLast].
 */
@Composable
fun DotStrip(
    count: Int,
    current: Int,
    modifier: Modifier = Modifier,
    heartAtLast: Boolean = false,
    heartFilled: Boolean = false,
) {
    if (count <= 0) return
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { idx ->
            val isCurrent = idx == current
            val color = if (isCurrent) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline
            }
            when {
                heartAtLast && idx == count - 1 -> Icon(
                    imageVector = if (heartFilled) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(16.dp),
                )
                isCurrent -> Box(
                    modifier = Modifier
                        .width(24.dp)
                        .height(10.dp)
                        .background(color, RoundedCornerShape(5.dp)),
                )
                else -> Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
            }
        }
    }
}

/**
 * Page-position variant used by the paged rail ([KnobPagedGrid][com.vladutu.copilot.ui.KnobPagedGrid]).
 * Hidden when there is only a single page.
 */
@Composable
fun PageIndicator(pageCount: Int, currentPage: Int, modifier: Modifier = Modifier) {
    if (pageCount <= 1) return
    DotStrip(count = pageCount, current = currentPage, modifier = modifier)
}
