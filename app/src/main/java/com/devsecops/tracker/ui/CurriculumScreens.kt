package com.devsecops.tracker.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devsecops.tracker.data.ChatPlanRow
import com.devsecops.tracker.data.ChatState
import com.devsecops.tracker.data.CoverageEntry
import com.devsecops.tracker.data.Curriculum
import com.devsecops.tracker.data.CurriculumBlocker
import com.devsecops.tracker.data.DocBody
import com.devsecops.tracker.data.DocMeta
import com.devsecops.tracker.data.Fetch
import com.devsecops.tracker.data.FeedEvent
import com.devsecops.tracker.data.LogHead
import com.devsecops.tracker.data.PathChat
import com.devsecops.tracker.data.SessionView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

// =========================================================================
// Screens built on the Supabase course skeleton: Path (52-chat trail),
// Chat detail (sessions + coverage table), Library (project files),
// Doc reader, and the tutor card on Today.
// Everything here is read-only; the tutor writes, the app shows.
// =========================================================================

private const val LIVE_POLL_MS = 30_000L

private val PHASE_NAMES = mapOf(
    1 to "Phase 0 · Foundations", 2 to "Phase 1 · Programming", 3 to "Phase 2 · Web Engineering",
    4 to "Phase 3 · Web Security / VAPT", 5 to "Phase 4 · AppSec Engineering", 6 to "Phase 5 · DevSecOps",
    7 to "Phase 6 · Cloud & Container", 8 to "Phase 7 · Supply Chain + Capstone"
)

private fun fmtHours(h: Double?): String =
    if (h == null) "" else if (h % 1.0 == 0.0) "${h.toInt()}h" else "${h}h"

fun relativeTime(ts: String?): String {
    if (ts.isNullOrBlank()) return ""
    val t = runCatching { Instant.parse(ts) }.getOrNull() ?: return ts.take(10)
    val mins = (Clock.System.now() - t).inWholeMinutes
    return when {
        mins < 1 -> "just now"
        mins < 60 -> "${mins}m ago"
        mins < 60 * 24 -> "${mins / 60}h ago"
        else -> "${mins / (60 * 24)}d ago"
    }
}

// ---------------------------------------------------------------- Path ---

@Composable
fun PathScreen(onOpenChat: (Int) -> Unit) {
    var chats by remember { mutableStateOf<List<PathChat>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        Fetch.safeCall { Curriculum.path() }
            .onSuccess { chats = it; error = null }
            .onFailure { error = it.message ?: "Load failed" }
    }
    LaunchedEffect(Unit) { load() }
    OnResume { scope.launch { load() } }

    val list = chats
    when {
        list == null && error == null -> SkeletonList(5)
        list == null -> ErrorBlock(error ?: "", onRetry = { scope.launch { load() } }, modifier = Modifier.padding(Sp.xl))
        else -> {
            val doneCount = list.count { it.state == ChatState.DONE }
            val totalH = list.sumOf { it.chat.hours ?: 0.0 }
            val doneH = list.filter { it.state == ChatState.DONE }.sumOf { it.chat.hours ?: 0.0 }
            val current = list.firstOrNull { it.state == ChatState.CURRENT }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(Sp.l, Sp.s, Sp.l, Sp.xxl),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                item {
                    Column(Modifier.appear(0).padding(start = Sp.xs, bottom = Sp.m)) {
                        Text("Your path", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "$doneCount of ${list.size} chats done · ${fmtHours(doneH)} of ${fmtHours(totalH)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(Sp.s))
                        ThinBar(if (totalH > 0) (doneH / totalH).toFloat() else 0f)
                    }
                }
                if (current != null) {
                    item {
                        AppCard(
                            modifier = Modifier.appear(1).padding(bottom = Sp.l),
                            onClick = { onOpenChat(current.chat.chat_no) },
                            container = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text("NOW · CHAT #${current.chat.chat_no}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text(current.chat.title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            current.chat.deliverable?.let {
                                Spacer(Modifier.height(Sp.xs))
                                Text("Deliverable: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            Spacer(Modifier.height(Sp.s))
                            Text("Open sessions & coverage →", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
                val groups = list.groupBy { it.chat.phase_id ?: 0 }
                groups.toSortedMap().forEach { (phase, rows) ->
                    item {
                        GroupHeader(
                            PHASE_NAMES[phase] ?: "Other",
                            "${rows.count { it.state == ChatState.DONE }}/${rows.size} chats · ${fmtHours(rows.sumOf { it.chat.hours ?: 0.0 })}",
                            Modifier.padding(top = Sp.m, bottom = Sp.s)
                        )
                    }
                    items(rows, key = { it.chat.chat_no }) { pc ->
                        TrailRow(pc, isLast = pc == rows.last(), onClick = { onOpenChat(pc.chat.chat_no) })
                    }
                }
            }
        }
    }
}

@Composable
private fun TrailRow(pc: PathChat, isLast: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val lineColor = if (pc.state == ChatState.DONE) cs.primary else cs.outlineVariant
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
        verticalAlignment = Alignment.Top
    ) {
        Box(Modifier.width(40.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            if (!isLast) {
                Box(Modifier.padding(top = 30.dp).width(3.dp).fillMaxHeight().background(lineColor))
            }
            TrailNode(pc.state, Modifier.padding(top = 10.dp))
        }
        Column(Modifier.weight(1f).padding(start = Sp.s, top = 10.dp, bottom = Sp.l, end = Sp.s)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "#${pc.chat.chat_no}  ${pc.chat.title}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (pc.state == ChatState.CURRENT) FontWeight.Bold else FontWeight.Normal,
                    color = if (pc.state == ChatState.LOCKED || pc.state == ChatState.UPCOMING) cs.onSurfaceVariant else cs.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(fmtHours(pc.chat.hours), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            }
            Spacer(Modifier.height(Sp.xs))
            Row(horizontalArrangement = Arrangement.spacedBy(Sp.xs)) {
                FileBadge(pc.chat.teaching_file_status)
                if (pc.state == ChatState.LOCKED || pc.state == ChatState.UPCOMING) {
                    pc.chat.prerequisite?.takeIf { it.isNotBlank() }?.let {
                        Pill("after: $it", cs.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun TrailNode(state: ChatState, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    when (state) {
        ChatState.DONE -> Icon(Icons.Filled.CheckCircle, "done", tint = cs.primary, modifier = modifier.size(22.dp).background(cs.surface, CircleShape))
        ChatState.CURRENT -> {
            val t = rememberInfiniteTransition(label = "node")
            val s by t.animateFloat(0.7f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "nodePulse")
            Box(modifier.size(22.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size((22 * s).dp).clip(CircleShape).background(cs.primary.copy(alpha = 0.3f)))
                Box(Modifier.size(12.dp).clip(CircleShape).background(cs.primary))
            }
        }
        ChatState.LOCKED -> Icon(Icons.Filled.Lock, "locked", tint = cs.onSurfaceVariant, modifier = modifier.size(20.dp).background(cs.surface, CircleShape))
        ChatState.UPCOMING -> Box(modifier.size(16.dp).clip(CircleShape).border(3.dp, cs.outlineVariant, CircleShape).background(cs.surface))
    }
}

@Composable
private fun FileBadge(status: String) {
    val cs = MaterialTheme.colorScheme
    when (status) {
        "ready" -> Pill("file ready", cs.primary)
        "locked" -> Pill("spec needed", cs.error)
        else -> Pill("file to write", cs.tertiary)
    }
}

// ---------------------------------------------------------- Chat detail ---

@Composable
fun ChatDetailScreen(chatNo: Int, onBack: () -> Unit, onOpenDoc: (String) -> Unit) {
    var chat by remember { mutableStateOf<ChatPlanRow?>(null) }
    var sessions by remember { mutableStateOf<List<SessionView>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    suspend fun load() {
        Fetch.safeCall {
            val c = Curriculum.chats().firstOrNull { it.chat_no == chatNo } ?: throw IllegalStateException("Chat #$chatNo not found")
            c to Curriculum.sessionsFor(c)
        }.onSuccess { chat = it.first; sessions = it.second; error = null }
            .onFailure { error = it.message ?: "Load failed" }
    }
    LaunchedEffect(chatNo) { load() }
    OnResume { scope.launch { load() } }
    LaunchedEffect(copied) { if (copied) { delay(1800); copied = false } }

    Scaffold(topBar = { AppTitleBar(title = "Chat #$chatNo", onBack = onBack, subtitle = chat?.module_code) }) { pad ->
        val c = chat
        when {
            c == null && error == null -> SkeletonList(4, Modifier.padding(pad))
            c == null -> ErrorBlock(error ?: "", onRetry = { scope.launch { load() } }, modifier = Modifier.padding(pad).padding(Sp.xl))
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(pad),
                contentPadding = PaddingValues(Sp.l, Sp.s, Sp.l, Sp.xxl),
                verticalArrangement = Arrangement.spacedBy(Sp.m)
            ) {
                item {
                    AppCard(Modifier.appear(0)) {
                        Text(c.title, style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(Sp.s))
                        Row(horizontalArrangement = Arrangement.spacedBy(Sp.xs)) {
                            FileBadge(c.teaching_file_status)
                            if (c.hours != null) Pill("~${fmtHours(c.hours)}", MaterialTheme.colorScheme.secondary)
                        }
                        InfoLine("Starts after", c.prerequisite)
                        InfoLine("Deliverable", c.deliverable)
                        c.teaching_file_ref?.let { ref ->
                            Spacer(Modifier.height(Sp.s))
                            OutlinedButton(onClick = { onOpenDoc(ref) }) {
                                Icon(Icons.Filled.Description, null, Modifier.size(18.dp)); Spacer(Modifier.width(Sp.s)); Text("Open $ref", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                c.start_message?.takeIf { it.isNotBlank() }?.let { msg ->
                    item {
                        AppCard(Modifier.appear(1)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Chat start message", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                FilledTonalButton(onClick = { clipboard.setText(AnnotatedString(msg)); copied = true }) {
                                    Icon(Icons.Filled.ContentCopy, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(if (copied) "Copied" else "Copy")
                                }
                            }
                            Spacer(Modifier.height(Sp.s))
                            Text(msg, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, maxLines = 8, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                val ss = sessions.orEmpty()
                if (ss.isEmpty()) {
                    item {
                        AppCard(Modifier.appear(2)) {
                            Text("No session list yet", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (c.teaching_file_status == "ready") "This chat is taught from its project/gate file; there is no session-by-session must-cover list."
                                else "The teaching file for this module isn't written yet. The tutor writes it (Must-cover list, minimum time, checks) before this chat starts.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    item {
                        val v = ss.sumOf { it.verified }; val t = ss.sumOf { it.total }
                        GroupHeader("Sessions", "$v of $t must-cover items verified by the tutor · ✅ needs your own words at Hint 2 or lower")
                    }
                    items(ss, key = { it.session.id }) { sv -> SessionCard(sv) }
                }
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Spacer(Modifier.height(Sp.s))
    Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun SessionCard(sv: SessionView) {
    val cs = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(sv.verified < sv.total) }
    val recorded = sv.items.count { it.second != null }
    AppCard(onClick = { open = !open }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${sv.session.module_code} · S${sv.session.session_no}", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                Text(sv.session.title, style = MaterialTheme.typography.titleMedium)
            }
            Icon(Icons.Filled.ExpandMore, null, Modifier.rotate(if (open) 180f else 0f))
        }
        Spacer(Modifier.height(Sp.s))
        if (sv.total > 0) {
            ThinBar(sv.verified.toFloat() / sv.total, if (sv.verified == sv.total) cs.primary else cs.tertiary)
            Spacer(Modifier.height(Sp.xs))
        }
        Text(
            buildString {
                append(if (sv.total == 0) "No must-cover list in the file" else "${sv.verified}/${sv.total} ✅ · $recorded recorded")
                sv.session.min_minutes?.let { append(" · min ${it / 60}h${if (it % 60 != 0) " ${it % 60}m" else ""}") }
            },
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
        )
        AnimatedVisibility(open) {
            Column(Modifier.padding(top = Sp.s)) {
                sv.session.goal?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(bottom = Sp.s)) }
                sv.items.forEach { (item, cov) -> CoverageRowView(item.item_no, item.item, cov) }
                sv.session.done_when?.let {
                    Spacer(Modifier.height(Sp.s))
                    Text("CHECK", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
                val first = sv.items.firstOrNull()?.first
                first?.misconception?.let { Spacer(Modifier.height(Sp.s)); Text("MISCONCEPTION", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant); Text(it, style = MaterialTheme.typography.bodySmall) }
                first?.attacker_view?.let { Spacer(Modifier.height(Sp.s)); Text("ATTACKER VIEW", style = MaterialTheme.typography.labelSmall, color = cs.error); Text(it, style = MaterialTheme.typography.bodySmall) }
                first?.defender_view?.let { Spacer(Modifier.height(Sp.s)); Text("DEFENDER VIEW", style = MaterialTheme.typography.labelSmall, color = cs.primary); Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun CoverageRowView(no: Int, text: String, cov: CoverageEntry?) {
    val cs = MaterialTheme.colorScheme
    val (icon, tint, label) = when (cov?.status) {
        "verified" -> Triple(Icons.Filled.CheckCircle, cs.primary, "verified")
        "redo" -> Triple(Icons.Filled.Autorenew, cs.tertiary, "re-check")
        "not_taught" -> Triple(Icons.Filled.Cancel, cs.error, "not taught")
        else -> Triple(Icons.Filled.RadioButtonUnchecked, cs.onSurfaceVariant, "not recorded")
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(20.dp).padding(top = 1.dp))
        Spacer(Modifier.width(Sp.s))
        Column(Modifier.weight(1f)) {
            Text("$no. ${text.replace("**", "").replace("`", "")}", style = MaterialTheme.typography.bodyMedium)
            if (!cov?.learner_quote.isNullOrBlank()) {
                Text("“${cov!!.learner_quote}”", style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, color = cs.onSurfaceVariant)
            }
            Text(
                label + (cov?.highest_hint?.let { " · Hint $it" } ?: "") + (cov?.recorded_at?.let { " · ${relativeTime(it)}" } ?: ""),
                style = MaterialTheme.typography.labelSmall, color = tint
            )
        }
    }
}

// -------------------------------------------------------------- Library ---

private val KIND_LABELS = listOf(
    "all" to "All", "teaching" to "Teaching", "curriculum" to "Curriculum", "governance" to "Rules",
    "assessment" to "Gates", "project" to "Projects", "lab" to "Labs", "resource" to "Resources",
    "career" to "Career", "template" to "Templates", "other" to "Other"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(onOpenDoc: (String) -> Unit) {
    var docs by remember { mutableStateOf<List<DocMeta>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("all") }
    var showOld by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        Fetch.safeCall { Curriculum.docs() }.onSuccess { docs = it; error = null }.onFailure { error = it.message ?: "Load failed" }
    }
    LaunchedEffect(Unit) { load() }

    val all = docs
    when {
        all == null && error == null -> SkeletonList(5)
        all == null -> ErrorBlock(error ?: "", onRetry = { scope.launch { load() } }, modifier = Modifier.padding(Sp.xl))
        else -> {
            val shown = all.filter { d ->
                (showOld || d.status == "authoritative" || d.status == "template") &&
                    (kind == "all" || d.kind == kind) &&
                    (query.isBlank() || d.title.contains(query, true) || d.path.contains(query, true))
            }
            val oldCount = all.count { it.status == "superseded" || it.status == "history" }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(Sp.l, Sp.s, Sp.l, Sp.xxl),
                verticalArrangement = Arrangement.spacedBy(Sp.s)
            ) {
                item {
                    Column(Modifier.padding(start = Sp.xs, bottom = Sp.s)) {
                        Text("Library", style = MaterialTheme.typography.headlineMedium)
                        Text("${all.size} project files · read-only copies (originals never change)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it },
                        leadingIcon = { Icon(Icons.Filled.Search, null) },
                        placeholder = { Text("Search files") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)
                    )
                }
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Sp.s)) {
                        KIND_LABELS.forEach { (k, label) ->
                            val n = if (k == "all") null else all.count { it.kind == k }
                            if (n == 0) return@forEach
                            FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(label) })
                        }
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = Sp.xs)) {
                        Text("Show old / superseded files ($oldCount)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Switch(checked = showOld, onCheckedChange = { showOld = it })
                    }
                }
                if (shown.isEmpty()) item { EmptyBlock(Icons.Filled.Description, "No files match", "Try another filter or search.") }
                items(shown, key = { it.path }) { d -> DocRow(d) { onOpenDoc(d.path) } }
            }
        }
    }
}

@Composable
private fun DocRow(d: DocMeta, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    AppCard(onClick = onClick, padding = Sp.m) {
        Text(d.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(d.path, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(Sp.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(Sp.xs)) {
            Pill(KIND_LABELS.firstOrNull { it.first == d.kind }?.second ?: d.kind, cs.secondary)
            when (d.status) {
                "superseded" -> Pill("superseded", cs.error)
                "history" -> Pill("history", cs.error)
                "template" -> Pill("template only", cs.onSurfaceVariant)
            }
            if (d.stale_notion_ref) Pill("mentions Notion", cs.tertiary)
        }
    }
}

// ---------------------------------------------------------- Doc reader ---

@Composable
fun DocScreen(path: String, onBack: () -> Unit) {
    var doc by remember { mutableStateOf<DocBody?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    suspend fun load() {
        Fetch.safeCall { Curriculum.doc(path) ?: throw IllegalStateException("File not found: $path") }
            .onSuccess { doc = it; error = null }.onFailure { error = it.message ?: "Load failed" }
    }
    LaunchedEffect(path) { load() }

    Scaffold(topBar = { AppTitleBar(title = doc?.title?.takeIf { it.isNotBlank() } ?: path.substringAfterLast('/'), onBack = onBack, subtitle = path) }) { pad ->
        val d = doc
        when {
            d == null && error == null -> SkeletonList(4, Modifier.padding(pad))
            d == null -> ErrorBlock(error ?: "", onRetry = { scope.launch { load() } }, modifier = Modifier.padding(pad).padding(Sp.xl))
            else -> {
                val lines = remember(d.content) { d.content.lines() }
                SelectionContainer(Modifier.padding(pad)) {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(Sp.l, Sp.s, Sp.l, Sp.xxl)) {
                        var inCode = false
                        val kinds = lines.map { l ->
                            if (l.trimStart().startsWith("```")) { inCode = !inCode; "fence" } else if (inCode) "code" else "text"
                        }
                        items(lines.size) { i -> MdLine(lines[i], kinds[i]) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MdLine(raw: String, kind: String) {
    val cs = MaterialTheme.colorScheme
    val clean = raw.replace("**", "").replace("`", "")
    when {
        kind == "fence" -> Spacer(Modifier.height(4.dp))
        kind == "code" -> Text(raw, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth().background(cs.surfaceContainerHigh).padding(horizontal = Sp.s, vertical = 1.dp))
        raw.isBlank() -> Spacer(Modifier.height(Sp.s))
        raw.startsWith("# ") -> Text(clean.removePrefix("# "), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = Sp.m, bottom = Sp.xs))
        raw.startsWith("## ") -> Text(clean.removePrefix("## "), style = MaterialTheme.typography.titleLarge, color = cs.primary, modifier = Modifier.padding(top = Sp.m, bottom = Sp.xs))
        raw.startsWith("#") -> Text(clean.trimStart('#', ' '), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Sp.s))
        raw.trimStart().startsWith("|") -> Text(raw, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall, softWrap = true)
        raw.trimStart().startsWith("- ") || raw.trimStart().startsWith("* ") -> {
            val indent = (raw.length - raw.trimStart().length) / 2
            Row(Modifier.padding(start = (indent * 12).dp, top = 2.dp)) {
                Text("•  ", style = MaterialTheme.typography.bodyMedium, color = cs.primary)
                Text(clean.trimStart().drop(2), style = MaterialTheme.typography.bodyMedium)
            }
        }
        raw.startsWith(">") -> Text(clean.trimStart('>', ' '), style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, color = cs.onSurfaceVariant,
            modifier = Modifier.padding(start = Sp.s, top = 2.dp))
        raw.trim() == "---" -> HorizontalDivider(Modifier.padding(vertical = Sp.s))
        else -> Text(clean, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
    }
}

// ----------------------------------------------------- Today: tutor card ---

// "Where the tutor left off": latest daily log, one-tap DAY X command,
// open blockers, and the live feed of tutor writes. Polls every 30 s while
// on screen and refreshes on resume.
@Composable
fun TutorCard(onOpenPath: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val clipboard = LocalClipboardManager.current
    var log by remember { mutableStateOf<LogHead?>(null) }
    var feed by remember { mutableStateOf<List<FeedEvent>>(emptyList()) }
    var blockers by remember { mutableStateOf<List<CurriculumBlocker>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    var showBlockers by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        Fetch.safeCall { Triple(Curriculum.latestLog(), Curriculum.feed(), Curriculum.blockers()) }
            .onSuccess { log = it.first; feed = it.second; blockers = it.third; failed = false }
            .onFailure { failed = true }
        loaded = true
    }
    LaunchedEffect(Unit) {
        while (true) { load(); delay(LIVE_POLL_MS) }
    }
    OnResume { scope.launch { load() } }
    LaunchedEffect(copied) { if (copied) { delay(1800); copied = false } }

    if (!loaded) return
    AppCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Map, null, tint = cs.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Sp.s))
            Text("Tutor sync", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.Sync, null, tint = if (failed) cs.error else cs.onSurfaceVariant, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(if (failed) "offline" else "live", style = MaterialTheme.typography.labelSmall, color = if (failed) cs.error else cs.onSurfaceVariant)
        }
        val l = log
        if (l != null) {
            Spacer(Modifier.height(Sp.s))
            Text("📍 Day ${l.study_day} · ${l.log_date} · ${l.status ?: "—"}", style = MaterialTheme.typography.labelLarge, color = cs.primary)
            l.chapter_topic?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            l.next_action?.let {
                Spacer(Modifier.height(Sp.xs))
                Text("Next: $it", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            val nextDay = if (l.status.equals("Done", true)) l.study_day + 1 else l.study_day
            val cmd = "DAY $nextDay"
            Spacer(Modifier.height(Sp.s))
            Row(horizontalArrangement = Arrangement.spacedBy(Sp.s)) {
                Button(onClick = { clipboard.setText(AnnotatedString(cmd)); copied = true }) {
                    Icon(Icons.Filled.ContentCopy, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                    Text(if (copied) "Copied — paste in tutor chat" else "Copy “$cmd”")
                }
                OutlinedButton(onClick = onOpenPath) { Text("Path") }
            }
        }
        if (blockers.isNotEmpty()) {
            Spacer(Modifier.height(Sp.m))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(cs.tertiary.copy(alpha = 0.12f))
                    .clickable { showBlockers = !showBlockers }.padding(horizontal = Sp.m, vertical = Sp.s),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.WarningAmber, null, tint = cs.tertiary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Sp.s))
                Text("${blockers.size} open decisions in the course files", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.ExpandMore, null, Modifier.rotate(if (showBlockers) 180f else 0f).size(18.dp))
            }
            AnimatedVisibility(showBlockers) {
                Column(Modifier.padding(top = Sp.s)) {
                    blockers.forEach { b ->
                        Text("${b.code?.let { "$it · " } ?: ""}${b.title}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = Sp.xs))
                        b.detail?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
        }
        if (feed.isNotEmpty()) {
            Spacer(Modifier.height(Sp.m))
            Text("LATEST TUTOR WRITES", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            feed.take(5).forEach { e ->
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.Top) {
                    Text("📝", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.width(Sp.s))
                    Column(Modifier.weight(1f)) {
                        Text("${e.kind}: ${e.text}", style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Text(relativeTime(e.at), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                }
            }
        }
    }
}
