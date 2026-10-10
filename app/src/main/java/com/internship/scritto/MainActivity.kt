package com.internship.scritto

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.internship.scritto.data.repository.ScrittoStore
import com.internship.scritto.navigation.ScrittoNavigation
import com.internship.scritto.notifications.FollowUpReminder
import com.internship.scritto.notifications.NotificationRouter
import com.internship.scritto.telemetry.Telemetry
import com.internship.scritto.ui.theme.ScrittoTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        ScrittoStore.initialize(applicationContext)
        Telemetry.init(applicationContext)
        // Not on rotation or other recreation: that isn't the user opening the app.
        if (savedInstanceState == null) Telemetry.track("app_opened")
        FollowUpReminder.schedule(applicationContext)
        NotificationRouter.handle(intent)

        setContent {
            ScrittoTheme {
                ScrittoNavigation()
            }
        }
    }

    // Used when a notification is tapped while the app is already open (singleTop).
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        NotificationRouter.handle(intent)
    }

    override fun onStart() {
        super.onStart()
        FollowUpReminder.markAppOpened(applicationContext)
    }
}