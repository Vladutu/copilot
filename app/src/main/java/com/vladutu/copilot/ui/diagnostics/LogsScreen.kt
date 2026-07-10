package com.vladutu.copilot.ui.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.vladutu.copilot.diagnostics.DiagnosticLog
import com.vladutu.copilot.diagnostics.LogUploader
import com.vladutu.copilot.ui.ScreenHeader
import com.vladutu.copilot.ui.qrBitmap
import kotlinx.coroutines.launch

@Composable
fun LogsScreen(uploader: LogUploader, onBack: () -> Unit) {
    var content by remember { mutableStateOf(DiagnosticLog.read()) }
    var uploading by remember { mutableStateOf(false) }
    var sharedUrl by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader(title = "Diagnostic log", onBack = onBack)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { content = DiagnosticLog.read() }) { Text("Refresh") }
            OutlinedButton(onClick = {
                copyToClipboard(context, content)
                Toast.makeText(context, "Log copied", Toast.LENGTH_SHORT).show()
            }) { Text("Copy") }
            OutlinedButton(
                enabled = !uploading,
                onClick = {
                    uploading = true
                    scope.launch {
                        try {
                            sharedUrl = uploader.upload(DiagnosticLog.read())
                        } catch (e: Exception) {
                            Toast.makeText(context, "Upload failed: ${e.message}", Toast.LENGTH_LONG).show()
                        } finally {
                            uploading = false
                        }
                    }
                },
            ) { Text(if (uploading) "Uploading…" else "Share") }
            OutlinedButton(onClick = {
                DiagnosticLog.clear()
                content = DiagnosticLog.read()
            }) { Text("Clear") }
        }
        Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Text(
                text = content,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 14.sp,
            )
        }
    }

    sharedUrl?.let { url ->
        ShareDialog(url = url, onDismiss = { sharedUrl = null })
    }
}

/**
 * The upload result: a QR of the log's URL for phone-camera scanning, with the URL spelled out
 * as the fallback for anyone who'd rather type it.
 *
 * Custom [Dialog] rather than AlertDialog: the QR must be sized from the SHORTER screen edge
 * (a width-sized square is taller than the whole car screen and swallows the title and link),
 * and on the wide-short landscape screen the texts go beside the QR, not under it.
 */
@Composable
private fun ShareDialog(url: String, onDismiss: () -> Unit) {
    val qr = remember(url) { qrBitmap(url, 600) }
    val config = LocalConfiguration.current
    val landscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
    val qrSide = (minOf(config.screenWidthDp, config.screenHeightDp) * 0.55f).dp

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            if (landscape) {
                Row(
                    modifier = Modifier.padding(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    QrImage(qr, qrSide)
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ShareDialogTexts(url)
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.align(Alignment.End),
                        ) { Text("Close") }
                    }
                }
            } else {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    QrImage(qr, qrSide)
                    ShareDialogTexts(url)
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.End),
                    ) { Text("Close") }
                }
            }
        }
    }
}

@Composable
private fun QrImage(qr: Bitmap, side: Dp) {
    Image(
        bitmap = qr.asImageBitmap(),
        contentDescription = "QR code of the log link",
        modifier = Modifier.size(side),
    )
}

@Composable
private fun ShareDialogTexts(url: String) {
    Text(
        text = "Scan with your phone",
        style = MaterialTheme.typography.headlineSmall,
    )
    Text(
        text = url,
        fontFamily = FontFamily.Monospace,
        fontSize = 16.sp,
    )
    Text(
        text = "Link expires in ${LogUploader.RETENTION.removeSuffix("h")} hours",
        style = MaterialTheme.typography.bodySmall,
    )
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Copilot diagnostic log", text))
}
