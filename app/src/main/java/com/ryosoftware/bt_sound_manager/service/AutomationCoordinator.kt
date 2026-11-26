package com.ryosoftware.bt_sound_manager.service

import android.content.Context
import com.ryosoftware.bt_sound_manager.R
import com.ryosoftware.bt_sound_manager.core.time.AppClock
import com.ryosoftware.bt_sound_manager.core.util.normalizeAddress
import com.ryosoftware.bt_sound_manager.domain.model.AutomationSettings
import com.ryosoftware.bt_sound_manager.domain.model.BluetoothDeviceInfo
import com.ryosoftware.bt_sound_manager.domain.model.BluetoothEvent
import com.ryosoftware.bt_sound_manager.domain.model.InterruptMode
import com.ryosoftware.bt_sound_manager.domain.repository.AutomationApplyScheduler
import com.ryosoftware.bt_sound_manager.domain.repository.AutomationScheduler
import com.ryosoftware.bt_sound_manager.domain.repository.BluetoothEventRepository
import com.ryosoftware.bt_sound_manager.domain.repository.BluetoothRepository
import com.ryosoftware.bt_sound_manager.domain.repository.DeviceRepository
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
    private val deviceRepository: DeviceRepository,
    private val interruptModeRepository: InterruptModeRepository,
    private val volumeRepository: VolumeRepository,
    private val notificationRepository: MonitorNotificationRepository,
    private val statusHolder: AutomationStatusHolder,
    private val modeChangeTracker: InterruptModeChangeTracker,
    private val pendingAutomationEvents: PendingAutomationEventHolder,
    private val scheduler: AutomationScheduler,
    private val applyScheduler: AutomationApplyScheduler,
    private val clock: AppClock,
) {
    private val planner = AutomationPlanner()

    suspend fun apply(
        doNotCheckEnabled: Boolean = false,
        doNotDisplayOutsideSchedule: Boolean = false,
        deviceAddress: String? = null,
        deviceConnected: Boolean? = null,
        suppressBlockedNotification: Boolean = false,
    ) = withContext(Dispatchers.Default) {
        applyInternal(
            doNotCheckEnabled = doNotCheckEnabled,
            doNotDisplayOutsideSchedule = doNotDisplayOutsideSchedule,
            deviceAddress = deviceAddress,
            deviceConnected = deviceConnected,
            suppressBlockedNotification = suppressBlockedNotification,
        )
    }

    private suspend fun applyInternal(
        doNotCheckEnabled: Boolean,
        doNotDisplayOutsideSchedule: Boolean,
        deviceAddress: String?,
        deviceConnected: Boolean?,
        suppressBlockedNotification: Boolean,
    ) {
        val settings = settingsRepository.snapshot()
        val automation = settings.automation
        val currentMode = interruptModeRepository.currentMode()
        settlePending(currentMode)
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
        val canAccessPolicy = interruptModeRepository.canAccessPolicy()
        val canAccessBluetooth = bluetoothRepository.hasPermissions()
        val bluetoothEnabled = bluetoothRepository.isEnabled()
        val connected = if (bluetoothEnabled && canAccessBluetooth) {
            bluetoothRepository.connectedDevices()
        } else {
            emptyList()
        }
        val monitoredConnected = connected.filter { automation.isMonitored(it) }
        val runtime = runtimeStateRepository.snapshot()
        val plan = planner.plan(
            settings = automation,
            minuteOfDay = minuteOfDay,
            connectedMonitoredDevices = monitoredConnected.size,
            currentMode = currentMode,
            lastAppliedMode = runtime.lastAppliedMode,
            canAccessPolicy = canAccessPolicy,
            canAccessBluetooth = canAccessBluetooth,
            doNotCheckEnabled = doNotCheckEnabled,
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
                addFailureEventIfNeeded(
                    AutomationEventFactory.createPermissionsFailure(
                        nowUtcMillis = clock.nowMillis(),
                        mode = plan.desiredMode,
                    ),
                )
                notificationRepository.showAutomationNotification(
                    mode = null,
                    body = context.getString(R.string.notification_automation_no_permissions),
                    showToggleAutomationAction = true,
                    showEnableBluetoothAction = !bluetoothEnabled && canAccessBluetooth,
                    showPolicySettingsAction = !canAccessPolicy,
                )
            }

            AutomationOutcome.WRONG_CURRENT_PROFILE -> {
                if (!suppressBlockedNotification) {
                    addFailureEventIfNeeded(
                        AutomationEventFactory.createFailure(
                            nowUtcMillis = clock.nowMillis(),
                            mode = plan.desiredMode,
                            bluetoothEnabled = bluetoothEnabled,
                            monitoredConnectedDevices = monitoredConnected,
                        ),
                    )
                    notificationRepository.showAutomationNotification(
                        mode = null,
                        body = blockedNotificationBody(
                            desiredMode = plan.desiredMode,
                            automation = automation,
                            deviceAddress = deviceAddress,
                            deviceConnected = deviceConnected,
                            monitoredConnected = monitoredConnected,
                            bluetoothEnabled = bluetoothEnabled,
                        ),
                        showToggleAutomationAction = false,
                        showEnableBluetoothAction = !bluetoothEnabled,
                    )
                }
            }

            AutomationOutcome.APPLIED -> {
                val previousVolume = volumeRepository.currentMediaVolume()
                if (previousVolume >= 0) {
                    runtimeStateRepository.savePreviousMediaVolume(previousVolume)
                }
                val attemptNow = clock.nowMillis()
                val attemptEvent = AutomationEventFactory.createAttempt(
                    nowUtcMillis = attemptNow,
                    mode = plan.desiredMode,
                    bluetoothEnabled = bluetoothEnabled,
                    monitoredConnectedDevices = monitoredConnected,
                )
                val attemptEventId = eventRepository.addEvent(attemptEvent)
                val confirmedEvent = AutomationEventFactory.create(
                    nowUtcMillis = attemptNow,
                    mode = plan.desiredMode,
                    bluetoothEnabled = bluetoothEnabled,
                    monitoredConnectedDevices = monitoredConnected,
                )
                val confirmedNotification = AutomationNotificationPayload(
                    mode = plan.desiredMode,
                    body = appliedNotificationBody(monitoredConnected, bluetoothEnabled),
                    showToggleAutomationAction = true,
                    showEnableBluetoothAction = !bluetoothEnabled,
                )
                pendingAutomationEvents.set(
                    event = confirmedEvent,
                    desiredMode = plan.desiredMode,
                    attemptEventId = attemptEventId,
                    notification = confirmedNotification,
                )
                notificationRepository.showAutomationNotification(
                    mode = plan.desiredMode,
                    body = confirmedNotification.body,
                    showToggleAutomationAction = true,
                    showEnableBluetoothAction = !bluetoothEnabled,
                    attempting = true,
                )
                applyScheduler.scheduleAttemptTimeout()
                modeChangeTracker.markExpected(plan.desiredMode)
                if (interruptModeRepository.setMode(plan.desiredMode)) {
                    modeChangeTracker.markApplied(plan.desiredMode)
                } else {
                    modeChangeTracker.cancelExpected()
                    pendingAutomationEvents.clear()
                    AutomationEventFactory.createFailureFromConfirmed(
                        confirmedEvent = confirmedEvent,
                        nowUtcMillis = clock.nowMillis(),
                    )?.let { failure ->
                        eventRepository.replaceEvent(attemptEventId, failure)
                    }
                    showFailureNotification(plan.desiredMode)
                    applyScheduler.cancelAttemptTimeout()
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
            deviceAddress = deviceAddress,
            deviceConnected = connected,
        )
    }

    suspend fun onInterruptModeChanged() {
        val settings = settingsRepository.snapshot()
        val currentMode = interruptModeRepository.currentMode() ?: return
        settlePending(currentMode)
        val runtime = runtimeStateRepository.snapshot()
        val isExternalChange = modeChangeTracker.isExternalChange(
            currentMode = currentMode,
            baselineMode = runtime.lastAppliedMode,
        )
        if (!isExternalChange) return
        pendingAutomationEvents.clear()
        applyScheduler.cancelAttemptTimeout()

        if (!settings.automation.enabled || !settings.automation.hasDeviceConfiguration) return

        eventRepository.addEvent(
            AutomationEventFactory.createExternalChange(
                nowUtcMillis = clock.nowMillis(),
                mode = currentMode,
            ),
        )
        notificationRepository.showAutomationNotification(
            mode = currentMode,
            body = context.getString(R.string.notification_automation_external_change),
            showToggleAutomationAction = true,
            showEnableBluetoothAction = !bluetoothRepository.isEnabled(),
        )
        if (settings.automation.shouldReapplyAfterExternalChange(currentMode)) {
            apply()
        }
    }

    suspend fun onAttemptTimeout() {
        settlePending(interruptModeRepository.currentMode())
    }

    suspend fun toggleAutomation() {
        val current = settingsRepository.snapshot().automation
        val newEnabled = !current.enabled
        settingsRepository.updateAutomation { it.copy(enabled = newEnabled) }
        if (newEnabled) {
            apply(
                doNotCheckEnabled = true,
                doNotDisplayOutsideSchedule = false,
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
        )
    }

    suspend fun onUserForeground() {
        apply(
            doNotCheckEnabled = false,
            doNotDisplayOutsideSchedule = true,
        )
        if (!bluetoothRepository.isEnabled() && !bluetoothRepository.tryEnableBluetooth()) {
            notificationRepository.showBluetoothDisabled()
        }
    }

    private suspend fun settlePending(currentMode: InterruptMode?) {
        pendingAutomationEvents.takeExpired()?.let { confirmed ->
            if (currentMode == confirmed.desiredMode) {
                commitConfirmedAutomation(confirmed)
            } else {
                commitFailureFromConfirmed(confirmed)
            }
        }
        if (currentMode != null) {
            pendingAutomationEvents.takeConfirmed(currentMode)?.let { confirmed ->
                commitConfirmedAutomation(confirmed)
            }
        }
    }

    private suspend fun commitConfirmedAutomation(confirmed: ConfirmedAutomation) {
        eventRepository.replaceEvent(
            removedId = confirmed.attemptEventId,
            event = confirmed.event.copy(timeUtc = clock.nowMillis()),
        )
        runtimeStateRepository.saveAppliedMode(
            mode = confirmed.desiredMode,
            elapsedRealtimeTics = clock.elapsedRealtime(),
        )
        notificationRepository.showAutomationNotification(
            mode = confirmed.notification.mode,
            body = confirmed.notification.body,
            showToggleAutomationAction = confirmed.notification.showToggleAutomationAction,
            showEnableBluetoothAction = confirmed.notification.showEnableBluetoothAction,
        )
        applyScheduler.cancelAttemptTimeout()
        restoreMediaVolumeIfNeeded(confirmed)
    }

    /**
     * Puts back the media volume saved before the mode change. A confirmation that
     * arrives late is ignored, so a volume chosen since then is not overwritten.
     */
    private suspend fun restoreMediaVolumeIfNeeded(confirmed: ConfirmedAutomation) {
        if (!settingsRepository.snapshot().automation.restoreMediaVolume) return
        val age = clock.elapsedRealtime() - confirmed.requestedAtElapsedTics
        if (age > RESTORE_VOLUME_WINDOW_MILLIS) return
        val previousVolume = runtimeStateRepository.snapshot().previousMediaVolume ?: return
        volumeRepository.setMediaVolume(previousVolume)
    }

    private suspend fun commitFailureFromConfirmed(confirmed: ConfirmedAutomation) {
        val failure = AutomationEventFactory.createFailureFromConfirmed(
            confirmedEvent = confirmed.event,
            nowUtcMillis = clock.nowMillis(),
        ) ?: return
        eventRepository.replaceEvent(confirmed.attemptEventId, failure)
        showFailureNotification(confirmed.desiredMode)
        applyScheduler.cancelAttemptTimeout()
    }

    private suspend fun addFailureEventIfNeeded(event: BluetoothEvent) {
        val latest = eventRepository.getLatestEvent()
        if (latest != null &&
            latest.type == event.type &&
            latest.mode == event.mode &&
            latest.deviceName == event.deviceName
        ) {
            return
        }
        eventRepository.addEvent(event)
    }

    private fun showFailureNotification(mode: InterruptMode) {
        notificationRepository.showAutomationNotification(
            mode = mode,
            body = context.getString(R.string.notification_automation_apply_failed),
            showToggleAutomationAction = true,
            showEnableBluetoothAction = !bluetoothRepository.isEnabled(),
        )
    }

    private suspend fun blockedNotificationBody(
        desiredMode: InterruptMode,
        automation: AutomationSettings,
        deviceAddress: String?,
        deviceConnected: Boolean?,
        monitoredConnected: List<BluetoothDeviceInfo>,
        bluetoothEnabled: Boolean,
    ): String {
        if (!bluetoothEnabled) {
            return context.getString(
                R.string.notification_automation_not_applied_bluetooth_off,
            )
        }
        val normalizedAddress = normalizeAddress(deviceAddress)
        if (desiredMode == automation.modeWhenConnected) {
            val connectedName = if (deviceConnected == true &&
                normalizedAddress != null &&
                automation.isMonitored(normalizedAddress)
            ) {
                deviceRepository.getDeviceName(normalizedAddress)
                    ?: monitoredConnected.firstOrNull {
                        normalizeAddress(it.address) == normalizedAddress
                    }?.name
                    ?: normalizedAddress
            } else {
                null
            }
            return if (connectedName != null) {
                context.getString(
                    R.string.notification_automation_not_applied_connected,
                    connectedName,
                )
            } else {
                context.getString(
                    R.string.notification_automation_not_applied_connected_generic,
                )
            }
        }

        val disconnectedName = if (deviceConnected == false &&
            normalizedAddress != null &&
            automation.isMonitored(normalizedAddress)
        ) {
            deviceRepository.getDeviceName(normalizedAddress) ?: normalizedAddress
        } else {
            null
        }
        return if (disconnectedName != null) {
            context.getString(
                R.string.notification_automation_not_applied_disconnected,
                disconnectedName,
            )
        } else {
            context.getString(R.string.notification_automation_not_applied_no_devices)
        }
    }

    private fun showApplied(
        mode: InterruptMode,
        monitoredConnected: List<BluetoothDeviceInfo>,
        bluetoothEnabled: Boolean,
    ) {
        notificationRepository.showAutomationNotification(
            mode = mode,
            body = appliedNotificationBody(monitoredConnected, bluetoothEnabled),
            showToggleAutomationAction = true,
            showEnableBluetoothAction = !bluetoothEnabled,
        )
    }

    private fun appliedNotificationBody(
        monitoredConnected: List<BluetoothDeviceInfo>,
        bluetoothEnabled: Boolean,
    ): String = when {
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

    private fun AutomationSettings.shouldReapplyAfterExternalChange(
        currentMode: InterruptMode,
    ): Boolean =
        (currentMode == modeWhenNone && forceModeOnDevicesConnected) ||
            (currentMode == modeWhenConnected && forceModeOnDevicesDisconnected)

    private fun AutomationSettings.isMonitored(device: BluetoothDeviceInfo): Boolean =
        isMonitored(device.address)

    private fun AutomationSettings.isMonitored(address: String?): Boolean {
        val normalizedAddress = normalizeAddress(address) ?: return false
        if (monitorAllDevices) return true
        return selectedDeviceAddresses.any { normalizeAddress(it) == normalizedAddress }
    }

    companion object {
        internal const val DELAY_BEFORE_APPLY_MILLIS = 3_000L
        private const val RESTORE_VOLUME_WINDOW_MILLIS = 15_000L
    }
}
