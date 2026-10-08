package com.devsecops.tracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devsecops.tracker.data.Fetch
import com.devsecops.tracker.data.NoteDue
import com.devsecops.tracker.data.NoteMeta
import com.devsecops.tracker.data.NoteRevisionRow
import com.devsecops.tracker.data.NoteRevisions
import com.devsecops.tracker.data.Notes
import com.devsecops.tracker.data.Repository
import com.devsecops.tracker.data.dueNotes
import com.devsecops.tracker.data.dueNotesTotal
import com.devsecops.tracker.data.localDateOf
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

// Revise tab: today's notes to revise (1, 3, 7, 14, 30 days after the note was
// written), then a small door to the older flash-card queue.
private data class ReviseState(
    val notes: List<NoteMeta>,
    val done: List<NoteRevisionRow>,
    val tableMissing: Boolean,
    val cardsDue: Int,
    val cardsOverdue: Int
)

fun rungLabel(rung: Int): String = when (rung) {
    1 -> "kal ka note"
    else -> "$rung din purana note"
}

@Composable
fun ReviseHome(onStartNote: (Long, Int) -> Unit, onOpenCards: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<ReviseState?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        val notes = Fetch.safeCall { Notes.list() }.getOrNull()
        if (notes == null) { error = "Notes load nahi hue (internet?)"; return }
        val done = Fetch.safeCall { NoteRevisions.list() }
        val rev = runCatching { Repository.loadRevise() }.getOrNull()
        state = ReviseState(
            notes = notes,
            done = done.getOrNull() ?: emptyList(),
            tableMissing = done.isFailure,
            cardsDue = rev?.queue?.size ?: 0,
            cardsOverdue = rev?.overdue ?: 0
        )
        error = null
    }
    LaunchedEffect(Unit) { load() }
    OnResume { scope.launch { load() } }

    Scaffold(topBar = { AppTitleBar(title = "Revise", subtitle = "Kal ke notes, phir sawal") }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val s = state
            when {
                s == null && error == null -> SkeletonList(3)
                s == null -> ErrorBlock(error ?: "", { scope.launch { load() } })
                else -> {
                    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
                    val doneToday = s.done.count { localDateOf(it.done_at) == today }
                    val due: List<NoteDue> = dueNotes(s.notes, s.done, today, doneToday)
                    val total = dueNotesTotal(s.notes, s.done, today)
                    if (s.tableMissing) {
                        AppCard(container = cs.errorContainer) {
                            Text("Table `note_revisions` abhi bani nahi", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Supabase SQL Editor me note_revisions_migration.sql chalao. Tab tak revision dikhega par save nahi hoga.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    GroupHeader("Aaj ka revision", "$doneToday done aaj · roz max 3 notes")
                    if (due.isEmpty()) {
                        AppCard {
                            Text(
                                if (doneToday > 0) "Aaj ka revision ho gaya." else "Aaj koi note due nahi.",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                if (total > 0) "$total aur notes due hain, kal se aayenge." else "Naya note aane par kal yahan dikhega.",
                                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                            )
                        }
                    } else {
                        due.forEach { d ->
                            AppCard(onClick = { onStartNote(d.note.id, d.rung) }) {
                                MonoLabel(rungLabel(d.rung))
                                Text(d.note.title, style = MaterialTheme.typography.titleMedium)
                                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                                    Button(onClick = { onStartNote(d.note.id, d.rung) }) { Text("Shuru karo") }
                                }
                            }
                        }
                        if (total > due.size) {
                            Text(
                                "${total - due.size} aur notes line me hain.",
                                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                            )
                        }
                    }
                    GroupHeader("Purane cards")
                    AppCard(onClick = onOpenCards) {
                        Text("${s.cardsDue} cards due" + if (s.cardsOverdue > 0) " (${s.cardsOverdue} overdue)" else "", style = MaterialTheme.typography.titleSmall)
                        Text("Tutor ke bhale hue sawal-jawab wale cards", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        OutlinedButton(onClick = onOpenCards, modifier = Modifier.padding(top = 8.dp)) { Text("Cards kholo") }
                    }
                }
            }
        }
    }
}
