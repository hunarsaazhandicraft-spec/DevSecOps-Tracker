package com.devsecops.tracker

import android.content.Intent
import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.devsecops.tracker.notifications.ReminderScheduler
import com.devsecops.tracker.ui.AuthGate
import com.devsecops.tracker.ui.DeepLink
import com.devsecops.tracker.ui.theme.TrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        // Idempotent: re-arms (or cancels) the single daily alarm based on
        // whatever Settings currently says. Safe on every launch.
        ReminderScheduler.refresh(this)

        // Only on a fresh launch: a rotation must not re-fire the deep link.
        if (savedInstanceState == null) {
            DeepLink.target = intent?.getStringExtra("open")
        }

        setContent {
            TrackerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AuthGate()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        DeepLink.target = intent.getStringExtra("open")
    }
}
