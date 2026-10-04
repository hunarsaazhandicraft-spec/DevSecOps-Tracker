package com.devsecops.tracker.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.TextButton
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.devsecops.tracker.data.Extras
import com.devsecops.tracker.data.Fsrs
import com.devsecops.tracker.data.Memory
import com.devsecops.tracker.data.StrengthSummary
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// Canvas-only charts. No dependency on Motion.kt.

private const val ANIM_MS = 500

// ---------------------------------------------------------------- MemoryRing

@Composable
fun MemoryRing(
    progress: Float,
    centerTop: String,
    centerBottom: String,
    modifier: Modifier = Modifier,
    fadingFraction: Float = 0f,
    size: Dp = 140.dp
) {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val target = if (started) progress.coerceIn(0f, 1f) else 0f
    val sweepFraction by animateFloatAsState(target, tween(ANIM_MS), label = "ringSweep")
    val fadeTarget = if (started) fadingFraction.coerceIn(0f, 1f) else 0f
    val fadeFraction by animateFloatAsState(fadeTarget, tween(ANIM_MS), label = "ringFade")

    val primary = MaterialTheme.colorScheme.primary
    val amber = MaterialTheme.colorScheme.tertiary
    val track = barTrackColor()
    val ringDesc = "$centerTop $centerBottom" + if (fadingFraction > 0f) ", some cards fading" else ""

    Box(
        modifier.size(size).semantics { contentDescription = ringDesc },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val mainStroke = this.size.width * 0.085f
            val thin = this.size.width * 0.02f
            val outerInset = thin / 2f
            val mainInset = thin + mainStroke * 0.9f
            val outerRect = Size(this.size.width - outerInset * 2, this.size.height - outerInset * 2)
            val mainRect = Size(this.size.width - mainInset * 2, this.size.height - mainInset * 2)
            if (mainRect.width > 0f && mainRect.height > 0f) {
                drawArc(
                    color = track, startAngle = 0f, sweepAngle = 360f, useCenter = false,
                    topLeft = Offset(mainInset, mainInset), size = mainRect,
                    style = Stroke(width = mainStroke)
                )
                if (sweepFraction > 0.001f) {
                    drawArc(
                        color = primary, startAngle = -90f, sweepAngle = 360f * sweepFraction,
                        useCenter = false, topLeft = Offset(mainInset, mainInset), size = mainRect,
                        style = Stroke(width = mainStroke, cap = StrokeCap.Round)
                    )
                }
            }
            if (fadeFraction > 0.001f && outerRect.width > 0f && outerRect.height > 0f) {
                drawArc(
                    color = amber, startAngle = -90f, sweepAngle = 360f * fadeFraction,
                    useCenter = false, topLeft = Offset(outerInset, outerInset), size = outerRect,
                    style = Stroke(width = thin, cap = StrokeCap.Round)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centerTop, style = MaterialTheme.typography.headlineMedium, maxLines = 1)
            if (centerBottom.isNotEmpty()) {
                Text(
                    centerBottom, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1
                )
            }
        }
    }
}

// ------------------------------------------------------------ MemoryRingCard

@Composable
fun MemoryRingCard(onOpenFading: () -> Unit, modifier: Modifier = Modifier) {
    var summary by remember { mutableStateOf<StrengthSummary?>(null) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        summary = runCatching { Memory.strengthSummary() }.getOrNull()
        loading = false
    }
    val s = summary
    when {
        loading -> AppCard(modifier) {
            Box(Modifier.fillMaxWidth().height(140.dp))
        }
        s == null || s.reviewedCards == 0 -> AppCard(modifier) {
            EmptyBlock(
                icon = Icons.Filled.Check,
                title = "No memory data yet",
                body = "Review a few cards and your memory strength will show up here."
            )
        }
        else -> {
            val pct = (s.averageRetrievability * 100).roundToInt().coerceIn(0, 100)
            val fadingFrac = if (s.reviewedCards > 0) s.fadingSoonCount.toFloat() / s.reviewedCards else 0f
            AppCard(modifier, onClick = onOpenFading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MemoryRing(
                        progress = s.averageRetrievability.toFloat(),
                        centerTop = "$pct%",
                        centerBottom = "remembered",
                        fadingFraction = fadingFrac
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill(
                            "${s.fadingSoonCount} fading soon",
                            if (s.fadingSoonCount > 0) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.primary
                        )
                        s.topics.take(3).forEach { t ->
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row {
                                    Text(
                                        t.topicName, style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1, modifier = Modifier.weight(1f)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "${(t.strength * 100).roundToInt()}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                ThinBar(
                                    t.strength.toFloat(),
                                    if (t.strength < 0.7) MaterialTheme.colorScheme.error
                                    else if (t.strength < 0.85) MaterialTheme.colorScheme.tertiary
                                    else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------- SkillMapCard

@Composable
fun SkillMapCard(modifier: Modifier = Modifier, preloaded: List<Float>? = null) {
    var values by remember { mutableStateOf<List<Float>?>(preloaded) }
    LaunchedEffect(Unit) {
        if (preloaded != null) return@LaunchedEffect
        val data = runCatching { Extras.loadInsights() }.getOrNull()
        val arr = FloatArray(8)
        data?.phaseSkills?.forEach { (name, v) ->
            val n = Regex("(\\d+)").find(name)?.groupValues?.get(1)?.toIntOrNull()
            if (n != null && n in 0..7) arr[n] = v.coerceIn(0f, 1f)
        }
        values = arr.toList()
    }
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val grow by animateFloatAsState(if (started) 1f else 0f, tween(ANIM_MS), label = "radarGrow")

    val primary = MaterialTheme.colorScheme.primary
    val grid = barTrackColor()
    val axis = MaterialTheme.colorScheme.outline

    AppCard(modifier) {
        GroupHeader("Skill map", "Share of topics at mastery level 3+, per phase")
        Spacer(Modifier.height(8.dp))
        val v = values
        if (v == null) {
            Box(Modifier.fillMaxWidth().height(240.dp))
        } else {
            val boxSize = 240.dp
            val radius = 88.dp
            val labelRadius = 108.dp
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(boxSize).semantics {
                        contentDescription = "Skill map radar: " + (0 until 8).joinToString(", ") { "P$it ${(v[it] * 100).roundToInt()} percent" }
                    }
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val c = Offset(this.size.width / 2f, this.size.height / 2f)
                        val r = radius.toPx()
                        val n = 8
                        fun pt(i: Int, frac: Float): Offset {
                            val a = (-Math.PI / 2 + 2 * Math.PI * i / n).toDouble()
                            return Offset(c.x + (r * frac * cos(a)).toFloat(), c.y + (r * frac * sin(a)).toFloat())
                        }
                        for (ring in 1..4) {
                            val f = ring / 4f
                            val p = Path()
                            for (i in 0 until n) {
                                val o = pt(i, f)
                                if (i == 0) p.moveTo(o.x, o.y) else p.lineTo(o.x, o.y)
                            }
                            p.close()
                            drawPath(p, color = grid, style = Stroke(width = 1.dp.toPx()))
                        }
                        for (i in 0 until n) {
                            drawLine(axis.copy(alpha = 0.5f), c, pt(i, 1f), strokeWidth = 1.dp.toPx())
                        }
                        val poly = Path()
                        for (i in 0 until n) {
                            val o = pt(i, v[i] * grow)
                            if (i == 0) poly.moveTo(o.x, o.y) else poly.lineTo(o.x, o.y)
                        }
                        poly.close()
                        drawPath(poly, color = primary.copy(alpha = 0.22f), style = Fill)
                        drawPath(
                            poly, color = primary,
                            style = Stroke(width = 2.dp.toPx(), join = StrokeJoin.Round)
                        )
                        for (i in 0 until n) {
                            drawCircle(primary, radius = 3.5.dp.toPx(), center = pt(i, v[i] * grow))
                        }
                    }
                    for (i in 0 until 8) {
                        val a = -Math.PI / 2 + 2 * Math.PI * i / 8
                        val cx = boxSize / 2 + labelRadius * cos(a).toFloat()
                        val cy = boxSize / 2 + labelRadius * sin(a).toFloat()
                        Box(
                            Modifier.wrapContentSize().offset(x = cx - 10.dp, y = cy - 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "P$i", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center, maxLines = 1
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                for (i in 0 until 8) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "P$i", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text("${(v[i] * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------- ForgettingCurveCard

@Composable
fun ForgettingCurveCard(modifier: Modifier = Modifier) {
    var summary by remember { mutableStateOf<StrengthSummary?>(null) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        summary = runCatching { Memory.strengthSummary() }.getOrNull()
        loading = false
    }
    val topic = summary?.topics?.firstOrNull()   // weakest first
    var expanded by remember { mutableStateOf(false) }

    AppCard(modifier) {
        GroupHeader("Example curve", "How a typical memory fades and how review resets it")
        Spacer(Modifier.height(8.dp))
        if (loading) {
            Box(Modifier.fillMaxWidth().height(160.dp))
        } else if (topic == null) {
            EmptyBlock(
                icon = Icons.Filled.Check,
                title = "Nothing to plot yet",
                body = "Once you have reviewed cards, your weakest topic's curve appears here."
            )
        } else if ((summary?.reviewedCards ?: 0) < 20 && !expanded) {
            TextButton(onClick = { expanded = true }) { Text("See example curve") }
        } else {
            // Approximation: the per-topic summary has no stability or review date, only mean
            // retrievability R now. Assume the last review was ASSUMED_ELAPSED days ago and solve
            // R = (1 + F*t/S)^-0.5 for S, with F = 19/81 (the FSRS constant), i.e.
            // S = F*t / (R^-2 - 1). If R is ~100% (undefined) fall back to S = 5.
            // After a review we draw stability x2.5 (typical "good" growth), starting at 100%.
            val assumedElapsed = 3.0
            val r = topic.strength.coerceIn(0.05, 0.999)
            val stability = if (topic.strength >= 0.999) 5.0 else {
                val denom = 1.0 / (r * r) - 1.0
                if (denom <= 1e-6) 5.0 else ((19.0 / 81.0) * assumedElapsed / denom).coerceIn(0.5, 90.0)
            }
            val t0 = if (topic.strength >= 0.999) 0.0 else assumedElapsed
            val nowR = if (topic.strength >= 0.999) 1.0 else r
            val boosted = stability * 2.5

            val curveColor = MaterialTheme.colorScheme.error
            val reviewColor = MaterialTheme.colorScheme.primary
            val targetColor = MaterialTheme.colorScheme.tertiary
            val grid = MaterialTheme.colorScheme.outlineVariant
            val bgColor = MaterialTheme.colorScheme.background

            var started by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { started = true }
            val reveal by animateFloatAsState(if (started) 1f else 0f, tween(ANIM_MS), label = "curveReveal")

            Canvas(Modifier.fillMaxWidth().height(160.dp)) {
                val w = this.size.width
                val h = this.size.height
                val days = 30.0
                fun x(t: Double) = (t / days * w).toFloat()
                fun y(rv: Double) = ((1.0 - rv.coerceIn(0.0, 1.0)) * h).toFloat()
                val dash = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
                // light horizontal grid at 25/50/75/100%
                for (g in 0..4) {
                    val gy = h * g / 4f
                    drawLine(grid, Offset(0f, gy), Offset(w, gy), strokeWidth = 1.dp.toPx())
                }
                // 90% target
                drawLine(
                    targetColor, Offset(0f, y(0.9)), Offset(w, y(0.9)),
                    strokeWidth = 1.5.dp.toPx(), pathEffect = dash
                )
                // current curve
                val cur = Path()
                val steps = 60
                val lastStep = (steps * reveal).toInt().coerceIn(1, steps)
                for (i in 0..lastStep) {
                    val t = days * i / steps
                    val px = x(t); val py = y(Fsrs.forgettingCurve(t, stability))
                    if (i == 0) cur.moveTo(px, py) else cur.lineTo(px, py)
                }
                drawPath(cur, curveColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                // after-review curve, from the current day
                val after = Path()
                var first = true
                for (i in 0..steps) {
                    val t = t0 + (days - t0) * i / steps
                    val px = x(t); val py = y(Fsrs.forgettingCurve(t - t0, boosted))
                    if (first) { after.moveTo(px, py); first = false } else after.lineTo(px, py)
                }
                if (reveal > 0.95f) {
                    drawPath(after, reviewColor, style = Stroke(width = 2.dp.toPx(), pathEffect = dash, cap = StrokeCap.Round))
                }
                // current point
                drawCircle(bgColor, radius = 7.dp.toPx(), center = Offset(x(t0), y(nowR)))
                drawCircle(curveColor, radius = 5.dp.toPx(), center = Offset(x(t0), y(nowR)))
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("today", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("90% target", style = MaterialTheme.typography.labelSmall, color = targetColor)
                Text("30 days", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Example curve, not a measurement: it uses ${topic.topicName} only as a starting point. Memory fades over time and each review resets it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ------------------------------------------------------------- MiniSparkline

@Composable
fun MiniSparkline(values: List<Float>, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier.size(width = 72.dp, height = 28.dp)) {
        val clean = values.filter { !it.isNaN() && !it.isInfinite() }
        if (clean.size < 2 || this.size.width <= 0f || this.size.height <= 0f) return@Canvas
        val pad = 4.dp.toPx()
        val lo = clean.min()
        val hi = clean.max()
        val range = if (hi - lo < 1e-6f) 1f else hi - lo
        val w = this.size.width - pad * 2
        val h = this.size.height - pad * 2
        val path = Path()
        var last = Offset.Zero
        clean.forEachIndexed { i, v ->
            val px = pad + w * i / (clean.size - 1)
            val py = if (hi - lo < 1e-6f) pad + h / 2f else pad + h * (1f - (v - lo) / range)
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            last = Offset(px, py)
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(color, radius = 3.dp.toPx(), center = last)
    }
}

// ------------------------------------------------------------ PhaseBarsCard

private val PHASE_NAMES = listOf(
    "Phase 0 \u2014 Foundations",
    "Phase 1 \u2014 Programming",
    "Phase 2 \u2014 Web engineering",
    "Phase 3 \u2014 Web security",
    "Phase 4 \u2014 AppSec",
    "Phase 5 \u2014 DevSecOps",
    "Phase 6 \u2014 Cloud security",
    "Phase 7 \u2014 Capstone"
)

@Composable
fun PhaseBarsCard(modifier: Modifier = Modifier) {
    var values by remember { mutableStateOf<List<Float>?>(null) }
    LaunchedEffect(Unit) {
        val data = runCatching { Extras.loadInsights() }.getOrNull()
        val arr = FloatArray(8)
        data?.phaseSkills?.forEach { (name, v) ->
            val n = Regex("(\\d+)").find(name)?.groupValues?.get(1)?.toIntOrNull()
            if (n != null && n in 0..7) arr[n] = v.coerceIn(0f, 1f)
        }
        values = arr.toList()
    }
    val v = values
    if (v != null && v.count { it > 0f } >= 3) {
        SkillMapCard(modifier, preloaded = v)
    }
    AppCard(modifier) {
        GroupHeader("Phase progress", "Share of topics at mastery level 3+")
        Spacer(Modifier.height(8.dp))
        if (v == null) {
            Box(Modifier.fillMaxWidth().height(120.dp))
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (i in 0 until 8) {
                    val pct = (v[i] * 100).roundToInt()
                    Column(
                        Modifier.fillMaxWidth().semantics {
                            contentDescription = "${PHASE_NAMES[i]}, $pct percent"
                        }
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                PHASE_NAMES[i], style = MaterialTheme.typography.bodySmall,
                                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "$pct%", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        ThinBar(v[i])
                    }
                }
            }
        }
    }
}
