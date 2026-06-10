package com.scholar.app.ui.screen

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scholar.app.di.AppGraph
import com.scholar.app.ui.theme.SerifSC
import com.scholar.app.ui.theme.Theme
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(graph: AppGraph, onOpenBook: (String) -> Unit) {
    val x = Theme.x
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val books by graph.books.booksFlow().collectAsStateWithLifecycle(emptyList())
    var status by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<com.scholar.app.data.user.BookEntity?>(null) }

    // bring each book's "X% readable" up to date with what the user knows now
    LaunchedEffect(Unit) { graph.books.refreshCoverage() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = queryName(context, uri)
        importing = true; status = "Importing $name…"
        scope.launch {
            val res = graph.books.import(uri, name)
            importing = false
            status = res.fold(
                onSuccess = { "Imported ‘${it.title}’ · ${(it.coverage * 100).toInt()}% readable" },
                onFailure = { "Could not import: ${it.message}" },
            )
        }
    }

    LazyColumn(Modifier.fillMaxSize().background(x.bg).padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Spacer(Modifier.height(14.dp))
            Text("The Book Pavilion · 书库", color = x.gold, fontSize = 14.sp)
            Text("Library", fontFamily = SerifSC, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, color = x.text)
            Spacer(Modifier.height(8.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(x.cinnabar)
                .clickable(enabled = !importing) {
                    picker.launch(arrayOf(
                        "application/epub+zip", "application/pdf", "application/x-mobipocket-ebook",
                        "text/plain", "image/*", "application/vnd.comicbook+zip", "application/zip",
                        "application/octet-stream",
                    ))
                }.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (importing) CircularProgressIndicator(color = androidx.compose.ui.graphics.Color.White, strokeWidth = 2.dp)
                else Text("+ Import ebook", color = androidx.compose.ui.graphics.Color.White,
                    fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(4.dp))
                Text("EPUB · PDF · MOBI · TXT · CBZ · page photo", color = androidx.compose.ui.graphics.Color(0xCCFFFFFF), fontSize = 12.sp)
            }
            status?.let { Spacer(Modifier.height(8.dp)); Text(it, color = x.textSoft, fontSize = 13.sp) }
            Spacer(Modifier.height(6.dp))
        }
        items(books) { b -> BookRow(b, onDelete = { confirmDelete = b }) { onOpenBook(b.id) } }
        item { Spacer(Modifier.height(24.dp)) }
    }

    confirmDelete?.let { b ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            containerColor = x.surface,
            title = { Text("Remove ‘${b.title}’?", color = x.text, fontFamily = SerifSC,
                fontWeight = FontWeight.SemiBold, fontSize = 18.sp) },
            text = { Text("The book and its reading position are removed from this device. " +
                "Words you mined from it stay in your review deck.", color = x.textSoft, fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    scope.launch {
                        graph.books.delete(b.id)
                        status = "Removed ‘${b.title}’."
                    }
                }) { Text("Remove", color = x.cinnabar, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Cancel", color = x.textSoft) }
            },
        )
    }
}

private fun queryName(context: android.content.Context, uri: Uri): String {
    var name = uri.lastPathSegment ?: "imported"
    runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) name = c.getString(idx) ?: name
        }
    }
    return name
}
