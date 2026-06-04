package com.scholar.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.scholar.app.data.SettingsStore
import com.scholar.app.notify.Reminders
import com.scholar.app.ui.ScholarRoot
import com.scholar.app.ui.onboarding.Onboarding
import com.scholar.app.ui.theme.ScholarTheme
import com.scholar.app.widget.WidgetUpdater

class MainActivity : ComponentActivity() {
    // Deep-link target from a notification/widget tap. Held as snapshot state so a tap while the app
    // is already running (delivered to onNewIntent) still navigates.
    private val pendingRoute = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        pendingRoute.value = intent?.getStringExtra("route")
        val graph = ScholarApp.graph(application)
        val settings = SettingsStore(this)
        setContent {
            var dark by remember { mutableStateOf(settings.darkTheme) }
            var onboarded by remember { mutableStateOf(settings.onboarded) }
            ScholarTheme(dark = dark) {
                if (!onboarded) {
                    Onboarding(onFinish = { settings.onboarded = true; onboarded = true })
                } else {
                    val context = LocalContext.current
                    val permLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission()) {}
                    LaunchedEffect(Unit) {
                        // best-effort auto-backup, ask for notification permission (Android 13+),
                        // and refresh the home-screen widgets with the latest counts.
                        graph.backup.maybeAutoBackup()
                        if (settings.remindersEnabled) {
                            Reminders.schedule(context)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                                != PackageManager.PERMISSION_GRANTED) {
                                permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                        WidgetUpdater.refresh(context)
                    }
                    ScholarRoot(
                        graph = graph,
                        dark = dark,
                        onToggleTheme = { dark = !dark; settings.darkTheme = dark },
                        startRoute = pendingRoute.value,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingRoute.value = intent.getStringExtra("route")
    }
}
