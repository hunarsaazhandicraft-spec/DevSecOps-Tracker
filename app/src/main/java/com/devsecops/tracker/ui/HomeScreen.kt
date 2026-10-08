package com.devsecops.tracker.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import com.devsecops.tracker.data.DayStatus
import com.devsecops.tracker.data.HomeDashboard
import com.devsecops.tracker.data.NextRevision
import com.devsecops.tracker.data.Repository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

// "What should I do right now" answered in the first screenful — that's the
// whole brief. One hero action at the top, everything else supporting it.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onLogStudy: () -> Unit,
    onViewWeakSpots: () -> Unit,
    onViewMastery: () -> Unit,
    onViewProgress: () -> Unit,
    onStartRevision: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpen: (String) -> Unit = {}
) {
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    var dashboard by remember { mutableStateOf<HomeDashboard?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshFailed by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        error = null
        runCatching { Repository.loadHomeDashboard() }
            .onSuccess {
                dashboard = it
                refreshFailed = false
                runCatching {
                    com.devsecops.tracker.widget.WidgetData.save(
                        appContext, it.dueRevisionCount, it.currentStreak,
                        it.todaysTarget?.let { t -> cleanTitle(t) } ?: ""
                    )
                }
            }
            .onFailure {
                if (it is CancellationException) throw it
                // Never touch the widget on failure: it keeps the last good snapshot.
                error = it.message ?: "Load failed"
                refreshFailed = dashboard != null
            }
        loading = false
    }

    LaunchedEffect(Unit) { refresh() }
    OnResume {
        refreshKey += 1
        scope.launch { refresh() }
    }

    Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0)) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loading && dashboard == null -> SkeletonList(4)

                error != null && dashboard == null -> ErrorBlock(
                    message = error ?: "",
                    onRetry = { scope.launch { refresh() } },
                    modifier = Modifier.align(Alignment.Center).padding(Sp.xl)
                )

                dashboard != null -> HomeContent(
                    d = dashboard!!,
                    refreshKey = refreshKey,
                    refreshFailed = refreshFailed,
                    onRefresh = { scope.launch { refresh() } },
                    onLogStudy = onLogStudy,
                    onViewWeakSpots = onViewWeakSpots,
                    onViewMastery = onViewMastery,
                    onViewProgress = onViewProgress,
                    onStartRevision = onStartRevision,
                    onOpenSettings = onOpenSettings,
                    onOpen = onOpen
                )
            }
        }
    }
}

private fun greetingText(): String {
    val hour = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour
    return when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..20 -> "Good evening"
        else -> "Still up?"
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
private fun HomeContent(
    d: HomeDashboard,
    refreshKey: Int,
    refreshFailed: Boolean,
    onRefresh: () -> Unit,
    onLogStudy: () -> Unit,
    onViewWeakSpots: () -> Unit,
    onViewMastery: () -> Unit,
    onViewProgress: () -> Unit,
    onStartRevision: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpen: (String) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var smartOpen by remember { mutableStateOf(false) }
    val chevron by animateFloatAsState(
        targetValue = if (smartOpen) 180f else 0f,
        animationSpec = tween(MOTION_MED),
        label = "smartChevron"
    )

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Sp.l, Sp.s, Sp.l, Sp.xxl),
        verticalArrangement = Arrangement.spacedBy(Sp.m)
    ) {
        if (refreshFailed) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(cs.error.copy(alpha = 0.10f))
                        .padding(start = Sp.l, top = Sp.xs, end = Sp.xs, bottom = Sp.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Couldn't refresh \u00B7 showing last data",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.error,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onRefresh) { Text("Retry", color = cs.error) }
                }
            }
        }

        // Header
        item {
            Row(
                Modifier.fillMaxWidth().appear(0).padding(start = Sp.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    MonoLabel("// ${todayLine()}")
                    Text("Aaj", style = MaterialTheme.typography.headlineMedium)
                }
                IconButton(onClick = { onOpen("search") }) {
                    Icon(Icons.Filled.Search, contentDescription = "Search")
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                }
            }
        }

        // Attendance: start / break / end day
        item { AttendanceCard(onOpen = { onOpen("attendance") }, modifier = Modifier.appear(1)) }

        // The day as a pipeline: warm-up, gap re-test, new session
        item {
            PipelineCard(
                d, onStartRevision = onStartRevision, onViewWeakSpots = onViewWeakSpots,
                onViewMastery = onViewMastery, modifier = Modifier.appear(1)
            )
        }

        // Backlog rescue (draws only when needed)
        item { BacklogRescueCard(onGo = onOpen, modifier = Modifier.appear(2), refreshKey = refreshKey) }

        // Three numbers
        item {
            Row(Modifier.fillMaxWidth().appear(2), horizontalArrangement = Arrangement.spacedBy(Sp.s)) {
                MiniTile(
                    "gaps", d.overdueWeakSpotCount.toString(),
                    if (d.overdueWeakSpotCount > 0) cs.error else cs.onSurface,
                    Modifier.weight(1f), onClick = onViewWeakSpots
                )
                MiniTile(
                    "cards due", d.dueRevisionCount.toString(),
                    if (d.dueRevisionCount > 0) cs.tertiary else cs.onSurface,
                    Modifier.weight(1f), onClick = onStartRevision
                )
                MiniTile("streak", "${d.currentStreak}d", cs.onSurface, Modifier.weight(1f), onClick = onViewProgress)
            }
        }

        item { SessionStrip(onOpenPath = { onOpen("path") }, modifier = Modifier.appear(3)) }

        item { TutorLogCard(onOpenPath = { onOpen("path") }, modifier = Modifier.appear(3)) }

        item { PaceCard(modifier = Modifier.appear(3), refreshKey = refreshKey) }

        // Smart pick (time and energy), collapsed by default
        item {
            GlowCard(onClick = { smartOpen = !smartOpen }, padding = 14.dp, modifier = Modifier.appear(4)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Kya karu abhi?", style = MaterialTheme.typography.titleSmall)
                        Text("30 / 60 / 90 min aur energy ke hisaab se suggestion", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                    Icon(
                        Icons.Filled.ExpandMore,
                        contentDescription = if (smartOpen) "Collapse" else "Expand",
                        tint = cs.onSurfaceVariant,
                        modifier = Modifier.graphicsLayer { rotationZ = chevron }
                    )
                }
            }
        }
        item {
            AnimatedVisibility(visible = smartOpen) {
                ContinueCard(onGo = onOpen, refreshKey = refreshKey)
            }
        }

        item {
            GlowCard(onClick = { onOpen("notes") }, padding = 14.dp, modifier = Modifier.appear(5)) {
                Text("Notes", style = MaterialTheme.typography.titleSmall)
                Text("Topic-wise deep notes aur apne words", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
        }

        // Quick actions
        item {
            Row(
                Modifier.fillMaxWidth().appear(6),
                horizontalArrangement = Arrangement.spacedBy(Sp.s)
            ) {
                OutlinedButton(
                    onClick = onLogStudy,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = Sp.m)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Sp.xs))
                    Text("Log study", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
                OutlinedButton(
                    onClick = { onOpen("focus") },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = Sp.m)
                ) {
                    Icon(Icons.Filled.Timer, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Sp.xs))
                    Text("Focus", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
                OutlinedButton(
                    onClick = { onOpen("planner") },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = Sp.m)
                ) { Text("Plan", style = MaterialTheme.typography.labelLarge, maxLines = 1) }
            }
        }
    }
}

private fun todayLine(): String {
    val d = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    val dow = d.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
    return "$dow ${friendlyDate(d.toString())}"
}
