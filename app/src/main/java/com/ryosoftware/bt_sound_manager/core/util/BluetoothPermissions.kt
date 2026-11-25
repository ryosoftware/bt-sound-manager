package com.ryosoftware.bt_sound_manager.core.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object BluetoothPermissions {

    fun requiredPermissions(): List<String> = if (Build.VERSION.SDK_INT >= 31) {
        listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
        )
    } else {
        emptyList()
    }

    fun missingPermissions(context: Context): List<String> =
        requiredPermissions().filterNot { isGranted(context, it) }

    fun isGranted(context: Context): Boolean =
        missingPermissions(context).isEmpty()

    private fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            permission,
        ) == PackageManager.PERMISSION_GRANTED
}
