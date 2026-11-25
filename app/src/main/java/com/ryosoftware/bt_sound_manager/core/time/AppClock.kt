package com.ryosoftware.bt_sound_manager.core.time

import android.os.SystemClock
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

interface AppClock {
    fun nowMillis(): Long
    fun elapsedRealtime(): Long
    fun zoneId(): ZoneId
}

@Singleton
class SystemAppClock @Inject constructor() : AppClock {
    override fun nowMillis(): Long = System.currentTimeMillis()
    override fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()
    override fun zoneId(): ZoneId = ZoneId.systemDefault()
}
