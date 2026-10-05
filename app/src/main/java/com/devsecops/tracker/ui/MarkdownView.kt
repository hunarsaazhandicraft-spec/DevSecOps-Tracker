package com.devsecops.tracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// A small, dependency-free Markdown renderer for tutor notes:
// headings, paragraphs, bullets, numbered lists, fenced code (monospace, no
// wrapping), tables, **bold** and `inline code`.

sealed class MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock()
    data class Para(val text: String) : MdBlock()
    data class Bullet(val text: String, val marker: String) : MdBlock()
    data class Code(val text: String) : MdBlock()
    data class Table(val header: List<String>, val rows: List<List<String>>) : MdBlock()
    // ```stack|nest|steps|seq|compare|bits|annot|path|flow <title>  rows are "a | b | c" cells (see DIAGRAM_KINDS)
    data class Diagram(val kind: String, val title: String, val rows: List<List<String>>) : MdBlock()
    // ```callout attacker|defender|misconception|recap|warn|info
    data class Callout(val kind: String, val text: String) : MdBlock()
}

val DIAGRAM_KINDS = setOf("stack", "nest", "steps", "seq", "compare", "bits", "annot", "path", "flow")

private fun splitCells(line: String): List<String> = line.split("|").map { it.trim() }

// An attacker callout directly followed by a defender callout are shown side by side.
fun pairCallouts(blocks: List<MdBlock>): List<List<MdBlock>> {
    val out = ArrayList<List<MdBlock>>()
    var i = 0
    while (i < blocks.size) {
        val b = blocks[i]
        val n = blocks.getOrNull(i + 1)
        if (b is MdBlock.Callout && n is MdBlock.Callout && b.kind == "attacker" && n.kind == "defender") {
            out.add(listOf(b, n)); i += 2
        } else {
            out.add(listOf(b)); i++
        }
    }
    return out
}

private fun splitRow(line: String): List<String> =
    line.trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() }

private fun isSeparatorRow(line: String): Boolean {
    val cells = splitRow(line)
    return cells.isNotEmpty() && cells.all { c -> c.isNotEmpty() && c.all { it == '-' || it == ':' } }
}

fun parseMarkdown(md: String): List<MdBlock> {
    val out = ArrayList<MdBlock>()
    val lines = md.replace("\r\n", "\n").split("\n")
    var i = 0
    val para = StringBuilder()
    fun flushPara() {
        if (para.isNotBlank()) out.add(MdBlock.Para(para.toString().trim()))
        para.clear()
    }
    while (i < lines.size) {
        val line = lines[i]
        val t = line.trim()
        when {
            t.startsWith("```") -> {
                flushPara()
                val head = t.removePrefix("```").trim()
                val kind = head.substringBefore(' ').lowercase()
                val title = head.substringAfter(' ', "").trim()
                val body = ArrayList<String>()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    body.add(lines[i]); i++
                }
                out.add(
                    when {
                        kind in DIAGRAM_KINDS ->
                            MdBlock.Diagram(kind, title, body.filter { it.isNotBlank() }.map { splitCells(it) })
                        kind == "callout" ->
                            MdBlock.Callout(title.lowercase().ifEmpty { "info" }, body.joinToString("\n").trim())
                        else -> MdBlock.Code(body.joinToString("\n").trimEnd('\n'))
                    }
                )
            }
            t.startsWith("#") && t.dropWhile { it == '#' }.startsWith(" ") -> {
                flushPara()
                val level = t.takeWhile { it == '#' }.length.coerceIn(1, 4)
                out.add(MdBlock.Heading(level, t.dropWhile { it == '#' }.trim()))
            }
            t.startsWith("|") && i + 1 < lines.size && isSeparatorRow(lines[i + 1]) -> {
                flushPara()
                val header = splitRow(t)
                i += 2
                val rows = ArrayList<List<String>>()
                while (i < lines.size && lines[i].trim().startsWith("|")) { rows.add(splitRow(lines[i])); i++ }
                out.add(MdBlock.Table(header, rows))
                continue
            }
            t.startsWith("- ") || t.startsWith("* ") -> {
                flushPara(); out.add(MdBlock.Bullet(t.drop(2).trim(), "•"))
            }
            Regex("^\\d+\\.\\s+.*").matches(t) -> {
                flushPara()
                val n = t.substringBefore('.')
                out.add(MdBlock.Bullet(t.substringAfter('.').trim(), "$n."))
            }
            t.isEmpty() -> flushPara()
            else -> { if (para.isNotEmpty()) para.append(' '); para.append(t) }
        }
        i++
    }
    flushPara()
    return out
}

@Composable
internal fun inline(text: String): AnnotatedString {
    val codeBg = MaterialTheme.colorScheme.surfaceContainerHighest
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end > i) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(i + 2, end)) }
                        i = end + 2
                    } else { append(text[i]); i++ }
                }
                text[i] == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end > i) {
                        withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg)) { append(text.substring(i + 1, end)) }
                        i = end + 1
                    } else { append(text[i]); i++ }
                }
                else -> { append(text[i]); i++ }
            }
        }
    }
}

@Composable
fun MarkdownView(md: String, modifier: Modifier = Modifier) {
    val blocks = androidx.compose.runtime.remember(md) { parseMarkdown(md) }
    val groups = androidx.compose.runtime.remember(blocks) { pairCallouts(blocks) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Sp.s)) {
        groups.forEach { g ->
            if (g.size == 2) {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Sp.s)) {
                    g.forEach { CalloutView(it as MdBlock.Callout, Modifier.weight(1f).fillMaxHeight()) }
                }
            } else {
                BlockView(g[0])
            }
        }
    }
}

@Composable
private fun BlockView(b: MdBlock) {
    val cs = MaterialTheme.colorScheme
    when (b) {
        is MdBlock.Heading -> if (b.level >= 3) Row(
            Modifier.padding(top = Sp.s), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Sp.s)
        ) {
            Box(Modifier.width(3.dp).height(18.dp).clip(RoundedCornerShape(2.dp)).background(cs.primary))
            Text(inline(b.text), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        } else Text(
            inline(b.text),
            style = if (b.level == 1) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = Sp.s)
        )
        is MdBlock.Para -> Text(inline(b.text), style = MaterialTheme.typography.bodyMedium, lineHeight = 23.sp)
        is MdBlock.Bullet -> Row(Modifier.padding(start = Sp.xs)) {
            Text(if (b.marker == "-" || b.marker == "*") "•" else b.marker, Modifier.width(24.dp), style = MaterialTheme.typography.bodyMedium, color = cs.primary, fontWeight = FontWeight.Bold)
            Text(inline(b.text), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, lineHeight = 22.sp)
        }
        is MdBlock.Code -> Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(cs.surfaceContainerHighest)
        ) {
            Box(Modifier.fillMaxWidth().background(cs.surfaceContainerHigh).padding(horizontal = Sp.m, vertical = 5.dp)) {
                MonoLabel("terminal / code")
            }
            Box(Modifier.horizontalScroll(rememberScrollState()).padding(Sp.m)) {
                Text(b.text, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 18.sp, softWrap = false)
            }
        }
        is MdBlock.Table -> Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(cs.surfaceContainer)
        ) {
            Row(Modifier.fillMaxWidth().background(cs.primaryContainer.copy(alpha = 0.6f)).padding(horizontal = Sp.s, vertical = Sp.s)) {
                b.header.forEach { Text(inline(it), Modifier.weight(1f).padding(horizontal = Sp.xs), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = cs.onPrimaryContainer) }
            }
            b.rows.forEachIndexed { ri, r ->
                Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Min)
                        .background(if (ri % 2 == 1) cs.surfaceContainerHigh.copy(alpha = 0.6f) else androidx.compose.ui.graphics.Color.Transparent)
                        .padding(horizontal = Sp.s, vertical = 6.dp)
                ) {
                    b.header.indices.forEach { c ->
                        Text(
                            inline(r.getOrElse(c) { "" }), Modifier.weight(1f).padding(horizontal = Sp.xs),
                            style = MaterialTheme.typography.bodySmall, lineHeight = 18.sp,
                            fontWeight = if (c == 0) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
        }
        is MdBlock.Diagram -> DiagramView(b)
        is MdBlock.Callout -> CalloutView(b, Modifier.fillMaxWidth())
    }
}
