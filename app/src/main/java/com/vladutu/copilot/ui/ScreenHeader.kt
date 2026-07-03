package com.vladutu.copilot.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Standard header for every non-home screen: back button at the start edge,
// title centered on the screen (not next to the back button). Both elements
// live in the same 64dp Box so the title centers against the screen width
// regardless of the back button's tap area. Optional [trailing] sits flush right
// (used for the paged rail's "1–5 / 24" position count — redesign-spec §3b).
@Composable
fun ScreenHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Box(modifier = modifier.fillMaxWidth().height(64.dp)) {
        BackHomeButton(onBack = onBack, modifier = Modifier.align(Alignment.CenterStart))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.align(Alignment.Center),
        )
        if (trailing != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp),
            ) {
                trailing()
            }
        }
    }
}
