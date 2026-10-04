package com.devsecops.tracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devsecops.tracker.data.DayStatus
import com.devsecops.tracker.data.Extras
import com.devsecops.tracker.data.LibraryCounts
import com.devsecops.tracker.data.ProgressData
import com.devsecops.tracker.data.Repository
import com.devsecops.tracker.data.TopicTime
import kotlinx.coroutines.launch

// Three tabs: Overview (time, streak, heatmap), Library (everything you keep:
// glossary, cards, projects, interview bank, commands, Bandit) and Insights
// (what the data says about how you're learning).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(onBack: () -> Unit, showBackButton: Boolean = true, onOpen: (String) -> Unit = {}) {
    var data by remember { mutableStateOf<ProgressData?>(null) }
    var counts by remember { mutableStateOf<LibraryCounts?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    suspend fun refresh(silent: Boolean = false) {
        if (!silent) loading = true
        error = null
        runCatching { Repository.loadProgressData() }
            .onSuccess { data = it }
            .onFailure { if (!silent) error = it.message ?: "Load failed" }
        counts = runCatching { Extras.libraryCounts() }.getOrNull() ?: counts
        loading = false
    }

    LaunchedEffect(Unit) { refresh() }
    OnResume { scope.launch { refresh(silent = true) } }

    Scaffold(
        topBar = {
            AppTitleBar(
                title = "Progress",
                onBack = if (showBackButton) onBack else null
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SegTabs(listOf("Overview", "Library", "Insights"), tab, { tab = it }, Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                when (tab) {
                    0 -> when {
                        loading -> SkeletonList(3)
                        error != null -> ErrorBlock(error ?: "", { scope.launch { refresh() } })
                        data != null -> OverviewContent(data!!, onOpen)
                    }
                    1 -> LibraryContent(counts, onOpen)
                    else -> InsightsBody()
                }
            }
        }
    }
}

private fun formatMinutes(total: Int): String {
    val h = total / 60
    val m = total % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}

@Composable
private fun OverviewContent(d: ProgressData, onOpen: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Sp.m)) {
        StatTile(formatMinutes(d.totalMinutes), "total time", Modifier.weight(1f))
        AppCard(modifier = Modifier.weight(1f), padding = Sp.m) {
            AnimatedCount(d.totalSessions, style = MaterialTheme.typography.headlineSmall)
            Text("sessions", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AppCard(modifier = Modifier.weight(1f), padding = Sp.m) {
            Row(verticalAlignment = Alignment.Bottom) {
                AnimatedCount(
                    d.currentStreak,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text("d", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            }
            Text("streak", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    MemoryRingCard(onOpenFading = { onOpen("fading") })
    PhaseBarsCard()
    AppCard(onClick = { onOpen("calibration") }, padding = 16.dp) {
        Text("Calibration", style = MaterialTheme.typography.titleSmall)
        Text(
            "How sure you feel vs how right you are",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    AppCard {
        Text("Last 30 days", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        HeatmapGrid(d.last30Days)
        Spacer(Modifier.height(Sp.m))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Sp.s)) {
            Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).border(1.5.dp, barTrackColor(), RoundedCornerShape(3.dp)))
            Text("no study", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(Sp.s))
            Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.primary))
            Text("studied", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    AppCard {
        Text("Time by topic", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        if (d.timeByTopic.isEmpty()) {
            Text(
                "No study sessions logged yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val maxMinutes = d.timeByTopic.maxOf { it.minutes }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                d.timeByTopic.forEach { topic -> TopicBar(topic, maxMinutes) }
            }
        }
    }
    ForgettingCurveCard()
}

private data class LibTile(val route: String, val title: String, val count: (LibraryCounts) -> Int, val unit: String)

private val LIB_TILES = listOf(
    LibTile("glossary", "Glossary", { it.glossary }, "terms"),
    LibTile("add_card", "Revision cards", { it.cards }, "cards, tap to add"),
    LibTile("interview", "Interview practice", { it.interview }, "questions"),
    LibTile("projects", "Projects", { it.projects }, "P01\u2013P09"),
    LibTile("commands", "Command vault", { it.commands }, "saved"),
    LibTile("bandit", "Bandit tracker", { it.bandit }, "of 34 levels")
)

@Composable
private fun LibraryContent(counts: LibraryCounts?, onOpen: (String) -> Unit) {
    LIB_TILES.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            row.forEach { t ->
                AppCard(Modifier.weight(1f), onClick = { onOpen(t.route) }, padding = 16.dp) {
                    Text(
                        counts?.let { t.count(it).toString() } ?: "\u2013",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(t.title, style = MaterialTheme.typography.titleSmall)
                    Text(t.unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    AppCard(onClick = { onOpen("mastery") }, padding = 16.dp) {
        Text("Learn: mastery by module", style = MaterialTheme.typography.titleSmall)
        Text("Evidence levels L1-L5 the tutor recorded", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    AppCard(onClick = { onOpen("gates") }, padding = 16.dp) {
        Text("Gates and labs", style = MaterialTheme.typography.titleSmall)
        Text("Every mastery gate with its checks, and the 22 lab setups with scope and safety rules", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    AppCard(onClick = { onOpen("weak_spots") }, padding = 16.dp) {
        Text("Weak spots", style = MaterialTheme.typography.titleSmall)
        Text("Open gaps the tutor found, and add your own", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    AppCard(onClick = { onOpen("focus") }, padding = 16.dp) {
        Text("Focus timer", style = MaterialTheme.typography.titleSmall)
        Text("Pomodoro that saves a study session", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    AppCard(onClick = { onOpen("planner") }, padding = 16.dp) {
        Text("Plan my day", style = MaterialTheme.typography.titleSmall)
        Text("Pick the time you have and get an ordered plan", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HeatmapGrid(days: List<DayStatus>) {
    val rows = days.chunked(7)
    val studiedCount = days.count { it.studied }
    val track = barTrackColor()
    Column(
        Modifier.semantics { contentDescription = "Studied $studiedCount of last ${days.size} days" },
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        rows.forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                week.forEach { day ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .then(
                                if (day.studied) Modifier.background(MaterialTheme.colorScheme.primary)
                                else Modifier.border(1.5.dp, track, RoundedCornerShape(6.dp))
                            )
                    )
                }
                // Keep the last (short) row's cells the same size as the rest.
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun TopicBar(topic: TopicTime, maxMinutes: Int) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(topic.topicName, style = MaterialTheme.typography.bodyMedium)
            Text(formatMinutes(topic.minutes), style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(4.dp))
        val fraction = if (maxMinutes > 0) (topic.minutes.toFloat() / maxMinutes).coerceIn(0f, 1f) else 0f
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(barTrackColor())
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}
