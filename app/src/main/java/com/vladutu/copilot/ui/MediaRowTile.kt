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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import com.vladutu.copilot.ui.theme.LocalTileAppearance

/**
 * The one inline tile every grid uses: a square left visual (thumbnail / icon /
 * app icon / drawable) and a big label on the right. Optional [trailing] slot is
 * overlaid at the right edge as an independent focusable (Discover's ▶ mix button).
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
    val border = if (isFocused) {
        BorderStroke(appearance.focusBorderWidth, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    }

    Box(modifier = modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    onClick = onClick,
                    onLongClick = onLongPress,
                ),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            border = border,
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                LeftVisual(
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
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = if (trailing != null) 64.dp else 0.dp),
                )
            }
        }
        if (trailing != null) {
            Box(
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
            ) {
                trailing()
            }
        }
    }
}

@Composable
private fun LeftVisual(
    busy: Boolean,
    thumbnail: ImageBitmap?,
    appIcon: Bitmap?,
    fallbackIcon: ImageVector?,
    iconTint: Color?,
    @DrawableRes fallbackRes: Int?,
    label: String,
) {
    Box(modifier = Modifier.size(80.dp), contentAlignment = Alignment.Center) {
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
