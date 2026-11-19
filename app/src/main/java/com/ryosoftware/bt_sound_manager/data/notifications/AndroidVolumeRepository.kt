package com.ryosoftware.bt_sound_manager.data.notifications

import android.content.Context
import android.media.AudioManager
import com.ryosoftware.bt_sound_manager.domain.repository.VolumeRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidVolumeRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : VolumeRepository {

    private val audioManager: AudioManager? =
        context.getSystemService(AudioManager::class.java)

    override fun currentMediaVolume(): Int =
        audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: -1

    override fun setMediaVolume(volume: Int) {
        if (volume < 0) return
        try {
            audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
        } catch (_: SecurityException) {
            // The system can reject volume changes in restricted profiles.
        }
    }
}
