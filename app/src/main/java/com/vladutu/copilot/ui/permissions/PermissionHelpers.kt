package com.vladutu.copilot.ui.permissions

import android.os.Build

object PermissionHelpers {
    fun needsNotificationPermission(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
}
