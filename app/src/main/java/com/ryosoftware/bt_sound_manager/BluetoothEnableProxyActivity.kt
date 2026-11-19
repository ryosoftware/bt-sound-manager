package com.ryosoftware.bt_sound_manager

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class BluetoothEnableProxyActivity : ComponentActivity() {

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { finish() }

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter
        val enabled = try {
            adapter?.isEnabled == true
        } catch (_: SecurityException) {
            false
        }
        if (adapter == null || enabled) {
            finish()
            return
        }
        try {
            enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        } catch (_: Exception) {
            finish()
        }
    }
}
