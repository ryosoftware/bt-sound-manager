package com.ryosoftware.bt_sound_manager.service

import android.content.Context
import com.ryosoftware.bt_sound_manager.R
import com.ryosoftware.bt_sound_manager.core.time.AppClock
import com.ryosoftware.bt_sound_manager.core.util.normalizeAddress
import com.ryosoftware.bt_sound_manager.domain.model.AutomationSettings
import com.ryosoftware.bt_sound_manager.domain.model.BluetoothDeviceInfo
import com.ryosoftware.bt_sound_manager.domain.repository.AutomationScheduler
import com.ryosoftware.bt_sound_manager.domain.repository.BluetoothEventRepository
import com.ryosoftware.bt_sound_manager.domain.repository.BluetoothRepository
import com.ryosoftware.bt_sound_manager.domain.repository.InterruptModeRepository
import com.ryosoftware.bt_sound_manager.domain.repository.MonitorNotificationRepository
import com.ryosoftware.bt_sound_manager.domain.repository.RuntimeStateRepository
import com.ryosoftware.bt_sound_manager.domain.repository.SettingsRepository
import com.ryosoftware.bt_sound_manager.domain.repository.VolumeRepository
import com.ryosoftware.bt_sound_manager.domain.usecase.AutomationEventFactory
import com.ryosoftware.bt_sound_manager.domain.usecase.AutomationOutcome
import com.ryosoftware.bt_sound_manager.domain.usecase.AutomationPlanner
import com.ryosoftware.bt_sound_manager.domain.usecase.AutomationScheduleStatusResolver
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Singleton
class AutomationCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val runtimeStateRepository: RuntimeStateRepository,
    private val eventRepository: BluetoothEventRepository,
    private val bluetoothRepository: BluetoothRepository,
    private val interruptModeRepository: InterruptModeRepository,
    private val volumeRepository: VolumeRepository,
    private val notificationRepository: MonitorNotificationRepository,
    private val statusHolder: AutomationStatusHolder,
    private val scheduler: AutomationScheduler,
    private val clock: AppClock,
) {
    private val planner = AutomationPlanner()

    suspend fun apply(
        doNotCheckEnabled: Boolean = false,
        doNotDisplayOutsideSchedule: Boolean = false,
        forceNotification: Boolean = false,
    ) = withContext(Dispatchers.Default) {
        applyInternal(doNotCheckEnabled, doNotDisplayOutsideSchedule, forceNotification)
    }

    private suspend fun applyInternal(
        doNotCheckEnabled: Boolean,
        doNotDisplayOutsideSchedule: Boolean,
        forceNotification: Boolean,
    ) {
        val settings = settingsRepository.snapshot()
        val automation = settings.automation
        if (!automation.enabled && !doNotCheckEnabled) {
            statusHolder.update(null)
            scheduler.cancel()
            notificationRepository.hideAutomationNotification()
            return
        }

        val now = clock.nowMillis()
        val minuteOfDay = Instant.ofEpochMilli(now)
            .atZone(clock.zoneId())
            .toLocalTime()
            .let { it.hour * 60 + it.minute }
        statusHolder.update(
            AutomationScheduleStatusResolver.resolve(automation, minuteOfDay),
        )
        val bluetoothEnabled = bluetoothRepository.isEnabled()
        val connected = if (bluetoothEnabled) bluetoothRepository.connectedDevices() else emptyList()
        val monitoredConnected = connected.filter { automation.isMonitored(it) }
        val runtime = runtimeStateRepository.snapshot()
        val plan = planner.plan(
            settings = automation,
            minuteOfDay = minuteOfDay,
            connectedMonitoredDevices = monitoredConnected.size,
            currentMode = interruptModeRepository.currentMode(),
            lastAppliedMode = runtime.lastAppliedMode,
            canAccessPolicy = interruptModeRepository.canAccessPolicy(),
            doNotCheckEnabled = doNotCheckEnabled,
            forceNotification = forceNotification,
        )

        when (plan.outcome) {
            AutomationOutcome.NOT_ENABLED,
            AutomationOutcome.NO_DEVICES_CONFIGURED,
            -> {
                scheduler.cancel()
                notificationRepository.hideAutomationNotification()
            }

            AutomationOutcome.OUTSIDE_SCHEDULE -> {
                if (!doNotDisplayOutsideSchedule) {
                    notificationRepository.showAutomationNotification(
                        mode = null,
                        body = context.getString(R.string.notification_automation_outside_schedule),
                        showToggleAutomationAction = false,
                        showEnableBluetoothAction = !bluetoothEnabled,
                    )
                }
            }

            AutomationOutcome.INSUFFICIENT_PERMISSIONS -> {
                notificationRepository.showAutomationNotification(
                    mode = null,
                    body = context.getString(R.string.notification_automation_no_permissions),
                    showToggleAutomationAction = true,
                    showEnableBluetoothAction = !bluetoothEnabled,
                    showPolicySettingsAction = true,
                )
            }

            AutomationOutcome.WRONG_CURRENT_PROFILE -> {
                notificationRepository.showAutomationNotification(
                    mode = null,
                    body = context.getString(R.string.notification_automation_wrong_profile),
                    showToggleAutomationAction = false,
                    showEnableBluetoothAction = !bluetoothEnabled,
                )
            }

            AutomationOutcome.APPLIED -> {
                val previousVolume = volumeRepository.currentMediaVolume()
                if (previousVolume >= 0) {
                    runtimeStateRepository.savePreviousMediaVolume(previousVolume)
                }
                if (interruptModeRepository.setMode(plan.desiredMode)) {
                    runtimeStateRepository.saveAppliedMode(
                        mode = plan.desiredMode,
                        elapsedRealtimeTics = clock.elapsedRealtime(),
                    )
                    eventRepository.addEvent(
                        AutomationEventFactory.create(
                            nowUtcMillis = clock.nowMillis(),
                            mode = plan.desiredMode,
                            bluetoothEnabled = bluetoothEnabled,
                            monitoredConnectedDevices = monitoredConnected,
                        ),
                    )
                    showApplied(plan.desiredMode, monitoredConnected, bluetoothEnabled)
                } else {
                    notificationRepository.showAutomationNotification(
                        mode = null,
                        body = context.getString(R.string.notification_automation_no_permissions),
                        showToggleAutomationAction = true,
                        showEnableBluetoothAction = !bluetoothEnabled,
                        showPolicySettingsAction = true,
                    )
                }
            }

            AutomationOutcome.ALREADY_CURRENT -> {
                runtimeStateRepository.saveAppliedMode(
                    mode = plan.desiredMode,
                    elapsedRealtimeTics = clock.elapsedRealtime(),
                )
                showApplied(plan.desiredMode, monitoredConnected, bluetoothEnabled)
            }
        }

        scheduler.scheduleNextTransition(automation)
    }

    suspend fun applyDelayed(
        eventTimeMillis: Long,
        deviceAddress: String?,
        connected: Boolean,
    ) {
        delay(DELAY_BEFORE_APPLY_MILLIS)
        applyForEvent(eventTimeMillis, deviceAddress, connected)
    }

    suspend fun applyForEvent(
        eventTimeMillis: Long,
        deviceAddress: String?,
        connected: Boolean,
    ) {
        val runtime = runtimeStateRepository.snapshot()
        val eventTics = clock.elapsedRealtime() + (eventTimeMillis - clock.nowMillis())
        if (runtime.lastExecutionElapsedTics > eventTics) return

        if (deviceAddress != null &&
            bluetoothRepository.isDeviceConnected(deviceAddress) != connected
        ) {
            return
        }
        apply(
            doNotCheckEnabled = false,
            doNotDisplayOutsideSchedule = true,
            forceNotification = true,
        )
    }

    suspend fun onInterruptModeChanged() {
        val settings = settingsRepository.snapshot()
        val runtime = runtimeStateRepository.snapshot()
        if (settings.automation.restoreMediaVolume) {
            val previousVolume = runtime.previousMediaVolume
            if (previousVolume != null &&
                runtime.lastExecutionElapsedTics + RESTORE_VOLUME_WINDOW_MILLIS >
                clock.elapsedRealtime()
            ) {
                volumeRepository.setMediaVolume(previousVolume)
            }
        }
        if (settings.automation.enabled && settings.automation.forceMode) {
            apply(forceNotification = true)
        }
    }

    suspend fun toggleAutomation() {
        val current = settingsRepository.snapshot().automation
        val newEnabled = !current.enabled
        settingsRepository.updateAutomation { it.copy(enabled = newEnabled) }
        if (newEnabled) {
            apply(
                doNotCheckEnabled = true,
                doNotDisplayOutsideSchedule = false,
                forceNotification = true,
            )
        } else {
            scheduler.cancel()
            notificationRepository.hideAutomationNotification()
        }
    }

    suspend fun onBoot() {
        runtimeStateRepository.clearLastExecution()
        apply(
            doNotCheckEnabled = false,
            doNotDisplayOutsideSchedule = true,
            forceNotification = true,
        )
    }

    suspend fun onUserForeground() {
        apply(
            doNotCheckEnabled = false,
            doNotDisplayOutsideSchedule = true,
            forceNotification = true,
        )
        if (!bluetoothRepository.isEnabled() && !bluetoothRepository.tryEnableBluetooth()) {
            notificationRepository.showBluetoothDisabled()
        }
    }

    private fun showApplied(
        mode: com.ryosoftware.bt_sound_manager.domain.model.InterruptMode,
        monitoredConnected: List<BluetoothDeviceInfo>,
        bluetoothEnabled: Boolean,
    ) {
        val body = when {
            monitoredConnected.isEmpty() && bluetoothEnabled ->
                context.getString(R.string.notification_automation_no_devices)

            monitoredConnected.isEmpty() ->
                context.getString(R.string.notification_automation_bluetooth_off)

            monitoredConnected.size == 1 ->
                context.getString(
                    R.string.notification_automation_one_device,
                    monitoredConnected.first().name,
                )

            else -> context.resources.getQuantityString(
                R.plurals.automation_more_devices,
                monitoredConnected.size - 1,
                monitoredConnected.first().name,
                monitoredConnected.size - 1,
            )
        }
        notificationRepository.showAutomationNotification(
            mode = mode,
            body = body,
            showToggleAutomationAction = true,
            showEnableBluetoothAction = !bluetoothEnabled,
        )
    }

    private fun AutomationSettings.isMonitored(device: BluetoothDeviceInfo): Boolean {
        if (monitorAllDevices) return true
        val address = normalizeAddress(device.address) ?: return false
        return selectedDeviceAddresses.any { normalizeAddress(it) == address }
    }

    companion object {
        internal const val DELAY_BEFORE_APPLY_MILLIS = 3_000L
        private const val RESTORE_VOLUME_WINDOW_MILLIS = 5_000L
    }
}
