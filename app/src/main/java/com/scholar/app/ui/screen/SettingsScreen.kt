package com.scholar.app.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scholar.app.di.AppGraph
import com.scholar.app.ui.theme.SerifSC
import com.scholar.app.ui.theme.Theme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(graph: AppGraph, dark: Boolean, onToggleTheme: () -> Unit, onBack: () -> Unit) {
    val x = Theme.x
    val context = LocalContext.current
    val settings = graph.settings
    val scope = rememberCoroutineScope()

    var retention by remember { mutableStateOf(settings.desiredRetention) }
    var autoBackup by remember { mutableStateOf(settings.autoBackup) }
    var backupDir by remember { mutableStateOf(settings.backupTreeUri) }
    var intervalHours by remember { mutableStateOf(settings.backupIntervalHours) }
    var status by remember { mutableStateOf<String?>(null) }

    // Export: write the backup JSON to a file the user picks.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) scope.launch {
            status = runCatching { graph.backup.writeTo(uri) }
                .fold({ "Exported ${it.cards} cards, ${it.known} known characters." },
                    { "Export failed: ${it.message}" })
        }
    }
    // Import: read and restore a backup the user picks.
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) scope.launch {
            status = runCatching { graph.backup.restoreFrom(uri) }
                .fold({ "Imported ${it.cards} cards, ${it.known} known characters, ${it.books} books." },
                    { "Import failed: ${it.message}" })
            retention = settings.desiredRetention
        }
    }
    // Backup folder: persist read/write access to a chosen directory.
    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            settings.backupTreeUri = uri.toString()
            backupDir = uri.toString()
            settings.autoBackup = true
            autoBackup = true
            scope.launch {
                status = runCatching { graph.backup.writeToTree(uri) }
                    .fold({ "Backup folder set. Saved ${it.cards} cards now." },
                        { "Folder set, but first backup failed: ${it.message}" })
            }
        }
    }

    Column(Modifier.fillMaxSize().background(x.bg).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp)) {
        ScreenHeader("Settings", onBack = onBack)

        // ── Theme ────────────────────────────────────────────────────────
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(x.surface).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Dark theme", color = x.text, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                Text("Ink-wash dark, or rice-paper light", color = x.textSoft, fontSize = 13.sp)
            }
            Switch(checked = dark, onCheckedChange = { onToggleTheme() },
                colors = SwitchDefaults.colors(checkedThumbColor = x.gold, checkedTrackColor = x.cinnabarDeep,
                    uncheckedThumbColor = x.textSoft, uncheckedTrackColor = x.surface2))
        }

        // ── Review difficulty (FSRS desired retention) ───────────────────
        Spacer(Modifier.height(20.dp))
        Text("Review", fontFamily = SerifSC, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = x.text)
        Spacer(Modifier.height(8.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(x.surface).padding(16.dp)) {
            Text("Target memory strength", color = x.text, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            Text("Higher means shorter gaps and more frequent reviews — safer, but more work. " +
                "Early reviews of a new card are always kept close together until it proves it has stuck.",
                color = x.textSoft, fontSize = 13.sp, lineHeight = 19.sp)
            Spacer(Modifier.height(10.dp))
            Text("${(retention * 100).roundToInt()}% recall target", color = x.gold, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Slider(
                value = retention, onValueChange = { retention = it },
                onValueChangeFinished = { settings.desiredRetention = retention },
                valueRange = 0.80f..0.97f,
                colors = SliderDefaults.colors(thumbColor = x.cinnabar, activeTrackColor = x.cinnabar,
                    inactiveTrackColor = x.surface2),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("relaxed (80%)", color = x.textFaint, fontSize = 11.sp)
                Text("intense (97%)", color = x.textFaint, fontSize = 11.sp)
            }
        }

        // ── Backup & restore ─────────────────────────────────────────────
        Spacer(Modifier.height(20.dp))
        Text("Backup & restore", fontFamily = SerifSC, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = x.text)
        Spacer(Modifier.height(8.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(x.surface).padding(16.dp)) {
            Text("Your progress — every card, review, known character and book — exports to one " +
                "portable JSON file. Move it to a new phone and import to pick up exactly where you left off.",
                color = x.textSoft, fontSize = 13.sp, lineHeight = 19.sp)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingButton("⬆ Export", x.surface2, Modifier.weight(1f)) {
                    exportLauncher.launch("scholar-backup.json")
                }
                SettingButton("⬇ Import", x.surface2, Modifier.weight(1f)) {
                    importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Automatic backups", color = x.text, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                    Text(if (backupDir == null) "Choose a folder to save snapshots to"
                        else "Saving to your chosen folder", color = x.textSoft, fontSize = 13.sp)
                }
                Switch(checked = autoBackup && backupDir != null,
                    onCheckedChange = { on ->
                        if (on && backupDir == null) folderLauncher.launch(null)
                        else { autoBackup = on; settings.autoBackup = on }
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = x.gold, checkedTrackColor = x.cinnabarDeep,
                        uncheckedThumbColor = x.textSoft, uncheckedTrackColor = x.surface2))
            }

            if (backupDir != null) {
                Spacer(Modifier.height(12.dp))
                Text("How often", color = x.textSoft, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Daily" to 24, "Every 3 days" to 72, "Weekly" to 168).forEach { (label, hrs) ->
                        val sel = intervalHours == hrs
                        Box(Modifier.clip(RoundedCornerShape(12.dp))
                            .background(if (sel) x.cinnabar else x.surface2)
                            .clickable { intervalHours = hrs; settings.backupIntervalHours = hrs }
                            .padding(horizontal = 14.dp, vertical = 8.dp)) {
                            Text(label, color = if (sel) Color.White else x.textSoft, fontSize = 13.sp,
                                fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingButton("Back up now", x.surface2, Modifier.weight(1f)) {
                        scope.launch {
                            status = runCatching { graph.backup.writeToTree(Uri.parse(backupDir)) }
                                .fold({ "Saved ${it.cards} cards to your folder." }, { "Backup failed: ${it.message}" })
                        }
                    }
                    SettingButton("Change folder", x.surface2, Modifier.weight(1f)) { folderLauncher.launch(null) }
                }
            }

            status?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = x.jade, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }

        // ── About ────────────────────────────────────────────────────────
        Spacer(Modifier.height(20.dp))
        Text("About", fontFamily = SerifSC, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = x.text)
        Spacer(Modifier.height(8.dp))
        AboutCard("Scholar", "A free, offline-first app for learning to read Chinese web novels — pinyin first, characters by their components, frequency-ordered vocabulary, spaced repetition, and a friction-free reader.")
        Spacer(Modifier.height(10.dp))
        AboutCard("Open data", "Dictionary: CC-CEDICT (CC BY-SA). Characters & strokes: Make Me a Hanzi (Arphic). Levels: complete-hsk-vocabulary (MIT). Frequency: wordfreq (MIT). The 214 radicals are curated reference data.")
        Spacer(Modifier.height(10.dp))
        AboutCard("Your data stays yours", "No account, no ads, no tracking. Your progress and imported books live only on this device — back them up above whenever you like.")
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun SettingButton(label: String, bg: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val x = Theme.x
    Box(modifier.clip(RoundedCornerShape(14.dp)).background(bg).clickable { onClick() }
        .padding(vertical = 13.dp), contentAlignment = Alignment.Center) {
        Text(label, color = x.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun AboutCard(title: String, body: String) {
    val x = Theme.x
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(x.surface).padding(16.dp)) {
        Text(title, color = x.gold, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Spacer(Modifier.height(4.dp))
        Text(body, color = x.textSoft, fontSize = 14.sp, lineHeight = 21.sp)
    }
}
