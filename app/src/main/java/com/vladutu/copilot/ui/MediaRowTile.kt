package com.vladutu.copilot.ui

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import com.vladutu.copilot.ui.theme.LocalTileAppearance

// Big content visual (real artwork / app icon) fills a square; a plain Material glyph
// stays small. Sized for glancing while driving (see redesign-spec §2a).
private val ContentVisualSize = 170.dp
private val IconVisualSize = 60.dp

/**
 * The one tile every rail uses: a **vertical** card — a visual on top (real artwork,
 * app icon, or a Material/drawable glyph) and a big centered label below. Optional
 * [trailing] slot is pinned full-width at the tile bottom as an independent focusable
 * (Discover's ▶ Play mix button — see redesign-spec §3d).
 *
 * Pure presentation: callers that need a network/disk image load it themselves and
 * pass the ready [thumbnail]. Knob focus: callers attach the FocusRequester they were
 * handed via [focusRequester]; when [trailing] is present it brings its own focus, so
 * the card is knob stop 0 and the trailing element is stop 1.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaRowTile(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    thumbnail: ImageBitmap? = null,
    fallbackIcon: ImageVector? = null,
    iconTint: Color? = null,
    @DrawableRes fallbackRes: Int? = null,
    packageName: String? = null,
    busy: Boolean = false,
    maxLines: Int = 2,
    trailing: (@Composable () -> Unit)? = null,
) {
    val context = LocalContext.current
    val appIcon = remember(packageName) {
        if (packageName != null) loadAppIcon(context.packageManager, packageName) else null
    }
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val appearance = LocalTileAppearance.current
    val shape = MaterialTheme.shapes.large
    val primary = MaterialTheme.colorScheme.primary
    val border = if (isFocused) {
        BorderStroke(appearance.focusBorderWidth, primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    }

    Box(modifier = modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                // Soft amber glow when focused (redesign-spec §2a).
                .then(
                    if (isFocused) {
                        Modifier.shadow(16.dp, shape, spotColor = primary, ambientColor = primary)
                    } else {
                        Modifier
                    },
                )
                .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    onClick = onClick,
                    onLongClick = onLongPress,
                ),
            shape = shape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            border = border,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // Reserve room at the bottom for the pinned trailing slot so the
                    // centered content never sits under it.
                    .padding(
                        start = 20.dp,
                        end = 20.dp,
                        top = 20.dp,
                        bottom = if (trailing != null) 76.dp else 20.dp,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            ) {
                TileVisual(
                    busy = busy,
                    thumbnail = thumbnail,
                    appIcon = appIcon,
                    fallbackIcon = fallbackIcon,
                    iconTint = iconTint,
                    fallbackRes = fallbackRes,
                    label = label,
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = appearance.fontSize,
                        lineHeight = appearance.lineHeight,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = maxLines,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
        if (trailing != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            ) {
                trailing()
            }
        }
    }
}

@Composable
private fun TileVisual(
    busy: Boolean,
    thumbnail: ImageBitmap?,
    appIcon: Bitmap?,
    fallbackIcon: ImageVector?,
    iconTint: Color?,
    @DrawableRes fallbackRes: Int?,
    label: String,
) {
    // Real artwork / app-icon / drawable fallback are "content" → big square.
    // A plain Material vector glyph stays small.
    val isContent = thumbnail != null || appIcon != null || fallbackRes != null
    val size = if (isContent) ContentVisualSize else IconVisualSize
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        when {
            busy -> CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary,
            )
            thumbnail != null -> Image(
                bitmap = thumbnail,
                contentDescription = label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium),
            )
            appIcon != null -> Image(
                bitmap = appIcon.asImageBitmap(),
                contentDescription = label,
                modifier = Modifier.fillMaxSize(),
            )
            fallbackIcon != null -> Icon(
                imageVector = fallbackIcon,
                contentDescription = label,
                tint = iconTint ?: MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxSize(),
            )
            fallbackRes != null -> Image(
                painter = painterResource(fallbackRes),
                contentDescription = label,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private fun loadAppIcon(pm: PackageManager, packageName: String): Bitmap? {
    return try {
        pm.getApplicationIcon(packageName).toBitmap(192)
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }
}

private fun Drawable.toBitmap(sizePx: Int): Bitmap {
    if (this is BitmapDrawable && bitmap != null) return bitmap
    val bmp = createBitmap(sizePx, sizePx)
    val canvas = Canvas(bmp)
    setBounds(0, 0, sizePx, sizePx)
    draw(canvas)
    return bmp
}
