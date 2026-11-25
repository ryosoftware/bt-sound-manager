package com.ryosoftware.bt_sound_manager.core.util

import java.util.Locale

fun normalizeAddress(address: String?): String? =
    address?.trim()?.takeIf { it.isNotEmpty() }?.uppercase(Locale.ROOT)

fun displayDeviceName(storedName: String?, eventName: String?, address: String?): String =
    storedName?.takeIf { it.isNotBlank() }
        ?: eventName?.takeIf { it.isNotBlank() }
        ?: address?.takeIf { it.isNotBlank() }
        ?: ""
