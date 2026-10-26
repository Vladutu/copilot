package com.vladutu.copilot.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Hands a downloaded APK to Android's package installer. The file lives in our cache, so it is
 * shared through the manifest's FileProvider (`<package>.fileprovider`, paths in
 * res/xml/file_paths.xml); a plain file:// URI is rejected on API 24+.
 *
 * First use prompts the one-time "allow installs from this source" toggle (REQUEST_INSTALL_PACKAGES).
 * The same signing key as the installed build is required, which release.sh guarantees.
 */
class ApkInstaller(private val context: Context) {

    fun install(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private companion object {
        const val APK_MIME = "application/vnd.android.package-archive"
    }
}
