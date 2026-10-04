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
    val studiedToday = d.last7Days.lastOrNull()?.studied == true
    val overdueWeak = d.overdueWeakSpot
    val reviseDone = d.dueRevisionCount == 0
    val weakDone = overdueWeak == null && d.overdueWeakSpotCount == 0
    val doneCount = listOf(reviseDone, weakDone, studiedToday).count { it }
    val studyText = d.todaysTarget
        ?.takeIf { !it.startsWith("Revise", ignoreCase = true) }
        ?.let { cleanTitle(it) }
        ?: "Pick the next topic in Learn"

    var planOpen by remember { mutableStateOf(false) }
    val chevron by animateFloatAsState(
        targetValue = if (planOpen) 180f else 0f,
        animationSpec = tween(MOTION_MED),
        label = "planChevron"
    )

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Sp.l, Sp.s, Sp.l, Sp.xxl),
        verticalArrangement = Arrangement.spacedBy(Sp.l)
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

        // 1. Header
        item {
            Row(
                Modifier.fillMaxWidth().appear(0).padding(start = Sp.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(greetingText(), style = MaterialTheme.typography.headlineMedium)
                    Text(
                        todayLine(),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }
                IconButton(onClick = { onOpen("search") }) {
                    Icon(Icons.Filled.Search, contentDescription = "Search")
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                }
            }
        }

        // 1a. Attendance: start / break / end day
        item { AttendanceCard(onOpen = { onOpen("attendance") }, modifier = Modifier.appear(1)) }

        // 1b. Tutor sync: where the tutor left off + live Supabase writes
        item { TutorCard(onOpenPath = { onOpen("path") }, modifier = Modifier.appear(1)) }

        // 2. Hero
        item { ContinueCard(onGo = onOpen, modifier = Modifier.appear(1), refreshKey = refreshKey) }

        // 3. Backlog rescue (draws only when needed), directly under the hero
        item { BacklogRescueCard(onGo = onOpen, modifier = Modifier.appear(2), refreshKey = refreshKey) }

        // 4. Status strip (single home for the streak) + last-7-days dots
        item {
            Column(Modifier.appear(3)) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = Sp.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatFigure(d.dueRevisionCount, "cards due", Modifier.weight(1f))
                Box(Modifier.width(1.dp).height(32.dp).background(cs.outlineVariant.copy(alpha = 0.5f)))
                StatFigure(d.currentStreak, if (d.currentStreak == 1) "day streak" else "day streak", Modifier.weight(1f))
                Box(Modifier.width(1.dp).height(32.dp).background(cs.outlineVariant.copy(alpha = 0.5f)))
                StatFigure(d.weekSessionCount, if (d.weekSessionCount == 1) "session / 7d" else "sessions / 7d", Modifier.weight(1f))
            }
            Spacer(Modifier.height(Sp.m))
            DayStrip(d.last7Days)
            if (d.currentStreak == 0 && d.last7Days.any { it.studied }) {
                Spacer(Modifier.height(Sp.s))
                Text(
                    "Streak restarts when you study today",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
            }
        }

        // 5. Memory ring

        // 6. Collapsible plan
        item {
            AppCard(modifier = Modifier.appear(5), padding = Sp.l) {
                Row(
                    Modifier.fillMaxWidth().clickable { planOpen = !planOpen },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Plan \u00B7 $doneCount of 3 done",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.Filled.ExpandMore,
                        contentDescription = if (planOpen) "Collapse plan" else "Expand plan",
                        tint = cs.onSurfaceVariant,
                        modifier = Modifier.graphicsLayer { rotationZ = chevron }
                    )
                }
                AnimatedVisibility(visible = planOpen) {
                    Column {
                        Spacer(Modifier.height(Sp.m))
                        PlanRow(
                            done = reviseDone,
                            title = if (reviseDone) "Revision done" else "Revise ${d.dueRevisionCount} card${if (d.dueRevisionCount == 1) "" else "s"}",
                            sub = d.nextRevision?.takeIf { !reviseDone }?.topicName ?: "Nothing due right now",
                            action = if (reviseDone) null else "Start",
                            onAction = onStartRevision
                        )
                        PlanRow(
                            done = weakDone,
                            title = if (weakDone) "No weak spot overdue" else if (d.overdueWeakSpotCount > 1) "Revisit weak spot (${d.overdueWeakSpotCount} overdue)" else "Revisit weak spot",
                            sub = overdueWeak?.let { cleanTitle(it.title ?: it.reason) } ?: "You're clear",
                            action = if (weakDone) null else "Open",
                            onAction = onViewWeakSpots,
                            warn = !weakDone
                        )
                        PlanRow(
                            done = studiedToday,
                            title = if (studiedToday) "Studied today" else "Study next",
                            sub = studyText,
                            action = if (studiedToday) null else "Open",
                            onAction = onViewMastery
                        )
                    }
                }
            }
        }

        // 7. Quick actions (outlined: the Continue button is the only filled element)
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

@Composable
private fun StatFigure(value: Int, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedCount(
            target = value,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun PlanRow(
    done: Boolean,
    title: String,
    sub: String,
    action: String?,
    onAction: () -> Unit,
    warn: Boolean = false
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusDot(done)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
            )
            Text(
                sub,
                style = MaterialTheme.typography.bodySmall,
                color = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
        if (action != null) {
            FilledTonalButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 16.dp)) {
                Text(action, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

private fun todayLine(): String {
    val d = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    val dow = d.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
    return "$dow ${friendlyDate(d.toString())}"
}

// Ringed calendar dates: a filled disc = studied, an outline = not, and the
// sky-blue ring marks today.
@Composable
private fun DayStrip(days: List<DayStatus>) {
    val studiedCount = days.count { it.studied }
    Row(
        Modifier.fillMaxWidth().semantics { contentDescription = "Studied $studiedCount of last ${days.size} days" },
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        days.forEach { day ->
            val dom = day.date.takeLast(2).trimStart('0')
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    day.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .sizeIn(minWidth = 34.dp, minHeight = 34.dp)
                        .clip(CircleShape)
                        .background(
                            if (day.studied) MaterialTheme.colorScheme.primary
                            else Color.Transparent
                        )
                        .border(
                            width = if (day.isToday) 2.5.dp else 1.5.dp,
                            color = when {
                                day.isToday -> MaterialTheme.colorScheme.secondary
                                day.studied -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.outline
                            },
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        dom,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (day.studied) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
