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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
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
        items(books) { b -> BookRow(b) { onOpenBook(b.id) } }
        item { Spacer(Modifier.height(24.dp)) }
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
