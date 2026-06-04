package com.vladutu.copilot.ui.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vladutu.copilot.diagnostics.DiagnosticLog
import com.vladutu.copilot.ui.ScreenHeader

@Composable
fun LogsScreen(onBack: () -> Unit) {
    var content by remember { mutableStateOf(DiagnosticLog.read()) }
    val context = LocalContext.current

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
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Copilot diagnostic log", text))
}
