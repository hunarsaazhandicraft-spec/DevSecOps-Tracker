package com.devsecops.tracker.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devsecops.tracker.data.ChatState
import com.devsecops.tracker.data.Curriculum
import com.devsecops.tracker.data.CurriculumBlocker
import com.devsecops.tracker.data.Fetch
import com.devsecops.tracker.data.FeedEvent
import com.devsecops.tracker.data.HomeDashboard
import com.devsecops.tracker.data.LogHead
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val LOG_POLL_MS = 30_000L

// ------------------------------------------------------------ pipeline ---

// The day as three stages: warm-up (revision), gap re-test (weak spot),
// new session. Everything is derived from the dashboard we already load.
@Composable
fun PipelineCard(
    d: HomeDashboard,
    onStartRevision: () -> Unit,
    onViewWeakSpots: () -> Unit,
    onViewMastery: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val reviseDone = d.dueRevisionCount == 0
    val weakDone = d.overdueWeakSpot == null && d.overdueWeakSpotCount == 0
    val studied = d.last7Days.lastOrNull()?.studied == true
    val stats = pipelineStats(listOf(reviseDone, weakDone, studied))

    var dayLabel by remember { mutableStateOf("aaj") }
    LaunchedEffect(Unit) {
        Fetch.safeCall { Curriculum.latestLog() }.onSuccess { l ->
            if (l != null) dayLabel = "day-${nextDayNumber(l.status, l.study_day)}"
        }
    }
    val studyText = d.todaysTarget
        ?.takeIf { !it.startsWith("Revise", ignoreCase = true) }
        ?.let { cleanTitle(it) } ?: "Next topic Learn me chuno"

    GlowCard(modifier, glow = stats.contains(Stat.RUN), padding = 16.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(dayLabel, fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = cs.primary)
            Spacer(Modifier.weight(1f))
            MonoLabel("${stats.count { it == Stat.PASS }} / 3")
        }
        Spacer(Modifier.height(14.dp))
        StageRow(
            1, "Warm-up",
            if (reviseDone) "Kuch due nahi" else "${d.dueRevisionCount} card${if (d.dueRevisionCount == 1) "" else "s"} due" +
                (d.nextRevision?.topicName?.let { " · $it" } ?: ""),
            stats[0], false, "Run warm-up", onStartRevision
        )
        StageRow(
            2, "Gap re-test",
            if (weakDone) "Koi gap overdue nahi"
            else (d.overdueWeakSpot?.let { cleanTitle(it.title ?: it.reason) } ?: "Weak spot") +
                (if (d.overdueWeakSpotCount > 1) " · ${d.overdueWeakSpotCount} overdue" else ""),
            stats[1], false, "Open gaps", onViewWeakSpots
        )
        StageRow(
            3, "Naya session", if (studied) "Aaj padh liya" else studyText,
            stats[2], true, "Open next topic", onViewMastery
        )
    }
}

@Composable
private fun StageRow(
    n: Int, title: String, sub: String, stat: Stat, last: Boolean, cta: String, onCta: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val dim = stat == Stat.QUEUED
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(Modifier.width(24.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            StatGlyph(stat)
            if (!last) Box(Modifier.padding(top = 4.dp).width(2.dp).weight(1f).background(cs.outlineVariant))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(bottom = if (last) 0.dp else 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MonoLabel("stage 0$n")
                Pill(
                    stat.label,
                    when (stat) {
                        Stat.PASS, Stat.RUN -> cs.primary
                        Stat.FAIL -> cs.error
                        Stat.PENDING -> cs.tertiary
                        else -> cs.onSurfaceVariant
                    }
                )
            }
            Text(
                title, style = MaterialTheme.typography.titleMedium,
                color = if (dim) cs.onSurfaceVariant else cs.onSurface
            )
            Text(sub, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (stat == Stat.RUN) {
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onCta,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) { Text(cta, style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}

// ---------------------------------------------------------------- tiles ---

@Composable
fun MiniTile(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    val base = modifier.clip(shape).background(cs.surfaceContainer).border(1.dp, cs.outlineVariant, shape)
    Column(
        (if (onClick != null) base.clickableScale(haptic = false, onClick = onClick) else base)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        MonoLabel(label)
        Text(value, style = MaterialTheme.typography.headlineSmall, color = color, maxLines = 1)
    }
}

// -------------------------------------------------------- session strip ---

// The current module's sessions as a row of status glyphs.
@Composable
fun SessionStrip(onOpenPath: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    var code by remember { mutableStateOf<String?>(null) }
    var nodes by remember { mutableStateOf<List<SessionNode>>(emptyList()) }
    LaunchedEffect(Unit) {
        Fetch.safeCall {
            val cur = Curriculum.path().firstOrNull { it.state == ChatState.CURRENT }?.chat
            if (cur == null) null else cur.module_code to sessionNodes(Curriculum.sessionsFor(cur))
        }.onSuccess { r -> if (r != null) { code = r.first; nodes = r.second } }
    }
    if (nodes.isEmpty()) return
    GlowCard(modifier, onClick = onOpenPath, padding = 12.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            MonoLabel("${code ?: "module"} · sessions", Modifier.weight(1f))
            nodes.firstOrNull { it.stat == Stat.RUN }?.let { MonoLabel("agla: ${it.label}", color = cs.primary) }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.Top) {
            nodes.forEachIndexed { i, n ->
                Column(
                    Modifier.width(52.dp).semantics { contentDescription = "Session ${n.label} ${n.stat.label}" },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(Modifier.fillMaxWidth().height(24.dp), contentAlignment = Alignment.Center) {
                        if (i > 0) Box(Modifier.align(Alignment.CenterStart).width(14.dp).height(2.dp).background(cs.outlineVariant))
                        if (i < nodes.size - 1) Box(Modifier.align(Alignment.CenterEnd).width(14.dp).height(2.dp).background(cs.outlineVariant))
                        StatGlyph(n.stat)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        n.label, fontFamily = FontFamily.Monospace, fontSize = 10.sp, maxLines = 1,
                        color = if (n.stat == Stat.RUN) cs.primary else cs.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------ tutor log ---

// "Where the tutor left off", as a small terminal: day, next action, the
// DAY X copy button, open decisions, and the last tutor writes (collapsed).
@Composable
fun TutorLogCard(onOpenPath: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val clipboard = LocalClipboardManager.current
    var log by remember { mutableStateOf<LogHead?>(null) }
    var feed by remember { mutableStateOf<List<FeedEvent>>(emptyList()) }
    var blockers by remember { mutableStateOf<List<CurriculumBlocker>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        Fetch.safeCall { Triple(Curriculum.latestLog(), Curriculum.feed(), Curriculum.blockers()) }
            .onSuccess { log = it.first; feed = it.second; blockers = it.third; failed = false }
            .onFailure { failed = true }
        loaded = true
    }
    LaunchedEffect(Unit) { while (true) { load(); delay(LOG_POLL_MS) } }
    OnResume { scope.launch { load() } }
    LaunchedEffect(copied) { if (copied) { delay(1800); copied = false } }

    if (!loaded) return
    TerminalCard(
        "tutor.log", modifier,
        trailing = if (failed) "offline" else "live",
        trailingColor = if (failed) cs.error else cs.primary
    ) {
        val l = log
        if (l == null) {
            TermLine("abhi tak koi log nahi")
        } else {
            TermLine("day ${l.study_day} · ${l.status ?: "-"}", maxLines = 1)
            l.chapter_topic?.let { TermLine(it) }
            l.next_action?.let { TermLine("next: $it", maxLines = 3) }
            val cmd = "DAY ${nextDayNumber(l.status, l.study_day)}"
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { clipboard.setText(AnnotatedString(cmd)); copied = true }, shape = RoundedCornerShape(10.dp)) {
                    Icon(Icons.Filled.ContentCopy, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (copied) "Copied" else "Copy “$cmd”", style = MaterialTheme.typography.labelLarge)
                }
                OutlinedButton(onClick = onOpenPath, shape = RoundedCornerShape(10.dp)) { Text("Path") }
            }
        }
        if (blockers.isNotEmpty() || feed.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { showMore = !showMore },
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (blockers.isNotEmpty()) {
                    Icon(Icons.Filled.WarningAmber, null, tint = cs.tertiary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("${blockers.size} open decisions", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = cs.tertiary)
                    Spacer(Modifier.width(12.dp))
                }
                Text(
                    if (showMore) "less" else "tutor writes (${feed.size.coerceAtMost(5)})",
                    fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = cs.onSurfaceVariant
                )
            }
            AnimatedVisibility(showMore) {
                Column {
                    blockers.forEach { b ->
                        Text(
                            "${b.code?.let { "$it · " } ?: ""}${b.title}",
                            style = MaterialTheme.typography.bodySmall, color = cs.tertiary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    feed.take(5).forEach { e ->
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                            Text("${e.kind}: ${e.text}", style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text(relativeTime(e.at), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
