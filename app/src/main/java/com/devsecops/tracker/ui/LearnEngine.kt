package com.devsecops.tracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.devsecops.tracker.data.ChatPlanRow
import com.devsecops.tracker.data.Curriculum
import com.devsecops.tracker.data.PacketExtras
import com.devsecops.tracker.data.Resources
import com.devsecops.tracker.data.SessionView

// =========================================================================
// Learn Engine: a study packet for the session to study now, and a recall
// drill over a session's must-cover items. Both are read-only; the drill keeps
// its score in memory and offers a text the learner can paste to the tutor.
// =========================================================================

private fun clean(s: String) = s.replace("**", "").replace("`", "")

@Composable
fun StudyPacketCard(
    chat: ChatPlanRow,
    sessions: List<SessionView>,
    onOpenNote: (Long) -> Unit,
    onDrill: (SessionView) -> Unit,
    modifier: Modifier = Modifier
) {
    val idx = currentSessionIndex(sessions)
    if (idx < 0) return
    val sv = sessions[idx]
    val cs = MaterialTheme.colorScheme
    var extras by remember { mutableStateOf<PacketExtras?>(null) }
    val uri = LocalUriHandler.current
    LaunchedEffect(chat.module_code) { extras = Curriculum.packetExtras(chat.module_code) }

    AppCard(modifier) {
        MonoLabel("// study packet · aaj ka session", color = cs.primary)
        Spacer(Modifier.height(Sp.xs))
        Text("S${sv.session.session_no} · ${sv.session.title}", style = MaterialTheme.typography.titleMedium)
        Text(
            buildString {
                append("${sv.verified}/${sv.total} verified")
                sv.session.min_minutes?.let { append(" · min ${it / 60}h${if (it % 60 != 0) " ${it % 60}m" else ""}") }
            },
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
        )
        sv.session.goal?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(Sp.s))
            MonoLabel("goal")
            Text(it, style = MaterialTheme.typography.bodySmall)
        }
        sv.session.done_when?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(Sp.s))
            MonoLabel("done when")
            Text(it, style = MaterialTheme.typography.bodySmall)
        }
        val ex = extras
        if (ex != null && ex.notes.isNotEmpty()) {
            Spacer(Modifier.height(Sp.s))
            MonoLabel("notes (${ex.notes.size})")
            ex.notes.take(5).forEach { n ->
                TextButton(onClick = { onOpenNote(n.id) }, contentPadding = PaddingValues(0.dp)) {
                    Text(n.title, maxLines = 1)
                }
            }
        }
        if (ex != null && ex.labs.isNotEmpty()) {
            Spacer(Modifier.height(Sp.s))
            MonoLabel("labs")
            ex.labs.forEach { Text("• ${it.name}", style = MaterialTheme.typography.bodySmall) }
        }
        if (ex != null && ex.resources.isNotEmpty()) {
            Spacer(Modifier.height(Sp.s))
            MonoLabel("resources")
            ex.resources.forEach { r ->
                val link = Resources.openableUrl(r.url)
                if (link != null) {
                    TextButton(onClick = { runCatching { uri.openUri(link) } }, contentPadding = PaddingValues(0.dp)) {
                        Text(r.title, maxLines = 1)
                    }
                } else Text("• ${r.title}", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (sv.total > 0) {
            Spacer(Modifier.height(Sp.m))
            FilledTonalButton(onClick = { onDrill(sv) }) { Text("Drill start karo (${sv.total} items)") }
        }
    }
}

@Composable
fun DrillDialog(sv: SessionView, onDismiss: () -> Unit) {
    val items = remember(sv.session.id) { sv.items.map { it.first } }
    if (items.isEmpty()) { onDismiss(); return }
    var idx by remember { mutableIntStateOf(0) }
    var revealed by remember { mutableStateOf(false) }
    val known = remember { mutableStateListOf<Boolean>() }
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.fillMaxWidth(0.94f).heightIn(max = 640.dp),
            shape = MaterialTheme.shapes.large, color = cs.surface, tonalElevation = 6.dp
        ) {
            Column(Modifier.padding(Sp.l).verticalScroll(rememberScrollState())) {
                MonoLabel("// drill · S${sv.session.session_no} · ${sv.session.title}", color = cs.primary)
                Spacer(Modifier.height(Sp.s))
                if (idx >= items.size) {
                    val summary = drillSummary(sv.session.title, items.map { clean(it.item) }, known.toList())
                    Text("${known.count { it }}/${items.size} yaad the", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(Sp.s))
                    Text(summary, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "Ye sirf tumhara self-check hai. Verified tab hota hai jab tutor chat mein tumhare shabdon se pass kare.",
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(top = Sp.s)
                    )
                    Spacer(Modifier.height(Sp.m))
                    Row(horizontalArrangement = Arrangement.spacedBy(Sp.s)) {
                        FilledTonalButton(onClick = { clipboard.setText(AnnotatedString(summary)); copied = true }) {
                            Text(if (copied) "Copied" else "Copy for tutor")
                        }
                        OutlinedButton(onClick = { idx = 0; revealed = false; known.clear(); copied = false }) { Text("Phir se") }
                        TextButton(onClick = onDismiss) { Text("Band") }
                    }
                } else {
                    val it = items[idx]
                    Text("${idx + 1} / ${items.size}", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                    Text(clean(it.item), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(Sp.s))
                    Text(
                        "Pehle apne shabdon mein bolo: ye kya hai, kya toot-ta hai, attacker aur defender kya dekhte hain. Phir reveal karo.",
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                    )
                    Spacer(Modifier.height(Sp.m))
                    if (!revealed) {
                        FilledTonalButton(onClick = { revealed = true }) { Text("Reveal") }
                    } else {
                        listOf(
                            "what breaks" to it.what_breaks, "attacker view" to it.attacker_view,
                            "defender view" to it.defender_view, "misconception" to it.misconception
                        ).forEach { (label, text) ->
                            if (!text.isNullOrBlank()) {
                                MonoLabel(label)
                                Text(clean(text), style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.height(Sp.s))
                            }
                        }
                        if (listOf(it.what_breaks, it.attacker_view, it.defender_view, it.misconception).all { x -> x.isNullOrBlank() }) {
                            Text("Is item ke liye tutor ne alag detail nahi likhi.", style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.height(Sp.s))
                        Row(horizontalArrangement = Arrangement.spacedBy(Sp.s)) {
                            Button(onClick = { known.add(true); idx++; revealed = false }) { Text("Yaad tha") }
                            OutlinedButton(onClick = { known.add(false); idx++; revealed = false }) { Text("Dobara dekhna") }
                        }
                    }
                    Spacer(Modifier.height(Sp.m))
                    TextButton(onClick = onDismiss) { Text("Band karo") }
                }
            }
        }
    }
}
