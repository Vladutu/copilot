package com.vladutu.copilot.update

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ApkInstallerTest {

    @Test fun `opens the system installer on a content uri for the apk`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val apk = File(context.cacheDir, "updates/copilot-0.31.0.apk").apply {
            parentFile!!.mkdirs()
            writeText("apk")
        }

        ApkInstaller(context).install(apk)

        val intent = shadowOf(context as android.app.Application).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("application/vnd.android.package-archive", intent.type)
        assertEquals("content", intent.data!!.scheme)
        assertEquals("${context.packageName}.fileprovider", intent.data!!.authority)
        assertTrue(intent.data!!.path!!.endsWith("copilot-0.31.0.apk"))
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }
}
