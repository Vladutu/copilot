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

// BMW M cockpit palette (docs/img.png). Dark-only, like the Pilot palette above.

val BmwBackground = Color(0xFF050A14)         // near-black navy
val BmwSurface = Color(0xFF0D1626)            // card navy
val BmwSurfaceVariant = Color(0xFF12203A)     // status pill bg, etc.
val BmwOutline = Color(0xFF1E2D45)            // subtle blue-grey card border

val BmwPrimary = Color(0xFF4FA8E8)            // ice blue — accent, focus
val BmwOnPrimary = Color(0xFF050A14)

val BmwOnSurface = Color(0xFFEAF1F8)
val BmwOnSurfaceVariant = Color(0xFF8FA3BC)

// M tricolor stripes (logo/accent use only, not part of the color scheme)
val BmwStripeLightBlue = Color(0xFF00A0E0)
val BmwStripeDarkBlue = Color(0xFF1A3E8C)
val BmwStripeRed = Color(0xFFE30613)
