package com.devsecops.tracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.devsecops.tracker.data.Fetch
import com.devsecops.tracker.data.NoteFull
import com.devsecops.tracker.data.NoteRevision
import com.devsecops.tracker.data.NoteRevisionInsert
import com.devsecops.tracker.data.NoteRevisions
import com.devsecops.tracker.data.Notes
import com.devsecops.tracker.data.buildRevision
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.random.Random

// One note's revision: read the recap, answer questions built from the note,
// put the steps in order, write it in your own words, tick what you covered.
// Nothing here is AI: every question and answer comes from the note itself.
private enum class Stage { RECAP, MCQ, ORDER, DUMP, DONE }

@Composable
fun NoteRevisionScreen(noteId: Long, rung: Int, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var note by remember { mutableStateOf<NoteFull?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(noteId) {
        val net = Fetch.safeCall { Notes.get(noteId) }
        val n = net.getOrNull() ?: Notes.cachedNote(ctx, noteId)
        if (n == null) loadError = net.exceptionOrNull()?.message ?: "Note load nahi hua" else { note = n; Notes.saveNote(ctx, n) }
    }

    Scaffold(topBar = { AppTitleBar(title = note?.title ?: "Revision", onBack = onBack, subtitle = rungLabel(rung)) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            val n = note
            when {
                loadError != null -> ErrorBlock(loadError ?: "", onRetry = onBack, modifier = Modifier.padding(16.dp))
                n == null -> SkeletonList(3, Modifier.padding(16.dp))
                else -> RevisionBody(n, rung, scope, onBack)
            }
        }
    }
}

@Composable
private fun RevisionBody(n: NoteFull, rung: Int, scope: kotlinx.coroutines.CoroutineScope, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val seed = n.id * 1000 + rung + (Clock.System.now().toEpochMilliseconds() / 86_400_000L)
    val rev: NoteRevision = remember(n.id, rung) { buildRevision(n.content_md, seed) }
    val stages = remember(rev) {
        buildList {
            if (rev.recap.isNotEmpty()) add(Stage.RECAP)
            if (rev.mcqs.isNotEmpty()) add(Stage.MCQ)
            if (rev.order != null) add(Stage.ORDER)
            add(Stage.DUMP)
            add(Stage.DONE)
        }
    }
    var si by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(0) }
    var asked by remember { mutableIntStateOf(0) }
    var dump by remember { mutableStateOf("") }
    val ticks = remember(rev) { mutableStateListOf<Boolean>().apply { repeat(rev.recap.size) { add(false) } } }
    var saveState by remember { mutableStateOf("idle") }     // idle | saving | ok | error
    var saveMsg by remember { mutableStateOf("") }
    fun next() { if (si < stages.lastIndex) si++ }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val stage = stages[si]
        ThinBar((si).toFloat() / (stages.size - 1).coerceAtLeast(1), cs.primary)
        when (stage) {
            Stage.RECAP -> {
                GroupHeader("1. Short mein", "30 second padho, phir sawal")
                AppCard { rev.recap.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 3.dp)) } }
                Button(onClick = { next() }, modifier = Modifier.fillMaxWidth()) { Text("Padh liya, sawal do") }
            }
            Stage.MCQ -> McqStage(rev, onAnswered = { right -> asked++; if (right) score++ }, onFinished = { next() })
            Stage.ORDER -> OrderStage(rev, seed, onChecked = { right -> asked++; if (right) score++ }, onFinished = { next() })
            Stage.DUMP -> {
                GroupHeader("Apne shabdon me likho", "Note band karke, bina dekhe. Skip bhi kar sakte ho.")
                var checking by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = dump, onValueChange = { dump = it }, enabled = !checking,
                    label = { Text("Is topic ko samjhao") },
                    modifier = Modifier.fillMaxWidth().height(180.dp)
                )
                if (!checking) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (rev.recap.isNotEmpty() && dump.isNotBlank()) {
                            Button(onClick = { checking = true }, modifier = Modifier.weight(1f)) { Text("Checklist se milao") }
                        } else {
                            Button(onClick = { next() }, modifier = Modifier.weight(1f)) { Text(if (dump.isBlank()) "Skip" else "Aage") }
                        }
                    }
                } else {
                    GroupHeader("Tumhare jawab me kya tha?", "Imaandari se tick karo")
                    AppCard {
                        rev.recap.forEachIndexed { i, point ->
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Checkbox(checked = ticks[i], onCheckedChange = { ticks[i] = it })
                                Text(point, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    Button(onClick = { next() }, modifier = Modifier.fillMaxWidth()) { Text("Aage") }
                }
            }
            Stage.DONE -> {
                val hits = ticks.count { it }
                GroupHeader("Ho gaya", n.title)
                AppCard {
                    if (asked > 0) Text("Sawal: $score / $asked sahi", style = MaterialTheme.typography.titleMedium)
                    if (dump.isNotBlank() && rev.recap.isNotEmpty()) Text("Apne shabdon me: $hits / ${rev.recap.size} points", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Jo galat ya chhoota, wo note me dobara dekho. Tutor ye `DAY` par padhega.",
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp)
                    )
                }
                when (saveState) {
                    "ok" -> { Text("Save ho gaya.", color = cs.primary); Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Wapas Revise") } }
                    else -> {
                        if (saveState == "error") Text("Save nahi hua: $saveMsg", color = cs.error, style = MaterialTheme.typography.bodySmall)
                        Button(
                            enabled = saveState != "saving",
                            onClick = {
                                saveState = "saving"
                                scope.launch {
                                    runCatching {
                                        NoteRevisions.save(
                                            NoteRevisionInsert(
                                                note_id = n.id, rung = rung, score = score, total = asked,
                                                recall_hits = if (dump.isNotBlank() && rev.recap.isNotEmpty()) ticks.count { it } else null,
                                                recall_total = if (dump.isNotBlank() && rev.recap.isNotEmpty()) rev.recap.size else null,
                                                brain_dump = dump.trim().ifBlank { null }
                                            )
                                        )
                                    }.onSuccess { saveState = "ok" }
                                        .onFailure { saveState = "error"; saveMsg = it.message?.take(120) ?: "error" }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (saveState == "saving") "Save ho raha hai..." else "Save karo") }
                        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Bina save kiye wapas") }
                    }
                }
            }
        }
    }
}

@Composable
private fun McqStage(rev: NoteRevision, onAnswered: (Boolean) -> Unit, onFinished: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var qi by remember { mutableIntStateOf(0) }
    var picked by remember(qi) { mutableStateOf<Int?>(null) }
    val q = rev.mcqs[qi]
    GroupHeader("Sawal ${qi + 1} / ${rev.mcqs.size}")
    AppCard { Text(q.prompt, style = MaterialTheme.typography.titleMedium) }
    q.options.forEachIndexed { i, opt ->
        val p = picked
        val container = when {
            p == null -> cs.surfaceContainer
            i == q.correct -> cs.primaryContainer
            i == p -> cs.errorContainer
            else -> cs.surfaceContainer
        }
        AppCard(container = container, onClick = if (p == null) ({ picked = i; onAnswered(i == q.correct) }) else null) {
            Text(opt, style = MaterialTheme.typography.bodyMedium)
        }
    }
    if (picked != null) {
        Text(
            (if (picked == q.correct) "Sahi. " else "Galat. ") + q.why,
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
        )
        Button(
            onClick = { if (qi < rev.mcqs.lastIndex) qi++ else onFinished() },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (qi < rev.mcqs.lastIndex) "Agla sawal" else "Aage") }
    }
}

@Composable
private fun OrderStage(rev: NoteRevision, seed: Long, onChecked: (Boolean) -> Unit, onFinished: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val o = rev.order ?: return
    val pool = remember(o) {
        val s = o.steps.shuffled(Random(seed))
        if (s == o.steps) s.drop(1) + s.first() else s
    }
    val chosen = remember(o) { mutableStateListOf<String>() }
    var checked by remember(o) { mutableStateOf(false) }
    GroupHeader("Steps ka order", o.title)
    AppCard {
        if (chosen.isEmpty()) Text("Neeche se steps ek-ek karke sahi order me chuno.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        chosen.forEachIndexed { i, s -> Text("${i + 1}. $s", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp)) }
    }
    if (!checked) {
        pool.filter { it !in chosen }.forEach { s ->
            OutlinedButton(onClick = { chosen.add(s) }, modifier = Modifier.fillMaxWidth()) { Text(s) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { chosen.clear() }, enabled = chosen.isNotEmpty()) { Text("Reset") }
            Button(
                onClick = { checked = true; onChecked(chosen.toList() == o.steps) },
                enabled = chosen.size == o.steps.size
            ) { Text("Check karo") }
        }
    } else {
        val right = chosen.toList() == o.steps
        Text(if (right) "Sahi order." else "Galat. Sahi order:", color = if (right) cs.primary else cs.error)
        if (!right) o.steps.forEachIndexed { i, s -> Text("${i + 1}. $s", style = MaterialTheme.typography.bodyMedium) }
        Spacer(Modifier.height(4.dp))
        Button(onClick = onFinished, modifier = Modifier.fillMaxWidth()) { Text("Aage") }
    }
}
