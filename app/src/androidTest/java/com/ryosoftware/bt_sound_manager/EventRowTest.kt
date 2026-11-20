package com.ryosoftware.bt_sound_manager

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ryosoftware.bt_sound_manager.domain.model.BluetoothEvent
import com.ryosoftware.bt_sound_manager.domain.model.BluetoothEventType
import com.ryosoftware.bt_sound_manager.domain.model.AppearanceSettings
import com.ryosoftware.bt_sound_manager.domain.model.InterruptMode
import com.ryosoftware.bt_sound_manager.presentation.components.EventRow
import com.ryosoftware.bt_sound_manager.presentation.theme.MonitorizeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EventRowTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersDeviceNameAndEventType() {
        composeRule.setContent {
            MonitorizeTheme(appearance = AppearanceSettings()) {
                EventRow(
                    event = BluetoothEvent(
                        id = 1,
                        timeUtc = 1_700_000_000_000,
                        type = BluetoothEventType.DEVICE_CONNECTED,
                        deviceAddress = "AA:BB",
                    ),
                    deviceName = "Headphones",
                )
            }
        }
        composeRule.onNodeWithText("Headphones").assertIsDisplayed()
        composeRule.onNodeWithText("AA:BB").assertIsDisplayed()
        composeRule.onNodeWithText("Device connected").assertIsDisplayed()
    }

    @Test
    fun rendersExternalModeChange() {
        composeRule.setContent {
            MonitorizeTheme(appearance = AppearanceSettings()) {
                EventRow(
                    event = BluetoothEvent(
                        id = 1,
                        timeUtc = 1_700_000_000_000,
                        type = BluetoothEventType.EXTERNAL_MODE_CHANGED,
                        mode = InterruptMode.ALARMS_ONLY,
                    ),
                    deviceName = null,
                )
            }
        }
        composeRule
            .onNodeWithText("Sound mode changed externally: \"Alarms only\"")
            .assertIsDisplayed()
    }

    @Test
    fun rendersAutomationEventWithModeAndDevice() {
        composeRule.setContent {
            MonitorizeTheme(appearance = AppearanceSettings()) {
                EventRow(
                    event = BluetoothEvent(
                        id = 1,
                        timeUtc = 1_700_000_000_000,
                        type = BluetoothEventType.AUTOMATION_APPLIED_CONNECTED,
                        deviceName = "Headphones",
                        mode = InterruptMode.DO_NOT_DISTURB,
                    ),
                    deviceName = null,
                )
            }
        }
        composeRule
            .onNodeWithText(
                "Mode \"Do Not Disturb\" is activated because \"Headphones\" is connected",
            )
            .assertIsDisplayed()
    }
}
