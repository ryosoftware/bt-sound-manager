package com.ryosoftware.bt_sound_manager.domain

import com.ryosoftware.bt_sound_manager.domain.model.BluetoothDeviceInfo
import com.ryosoftware.bt_sound_manager.domain.usecase.mergeBluetoothDevices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothDeviceMergeTest {

    @Test
    fun connectedDeviceOverridesBondedAndPreservesBondState() {
        val bonded = device("AA:BB", "Headphones", connected = false, bonded = true)
        val connected = device("AA:BB", "Headphones", connected = true, bonded = true)

        val merged = mergeBluetoothDevices(listOf(bonded), listOf(connected))

        assertEquals(1, merged.size)
        assertTrue(merged.first().isConnected)
        assertTrue(merged.first().isBonded)
    }

    @Test
    fun connectedOnlyDeviceIsIncluded() {
        val connected = device("CC:DD", "Speaker", connected = true, bonded = false)

        val merged = mergeBluetoothDevices(emptyList(), listOf(connected))

        assertEquals(1, merged.size)
        assertEquals("CC:DD", merged.first().address)
        assertTrue(merged.first().isConnected)
    }

    @Test
    fun addressFallbackDoesNotReplaceStoredName() {
        val bonded = device("AA:BB", "Headphones", connected = false, bonded = true)
        val connected = device("AA:BB", "AA:BB", connected = true, bonded = true)

        val merged = mergeBluetoothDevices(listOf(bonded), listOf(connected))

        assertEquals("Headphones", merged.first().name)
    }

    @Test
    fun devicesAreNotDuplicated() {
        val bonded = listOf(
            device("AA:BB", "Headphones", connected = false, bonded = true),
            device("CC:DD", "Speaker", connected = false, bonded = true),
        )
        val connected = listOf(
            device("AA:BB", "Headphones", connected = true, bonded = true),
            device("EE:FF", "Watch", connected = true, bonded = false),
        )

        val merged = mergeBluetoothDevices(bonded, connected)

        assertEquals(3, merged.size)
        assertEquals(3, merged.map { it.address }.distinct().size)
    }

    private fun device(
        address: String,
        name: String,
        connected: Boolean,
        bonded: Boolean,
    ) = BluetoothDeviceInfo(
        address = address,
        name = name,
        isConnected = connected,
        isBonded = bonded,
    )
}
