package com.devsecops.tracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.devsecops.tracker.data.ActionKind
import com.devsecops.tracker.data.BacklogPlan
import com.devsecops.tracker.data.ContinueAction
import com.devsecops.tracker.data.Energy
import com.devsecops.tracker.data.Learner
import com.devsecops.tracker.data.LearnerModel
import kotlinx.coroutines.CancellationException

// UI for the learner model: the "Continue" card, backlog rescue and the
// calibration screen. No points, badges or streak shaming: just what to do
// next and why.

@Composable
private fun kindColor(kind: ActionKind): Color = when (kind) {
    ActionKind.REVISE -> MaterialTheme.colorScheme.secondary
    ActionKind.WEAK_SPOT -> MaterialTheme.colorScheme.error
    ActionKind.STUDY_TOPIC -> MaterialTheme.colorScheme.primary
    ActionKind.RECALL_WARMUP -> MaterialTheme.colorScheme.tertiary
    ActionKind.REST -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun kindLabel(kind: ActionKind): String = when (kind) {
    ActionKind.REVISE -> "REVISE"
    ActionKind.WEAK_SPOT -> "WEAK SPOT"
    ActionKind.STUDY_TOPIC -> "STUDY"
    ActionKind.RECALL_WARMUP -> "WARM-UP"
    ActionKind.REST -> "REST"
}

@Composable
fun ContinueCard(
    onGo: (route: String) -> Unit,
    modifier: Modifier = Modifier,
    // Change this (e.g. bump on resume) to force a fresh load from the server.
    refreshKey: Int = 0
) {
    var budgetIdx by remember { mutableStateOf(1) }
    var energyIdx by remember { mutableStateOf(1) }
    var action by remember { mutableStateOf<ContinueAction?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableStateOf(0) }
    val budgets = listOf(30, 60, 90)
    val energies = listOf(Energy.LOW, Energy.OK, Energy.HIGH)

    LaunchedEffect(refreshKey, budgetIdx, energyIdx, retry) {
        loading = action == null
        error = null
        runCatching {
            // Rely on the data layer's short cache; only an explicit retry forces a reload.
            if (retry > 0) Learner.loadModel(force = true)
            Learner.nextAction(budgets[budgetIdx], energies[energyIdx])
        }.onSuccess {
            action = it
        }.onFailure {
            if (it is CancellationException) throw it
            error = it.message ?: "Couldn't work out what's next"
        }
        loading = false
    }

    val heroBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        .compositeOver(MaterialTheme.colorScheme.surfaceContainer)
    AppCard(modifier = modifier, container = heroBg, padding = Sp.xl) {
        val a = action
        when {
            loading && a == null -> SkeletonList(count = 1)
            a == null -> ErrorBlock(error ?: "", onRetry = { retry += 1 })
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Sp.s)) {
                    Pill(kindLabel(a.kind), kindColor(a.kind))
                    if (a.minutes > 0) {
                        Text(
                            "about ${a.minutes} min",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(Sp.m))
                Text(a.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(Sp.s))
                Text(
                    a.why,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Sp.l))
                val isRest = a.kind == ActionKind.REST
                val btnLabel = if (isRest) {
                    if (a.minutes == 0 && a.title.startsWith("Couldn't")) "Open planner" else "Plan tomorrow"
                } else "Continue"
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(
                            if (isRest) MaterialTheme.colorScheme.surfaceContainerHigh
                            else MaterialTheme.colorScheme.primary
                        )
                        .clickableScale(onClick = { onGo(a.route) }),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        btnLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isRest) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }

        Spacer(Modifier.height(Sp.l))
        CompactChips(listOf("30 min", "60 min", "90 min"), budgetIdx, { budgetIdx = it })
        Spacer(Modifier.height(Sp.s))
        CompactChips(listOf("Low energy", "OK", "High energy"), energyIdx, { energyIdx = it })
    }
}

// Compact selector: tonal (not mint-filled) so the Continue button stays the
// only filled element on the Today screen.
@Composable
private fun CompactChips(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Sp.s)) {
        options.forEachIndexed { i, label ->
            val sel = i == selected
            val shape = RoundedCornerShape(16.dp)
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .clip(shape)
                    .background(if (sel) primary.copy(alpha = 0.16f) else Color.Transparent)
                    .border(1.dp, if (sel) primary else MaterialTheme.colorScheme.outlineVariant, shape)
                    .selectable(selected = sel, onClick = { onSelect(i) }, role = Role.RadioButton)
                    .padding(horizontal = Sp.s, vertical = Sp.s),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (sel) primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun BacklogRescueCard(
    onGo: (route: String) -> Unit,
    modifier: Modifier = Modifier,
    refreshKey: Int = 0
) {
    var plan by remember { mutableStateOf<BacklogPlan?>(null) }

    LaunchedEffect(refreshKey) {
        val result = runCatching { Learner.backlogPlan() }
        val p = result.getOrNull()
        val ex = result.exceptionOrNull()
        if (ex is CancellationException) throw ex
        plan = p
    }

    val p = plan ?: return
    AppCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Backlog rescue", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Pill("${p.totalDue} due", MaterialTheme.colorScheme.tertiary)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Nothing is lost. Here is a calm 3-day catch-up instead of one big pile.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        val maxDay = (p.days.maxOfOrNull { it.cards } ?: 1).coerceAtLeast(1)
        p.days.forEach { d ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    d.label, style = MaterialTheme.typography.labelLarge,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "${d.cards} cards" + if (d.mustKeep > 0) " (${d.mustKeep} must-keep)" else "",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.End,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(4.dp))
            ThinBar(d.cards.toFloat() / maxDay.toFloat(), MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(10.dp))
        }

        Text(
            "Must-keep: ${p.mustKeep}   Regular: ${p.regular}   Can wait: ${p.deferrable}",
            style = MaterialTheme.typography.labelMedium
        )
        if (p.mustKeepTopics.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Most at risk: " + p.mustKeepTopics.joinToString(", "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            p.why,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Revise shows the oldest cards first. Stop when you reach today's count.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { onGo("revise") }, modifier = Modifier.fillMaxWidth()) {
            Text("Start today's ${p.days.firstOrNull()?.cards ?: 0} cards")
        }
    }
}

@Composable
fun CalibrationScreen(onBack: () -> Unit) {
    var model by remember { mutableStateOf<LearnerModel?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableStateOf(0) }
    var tab by remember { mutableStateOf(0) }

    LaunchedEffect(retry) {
        loading = true
        error = null
        runCatching { Learner.loadModel(force = true) }
            .onSuccess { model = it }
            .onFailure {
                if (it is CancellationException) throw it
                error = it.message ?: "Load failed"
            }
        loading = false
    }

    Scaffold(
        topBar = {
            AppTitleBar(
                title = "Calibration",
                onBack = onBack,
                subtitle = "How sure you feel vs how right you are"
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val m = model
            when {
                loading && m == null -> SkeletonList(3)
                m == null -> ErrorBlock(error ?: "", onRetry = { retry += 1 })
                else -> {
                    SegTabs(listOf("Confidence", "Watch list"), tab, { tab = it })
                    if (tab == 0) CalibrationTab(m) else WatchListTab(m)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CalibrationTab(m: LearnerModel) {
    val cal = m.calibration
    if (cal.totalRated < 5) {
        EmptyBlock(
            icon = Icons.Filled.Insights,
            title = "Not enough data yet",
            body = cal.message
        )
        AppCard {
            Text("Why this matters", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(cal.why, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val flagColor = when {
        cal.overconfident -> MaterialTheme.colorScheme.error
        cal.underconfident -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    AppCard(container = flagColor.copy(alpha = 0.12f)) {
        Pill(
            when {
                cal.overconfident -> "OVERCONFIDENT"
                cal.underconfident -> "UNDERCONFIDENT"
                else -> "WELL CALIBRATED"
            },
            flagColor
        )
        Spacer(Modifier.height(8.dp))
        Text(cal.message, style = MaterialTheme.typography.bodyLarge)
    }

    val expectedColor = MaterialTheme.colorScheme.outline
    val actualColor = MaterialTheme.colorScheme.secondary
    AppCard {
        Text("Confidence vs accuracy", style = MaterialTheme.typography.titleSmall)
        Text(
            "Grey = what that confidence should score. Blue = what you actually scored.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        val buckets = cal.buckets
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val n = buckets.size
            if (n > 0) {
                val slot = size.width / n
                val barW = slot * 0.26f
                buckets.forEachIndexed { i, b ->
                    val cx = slot * i + slot / 2f
                    val hE = size.height * b.expected
                    drawRect(
                        color = expectedColor,
                        topLeft = Offset(cx - barW - 3f, size.height - hE),
                        size = Size(barW, hE)
                    )
                    val acc = b.accuracy
                    if (acc != null) {
                        val hA = size.height * acc
                        drawRect(
                            color = actualColor,
                            topLeft = Offset(cx + 3f, size.height - hA),
                            size = Size(barW, hA)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            cal.buckets.forEach { b ->
                Text(
                    b.label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        cal.buckets.forEach { b ->
            val acc = b.accuracy
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${b.label} (${labelFor(b.key)})", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(
                    if (acc == null) "no data" else "${b.correct}/${b.total} right (${(acc * 100).toInt()}%)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(4.dp))
            ThinBar(acc ?: 0f, actualColor)
            Spacer(Modifier.height(10.dp))
        }
    }

    AppCard {
        Text("Why this matters", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        Text(cal.why, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun labelFor(key: String): String = when (key) {
    "guess" -> "Guess"
    "sure" -> "Sure"
    else -> "Certain"
}

@Composable
private fun WatchListTab(m: LearnerModel) {
    val risky = m.topics
        .filter { it.studied && (it.confidentMisses > 0 || it.openWeakSpots > 0 || (it.retrievability ?: 1.0) < 0.7 || (it.hintAvg ?: 0.0) >= 3.0) }
        .sortedByDescending {
            it.confidentMisses * 2.0 + it.openWeakSpots * 2.0 + (1.0 - (it.retrievability ?: 1.0)) * 3.0 + (it.hintAvg ?: 0.0) * 0.3
        }
        .take(12)

    if (risky.isEmpty()) {
        EmptyBlock(
            icon = Icons.Filled.Insights,
            title = "Nothing to watch",
            body = "No topic shows fading recall, confident misses, heavy hint use or open weak spots."
        )
        return
    }
    Text(
        "Topics where your feeling of knowing and your results disagree, or recall is slipping.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    risky.forEach { t ->
        AppCard(padding = 14.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    t.topic.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (t.confidentMisses > 0) Pill("sure but wrong ${t.confidentMisses}x", MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(4.dp))
            Text(t.why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val r = t.retrievability
            if (r != null) {
                Spacer(Modifier.height(8.dp))
                ThinBar(r.toFloat(), if (r < 0.7) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        }
    }
}
