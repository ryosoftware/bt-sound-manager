package com.ryosoftware.bt_sound_manager.presentation

import com.ryosoftware.bt_sound_manager.MainDispatcherRule
import com.ryosoftware.bt_sound_manager.domain.model.AppSettings
import com.ryosoftware.bt_sound_manager.domain.model.AutomationSettings
import com.ryosoftware.bt_sound_manager.domain.repository.BluetoothRepository
import com.ryosoftware.bt_sound_manager.domain.repository.DeviceRepository
import com.ryosoftware.bt_sound_manager.domain.repository.SettingsRepository
import com.ryosoftware.bt_sound_manager.domain.usecase.RefreshDeviceNames
import com.ryosoftware.bt_sound_manager.presentation.devices.DeviceSelectionEvent
import com.ryosoftware.bt_sound_manager.presentation.devices.DeviceSelectionViewModel
import com.ryosoftware.bt_sound_manager.service.AutomationCoordinator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DeviceSelectionViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val stored = AppSettings(
        automation = AutomationSettings(selectedDeviceAddresses = setOf("AA:BB")),
    )
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val bluetoothRepository = mockk<BluetoothRepository>(relaxed = true) {
        every { observeConnectedDevices() } returns MutableStateFlow(emptyList())
    }
    private val deviceRepository = mockk<DeviceRepository>(relaxed = true) {
        every { observeDeviceNames() } returns flowOf(emptyMap())
    }
    private val refreshDeviceNames = mockk<RefreshDeviceNames> {
        coEvery { this@mockk.invoke() } returns emptyList()
    }
    private val coordinator = mockk<AutomationCoordinator>(relaxed = true)

    private fun viewModel() = DeviceSelectionViewModel(
        settingsRepository,
        bluetoothRepository,
        deviceRepository,
        refreshDeviceNames,
        coordinator,
    )

    @Test
    fun leavingBeforeSelectionIsLoadedDoesNotOverwriteIt() = runTest {
        val pending = CompletableDeferred<AppSettings>()
        coEvery { settingsRepository.snapshot() } coAnswers { pending.await() }
        val viewModel = viewModel()

        viewModel.save()

        assertEquals(DeviceSelectionEvent.Saved, viewModel.events.first())
        coVerify(exactly = 0) { settingsRepository.updateAutomation(any()) }
    }

    @Test
    fun refreshKeepsUnsavedSelection() = runTest {
        coEvery { settingsRepository.snapshot() } returns stored
        val transform = slot<(AutomationSettings) -> AutomationSettings>()
        coEvery { settingsRepository.updateAutomation(capture(transform)) } returns Unit
        val viewModel = viewModel()

        viewModel.toggleDevice("CC:DD")
        viewModel.refresh()
        viewModel.save()

        assertEquals(DeviceSelectionEvent.Saved, viewModel.events.first())
        assertEquals(
            setOf("AA:BB", "CC:DD"),
            transform.captured(stored.automation).selectedDeviceAddresses,
        )
    }
}
