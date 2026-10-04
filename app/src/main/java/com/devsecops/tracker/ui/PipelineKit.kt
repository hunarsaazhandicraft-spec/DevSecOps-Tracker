package com.devsecops.tracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// V3 "pipeline" building blocks: one status vocabulary (Stat) drawn the same
// way everywhere, mono labels, terminal-style cards, a glow card.

@Composable
private fun Glyph(bg: Color, fg: Color, icon: ImageVector, sz: Dp, modifier: Modifier) {
    Box(
        modifier.size(sz).clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center
    ) { Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(sz * 0.58f)) }
}

@Composable
fun StatGlyph(stat: Stat, sz: Dp = 24.dp, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val m = modifier.semantics { contentDescription = stat.label }
    when (stat) {
        Stat.PASS -> Glyph(cs.primaryContainer, cs.primary, Icons.Filled.Check, sz, m)
        Stat.FAIL -> Glyph(cs.error.copy(alpha = 0.16f), cs.error, Icons.Filled.Close, sz, m)
        Stat.PENDING -> Glyph(cs.tertiary.copy(alpha = 0.16f), cs.tertiary, Icons.Filled.Refresh, sz, m)
        Stat.LOCKED -> Glyph(cs.surfaceContainerHigh, cs.onSurfaceVariant, Icons.Filled.Lock, sz, m)
        Stat.RUN -> {
            val ring = cs.primary
            Canvas(m.size(sz)) {
                val w = 2.dp.toPx()
                drawCircle(ring.copy(alpha = 0.14f))
                drawCircle(ring.copy(alpha = 0.30f), radius = (this.size.minDimension - w) / 2f, style = Stroke(w))
                drawArc(
                    ring, startAngle = -90f, sweepAngle = 110f, useCenter = false,
                    topLeft = Offset(w / 2f, w / 2f),
                    size = Size(this.size.width - w, this.size.height - w),
                    style = Stroke(w, cap = StrokeCap.Round)
                )
            }
        }
        Stat.QUEUED -> {
            val line = cs.outline
            Canvas(m.size(sz)) {
                val w = 1.5.dp.toPx()
                drawCircle(
                    line, radius = (this.size.minDimension - w) / 2f,
                    style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 6f)))
                )
            }
        }
    }
}

// Small mono caps label: "STAGE 01", "M0.2 · SESSIONS".
@Composable
fun MonoLabel(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text.uppercase(), modifier, color = color, fontFamily = FontFamily.Monospace,
        fontSize = 10.5.sp, letterSpacing = 0.6.sp, maxLines = 1
    )
}

// Evidence level L1..L4 as four small bars.
@Composable
fun EvidenceLadder(level: Int, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier.semantics { contentDescription = "Evidence level $level of 4" },
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        for (i in 0 until 4) {
            Box(
                Modifier.size(12.dp, 6.dp).clip(RoundedCornerShape(2.dp))
                    .background(if (i < level) cs.primary else cs.surfaceContainerHighest)
            )
        }
    }
}

@Composable
fun GlowCard(
    modifier: Modifier = Modifier,
    glow: Boolean = false,
    onClick: (() -> Unit)? = null,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    val bg = if (glow) Brush.verticalGradient(listOf(cs.primaryContainer.copy(alpha = 0.75f), cs.surfaceContainer))
             else Brush.verticalGradient(listOf(cs.surfaceContainer, cs.surfaceContainer))
    val border = if (glow) cs.primary.copy(alpha = 0.35f) else cs.outlineVariant
    val base = modifier.fillMaxWidth().clip(shape).background(bg).border(1.dp, border, shape)
    Column(
        (if (onClick != null) base.clickableScale(haptic = false, onClick = onClick) else base).padding(padding),
        content = content
    )
}

@Composable
fun TerminalCard(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    trailingColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    content: @Composable ColumnScope.() -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    Column(modifier.fillMaxWidth().clip(shape).background(Color(0xFF070909)).border(1.dp, cs.outlineVariant, shape)) {
        Row(Modifier.fillMaxWidth().height(30.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(3) { Box(Modifier.padding(end = 5.dp).size(7.dp).clip(CircleShape).background(cs.surfaceContainerHigh)) }
            Spacer(Modifier.width(4.dp))
            Text(title, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 1)
            if (trailing != null) Text(trailing, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = trailingColor)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(cs.outlineVariant))
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), content = content)
    }
}

// "> text" line inside a TerminalCard.
@Composable
fun TermLine(text: String, modifier: Modifier = Modifier, maxLines: Int = 2) {
    val cs = MaterialTheme.colorScheme
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = cs.primary)) { append("> ") }
            append(text)
        },
        modifier, fontFamily = FontFamily.Monospace, fontSize = 12.5.sp, lineHeight = 19.sp,
        maxLines = maxLines, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
    )
}
