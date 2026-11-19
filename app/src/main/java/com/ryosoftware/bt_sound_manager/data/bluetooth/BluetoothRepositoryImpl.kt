package com.ryosoftware.bt_sound_manager.data.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.ryosoftware.bt_sound_manager.core.util.normalizeAddress
import com.ryosoftware.bt_sound_manager.domain.model.BluetoothDeviceInfo
import com.ryosoftware.bt_sound_manager.domain.model.BluetoothSnapshot
import com.ryosoftware.bt_sound_manager.domain.repository.BluetoothRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

@Singleton
class BluetoothRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : BluetoothRepository {

    private val manager: BluetoothManager? =
        context.getSystemService(BluetoothManager::class.java)

    private val adapter: BluetoothAdapter?
        get() = manager?.adapter

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val queryMutex = Mutex()
    private val connectedState = MutableStateFlow<List<BluetoothDeviceInfo>>(emptyList())

    override fun isSupported(): Boolean = adapter != null

    override fun isEnabled(): Boolean = try {
        adapter?.isEnabled == true
    } catch (_: SecurityException) {
        false
    }

    @SuppressLint("MissingPermission")
    override fun bondedDevices(): List<BluetoothDeviceInfo> {
        if (!hasConnectPermission()) return emptyList()
        val adapter = adapter ?: return emptyList()
        return try {
            adapter.bondedDevices.orEmpty()
                .map { it.toInfo(connected = false) }
                .sortedBy { it.name.lowercase() }
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun connectedDevices(): List<BluetoothDeviceInfo> = queryMutex.withLock {
        if (!hasConnectPermission()) {
            connectedState.value = emptyList()
            return@withLock emptyList()
        }
        val manager = manager ?: return@withLock emptyList()
        if (!isEnabled()) {
            connectedState.value = emptyList()
            return@withLock emptyList()
        }

        val devices = linkedMapOf<String, BluetoothDeviceInfo>()

        coroutineScope {
            PROXY_CONNECTED_PROFILES.map { profile ->
                async { connectedDevicesForProfile(profile) }
            }.awaitAll()
        }.flatten().forEach { info ->
            devices[info.address] = mergeDevice(devices[info.address], info)
        }

        GATT_PROFILES.forEach { profile ->
            runCatching { manager.getConnectedDevices(profile) }
                .getOrNull()
                .orEmpty()
                .forEach { device ->
                    val info = device.toInfo(connected = true)
                    devices[info.address] = mergeDevice(devices[info.address], info)
                }
        }

        connectedState.value.associateBy { it.address }.forEach { (address, info) ->
            devices.putIfAbsent(address, info)
        }

        val result = devices.values.sortedBy { it.name.lowercase() }
        connectedState.value = result
        result
    }

    override fun observeConnectedDevices(): StateFlow<List<BluetoothDeviceInfo>> =
        connectedState.asStateFlow()

    @SuppressLint("MissingPermission")
    override fun updateConnectionState(address: String?, name: String?, connected: Boolean) {
        val normalized = normalizeAddress(address) ?: return
        val current = connectedState.value
        if (!connected) {
            connectedState.value = current.filterNot { it.address == normalized }
            return
        }
        val existing = current.firstOrNull { it.address == normalized }
        val cleanName = name?.trim()?.takeIf { it.isNotEmpty() }
        val resolvedName = when {
            existing != null &&
                existing.name != normalized &&
                existing.name.isNotBlank() -> existing.name

            cleanName != null -> cleanName
            else -> normalized
        }
        val info = BluetoothDeviceInfo(
            address = normalized,
            name = resolvedName,
            isConnected = true,
            isBonded = existing?.isBonded == true || isBonded(normalized),
        )
        connectedState.value = (current.filterNot { it.address == normalized } + info)
            .sortedBy { it.name.lowercase() }
    }

    override fun clearConnectionState() {
        connectedState.value = emptyList()
    }

    override fun isDeviceConnected(address: String): Boolean {
        val normalized = normalizeAddress(address) ?: return false
        return connectedState.value.any { it.address == normalized }
    }

    override fun observeSnapshot(): Flow<BluetoothSnapshot> = combine(
        adapterEnabledFlow(),
        connectedState,
    ) { enabled, devices ->
        BluetoothSnapshot(
            supported = isSupported(),
            enabled = enabled,
            connectedDevices = devices,
        )
    }.distinctUntilChanged()

    override fun refreshSnapshot() {
        repositoryScope.launch {
            runCatching { connectedDevices() }
        }
    }

    @Suppress("DEPRECATION")
    override fun tryEnableBluetooth(): Boolean {
        if (Build.VERSION.SDK_INT >= 33) return false
        if (!hasConnectPermission()) return false
        return try {
            adapter?.enable() == true
        } catch (_: SecurityException) {
            false
        }
    }

    private fun adapterEnabledFlow(): Flow<Boolean> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                trySend(isEnabled())
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_EXPORTED,
        )
        trySend(isEnabled())
        awaitClose { context.unregisterReceiver(receiver) }
    }

    @SuppressLint("MissingPermission")
    private suspend fun connectedDevicesForProfile(profile: Int): List<BluetoothDeviceInfo> {
        val adapter = adapter ?: return emptyList()
        return withTimeoutOrNull(PROFILE_QUERY_TIMEOUT_MILLIS) {
            suspendCancellableCoroutine { continuation ->
                var proxy: BluetoothProfile? = null
                var finished = false

                fun closeProxy(bluetoothProfile: BluetoothProfile) {
                    runCatching { adapter.closeProfileProxy(profile, bluetoothProfile) }
                }

                fun finish(result: List<BluetoothDeviceInfo>) {
                    if (finished) return
                    finished = true
                    proxy?.let(::closeProxy)
                    proxy = null
                    if (continuation.isActive) continuation.resume(result)
                }

                val listener = object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(p: Int, service: BluetoothProfile) {
                        if (finished) {
                            closeProxy(service)
                            return
                        }
                        proxy = service
                        val devices = runCatching {
                            service.connectedDevices.orEmpty()
                                .map { it.toInfo(connected = true) }
                        }.getOrDefault(emptyList())
                        finish(devices)
                    }

                    override fun onServiceDisconnected(p: Int) {
                        finish(emptyList())
                    }
                }

                val requested = runCatching {
                    adapter.getProfileProxy(context, listener, profile)
                }.getOrDefault(false)
                if (!requested) finish(emptyList())

                continuation.invokeOnCancellation {
                    if (!finished) {
                        finished = true
                        proxy?.let(::closeProxy)
                        proxy = null
                    }
                }
            }
        } ?: emptyList()
    }

    private fun mergeDevice(
        existing: BluetoothDeviceInfo?,
        incoming: BluetoothDeviceInfo,
    ): BluetoothDeviceInfo {
        if (existing == null) return incoming
        val name = if (incoming.name == incoming.address && existing.name != existing.address) {
            existing.name
        } else {
            incoming.name
        }
        return incoming.copy(
            name = name,
            isBonded = existing.isBonded || incoming.isBonded,
        )
    }

    @SuppressLint("MissingPermission")
    private fun isBonded(address: String): Boolean = try {
        adapter?.getRemoteDevice(address)?.bondState == BluetoothDevice.BOND_BONDED
    } catch (_: Exception) {
        false
    }

    private fun hasConnectPermission(): Boolean =
        Build.VERSION.SDK_INT < 31 ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT,
            ) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun BluetoothDevice.toInfo(connected: Boolean): BluetoothDeviceInfo {
        val address = normalizeAddress(this.address) ?: this.address
        val displayName = name?.trim()?.takeIf { it.isNotEmpty() } ?: address
        return BluetoothDeviceInfo(
            address = address,
            name = displayName,
            isConnected = connected,
            isBonded = try {
                bondState == BluetoothDevice.BOND_BONDED
            } catch (_: SecurityException) {
                false
            },
        )
    }

    private companion object {
        const val PROFILE_QUERY_TIMEOUT_MILLIS = 2_000L

        val PROXY_CONNECTED_PROFILES = listOf(
            BluetoothProfile.HEADSET,
            BluetoothProfile.A2DP,
            BluetoothProfile.SAP,
            BluetoothProfile.HID_DEVICE,
            BluetoothProfile.HEARING_AID,
            BluetoothProfile.LE_AUDIO,
        )

        val GATT_PROFILES = listOf(
            BluetoothProfile.GATT,
            BluetoothProfile.GATT_SERVER,
        )
    }
}
