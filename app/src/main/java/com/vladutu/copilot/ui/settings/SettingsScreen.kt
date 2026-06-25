package com.vladutu.copilot.ui.settings

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.vladutu.copilot.R
import com.vladutu.copilot.settings.PairingUri
import com.vladutu.copilot.ui.ScreenHeader
import com.vladutu.copilot.ui.permissions.PermissionHelpers
import com.vladutu.copilot.ui.theme.TileAppearanceDefaults
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    autoStart: Boolean,
    onAutoStartChange: (Boolean) -> Unit,
    tileFontSize: Float,
    onTileFontSizeChange: (Float) -> Unit,
    tileBorderWidth: Float,
    onTileBorderWidthChange: (Float) -> Unit,
    topic: String?,
    onCopyTopic: () -> Unit,
    onRegenerate: () -> Unit,
    onOpenLogs: () -> Unit,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    var showQr by remember { mutableStateOf(false) }
    var confirmRegen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        ScreenHeader(title = stringResource(R.string.settings_title), onBack = onBack)

        // Auto-start toggle row.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.settings_autostart_label),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f, fill = false),
            )
            Switch(checked = autoStart, onCheckedChange = onAutoStartChange)
        }

        // Tile appearance section.
        Text(
            text = stringResource(R.string.settings_tiles_label),
            style = MaterialTheme.typography.titleMedium,
        )
        SliderRow(
            label = stringResource(R.string.settings_tile_font_size),
            value = tileFontSize,
            valueRange = TileAppearanceDefaults.FONT_SIZE_MIN..TileAppearanceDefaults.FONT_SIZE_MAX,
            onValueChange = onTileFontSizeChange,
        )
        SliderRow(
            label = stringResource(R.string.settings_tile_border_width),
            value = tileBorderWidth,
            valueRange = TileAppearanceDefaults.BORDER_WIDTH_MIN..TileAppearanceDefaults.BORDER_WIDTH_MAX,
            onValueChange = onTileBorderWidthChange,
        )

        // Pairing section.
        Text(
            text = stringResource(R.string.settings_pairing_label),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = topic?.let { "${it.take(16)}…" } ?: stringResource(R.string.settings_topic_none),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onCopyTopic, enabled = topic != null) {
                Text(stringResource(R.string.settings_copy_topic))
            }
            OutlinedButton(onClick = { showQr = true }, enabled = topic != null) {
                Text(stringResource(R.string.settings_show_qr))
            }
        }
        OutlinedButton(onClick = { confirmRegen = true }) {
            Text(stringResource(R.string.settings_regenerate))
        }

        // Now-playing (notification access) grant — shown only while access is missing.
        if (!PermissionHelpers.isNotificationAccessGranted(ctx)) {
            OutlinedButton(onClick = { PermissionHelpers.openNotificationAccessSettings(ctx) }) {
                Text(stringResource(R.string.grant_now_playing_access))
            }
        }

        // Accessibility (BACK interception / auto-return) grant — shown only while disabled.
        // Android disables this on force-stop/reinstall, so it must be re-grantable in-app.
        if (!PermissionHelpers.isAccessibilityServiceEnabled(ctx)) {
            OutlinedButton(onClick = { PermissionHelpers.openAccessibilitySettings(ctx) }) {
                Text(stringResource(R.string.grant_accessibility))
            }
        }

        OutlinedButton(onClick = onOpenLogs) {
            Text(stringResource(R.string.settings_diagnostic_log))
        }
    }

    val qr = if (topic != null) remember(topic) { qrBitmap(PairingUri.forTopic(topic), 600) } else null

    if (showQr && qr != null) {
        AlertDialog(
            onDismissRequest = { showQr = false },
            confirmButton = {
                TextButton(onClick = { showQr = false }) {
                    Text(stringResource(R.string.settings_qr_close))
                }
            },
            title = { Text(stringResource(R.string.settings_qr_title)) },
            text = {
                Image(
                    bitmap = qr.asImageBitmap(),
                    contentDescription = stringResource(R.string.settings_qr_title),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                )
            },
        )
    }

    if (confirmRegen) {
        AlertDialog(
            onDismissRequest = { confirmRegen = false },
            confirmButton = {
                TextButton(onClick = {
                    confirmRegen = false
                    onRegenerate()
                }) {
                    Text(stringResource(R.string.settings_regenerate_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRegen = false }) {
                    Text(stringResource(R.string.settings_regenerate_cancel))
                }
            },
            title = { Text(stringResource(R.string.settings_regenerate_title)) },
            text = { Text(stringResource(R.string.settings_regenerate_message)) },
        )
    }
}

/**
 * A labeled slider that snaps to whole units and shows the current value next to its
 * label. Used for the tile font size (sp) and highlighted border thickness (dp).
 */
@Composable
private fun SliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    fun step(delta: Float) =
        onValueChange((value + delta).coerceIn(valueRange.start, valueRange.endInclusive))

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = value.roundToInt().toString(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // −/+ buttons flank the slider: the carbox touchscreen lags, so dragging the
        // thumb precisely is fiddly — the buttons give a reliable one-step nudge.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = { step(-1f) },
                enabled = value > valueRange.start,
                modifier = Modifier.size(56.dp),
                contentPadding = PaddingValues(0.dp),
            ) {
                Text("−", style = MaterialTheme.typography.headlineSmall)
            }
            Slider(
                value = value,
                onValueChange = { onValueChange(it.roundToInt().toFloat()) },
                valueRange = valueRange,
                steps = (valueRange.endInclusive - valueRange.start).roundToInt() - 1,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = { step(1f) },
                enabled = value < valueRange.endInclusive,
                modifier = Modifier.size(56.dp),
                contentPadding = PaddingValues(0.dp),
            ) {
                Text("+", style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}

/** Renders [content] (the pilot://pair URI) as a square QR [Bitmap] of [sizePx]. */
private fun qrBitmap(content: String, sizePx: Int): Bitmap {
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
    val w = matrix.width
    val h = matrix.height
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
    for (x in 0 until w) {
        for (y in 0 until h) {
            bmp.setPixel(x, y, if (matrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE)
        }
    }
    return bmp
}
