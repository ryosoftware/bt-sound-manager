package com.ryosoftware.bt_sound_manager.presentation

import android.content.Context
import com.ryosoftware.bt_sound_manager.MainDispatcherRule
import com.ryosoftware.bt_sound_manager.domain.model.AppSettings
import com.ryosoftware.bt_sound_manager.domain.repository.SettingsRepository
import com.ryosoftware.bt_sound_manager.domain.usecase.RefreshDeviceNames
import com.ryosoftware.bt_sound_manager.service.AutomationCoordinator
import com.ryosoftware.bt_sound_manager.service.MonitorServiceController
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class MainViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val settingsRepository = mockk<SettingsRepository>()
    private val serviceController = mockk<MonitorServiceController>(relaxed = true)
    private val deviceNameRefresher = mockk<RefreshDeviceNames>(relaxed = true)
    private val automationCoordinator = mockk<AutomationCoordinator>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)

    @Before
    fun setUp() {
        every { settingsRepository.settings } returns flowOf(AppSettings())
        coEvery { settingsRepository.snapshot() } returns AppSettings()
    }

    @Test
    fun appliesAutomationOnStart() = runTest {
        val viewModel = MainViewModel(
            settingsRepository = settingsRepository,
            serviceController = serviceController,
            deviceNameRefresher = deviceNameRefresher,
            automationCoordinator = automationCoordinator,
            context = context,
        )

        viewModel.applyAutomationOnStart()

        coVerify { automationCoordinator.apply(false, false, false) }
    }
}
