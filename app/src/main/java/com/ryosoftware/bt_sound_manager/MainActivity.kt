package com.ryosoftware.bt_sound_manager

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ryosoftware.bt_sound_manager.core.util.AppActions
import com.ryosoftware.bt_sound_manager.presentation.MainViewModel
import com.ryosoftware.bt_sound_manager.presentation.navigation.MonitorizeApp
import com.ryosoftware.bt_sound_manager.presentation.theme.MonitorizeTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val pendingDestination = MutableStateFlow<String?>(null)
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingDestination.value = intent?.getStringExtra(AppActions.EXTRA_DESTINATION)
        setContent {
            val appearance by viewModel.appearance.collectAsStateWithLifecycle()
            MonitorizeTheme(appearance = appearance) {
                MonitorizeApp(
                    pendingDestination = pendingDestination,
                    onDestinationConsumed = { pendingDestination.value = null },
                )
            }
        }
        if (savedInstanceState == null) {
            viewModel.applyAutomationOnStart()
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshDeviceNames()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingDestination.value = intent.getStringExtra(AppActions.EXTRA_DESTINATION)
    }
}
