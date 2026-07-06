package com.vladutu.copilot.ui.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.vladutu.copilot.BuildConfig
import com.vladutu.copilot.R
import com.vladutu.copilot.settings.PairingUri
import com.vladutu.copilot.ui.ScreenHeader
import com.vladutu.copilot.ui.permissions.PermissionHelpers
import com.vladutu.copilot.ui.theme.AllThemes
import com.vladutu.copilot.ui.theme.PilotOk
import com.vladutu.copilot.ui.theme.TileAppearanceDefaults
import com.vladutu.copilot.ui.theme.themeById
import kotlin.math.roundToInt

/**
 * Settings, grouped into cards (see docs/design/img.png): Permissions, Display, General,
 * Tiles, Waze, Pairing — health checks first, everyday tweaks above the fold, and set-once
 * pairing (with its destructive regenerate) last. Diagnostics lives as the bug button
 * in the header, not as a setting.
 */
@Composable
fun SettingsScreen(
    themeId: String,
    onThemeChange: (String) -> Unit,
    autoStart: Boolean,
    onAutoStartChange: (Boolean) -> Unit,
    tileFontSize: Float,
    onTileFontSizeChange: (Float) -> Unit,
    tileBorderWidth: Float,
    onTileBorderWidthChange: (Float) -> Unit,
    tileFocusFill: Boolean,
    onTileFocusFillChange: (Boolean) -> Unit,
    wazeGoEnabled: Boolean,
    onWazeGoEnabledChange: (Boolean) -> Unit,
    wazeGoLabel: String,
    onWazeGoLabelChange: (String) -> Unit,
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
        ScreenHeader(
            title = stringResource(R.string.settings_title),
            onBack = onBack,
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    IconButton(onClick = onOpenLogs, modifier = Modifier.size(64.dp)) {
                        Icon(
                            imageVector = Icons.Filled.BugReport,
                            contentDescription = stringResource(R.string.settings_diagnostic_log),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            },
        )

        SettingsSection(title = stringResource(R.string.settings_permissions_label)) {
            PermissionRow(
                label = stringResource(R.string.settings_now_playing_access),
                granted = rememberPermissionGranted(PermissionHelpers::isNotificationAccessGranted),
                onEnable = { PermissionHelpers.openNotificationAccessSettings(ctx) },
            )
            // Android disables the accessibility service on force-stop/reinstall, so this
            // row is the first place to look when auto-return silently stops working.
            PermissionRow(
                label = stringResource(R.string.settings_auto_return),
                granted = rememberPermissionGranted(PermissionHelpers::isAccessibilityServiceEnabled),
                onEnable = { PermissionHelpers.openAccessibilitySettings(ctx) },
            )
        }

        SettingsSection(title = stringResource(R.string.settings_display_label)) {
            DropdownRow(
                label = stringResource(R.string.settings_theme_label),
                selectedLabel = themeById(themeId).label,
                options = AllThemes.map { it.id to it.label },
                onSelect = onThemeChange,
            )
        }

        SettingsSection(title = stringResource(R.string.settings_general_label)) {
            SwitchRow(
                label = stringResource(R.string.settings_autostart_label),
                checked = autoStart,
                onCheckedChange = onAutoStartChange,
            )
        }

        SettingsSection(title = stringResource(R.string.settings_tiles_label)) {
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
            SwitchRow(
                label = stringResource(R.string.settings_tile_focus_fill),
                checked = tileFocusFill,
                onCheckedChange = onTileFocusFillChange,
            )
        }

        SettingsSection(title = stringResource(R.string.settings_waze_label)) {
            SwitchRow(
                label = stringResource(R.string.settings_waze_go_toggle),
                checked = wazeGoEnabled,
                onCheckedChange = onWazeGoEnabledChange,
            )
            var labelDraft by remember(wazeGoLabel) { mutableStateOf(wazeGoLabel) }
            OutlinedTextField(
                value = labelDraft,
                onValueChange = {
                    labelDraft = it
                    onWazeGoLabelChange(it)
                },
                singleLine = true,
                enabled = wazeGoEnabled,
                label = { Text(stringResource(R.string.settings_waze_go_button_label)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        SettingsSection(title = stringResource(R.string.settings_pairing_label)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.settings_topic_label),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = topic?.let { "${it.take(16)}…" } ?: stringResource(R.string.settings_topic_none),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onCopyTopic, enabled = topic != null) {
                    Text(stringResource(R.string.settings_copy_topic))
                }
                OutlinedButton(onClick = { showQr = true }, enabled = topic != null) {
                    Text(stringResource(R.string.settings_show_qr))
                }
                // Destructive — breaks pairing with Pilot and Wingman, hence the red.
                OutlinedButton(
                    onClick = { confirmRegen = true },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                ) {
                    Text(stringResource(R.string.settings_regenerate))
                }
            }
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

/** A settings group: a bordered card with an all-caps muted title above its rows. */
@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.5.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            content()
        }
    }
}

/**
 * Re-checks a permission every time the screen resumes, so granting it in the system
 * settings and coming back flips the row to "Granted" without reopening the screen.
 */
@Composable
private fun rememberPermissionGranted(check: (Context) -> Boolean): Boolean {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(check(ctx)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = check(ctx)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return granted
}

/** Permission status row: green "✓ Granted" once granted, an Enable button until then. */
@Composable
private fun PermissionRow(label: String, granted: Boolean, onEnable: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (granted) {
            Text(
                text = stringResource(R.string.settings_permission_granted),
                style = MaterialTheme.typography.bodyLarge,
                color = PilotOk,
            )
        } else {
            Button(onClick = onEnable) {
                Text(stringResource(R.string.settings_permission_enable))
            }
        }
    }
}

/**
 * A label + current-value button that opens a dropdown of options. First dropdown in
 * the app — sized like the other rows so knob focus travels through it naturally.
 */
@Composable
private fun DropdownRow(
    label: String,
    selectedLabel: String,
    options: List<Pair<String, String>>, // id to display label
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f, fill = false),
        )
        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text(selectedLabel)
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (id, optionLabel) ->
                    DropdownMenuItem(
                        text = { Text(optionLabel, style = MaterialTheme.typography.bodyLarge) },
                        onClick = {
                            expanded = false
                            onSelect(id)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f, fill = false),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
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
