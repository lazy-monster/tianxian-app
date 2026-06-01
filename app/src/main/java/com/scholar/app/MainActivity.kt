package com.scholar.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.scholar.app.data.SettingsStore
import com.scholar.app.ui.ScholarRoot
import com.scholar.app.ui.onboarding.Onboarding
import com.scholar.app.ui.theme.ScholarTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val graph = ScholarApp.graph(application)
        val settings = SettingsStore(this)
        setContent {
            var dark by remember { mutableStateOf(settings.darkTheme) }
            var onboarded by remember { mutableStateOf(settings.onboarded) }
            ScholarTheme(dark = dark) {
                if (!onboarded) {
                    Onboarding(onFinish = { settings.onboarded = true; onboarded = true })
                } else {
                    // Write an automatic backup on launch if it's enabled and due (best-effort).
                    LaunchedEffect(Unit) { graph.backup.maybeAutoBackup() }
                    ScholarRoot(
                        graph = graph,
                        dark = dark,
                        onToggleTheme = { dark = !dark; settings.darkTheme = dark },
                    )
                }
            }
        }
    }
}
