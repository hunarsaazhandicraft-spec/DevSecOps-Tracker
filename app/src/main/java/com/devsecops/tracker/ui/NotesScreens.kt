package com.devsecops.tracker.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.devsecops.tracker.data.Extras
import com.devsecops.tracker.data.Fetch
import com.devsecops.tracker.data.Module
import com.devsecops.tracker.data.NoteFull
import com.devsecops.tracker.data.NoteMeta
import com.devsecops.tracker.data.Notes
import com.devsecops.tracker.data.RevisionCard
import com.devsecops.tracker.data.Topic
import com.devsecops.tracker.data.WeakSpot
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun shareOut(ctx: android.content.Context, subject: String, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    ctx.startActivity(Intent.createChooser(send, "Share note").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private data class NotesLists(
    val notes: List<NoteMeta>,
    val topics: Map<Int, Topic>,
    val modules: List<Module>,
    val phases: Map<Int, String>,
    val weak: List<WeakSpot>,
    val offline: Boolean
)

@Composable
fun NotesScreen(onBack: () -> Unit, onOpenNote: (Long) -> Unit, showBackButton: Boolean = true) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<NotesLists?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableIntStateOf(0) }   // 0 all, 1 draft, 2 reviewed, 3 weak spot
    var hits by remember { mutableStateOf<Set<Long>?>(null) }
    var busyShare by remember { mutableStateOf(false) }

    suspend fun load() {
        val net = Fetch.safeCall { Notes.list() }
        val offline = net.isFailure
        val notes = net.getOrNull() ?: Notes.cachedIndex(ctx)
        if (net.isSuccess) Notes.saveIndex(ctx, notes)
        if (offline && notes.isEmpty()) {
            err = net.exceptionOrNull()?.message ?: "Could not load notes"; return
        }
        val topics = Fetch.orDefault(emptyList()) { Fetch.topics() }.associateBy { it.id }
        val modules = Fetch.orDefault(emptyList()) { Fetch.modules() }
        val phases = Fetch.orDefault(emptyMap()) { Extras.listPhases() }
        val weak = Fetch.orDefault(emptyList()) { Fetch.openWeakSpots() }
        data = NotesLists(notes, topics, modules, phases, weak, offline)
        err = null
    }
    LaunchedEffect(Unit) { load() }
    OnResume { scope.launch { load() } }
    LaunchedEffect(query) {
        if (query.trim().length < 3) { hits = null; return@LaunchedEffect }
        delay(350)
        hits = Fetch.safeCall { Notes.searchIds(query) }.getOrNull()
    }

    Column(Modifier.fillMaxSize()) {
        AppTitleBar(title = "Notes", onBack = if (showBackButton) onBack else null,
            subtitle = data?.let { d -> "${d.notes.size} notes" + if (d.offline) " - offline copy" else "" })
        val d = data
        when {
            err != null && d == null -> ErrorBlock(err!!, onRetry = { scope.launch { load() } })
            d == null -> SkeletonList()
            else -> {
                val weakTopics = d.weak.map { it.topic_id }.toSet()
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    label = { Text("Search title or content") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Sp.l)
                )
                SegTabs(listOf("All", "Draft", "Reviewed", "Weak"), filter, { filter = it },
                    Modifier.padding(horizontal = Sp.l, vertical = Sp.s))
                val q = query.trim().lowercase()
                fun topicName(n: NoteMeta) = n.topic_id?.let { d.topics[it]?.name }.orEmpty()
                val shown = d.notes.filter { n ->
                    val okFilter = when (filter) {
                        1 -> n.status == "draft"
                        2 -> n.status == "reviewed"
                        3 -> n.topic_id != null && n.topic_id in weakTopics
                        else -> true
                    }
                    val okQuery = q.isEmpty() || n.title.lowercase().contains(q) ||
                        topicName(n).lowercase().contains(q) || (hits?.contains(n.id) == true)
                    okFilter && okQuery
                }
                val filtering = filter != 0 || q.isNotEmpty()
                val byModule = shown.groupBy { it.module_id }
                val mods = d.modules.sortedBy { it.order_num + it.phase_id * 1000 }
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = Sp.l, vertical = Sp.s),
                    verticalArrangement = Arrangement.spacedBy(Sp.s)
                ) {
                    if (filtering && shown.isEmpty()) {
                        item { Text("Is filter/search se koi note nahi mila.", color = cs.onSurfaceVariant) }
                    }
                    var lastPhase = -1
                    mods.forEach { m ->
                        val list = byModule[m.id].orEmpty()
                        val hasAny = list.isNotEmpty()
                        if (filtering && !hasAny) return@forEach
                        if (m.phase_id != lastPhase) {
                            lastPhase = m.phase_id
                            val pName = d.phases[m.phase_id] ?: "Phase ${m.phase_id}"
                            item(key = "p${m.phase_id}") { GroupHeader(pName) }
                        }
                        val allTopics = d.topics.values.filter { it.module_id == m.id }
                        if (!hasAny) {
                            item(key = "m${m.id}") {
                                Text("${m.name}: abhi padha nahi / note nahi (${allTopics.size} topics)",
                                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                            }
                            return@forEach
                        }
                        item(key = "m${m.id}") {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Text(m.name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = cs.primary)
                                TextButton(enabled = !busyShare && !d.offline, onClick = {
                                    scope.launch {
                                        busyShare = true
                                        Fetch.safeCall { Notes.forModule(m.id) }.onSuccess {
                                            if (it.isNotEmpty()) shareOut(ctx, m.name, Notes.shareText(it))
                                        }
                                        busyShare = false
                                    }
                                }) { Text("Share module") }
                            }
                        }
                        items(list, key = { "n${it.id}" }) { n ->
                            val weakHere = n.topic_id != null && n.topic_id in weakTopics
                            AppCard(onClick = { onOpenNote(n.id) }, padding = Sp.m) {
                                Text(n.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                val sub = listOfNotNull(
                                    topicName(n).takeIf { it.isNotBlank() && it != n.title },
                                    if (n.part > 1) "Part ${n.part}" else null
                                ).joinToString(" - ")
                                if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Sp.xs), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    Pill(if (n.status == "reviewed") "Reviewed" else "Draft",
                                        if (n.status == "reviewed") cs.primary else cs.onSurfaceVariant)
                                    if (weakHere) Pill("Weak spot", cs.error)
                                }
                            }
                        }
                        if (!filtering) {
                            val covered = list.mapNotNull { it.topic_id }.toSet()
                            val missing = allTopics.filter { it.id !in covered }
                            if (missing.isNotEmpty()) item(key = "miss${m.id}") {
                                Text(
                                    "Abhi note nahi: " + missing.joinToString(", ") { it.name },
                                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                                )
                            }
                        }
                    }
                    item { Spacer(Modifier.height(Sp.xxl)) }
                }
            }
        }
    }
}

@Composable
fun NoteDetailScreen(noteId: Long, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var note by remember { mutableStateOf<NoteFull?>(null) }
    var offline by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var topic by remember { mutableStateOf<Topic?>(null) }
    var weak by remember { mutableStateOf<WeakSpot?>(null) }
    var cards by remember { mutableStateOf<List<RevisionCard>>(emptyList()) }
    var words by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    suspend fun load() {
        val net = Fetch.safeCall { Notes.get(noteId) }
        val n = net.getOrNull() ?: Notes.cachedNote(ctx, noteId)
        if (n == null) { err = net.exceptionOrNull()?.message ?: "Could not load note"; return }
        if (net.isSuccess) Notes.saveNote(ctx, n)
        offline = net.isFailure
        if (note == null || !net.isFailure) words = n.my_words.orEmpty()
        note = n
        err = null
        val tid = n.topic_id
        if (tid != null) {
            topic = Fetch.orDefault(null) { Fetch.topics().firstOrNull { it.id == tid } }
            weak = Fetch.orDefault(null) { Fetch.openWeakSpots().firstOrNull { it.topic_id == tid } }
            cards = Fetch.orDefault(emptyList()) { Fetch.cards().filter { it.topic_id == tid } }
        }
    }
    LaunchedEffect(noteId) { load() }

    fun save(reviewed: Boolean) {
        if (busy) return
        scope.launch {
            busy = true
            Fetch.safeCall { Notes.saveMyWords(noteId, words, reviewed) }
                .onSuccess { msg = if (reviewed && words.isNotBlank()) "Reviewed" else "Saved" }
                .onFailure { msg = it.message ?: "Save failed" }
            load()
            busy = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        AppTitleBar(
            title = note?.title ?: "Note", onBack = onBack,
            subtitle = if (offline) "Offline copy" else topic?.name,
            actions = {
                val n = note
                if (n != null) IconButton(onClick = { shareOut(ctx, n.title, Notes.shareText(listOf(n.copy(my_words = words)))) }) {
                    Icon(Icons.Default.Share, contentDescription = "Share note")
                }
            }
        )
        val n = note
        when {
            err != null && n == null -> ErrorBlock(err!!, onRetry = { scope.launch { load() } })
            n == null -> SkeletonList()
            else -> {
                val sections = remember(n.content_md) { splitSections(n.content_md) }
                val listState = rememberLazyListState()
                var find by remember { mutableStateOf("") }
                var matchPos by remember { mutableIntStateOf(0) }
                val matches = remember(find, sections) { sectionMatches(sections, find) }
                val nums = remember(sections) { sectionNumbers(sections) }
                val closed = remember(n.content_md) { androidx.compose.runtime.mutableStateMapOf<Int, Boolean>() }
                fun isOpen(i: Int) = closed[i] != true
                LaunchedEffect(find) { matchPos = 0 }
                // Items before the first section: pills, [weak card], outline, find.
                val pre = 3 + (if (weak != null) 1 else 0)
                fun jump(idx: Int) { closed[idx] = false; scope.launch { listState.animateScrollToItem(pre + idx) } }
                val wordsCard: @Composable () -> Unit = {
                    WordsCard(
                        words = words, onWords = { words = it }, offline = offline, busy = busy,
                        reviewed = n.status == "reviewed", msg = msg, onSave = { save(it) }
                    )
                }
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val twoPane = maxWidth > 840.dp
                    Row(Modifier.fillMaxSize()) {
                        LazyColumn(
                            Modifier.weight(if (twoPane) 1.6f else 1f).fillMaxHeight(),
                            state = listState,
                            contentPadding = PaddingValues(horizontal = Sp.l, vertical = Sp.s),
                            verticalArrangement = Arrangement.spacedBy(Sp.m)
                        ) {
                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(Sp.xs)) {
                                    Pill(if (n.status == "reviewed") "Reviewed" else "Draft", if (n.status == "reviewed") cs.primary else cs.onSurfaceVariant)
                                    if (weak != null) Pill("Weak spot", cs.error)
                                    if (n.part > 1) Pill("Part ${n.part}", cs.tertiary)
                                    Spacer(Modifier.weight(1f))
                                    val anyOpen = sections.indices.any { nums[it] > 0 && isOpen(it) }
                                    TextButton(onClick = {
                                        sections.indices.forEach { if (nums[it] > 0) closed[it] = anyOpen }
                                    }) { Text(if (anyOpen) "Sab band" else "Sab kholo") }
                                }
                            }
                            weak?.let { w ->
                                item {
                                    AppCard(padding = Sp.m) {
                                        Text("Is topic par weak spot khula hai", style = MaterialTheme.typography.labelLarge, color = cs.error)
                                        Text(w.title ?: w.reason, style = MaterialTheme.typography.bodySmall)
                                        w.required_action?.takeIf { it.isNotBlank() }?.let {
                                            Text("Karna hai: $it", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                            item {
                                val heads = sections.withIndex().filter { it.value.heading.isNotBlank() }
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(Sp.xs)) {
                                    items(heads, key = { it.index }) { h ->
                                        AssistChip(onClick = { jump(h.index) }, label = { Text("${nums[h.index]}  ${h.value.heading}", maxLines = 1) })
                                    }
                                }
                            }
                            item {
                                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Sp.s)) {
                                    OutlinedTextField(
                                        value = find, onValueChange = { find = it }, singleLine = true,
                                        label = { Text("Note mein dhundo") }, modifier = Modifier.weight(1f)
                                    )
                                    if (find.trim().length >= 2) {
                                        if (matches.isEmpty()) Text("0", color = cs.onSurfaceVariant)
                                        else TextButton(onClick = {
                                            val next = (matchPos + 1) % matches.size
                                            matchPos = next
                                            jump(matches[next])
                                        }) { Text("${matchPos + 1}/${matches.size} next") }
                                    }
                                }
                            }
                            items(sections.size, key = { "s$it" }) { i ->
                                val sec = sections[i]
                                if (nums[i] == 0) MarkdownView(sec.body)
                                else NoteSectionCard(nums[i], sec.heading, isOpen(i), { closed[i] = isOpen(i) }) {
                                    MarkdownView(sectionBody(sec))
                                }
                            }
                            if (!twoPane) item { wordsCard() }
                            if (cards.isNotEmpty()) {
                                item { GroupHeader("Revision cards (${cards.size})", "Sirf sawaal. Jawab Revise tab mein.") }
                                items(cards, key = { it.id }) { c ->
                                    AppCard(padding = Sp.m) { Text(c.question, style = MaterialTheme.typography.bodyMedium) }
                                }
                            }
                            item { Spacer(Modifier.height(Sp.xxl)) }
                        }
                        if (twoPane) {
                            Column(
                                Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())
                                    .padding(end = Sp.l, top = Sp.s, bottom = Sp.xxl)
                            ) { wordsCard() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WordsCard(
    words: String, onWords: (String) -> Unit, offline: Boolean, busy: Boolean,
    reviewed: Boolean, msg: String?, onSave: (Boolean) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    AppCard(padding = Sp.m) {
        Text("Mere shabdon mein", style = MaterialTheme.typography.titleSmall)
        Text("Bina dekhe, apne words mein likho. Reviewed tabhi hoga jab yahan kuch likha ho.",
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        Spacer(Modifier.height(Sp.s))
        OutlinedTextField(
            value = words, onValueChange = onWords,
            enabled = !offline && !busy, minLines = 4,
            modifier = Modifier.fillMaxWidth()
        )
        if (offline) Text("Offline: editing band hai.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        Spacer(Modifier.height(Sp.s))
        Row(horizontalArrangement = Arrangement.spacedBy(Sp.s)) {
            OutlinedButton(enabled = !offline && !busy, onClick = { onSave(reviewed) }) { Text("Save") }
            if (reviewed) {
                OutlinedButton(enabled = !offline && !busy, onClick = { onSave(false) }) { Text("Back to draft") }
            } else {
                Button(enabled = !offline && !busy && words.isNotBlank(), onClick = { onSave(true) }) { Text("Mark reviewed") }
            }
        }
        msg?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = cs.primary) }
    }
}
