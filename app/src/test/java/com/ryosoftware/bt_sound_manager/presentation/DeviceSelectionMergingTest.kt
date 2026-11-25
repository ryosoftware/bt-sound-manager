package com.ryosoftware.bt_sound_manager.presentation

import com.ryosoftware.bt_sound_manager.domain.model.BluetoothDeviceInfo
import com.ryosoftware.bt_sound_manager.presentation.devices.mergeSelectableDevices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceSelectionMergingTest {

    private val address = "AA:BB:CC:DD:EE:FF"

    @Test
    fun selectedDeviceWithoutBluetoothOrCachedNameAppearsWithAddress() {
        val devices = mergeSelectableDevices(
            bluetoothDevices = emptyList(),
            cachedNames = emptyMap(),
            selectedAddresses = setOf(address),
        )

        val device = devices.single()
        assertEquals(address, device.address)
        assertEquals(address, device.name)
        assertFalse(device.connected)
        assertFalse(device.bonded)
    }

    @Test
    fun cachedNameTakesPrecedenceOverBluetoothName() {
        val devices = mergeSelectableDevices(
            bluetoothDevices = listOf(
                BluetoothDeviceInfo(
                    address = address,
                    name = "Bluetooth name",
                    isConnected = true,
                    isBonded = true,
                ),
            ),
            cachedNames = mapOf(address to "Cached name"),
            selectedAddresses = setOf(address),
        )

        val device = devices.single()
        assertEquals("Cached name", device.name)
        assertTrue(device.connected)
        assertTrue(device.bonded)
    }

    @Test
    fun bluetoothNameUsedWhenNoCachedName() {
        val devices = mergeSelectableDevices(
            bluetoothDevices = listOf(
                BluetoothDeviceInfo(
                    address = address,
                    name = "Bluetooth name",
                    isConnected = false,
                    isBonded = true,
                ),
            ),
            cachedNames = emptyMap(),
            selectedAddresses = emptySet(),
        )

        assertEquals("Bluetooth name", devices.single().name)
    }

    @Test
    fun selectedDeviceAlreadyListedIsNotDuplicated() {
        val devices = mergeSelectableDevices(
            bluetoothDevices = listOf(
                BluetoothDeviceInfo(
                    address = address,
                    name = "Bluetooth name",
                    isConnected = false,
                    isBonded = true,
                ),
            ),
            cachedNames = mapOf(address to "Cached name"),
            selectedAddresses = setOf(address),
        )

        assertEquals(1, devices.size)
        assertTrue(devices.single().bonded)
    }
}
