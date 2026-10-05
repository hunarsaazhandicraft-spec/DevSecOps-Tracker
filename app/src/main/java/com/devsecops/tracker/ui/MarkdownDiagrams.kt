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
//   ```compare <title>      first line: (blank) | A | B ; then: row label | A cell | B cell
//   ```bits <title>         field | bits                    (header layout, 32 bits per row)
//   ```annot <title>        output line | what it means     (annotated terminal output)
//   ```path <title>         node | detail | label of the arrow to the NEXT node   (a packet's route)
//   ```flow <title>         "? question | yes: ... | no: ..."  or  "> step / outcome"   (decision walk)
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
    val summary = "Diagram ${diagramKindLabel(d.kind)} ${d.title}: " + d.rows.joinToString("; ") { it.joinToString(" - ") }
    Column(
        modifier.fillMaxWidth().semantics { contentDescription = summary }
            .clip(shape).background(cs.surfaceContainer).border(1.dp, cs.outlineVariant, shape).padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(cs.primary))
            MonoLabel("diagram · ${diagramKindLabel(d.kind)}", color = cs.primary)
        }
        val cap = d.title.replace(Regex("^(?i:fig)\\s*[·.:\\-]?\\s*"), "")
        if (cap.isNotBlank()) {
            Text(cap, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(10.dp))
        when (d.kind) {
            "stack" -> StackBody(d.rows)
            "nest" -> NestBody(d.rows, 0)
            "steps" -> StepsBody(d.rows)
            "seq" -> SeqBody(d.rows)
            "compare" -> CompareBody(d.rows)
            "bits" -> BitsBody(d.rows)
            "annot" -> AnnotBody(d.rows)
            "path" -> PathBody(d.rows)
            "flow" -> FlowBody(d.rows)
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

// Packs "name | bits" rows into lines of at most 32 bits (a protocol header).
fun bitRows(rows: List<List<String>>): List<List<Pair<String, Int>>> {
    val lines = ArrayList<List<Pair<String, Int>>>()
    var cur = ArrayList<Pair<String, Int>>()
    var used = 0
    for (r in rows) {
        val name = r.getOrElse(0) { "" }
        val bits = (r.getOrNull(1)?.trim()?.toIntOrNull() ?: 32).coerceIn(1, 32)
        if (used + bits > 32) { lines.add(cur); cur = ArrayList(); used = 0 }
        cur.add(name to bits); used += bits
        if (used == 32) { lines.add(cur); cur = ArrayList(); used = 0 }
    }
    if (cur.isNotEmpty()) lines.add(cur)
    return lines
}

@Composable
private fun CompareBody(rows: List<List<String>>) {
    if (rows.isEmpty()) return
    val cs = MaterialTheme.colorScheme
    val ac = accents()
    val head = rows[0]
    val n = head.size.coerceAtLeast(2)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (i in 0 until n) {
                val c = if (i == 0) cs.onSurfaceVariant else ac[(i - 1) % ac.size]
                Box(
                    Modifier.weight(if (i == 0) 0.8f else 1f).clip(RoundedCornerShape(8.dp))
                        .background(c.copy(alpha = 0.16f)).padding(8.dp)
                ) {
                    Text(head.getOrElse(i) { "" }, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = c)
                }
            }
        }
        rows.drop(1).forEach { r ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (i in 0 until n) {
                    Box(
                        Modifier.weight(if (i == 0) 0.8f else 1f).fillMaxHeight().clip(RoundedCornerShape(8.dp))
                            .background(cs.surfaceContainerHigh).padding(8.dp)
                    ) {
                        Text(
                            inline(r.getOrElse(i) { "" }), style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (i == 0) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BitsBody(rows: List<List<String>>) {
    val cs = MaterialTheme.colorScheme
    val ac = accents()
    var idx = 0
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text("0", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = cs.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text("31", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = cs.onSurfaceVariant)
        }
        bitRows(rows).forEach { line ->
            val used = line.sumOf { it.second }
            Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                line.forEach { (name, bits) ->
                    val c = ac[idx++ % ac.size]
                    Box(
                        Modifier.weight(bits.toFloat()).fillMaxHeight().clip(RoundedCornerShape(6.dp))
                            .background(c.copy(alpha = 0.16f)).border(1.dp, c.copy(alpha = 0.6f), RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (bits >= 4) "$name\n$bits" else name,
                            Modifier.padding(horizontal = 2.dp), textAlign = TextAlign.Center,
                            fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 12.sp,
                            maxLines = 3, color = cs.onSurface
                        )
                    }
                }
                if (used < 32) Spacer(Modifier.weight((32 - used).toFloat()))
            }
        }
    }
}

@Composable
private fun AnnotBody(rows: List<List<String>>) {
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { r ->
            Column {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(cs.surfaceContainerHighest).padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text(r.getOrElse(0) { "" }, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = cs.primary)
                }
                r.getOrNull(1)?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        inline("\u21B3 $it"), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp, top = 3.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun VArrow(label: String, color: Color, height: Dp = 30.dp) {
    Row(Modifier.fillMaxWidth().height(height).padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.width(14.dp).fillMaxHeight()) {
            val x = size.width / 2
            val sw = 2.dp.toPx()
            val a = 5.dp.toPx()
            drawLine(color, Offset(x, 0f), Offset(x, size.height), sw)
            drawLine(color, Offset(x, size.height), Offset(x - a * 0.7f, size.height - a), sw)
            drawLine(color, Offset(x, size.height), Offset(x + a * 0.7f, size.height - a), sw)
        }
        if (label.isNotBlank()) {
            Spacer(Modifier.width(8.dp))
            Text(label, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = color)
        }
    }
}

@Composable
private fun PathBody(rows: List<List<String>>) {
    val cs = MaterialTheme.colorScheme
    val ac = accents()
    Column {
        rows.forEachIndexed { i, r ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(cs.surfaceContainerHigh).height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.width(3.dp).fillMaxHeight().background(ac[i % ac.size]))
                Column(Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 8.dp)) {
                    Text(r.getOrElse(0) { "" }, style = MaterialTheme.typography.titleSmall)
                    r.getOrNull(1)?.takeIf { it.isNotBlank() }?.let {
                        Text(inline(it), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                }
            }
            if (i < rows.size - 1) VArrow(r.getOrElse(2) { "" }, cs.primary)
        }
    }
}

@Composable
private fun FlowBody(rows: List<List<String>>) {
    val cs = MaterialTheme.colorScheme
    Column {
        rows.forEachIndexed { i, r ->
            val first = r.getOrElse(0) { "" }
            val isQ = first.startsWith("?")
            val text = first.removePrefix("?").removePrefix(">").trim()
            if (isQ) {
                Column(
                    Modifier.fillMaxWidth().border(1.dp, cs.tertiary, RoundedCornerShape(12.dp)).padding(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MonoLabel("?", color = cs.tertiary)
                        Spacer(Modifier.width(8.dp))
                        Text(text, style = MaterialTheme.typography.titleSmall)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        r.drop(1).take(3).forEach { b ->
                            val label = b.substringBefore(":", "").trim()
                            val body = if (b.contains(":")) b.substringAfter(":").trim() else b
                            val c = if (label.equals("yes", true) || label.equals("haan", true)) cs.primary else cs.error
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(c.copy(alpha = 0.14f)).padding(8.dp)
                            ) {
                                if (label.isNotBlank()) MonoLabel(label, color = c)
                                Text(inline(body), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(cs.surfaceContainerHigh).padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Column {
                        Text(inline(text), style = MaterialTheme.typography.titleSmall)
                        r.getOrNull(1)?.takeIf { it.isNotBlank() }?.let {
                            Text(inline(it), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        }
                    }
                }
            }
            if (i < rows.size - 1) VArrow("", cs.outline, 18.dp)
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
