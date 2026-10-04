package com.devsecops.tracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Diagrams and callouts drawn from plain note text, so the tutor can write
// visual notes into Supabase and the app renders them. No images, no library.
//
//   ```stack <title>        name | detail | unit            (top to bottom)
//   ```nest <title>         label | detail                  (outer to inner, last = payload)
//   ```steps <title>        title | detail                  (numbered chain)
//   ```seq <title>          first line: actor | actor ; then: A -> B | label
//   ```callout <kind>       attacker|defender|misconception|recap|warn|info

@Composable
private fun accents(): List<Color> {
    val cs = MaterialTheme.colorScheme
    return listOf(cs.primary, cs.secondary, cs.tertiary, cs.error, cs.onSurfaceVariant)
}

@Composable
fun DiagramView(d: MdBlock.Diagram, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    val summary = "Diagram ${d.title}: " + d.rows.joinToString("; ") { it.joinToString(" - ") }
    Column(
        modifier.fillMaxWidth().semantics { contentDescription = summary }
            .clip(shape).background(cs.surfaceContainer).border(1.dp, cs.outlineVariant, shape).padding(12.dp)
    ) {
        when (d.kind) {
            "stack" -> StackBody(d.rows)
            "nest" -> NestBody(d.rows, 0)
            "steps" -> StepsBody(d.rows)
            "seq" -> SeqBody(d.rows)
        }
        if (d.title.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            MonoLabel("fig · ${d.title}")
        }
    }
}

@Composable
private fun StackBody(rows: List<List<String>>) {
    val cs = MaterialTheme.colorScheme
    val ac = accents()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEachIndexed { i, r ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(cs.surfaceContainerHigh).height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.width(3.dp).fillMaxHeight().background(ac[i % ac.size]))
                Column(Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 8.dp)) {
                    Text(r.getOrElse(0) { "" }, style = MaterialTheme.typography.titleSmall)
                    r.getOrNull(1)?.takeIf { it.isNotBlank() }?.let {
                        Text(it, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = cs.onSurfaceVariant)
                    }
                }
                r.getOrNull(2)?.takeIf { it.isNotBlank() }?.let { Pill(it, cs.onSurfaceVariant, Modifier.padding(end = 10.dp)) }
            }
        }
    }
}

@Composable
private fun NestBody(rows: List<List<String>>, idx: Int) {
    if (idx >= rows.size) return
    val cs = MaterialTheme.colorScheme
    val c = accents()[idx % 5]
    val r = rows[idx]
    if (idx == rows.size - 1) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(cs.primaryContainer).padding(10.dp)) {
            Text(r.getOrNull(1)?.takeIf { it.isNotBlank() } ?: r.getOrElse(0) { "" }, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = cs.primary)
        }
        return
    }
    Column(Modifier.fillMaxWidth().border(1.dp, c, RoundedCornerShape(10.dp)).padding(8.dp)) {
        Text(
            listOf(r.getOrElse(0) { "" }, r.getOrElse(1) { "" }).filter { it.isNotBlank() }.joinToString(" · "),
            fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = c
        )
        Spacer(Modifier.height(6.dp))
        NestBody(rows, idx + 1)
    }
}

@Composable
private fun StepsBody(rows: List<List<String>>) {
    val cs = MaterialTheme.colorScheme
    Column {
        rows.forEachIndexed { i, r ->
            val last = i == rows.size - 1
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                Column(Modifier.width(24.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(24.dp).clip(CircleShape).background(cs.primaryContainer), contentAlignment = Alignment.Center) {
                        Text("${i + 1}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = cs.primary)
                    }
                    if (!last) Box(Modifier.padding(top = 3.dp).width(2.dp).weight(1f).background(cs.outlineVariant))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f).padding(bottom = if (last) 0.dp else 12.dp)) {
                    Text(r.getOrElse(0) { "" }, style = MaterialTheme.typography.titleSmall)
                    r.getOrNull(1)?.takeIf { it.isNotBlank() }?.let {
                        Text(inline(it), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SeqBody(rows: List<List<String>>) {
    if (rows.isEmpty()) return
    val cs = MaterialTheme.colorScheme
    val actors = rows[0]
    val msgs = rows.drop(1)
    val n = actors.size.coerceAtLeast(1)
    val lifeline = cs.outlineVariant
    val right = cs.primary
    val left = cs.secondary
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val w = maxWidth
        fun cx(i: Int): Dp = w * ((i + 0.5f) / n)
        Column {
            Row(Modifier.fillMaxWidth()) {
                actors.forEach { a ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(a, fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            val rowH = 46.dp
            Box(Modifier.fillMaxWidth()) {
                Canvas(Modifier.fillMaxWidth().height(rowH * msgs.size)) {
                    for (i in 0 until n) {
                        val x = size.width * (i + 0.5f) / n
                        drawLine(
                            lifeline, Offset(x, 0f), Offset(x, size.height), 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                        )
                    }
                }
                Column {
                    msgs.forEach { m ->
                        val route = m.getOrElse(0) { "" }
                        val from = actors.indexOf(route.substringBefore("->").trim())
                        val to = actors.indexOf(route.substringAfter("->", "").trim())
                        Box(Modifier.fillMaxWidth().height(rowH)) {
                            if (from >= 0 && to >= 0 && from != to) {
                                val l = cx(minOf(from, to))
                                val r = cx(maxOf(from, to))
                                Text(
                                    m.getOrElse(1) { "" },
                                    Modifier.offset(x = l).width(r - l).padding(top = 2.dp),
                                    textAlign = TextAlign.Center, fontFamily = FontFamily.Monospace, fontSize = 12.sp, maxLines = 1
                                )
                                val color = if (to > from) right else left
                                val dir = if (to > from) 1f else -1f
                                Canvas(Modifier.fillMaxSize()) {
                                    val y = size.height * 0.72f
                                    val x1 = size.width * (from + 0.5f) / n
                                    val x2 = size.width * (to + 0.5f) / n
                                    val sw = 2.dp.toPx()
                                    val a = 6.dp.toPx()
                                    drawLine(color, Offset(x1, y), Offset(x2, y), sw)
                                    drawLine(color, Offset(x2, y), Offset(x2 - dir * a, y - a * 0.6f), sw)
                                    drawLine(color, Offset(x2, y), Offset(x2 - dir * a, y + a * 0.6f), sw)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CalloutView(c: MdBlock.Callout, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val (label, color, bg) = when (c.kind) {
        "attacker" -> Triple("attacker", cs.error, cs.errorContainer)
        "defender" -> Triple("defender", cs.secondary, cs.secondaryContainer)
        "misconception", "galatfehmi" -> Triple("galatfehmi", cs.tertiary, cs.tertiaryContainer)
        "recap" -> Triple("30 second recap", cs.primary, cs.primaryContainer)
        "warn" -> Triple("dhyan", cs.tertiary, cs.tertiaryContainer)
        else -> Triple(c.kind, cs.secondary, cs.secondaryContainer)
    }
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(bg.copy(alpha = 0.75f)).padding(horizontal = 12.dp, vertical = 10.dp)) {
        MonoLabel(label, color = color)
        c.text.lines().filter { it.isNotBlank() }.forEach { ln ->
            val t = ln.trim()
            val bullet = t.startsWith("- ")
            Row(Modifier.padding(top = 4.dp)) {
                if (bullet) Text("›", Modifier.width(14.dp), color = color, style = MaterialTheme.typography.bodyMedium)
                Text(inline(if (bullet) t.drop(2) else t), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
