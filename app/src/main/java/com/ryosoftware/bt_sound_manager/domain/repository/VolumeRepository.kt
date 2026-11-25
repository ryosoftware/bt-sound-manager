package com.ryosoftware.bt_sound_manager.domain.repository

interface VolumeRepository {
    fun currentMediaVolume(): Int

    fun setMediaVolume(volume: Int)
}
