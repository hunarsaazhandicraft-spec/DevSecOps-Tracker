package com.devsecops.tracker.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devsecops.tracker.data.CardStatus
import com.devsecops.tracker.data.DailyLog
import com.devsecops.tracker.data.Extras
import com.devsecops.tracker.data.GateView
import com.devsecops.tracker.data.MasteryRecord
import com.devsecops.tracker.data.ModuleDetail
import com.devsecops.tracker.data.Repository
import com.devsecops.tracker.data.Topic
import com.devsecops.tracker.data.WeakSpot
import kotlinx.coroutines.launch

// One chapter, everything about it: how it went day by day, what got stuck,
// what each topic's evidence says, the open weak spots and the questions.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModuleDetailScreen(moduleId: Int, onBack: () -> Unit) {
    var detail by remember { mutableStateOf<ModuleDetail?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        loading = true
        error = null
        catching { Repository.loadModuleDetail(moduleId) }
            .onSuccess { detail = it; if (it == null) error = "Module not found" }
            .onFailure { error = it.message ?: "Load failed" }
        loading = false
    }
    LaunchedEffect(moduleId) { refresh() }

    Scaffold(
        topBar = {
            AppTitleBar(
                title = detail?.module?.name ?: "Chapter",
                onBack = onBack
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val d = detail
            when {
                loading -> SkeletonList()
                error != null || d == null -> ErrorBlock(error ?: "", { scope.launch { refresh() } }, Modifier.padding(16.dp))
                else -> DetailBody(d)
            }
        }
    }
}

@Composable
private fun DetailBody(d: ModuleDetail) {
    val latestByTopic = d.records.groupBy { it.topic_id }
        .mapValues { (_, l) -> l.sortedByDescending { it.recorded_at } }
    val openWeak = d.weakSpots.filter { it.cleared_at == null }
    val closedWeak = d.weakSpots.filter { it.cleared_at != null }
    val hours = d.logs.sumOf { it.actual_hours ?: 0.0 }
    val studiedCards = d.cards.filter { it.card.topic_id in d.coveredTopicIds }
    var tab by rememberSaveable { mutableStateOf(0) }
    var gate by remember { mutableStateOf<GateView?>(null) }
    LaunchedEffect(d.module.id) { gate = catching { Extras.loadGate(d.module.id) }.getOrNull() }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryCard(d, hours, openWeak.size)
            SegTabs(listOf("Days", "Topics", "Weak", "Cards", "Gate"), tab, { tab = it })
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 14.dp)
        ) {
            when (tab) {
                0 -> {
                    if (d.logs.isEmpty()) item { EmptyBlock(Icons.Filled.CalendarMonth, "No study days yet", "Days appear once you log study on this module.") }
                    items(d.logs, key = { "log${it.id}" }) { LogCard(it) }
                }
                1 -> items(d.topics, key = { "t${it.id}" }) { t ->
                    TopicCard(
                        topic = t,
                        studied = t.id in d.coveredTopicIds,
                        notes = d.coveredNotes[t.id].orEmpty(),
                        records = latestByTopic[t.id].orEmpty()
                    )
                }
                2 -> {
                    val blockers = d.logs.filter { !it.blocker.isNullOrBlank() }
                    if (openWeak.isEmpty() && closedWeak.isEmpty() && blockers.isEmpty()) {
                        item { EmptyBlock(Icons.Filled.CheckCircle, "No blockers", "Weak spots and blockers for this module show up here.") }
                    }
                    items(openWeak, key = { "w${it.id}" }) { WeakCard(it, d.topics) }
                    items(closedWeak, key = { "w${it.id}" }) { WeakCard(it, d.topics) }
                    items(blockers, key = { "b${it.id}" }) { log ->
                        AppCard(padding = 14.dp) {
                            Text("Day ${log.study_day} \u00B7 ${friendlyDate(log.log_date)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            LabeledText("Blocker", log.blocker, MaterialTheme.colorScheme.error)
                        }
                    }
                }
                3 -> {
                    if (studiedCards.isEmpty()) item { EmptyBlock(Icons.Filled.Quiz, "No cards yet", "Cards appear once their topic has been studied.") }
                    items(studiedCards, key = { "c${it.card.id}" }) { CardRow(it) }
                }
                else -> item {
                    val g = gate
                    if (g == null) SkeletonList(2) else GateContent(g)
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun SummaryCard(d: ModuleDetail, hours: Double, openWeak: Int) {
    val p = d.progress
    GlowCard(glow = p?.status == "In progress", padding = 0.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MonoLabel("// module · ${d.topics.size} topics")
            val pct = if (d.topics.isEmpty()) 0f else d.topics.count { t -> d.records.any { it.topic_id == t.id } }.toFloat() / d.topics.size
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(p?.status ?: "Not started", style = MaterialTheme.typography.titleLarge)
                if (p?.mastery_level != null) {
                    EvidenceLadder(levelNumber(p.mastery_level))
                    LevelChip(p.mastery_level)
                }
                if (p?.target_level != null) {
                    Text("target ${p.target_level}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            ThinBar(pct)
            Text("${d.topics.count { t -> d.records.any { it.topic_id == t.id } }} of ${d.topics.size} topics have evidence \u00B7 ${(pct * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val bits = buildList {
                add("${d.logs.size} study day${if (d.logs.size == 1) "" else "s"}")
                if (hours > 0) add("%.1f h logged".format(hours))
                else if (d.minutes > 0) add("${d.minutes / 60}h ${d.minutes % 60}m logged")
                if (p?.highest_hint != null) add("highest hint ${p.highest_hint}")
                if (p?.gate_result != null) add("gate: ${p.gate_result}")
                if (openWeak > 0) add("$openWeak open weak spot${if (openWeak == 1) "" else "s"}")
            }
            Text(bits.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
            if (p?.start_date != null) {
                Text(
                    "Started ${friendlyDate(p.start_date)}" + (p.completion_date?.let { " · finished ${friendlyDate(it)}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!p?.evidence.isNullOrBlank()) {
                Text(p!!.evidence!!, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun LogCard(log: DailyLog) {
    var open by remember { mutableStateOf(false) }
    AppCard(Modifier.animateContentSize(), onClick = { open = !open }, padding = 0.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Day ${log.study_day} · ${friendlyDate(log.log_date)}", fontWeight = FontWeight.Bold)
                val h = log.actual_hours
                Text(
                    listOfNotNull(log.status, h?.let { if (it > 0.0) "%.1f h".format(it).replace(".0 h", " h") else null }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            log.chapter_topic?.let { Text(cleanTitle(it), style = MaterialTheme.typography.bodyMedium) }

            // Blockers are the point of this screen, so they're never hidden.
            if (!log.blocker.isNullOrBlank()) {
                LabeledText("Blocker", log.blocker, MaterialTheme.colorScheme.error)
            }
            if (open) {
                LabeledText("Learned", log.learning)
                LabeledText("Practice", log.practice)
                LabeledText("Lab", log.lab)
                LabeledText("Revision", log.revision)
                LabeledText("Notes", log.notes)
                LabeledText("Next action", log.next_action)
                LabeledText("Handoff", log.session_handoff)
            } else {
                Text("Details ▾", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LabeledText(label: String, text: String?, color: Color = Color.Unspecified) {
    if (text.isNullOrBlank()) return
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
            color = if (color == Color.Unspecified) MaterialTheme.colorScheme.primary else color)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun TopicCard(topic: Topic, studied: Boolean, notes: List<String>, records: List<MasteryRecord>) {
    var open by remember { mutableStateOf(false) }
    val latest = records.firstOrNull()
    AppCard(Modifier.animateContentSize(), onClick = { open = !open }, padding = 0.dp) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                StatGlyph(topicStat(latest?.level, studied), 22.dp)
                Spacer(Modifier.width(10.dp))
                Text(topic.name, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                when {
                    latest != null -> LevelChip(latest.level)
                    studied -> Text("Studied · not assessed", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary)
                    else -> Text("Not started", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (latest?.hint_used != null) {
                Text("Hint ${latest.hint_used} needed · target ${topic.depth_target ?: "—"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (open) {
                notes.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                records.forEach { r ->
                    Text("${localDateString(r.recorded_at)} · ${r.level}" + (r.hint_used?.let { " · hint $it" } ?: ""),
                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text("“${r.evidence_quote}”", style = MaterialTheme.typography.bodySmall)
                }
                if (notes.isEmpty() && records.isEmpty()) {
                    Text("Nothing recorded yet.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun WeakCard(w: WeakSpot, topics: List<Topic>) {
    val cleared = w.cleared_at != null
    AppCard(padding = 0.dp) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(w.title ?: topics.firstOrNull { it.id == w.topic_id }?.name ?: "Weak spot",
                fontWeight = FontWeight.Bold,
                color = if (cleared) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
            Text(
                listOfNotNull(
                    if (cleared) "Cleared ${localDateString(w.cleared_at)}" else "Open since ${localDateString(w.opened_at)}",
                    w.severity, w.current_level?.let { "now $it" },
                    w.revisit_date?.let { "revisit $it" }
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(w.reason, style = MaterialTheme.typography.bodyMedium)
            LabeledText("Do this", w.required_action)
            LabeledText("Practice", w.practice_needed)
        }
    }
}

@Composable
private fun CardRow(c: CardStatus) {
    var open by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable { open = !open }.animateContentSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(c.card.question, style = MaterialTheme.typography.bodyMedium)
        Text(
            listOfNotNull(c.lastResult?.let { "last: $it" } ?: "not reviewed yet", c.nextDue?.let { "due $it" })
                .joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (open) Text(c.card.answer, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun LevelChip(level: String) {
    val color = when (level) {
        "L1", "L2" -> MaterialTheme.colorScheme.error
        "L3" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(level, fontWeight = FontWeight.Bold, color = color)
    }
}
