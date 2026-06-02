package com.vladutu.copilot.ui.home

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap

@Composable
fun BigAppButton(
    modifier: Modifier = Modifier,
    packageName: String,
    @DrawableRes fallbackRes: Int,
    label: String,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val icon = remember(packageName) { loadAppIcon(context.packageManager, packageName) }

    Surface(
        modifier = modifier
            .fillMaxHeight()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Image(bitmap = icon.asImageBitmap(), contentDescription = label,
                    modifier = Modifier.size(160.dp))
            } else {
                Image(painter = painterResource(fallbackRes), contentDescription = label,
                    modifier = Modifier.size(160.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(label, style = MaterialTheme.typography.titleLarge)
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
