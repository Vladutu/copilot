package com.vladutu.copilot.ui.lists

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun PageIndicator(pageCount: Int, currentPage: Int, modifier: Modifier = Modifier) {
    if (pageCount <= 1) return
    Row(modifier = modifier) {
        repeat(pageCount) { idx ->
            val color = if (idx == currentPage) MaterialTheme.colorScheme.primary else Color.Gray
            Box(modifier = Modifier.padding(horizontal = 4.dp).size(10.dp).background(color, CircleShape))
        }
    }
}
