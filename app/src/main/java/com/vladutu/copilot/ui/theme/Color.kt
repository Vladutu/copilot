package com.vladutu.copilot.ui.theme

import androidx.compose.ui.graphics.Color

// Dark automotive-cockpit palette mirrored from Pilot's 4387a43. Dark-only —
// the carbox screen reads the same at noon and midnight, so no light variant.

val PilotBackground = Color(0xFF0E1116)       // near-black with a touch of blue
val PilotSurface = Color(0xFF161B22)          // cards / tiles sit on this
val PilotSurfaceVariant = Color(0xFF1E2530)   // status pill bg, etc.
val PilotOutline = Color(0xFF2A323D)          // 1dp tile borders, dividers

val PilotPrimary = Color(0xFFFFB020)          // warm amber — accent, focus
val PilotOnPrimary = Color(0xFF0E1116)

val PilotOnSurface = Color(0xFFE6EAF0)        // primary text
val PilotOnSurfaceVariant = Color(0xFF9AA4B2) // secondary text, muted

val PilotError = Color(0xFFE5484D)            // error states
val PilotOk = Color(0xFF4FCB66)               // healthy / connected
