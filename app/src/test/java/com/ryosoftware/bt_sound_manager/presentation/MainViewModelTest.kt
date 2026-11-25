package com.ryosoftware.bt_sound_manager.presentation

import android.content.Context
import com.ryosoftware.bt_sound_manager.MainDispatcherRule
import com.ryosoftware.bt_sound_manager.domain.model.AppSettings
import com.ryosoftware.bt_sound_manager.domain.repository.SettingsRepository
import com.ryosoftware.bt_sound_manager.domain.usecase.RefreshDeviceNames
import com.ryosoftware.bt_sound_manager.service.MonitorServiceController
import io.mockk.coEvery
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
class MainViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val settingsRepository = mockk<SettingsRepository>()
    private val serviceController = mockk<MonitorServiceController>(relaxed = true)
    private val deviceNameRefresher = mockk<RefreshDeviceNames>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)

    @Before
    fun setUp() {
        every { settingsRepository.settings } returns flowOf(AppSettings())
        coEvery { settingsRepository.snapshot() } returns AppSettings()
    }

    @Test
    fun syncsServiceOnInit() = runTest {
        MainViewModel(
            settingsRepository = settingsRepository,
            serviceController = serviceController,
            deviceNameRefresher = deviceNameRefresher,
            context = context,
        )

        advanceUntilIdle()

        verify(exactly = 1) { serviceController.sync(AppSettings().background) }
    }
}
