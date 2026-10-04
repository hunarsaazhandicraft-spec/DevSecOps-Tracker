package com.devsecops.tracker.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devsecops.tracker.data.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

// runCatching that never swallows coroutine cancellation.
internal suspend fun <T> catching(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

// A UTC timestamp from the database -> the date on this device ("2026-09-25").
// Falls back to the first 10 characters when it can't be parsed.
internal fun localDateString(ts: String?): String {
    if (ts.isNullOrBlank()) return ""
    return runCatching {
        Instant.parse(ts).toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
    }.getOrElse { ts.take(10) }
}

// Status values in the database are capitalised ("In Progress"); compare loosely.
internal fun statusIs(status: String?, expected: String): Boolean =
    status != null && status.trim().equals(expected, ignoreCase = true)

// ---- Bandit tracker -------------------------------------------------------
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BanditScreen(onBack: () -> Unit) {
    var levels by remember { mutableStateOf<List<BanditLevel>>(emptyList()) }
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    var note by rememberSaveable { mutableStateOf("") }
    var loadErr by remember { mutableStateOf<String?>(null) }
    var actionErr by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        catching { Extras.listBandit() }
            .onSuccess { levels = it; loadErr = null }
            .onFailure { loadErr = it.message ?: "Load failed" }
    }
    LaunchedEffect(Unit) { load() }

    val doneSet = levels.filter { it.done }.map { it.level }.toSet()

    ToolScaffold(title = "Bandit tracker", onBack = onBack) {
        loadErr?.let { ErrorBlock(it, { scope.launch { load() } }) }
        Text("${doneSet.size} / 34 levels done", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        LinearProgressIndicator(progress = { doneSet.size / 34f }, modifier = Modifier.fillMaxWidth().height(8.dp))
        Text(
            "OverTheWire Bandit is a legal practice wargame. Store only notes about what you learned. Never save level passwords here.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (0..33).forEach { lvl ->
                FilterChip(
                    selected = lvl in doneSet,
                    onClick = {
                        selected = lvl
                        actionErr = null
                        note = levels.firstOrNull { it.level == lvl }?.note ?: ""
                    },
                    label = { Text("$lvl") }
                )
            }
        }
        selected?.let { lvl ->
            val isDone = lvl in doneSet
            AppCard(padding = 0.dp) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Level $lvl", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = note, onValueChange = { note = it },
                        label = { Text("What command/idea solved it? (no passwords)") },
                        modifier = Modifier.fillMaxWidth(), minLines = 2
                    )
                    actionErr?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            scope.launch {
                                actionErr = null
                                catching { Extras.setBandit(lvl, true, note.trim().ifBlank { null }) }
                                    .onFailure { actionErr = "Couldn't save: " + (it.message ?: "unknown error") }
                                load()
                            }
                        }) { Text(if (isDone) "Update" else "Mark done") }
                        if (isDone) OutlinedButton(onClick = {
                            scope.launch {
                                actionErr = null
                                catching { Extras.setBandit(lvl, false, note.trim().ifBlank { null }) }
                                    .onFailure { actionErr = "Couldn't save: " + (it.message ?: "unknown error") }
                                load()
                            }
                        }) { Text("Mark not done") }
                    }
                }
            }
        }
    }
}

// ---- Command vault --------------------------------------------------------
@Composable
fun CommandsScreen(onBack: () -> Unit) {
    var items by remember { mutableStateOf<List<CommandItem>>(emptyList()) }
    var title by rememberSaveable { mutableStateOf("") }
    var cmd by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    var loadErr by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    suspend fun load() {
        catching { Extras.listCommands() }
            .onSuccess { items = it; loadErr = null }
            .onFailure { loadErr = it.message ?: "Load failed" }
    }
    LaunchedEffect(Unit) { load() }

    ToolScaffold(title = "Command vault", onBack = onBack) {
        loadErr?.let { ErrorBlock(it, { scope.launch { load() } }) }
        Text(
            "Never store passwords, tokens or keys here.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error
        )
        OutlinedTextField(title, { title = it }, label = { Text("Title (e.g. find SUID files)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(cmd, { cmd = it }, label = { Text("Command") }, modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
        OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, modifier = Modifier.fillMaxWidth())
        Button(
            enabled = title.isNotBlank() && cmd.isNotBlank(),
            onClick = {
                scope.launch {
                    catching { Extras.addCommand(CommandInsert(title.trim(), cmd.trim(), note.trim().ifBlank { null })) }
                        .onSuccess { title = ""; cmd = ""; note = ""; msg = null }
                        .onFailure { msg = it.message ?: "Save failed" }
                    load()
                }
            }
        ) { Text("Save command") }
        msg?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        OutlinedTextField(filter, { filter = it }, label = { Text("Search saved commands") }, modifier = Modifier.fillMaxWidth())
        items.filter { filter.isBlank() || it.title.contains(filter, true) || it.command.contains(filter, true) }
            .forEach { c ->
                AppCard(padding = 0.dp) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(c.title, fontWeight = FontWeight.Bold)
                        Text(c.command, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                        c.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { clipboard.setText(AnnotatedString(c.command)) }) { Text("Copy") }
                            TextButton(onClick = {
                                scope.launch {
                                    catching { Extras.deleteCommand(c.id) }
                                        .onSuccess { msg = null }
                                        .onFailure { msg = "Couldn't delete: " + (it.message ?: "unknown error") }
                                    load()
                                }
                            }) { Text("Delete") }
                        }
                    }
                }
            }
    }
}

// ---- Projects -------------------------------------------------------------
private val PROJECT_STATUSES = listOf("spec_required", "planned", "in_progress", "complete")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProjectsScreen(onBack: () -> Unit) {
    var items by remember { mutableStateOf<List<ProjectItem>>(emptyList()) }
    var open by rememberSaveable { mutableStateOf<Long?>(null) }
    var repo by rememberSaveable { mutableStateOf("") }
    var loadErr by remember { mutableStateOf<String?>(null) }
    var actionErr by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        catching { Extras.listProjects() }
            .onSuccess { items = it; loadErr = null }
            .onFailure { loadErr = it.message ?: "Load failed" }
    }
    suspend fun update(id: Long, u: ProjectUpdate) {
        actionErr = null
        catching { Extras.updateProject(id, u) }
            .onFailure { actionErr = "Couldn't save: " + (it.message ?: "unknown error") }
        load()
    }
    LaunchedEffect(Unit) { load() }

    ToolScaffold(title = "Projects", onBack = onBack) {
        loadErr?.let { ErrorBlock(it, { scope.launch { load() } }) }
        actionErr?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        val done = items.count { it.status == "complete" }
        Text("$done / ${items.size} complete", fontWeight = FontWeight.Bold)
        items.forEach { p ->
            AppCard(onClick = {
                    open = if (open == p.id) null else p.id
                    repo = p.repo_url ?: ""
                }, padding = 0.dp) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${p.code ?: ""} ${p.name}", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Text(p.status.replace('_', ' '), style = MaterialTheme.typography.labelMedium)
                    }
                    p.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (p.rubric_passed) Text("Rubric passed", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                    if (open == p.id) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PROJECT_STATUSES.forEach { st ->
                                FilterChip(
                                    selected = p.status == st,
                                    onClick = {
                                        scope.launch { update(p.id, ProjectUpdate(st, repo.trim().ifBlank { null }, p.rubric_passed)) }
                                    },
                                    label = { Text(st.replace('_', ' ')) }
                                )
                            }
                        }
                        OutlinedTextField(repo, { repo = it }, label = { Text("Private repo URL") }, modifier = Modifier.fillMaxWidth())
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = p.rubric_passed, onCheckedChange = { v ->
                                scope.launch { update(p.id, ProjectUpdate(p.status, repo.trim().ifBlank { null }, v)) }
                            })
                            Text("Rubric passed (graded by tutor)")
                        }
                        TextButton(onClick = {
                            scope.launch { update(p.id, ProjectUpdate(p.status, repo.trim().ifBlank { null }, p.rubric_passed)) }
                        }) { Text("Save repo link") }
                    } else p.repo_url?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
                }
            }
        }
    }
}

// ---- Interview practice ---------------------------------------------------
@Composable
fun InterviewScreen(onBack: () -> Unit) {
    var items by remember { mutableStateOf<List<InterviewQ>>(emptyList()) }
    var index by rememberSaveable { mutableStateOf(0) }
    var revealed by rememberSaveable { mutableStateOf(false) }
    var loadErr by remember { mutableStateOf<String?>(null) }
    var actionErr by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // A missing status counts as "new" everywhere (sorting and label).
    fun statusOf(q: InterviewQ): String = q.revision_status?.takeIf { it.isNotBlank() } ?: "new"

    suspend fun load() {
        // Least-practised first: new, then reviewed, then confident.
        catching { Extras.listInterview() }
            .onSuccess { list ->
                items = list.sortedBy {
                    when (statusOf(it)) { "new" -> 0; "reviewed" -> 1; else -> 2 }
                }
                loadErr = null
            }
            .onFailure { loadErr = it.message ?: "Load failed" }
    }
    LaunchedEffect(Unit) { load() }

    ToolScaffold(title = "Interview practice", onBack = onBack) {
        loadErr?.let { ErrorBlock(it, { scope.launch { load() } }) }
        if (items.isEmpty()) {
            if (loadErr == null) Text("No questions yet.")
        } else {
            val q = items[index.coerceIn(0, items.size - 1)]
            Text("Question ${index + 1} of ${items.size} · ${q.difficulty ?: ""} · ${statusOf(q)}",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AppCard(padding = 0.dp) {
                Text(q.question, Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
            }
            if (!revealed) {
                Text("Answer out loud first, then reveal.", style = MaterialTheme.typography.bodySmall)
                Button(onClick = { revealed = true }, modifier = Modifier.fillMaxWidth()) { Text("Show model answer") }
            } else {
                AppCard(padding = 0.dp) {
                    Text(q.answer ?: "No model answer saved.", Modifier.padding(16.dp))
                }
                actionErr?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Text("How did your answer compare?", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        scope.launch {
                            actionErr = null
                            catching { Extras.markInterview(q.id, "reviewed") }
                                .onSuccess { revealed = false; index = (index + 1) % items.size }
                                .onFailure { actionErr = "Couldn't save your rating: " + (it.message ?: "unknown error") }
                        }
                    }) { Text("Needs work") }
                    Button(onClick = {
                        scope.launch {
                            actionErr = null
                            catching { Extras.markInterview(q.id, "confident") }
                                .onSuccess { revealed = false; index = (index + 1) % items.size }
                                .onFailure { actionErr = "Couldn't save your rating: " + (it.message ?: "unknown error") }
                        }
                    }) { Text("Nailed it") }
                }
            }
        }
    }
}

// ---- Glossary -------------------------------------------------------------
@Composable
fun GlossaryScreen(onBack: () -> Unit) {
    var items by remember { mutableStateOf<List<GlossaryTerm>>(emptyList()) }
    var q by rememberSaveable { mutableStateOf("") }
    var open by rememberSaveable { mutableStateOf<Long?>(null) }
    var mine by rememberSaveable { mutableStateOf("") }
    var loadErr by remember { mutableStateOf<String?>(null) }
    var actionErr by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        catching { Extras.listGlossary() }
            .onSuccess { items = it; loadErr = null }
            .onFailure { loadErr = it.message ?: "Load failed" }
    }
    LaunchedEffect(Unit) { load() }

    ToolScaffold(title = "Glossary", onBack = onBack) {
        loadErr?.let { ErrorBlock(it, { scope.launch { load() } }) }
        actionErr?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text("${items.size} terms", style = MaterialTheme.typography.labelMedium)
        OutlinedTextField(q, { q = it }, label = { Text("Search terms") }, modifier = Modifier.fillMaxWidth())
        items.filter { q.isBlank() || it.term.contains(q, true) || it.definition.contains(q, true) }.forEach { t ->
            AppCard(onClick = {
                    open = if (open == t.id) null else t.id
                    mine = t.my_understanding ?: ""
                }, padding = 0.dp) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(t.term, fontWeight = FontWeight.Bold)
                    if (open == t.id) {
                        t.expansion?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        Text(t.plain_meaning ?: t.definition, style = MaterialTheme.typography.bodyMedium)
                        t.example?.let { Text("Example: $it", style = MaterialTheme.typography.bodySmall) }
                        OutlinedTextField(mine, { mine = it }, label = { Text("In my own words") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                        TextButton(onClick = {
                            scope.launch {
                                actionErr = null
                                catching { Extras.saveUnderstanding(t.id, mine.trim()) }
                                    .onFailure { actionErr = "Couldn't save: " + (it.message ?: "unknown error") }
                                load()
                            }
                        }) { Text("Save my words") }
                    } else {
                        Text((t.plain_meaning ?: t.definition).take(90), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// ---- Global search --------------------------------------------------------
@Composable
fun SearchScreen(onBack: () -> Unit) {
    var q by rememberSaveable { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    var searched by remember { mutableStateOf(false) }

    LaunchedEffect(q) {
        if (q.trim().length < 2) { hits = emptyList(); searched = false; return@LaunchedEffect }
        kotlinx.coroutines.delay(300)
        hits = catching { Extras.search(q) }.getOrDefault(emptyList())
        searched = true
    }

    ToolScaffold(title = "Search", onBack = onBack) {
        OutlinedTextField(q, { q = it }, label = { Text("Topics, glossary, commands, cards") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        if (searched && hits.isEmpty()) Text("Nothing found.")
        hits.forEach { h ->
            AppCard(padding = 0.dp) {
                Column(Modifier.padding(12.dp)) {
                    Text(h.kind.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text(h.title, fontWeight = FontWeight.SemiBold)
                    if (h.detail.isNotBlank()) Text(h.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
