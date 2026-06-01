package com.scholar.app.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.PathParser
import com.scholar.app.data.content.StrokeData
import com.scholar.app.di.AppGraph
import com.scholar.app.ui.theme.SerifSC
import com.scholar.app.ui.theme.Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val STROKE_MS = 750L   // a bit slower than before, easier to follow

/* Make Me a Hanzi stroke data lives in a 1024×1024, y-up coordinate system; the
   documented render transform is matrix(1,0,0,-1,0,900). We scale that to the canvas. */
@Composable
fun WritingScreen(graph: AppGraph, ch: String, onBack: () -> Unit, onOpenChar: (String) -> Unit) {
    val x = Theme.x
    var data by remember { mutableStateOf<StrokeData?>(null) }
    var shown by remember { mutableStateOf(0) }          // strokes revealed (animation)
    var playing by remember { mutableStateOf(false) }    // auto-animation running
    var traces by remember { mutableStateOf<List<List<Offset>>>(emptyList()) }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }

    // Load the character, then play the whole stroke order automatically on open.
    LaunchedEffect(ch) {
        shown = 0; traces = emptyList(); current = emptyList()
        data = withContext(Dispatchers.IO) { graph.dictionary.strokeData(ch) }
        if (data != null) playing = true
    }

    // The animation driver: reveal strokes one at a time while `playing`.
    LaunchedEffect(playing, data) {
        val total = data?.strokes?.size ?: 0
        if (playing && total > 0) {
            shown = 0
            for (i in 1..total) { delay(STROKE_MS); shown = i }
            playing = false
        }
    }

    Column(Modifier.fillMaxSize().background(x.bg).padding(horizontal = 22.dp)) {
        ScreenHeader("Handwriting", "Watch the stroke order play through, then trace it with your finger. Correct order is the key to fast, legible writing.", onBack)

        val d = data
        if (d == null) {
            Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                Text("No stroke data for this character yet.", color = x.textFaint)
            }
        } else {
            Box(
                Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp)).background(x.surface)
                    .pointerInput(ch) {
                        detectDragGestures(
                            onDragStart = { current = listOf(it) },
                            onDrag = { change, _ -> current = current + change.position },
                            onDragEnd = { traces = traces + listOf(current); current = emptyList() },
                        )
                    },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val s = size.minDimension / 1024f
                    fun parse(p: String): Path {
                        val ap = PathParser.createPathFromPathData(p)
                        val m = android.graphics.Matrix()
                        m.setValues(floatArrayOf(s, 0f, 0f, 0f, -s, 900f * s, 0f, 0f, 1f))
                        ap.transform(m)
                        return ap.asComposePath()
                    }
                    // faint guide of the whole character
                    d.strokes.forEach { drawPath(parse(it), color = x.line) }
                    // revealed strokes in ink
                    d.strokes.take(shown).forEach { drawPath(parse(it), color = x.text) }
                    // user's traced ink
                    (traces + listOf(current)).forEach { pts ->
                        if (pts.size > 1) {
                            val path = Path().apply {
                                moveTo(pts.first().x, pts.first().y)
                                pts.drop(1).forEach { lineTo(it.x, it.y) }
                            }
                            drawPath(path, color = x.cinnabar,
                                style = Stroke(width = 26f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(if (playing) "Playing… stroke ${shown.coerceAtMost(d.strokes.size)} of ${d.strokes.size}"
                else "Stroke ${shown.coerceAtMost(d.strokes.size)} of ${d.strokes.size}",
                color = x.textFaint, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
            // Manual stepping takes over from the auto-play.
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill("◁ Step", x.surface2, Modifier.weight(1f)) { playing = false; if (shown > 0) shown-- }
                Pill("Step ▷", x.surface2, Modifier.weight(1f)) { playing = false; if (shown < d.strokes.size) shown++ }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Reset returns to a blank slate to practice on: no revealed strokes, no
                // animation, no ink — just the faint guide. Replay is for the animation.
                Pill("↺ Reset", x.surface2, Modifier.weight(1f)) {
                    playing = false; shown = 0; traces = emptyList(); current = emptyList()
                }
                Pill(if (playing) "▶ Playing…" else "▶ Replay", x.cinnabar, Modifier.weight(1f), white = true) {
                    if (!playing) { traces = emptyList(); current = emptyList(); playing = true }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("View character details ›", color = x.jade, fontSize = 14.sp,
                modifier = Modifier.clickable { onOpenChar(ch) })
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun Pill(label: String, bg: Color, modifier: Modifier = Modifier, white: Boolean = false, onClick: () -> Unit) {
    val x = Theme.x
    Box(modifier.clip(RoundedCornerShape(14.dp)).background(bg).clickable { onClick() }.padding(vertical = 13.dp),
        contentAlignment = Alignment.Center) {
        Text(label, color = if (white) Color.White else x.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
