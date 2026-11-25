package com.ryosoftware.bt_sound_manager.presentation

import com.ryosoftware.bt_sound_manager.MainDispatcherRule
import com.ryosoftware.bt_sound_manager.domain.model.AppSettings
import com.ryosoftware.bt_sound_manager.domain.repository.BluetoothRepository
import com.ryosoftware.bt_sound_manager.domain.repository.DeviceRepository
import com.ryosoftware.bt_sound_manager.domain.repository.InterruptModeRepository
import com.ryosoftware.bt_sound_manager.domain.repository.SettingsRepository
import com.ryosoftware.bt_sound_manager.presentation.automation.AutomationViewModel
import com.ryosoftware.bt_sound_manager.service.AutomationCoordinator
import com.ryosoftware.bt_sound_manager.service.MonitorServiceController
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AutomationViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val settingsRepository = mockk<SettingsRepository>()
    private val deviceRepository = mockk<DeviceRepository>()
    private val interruptModeRepository = mockk<InterruptModeRepository>()
    private val bluetoothRepository = mockk<BluetoothRepository>()
    private val automationCoordinator = mockk<AutomationCoordinator>(relaxed = true)
    private val serviceController = mockk<MonitorServiceController>(relaxed = true)

    @Before
    fun setUp() {
        every { settingsRepository.settings } returns flowOf(AppSettings())
        every { deviceRepository.observeDeviceNames() } returns flowOf(emptyMap())
        every { interruptModeRepository.canAccessPolicy() } returns true
        every { bluetoothRepository.hasPermissions() } returns true
        coEvery { settingsRepository.updateAutomation(any()) } returns Unit
        coEvery { settingsRepository.updateBackground(any()) } returns Unit
        coEvery { settingsRepository.snapshot() } returns AppSettings()
    }

    @Test
    fun forceModeSettersSuppressBlockedNotification() = runTest {
        val viewModel = viewModel()

        viewModel.setForceModeOnDevicesConnected(true)
        viewModel.setForceModeOnDevicesDisconnected(false)
        advanceUntilIdle()

        coVerify(exactly = 2) {
            automationCoordinator.apply(suppressBlockedNotification = true)
        }
    }

    @Test
    fun otherSettingsKeepBlockedNotification() = runTest {
        val viewModel = viewModel()

        viewModel.setRestoreMediaVolume(true)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            automationCoordinator.apply(suppressBlockedNotification = false)
        }
    }

    @Test
    fun reactivateUpdatesBackgroundAndSyncsService() = runTest {
        val viewModel = viewModel()

        viewModel.setReactivateOnUserChange(true)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            settingsRepository.updateBackground(any())
        }
        verify(exactly = 1) {
            serviceController.sync(any())
        }
    }

    @Test
    fun enablingAutomationWithoutBluetoothPermissionIsIgnored() = runTest {
        every { bluetoothRepository.hasPermissions() } returns false
        val viewModel = viewModel()

        viewModel.setEnabled(true)
        advanceUntilIdle()

        coVerify(exactly = 0) {
            settingsRepository.updateAutomation(any())
        }
    }

    private fun viewModel() = AutomationViewModel(
        settingsRepository = settingsRepository,
        deviceRepository = deviceRepository,
        interruptModeRepository = interruptModeRepository,
        bluetoothRepository = bluetoothRepository,
        automationCoordinator = automationCoordinator,
        serviceController = serviceController,
    )
}
