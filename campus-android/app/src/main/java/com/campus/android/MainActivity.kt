package com.campus.android

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.campus.android.core.system.AppSystemSettings
import com.campus.android.ui.CampusApp
import com.campus.android.ui.CampusViewModel
import com.campus.android.ui.CampusViewModelFactory
import com.campus.android.ui.theme.CampusTheme

class MainActivity : ComponentActivity() {

    private var notificationEventId by mutableStateOf<String?>(null)

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // The app keeps working without notification permission; high-risk reminders fall back to in-app state.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notificationEventId = intent.eventIdExtra()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !AppSystemSettings.canPostNotifications(this)) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val app = application as CampusApplication
        setContent {
            CampusTheme {
                val viewModel: CampusViewModel = viewModel(
                    factory = CampusViewModelFactory(app.repository, app.realtimeClient)
                )
                val eventId = notificationEventId
                LaunchedEffect(eventId) {
                    if (!eventId.isNullOrBlank()) {
                        viewModel.openEventFromNotification(eventId)
                        notificationEventId = null
                    }
                }
                CampusApp(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        notificationEventId = intent.eventIdExtra()
    }

    private fun Intent?.eventIdExtra(): String? {
        return this?.getStringExtra(EXTRA_EVENT_ID)?.takeIf { it.isNotBlank() }
    }

    companion object {
        const val EXTRA_EVENT_ID = "event_id"
    }
}
