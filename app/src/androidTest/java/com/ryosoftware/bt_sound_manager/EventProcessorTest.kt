package com.ryosoftware.bt_sound_manager

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ryosoftware.bt_sound_manager.domain.model.BluetoothEvent
import com.ryosoftware.bt_sound_manager.domain.model.BluetoothEventType
import com.ryosoftware.bt_sound_manager.domain.model.InterruptMode
import com.ryosoftware.bt_sound_manager.domain.repository.BluetoothEventRepository
import com.ryosoftware.bt_sound_manager.service.EventProcessor
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class EventProcessorTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var eventProcessor: EventProcessor

    @Inject
    lateinit var eventRepository: BluetoothEventRepository

    @Before
    fun setUp() {
        hiltRule.inject()
        runBlocking { eventRepository.deleteAll() }
    }

    @After
    fun tearDown() {
        runBlocking { eventRepository.deleteAll() }
    }

    @Test
    fun bluetoothOnCreatesPersistedEvent() = runBlocking {
        val now = System.currentTimeMillis()
        eventProcessor.onBluetoothStateChanged(enabled = true, eventTimeMillis = now)
        val events = withTimeout(5_000) {
            eventRepository.observeEvents().first { it.isNotEmpty() }
        }
        assertEquals(1, events.size)
        assertEquals(BluetoothEventType.BLUETOOTH_ON, events.first().type)
        assertTrue(events.first().timeUtc > 0)
    }

    @Test
    fun replacesAttemptingEventWithConfirmedEvent() = runBlocking {
        val attemptId = eventRepository.addEvent(
            BluetoothEvent(
                timeUtc = 1_000,
                type = BluetoothEventType.AUTOMATION_ATTEMPTING_CONNECTED,
                deviceName = "Headphones",
                mode = InterruptMode.DO_NOT_DISTURB,
            ),
        )

        eventRepository.replaceEvent(
            removedId = attemptId,
            event = BluetoothEvent(
                timeUtc = 2_000,
                type = BluetoothEventType.AUTOMATION_APPLIED_CONNECTED,
                deviceName = "Headphones",
                mode = InterruptMode.DO_NOT_DISTURB,
            ),
        )

        val events = eventRepository.getAllEvents()
        assertEquals(1, events.size)
        assertEquals(BluetoothEventType.AUTOMATION_APPLIED_CONNECTED, events.single().type)
        assertEquals(2_000, events.single().timeUtc)
    }
}
