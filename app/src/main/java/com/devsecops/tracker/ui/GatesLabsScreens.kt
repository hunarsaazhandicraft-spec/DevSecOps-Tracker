package com.devsecops.tracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.devsecops.tracker.data.Curriculum
import com.devsecops.tracker.data.Fetch
import com.devsecops.tracker.data.GateCheckRow
import com.devsecops.tracker.data.GateRow
import com.devsecops.tracker.data.GateStatus
import com.devsecops.tracker.data.LabRow
import com.devsecops.tracker.data.LabRun
import com.devsecops.tracker.data.labStatusLabel
import com.devsecops.tracker.data.latestLabRuns
import com.devsecops.tracker.data.ProgressItem
import kotlinx.coroutines.launch

private data class GatesLabsData(
    val gates: List<GateRow>,
    val checks: Map<String, List<GateCheckRow>>,
    val labs: List<LabRow>,
    val progress: List<ProgressItem>,
    val runs: List<LabRun> = emptyList()
)

private val KIND_ORDER = listOf("module", "phase", "project", "critical_skill", "capstone")

private fun kindTitle(k: String) = when (k) {
    "module" -> "Module gates"
    "phase" -> "Phase gates"
    "project" -> "Project gates"
    "critical_skill" -> "Critical skills"
    "capstone" -> "Capstone"
    else -> "Other rules"
}

@Composable
fun GatesLabsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<GatesLabsData?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableIntStateOf(0) }

    suspend fun load() {
        Fetch.safeCall {
            val g = Curriculum.gates()
            val c = Curriculum.gateChecks().groupBy { it.gate_id }
            val l = Curriculum.labs()
            val p = Fetch.progressItems()
            GatesLabsData(g, c, l, p, Curriculum.labRuns())
        }.onSuccess { data = it; err = null }
            .onFailure { err = it.message ?: "Could not load gates and labs" }
    }
    LaunchedEffect(Unit) { load() }
    OnResume { scope.launch { load() } }

    Column(Modifier.fillMaxSize()) {
        AppTitleBar(
            title = "Gates and labs",
            onBack = onBack,
            subtitle = data?.let { "${it.gates.size} gates - ${it.labs.size} labs (read-only, the tutor records results)" }
        )
        SegTabs(listOf("Gates", "Labs"), tab, { tab = it }, Modifier.padding(horizontal = Sp.l, vertical = Sp.s))
        val d = data
        when {
            err != null && d == null -> ErrorBlock(err!!, onRetry = { scope.launch { load() } })
            d == null -> SkeletonList()
            tab == 0 -> GatesList(d)
            else -> LabsList(d)
        }
    }
}

@Composable
private fun GatesList(d: GatesLabsData) {
    val cs = MaterialTheme.colorScheme
    val grouped = d.gates.groupBy { it.kind }
    val kinds = KIND_ORDER.filter { grouped.containsKey(it) } + grouped.keys.filter { it !in KIND_ORDER }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Sp.l, vertical = Sp.s),
        verticalArrangement = Arrangement.spacedBy(Sp.s)
    ) {
        kinds.forEach { kind ->
            val list = grouped[kind].orEmpty()
            item(key = "h-$kind") { GroupHeader(kindTitle(kind), "${list.size}") }
            items(list, key = { it.id }) { g ->
                var open by remember(g.id) { mutableStateOf(false) }
                val checks = d.checks[g.id].orEmpty()
                val st: GateStatus? = if (g.kind == "module") Curriculum.moduleGateStatus(d.progress, g.module_code) else null
                AppCard(onClick = { open = !open }, padding = Sp.m) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(g.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${checks.size} check" + (if (checks.size == 1) "" else "s") +
                                    (g.module_code?.let { " - $it" } ?: ""),
                                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                            )
                        }
                        st?.let { Pill(it.label, if (it.passed) cs.primary else cs.onSurfaceVariant) }
                    }
                    if (st?.bestLevel != null) {
                        Text("Best evidence: ${st.bestLevel}", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                    }
                    if (open) {
                        Spacer(Modifier.height(Sp.s))
                        g.description?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                            Spacer(Modifier.height(Sp.s))
                        }
                        if (checks.isEmpty()) {
                            Text("No separate checks listed.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        }
                        checks.forEach { c ->
                            Row(Modifier.fillMaxWidth().padding(vertical = Sp.xs)) {
                                Text("${c.check_no}.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(Sp.xl))
                                Column(Modifier.weight(1f)) {
                                    Text(c.check_text, style = MaterialTheme.typography.bodyMedium)
                                    Row(horizontalArrangement = Arrangement.spacedBy(Sp.xs)) {
                                        if (c.requires_doing) Pill("hands-on", cs.tertiary)
                                        c.min_level?.let { Pill("min $it", cs.primary) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(Sp.xxl)) }
    }
}

@Composable
private fun LabsList(d: GatesLabsData) {
    val cs = MaterialTheme.colorScheme
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Sp.l, vertical = Sp.s),
        verticalArrangement = Arrangement.spacedBy(Sp.s)
    ) {
        item {
            AppCard(padding = Sp.m) {
                Text("Golden rule", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Only test systems you own or are explicitly allowed to test. Every lab below states its own scope.",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                )
            }
        }
        item { LabPreflightCard() }
        val latest = latestLabRuns(d.runs)
        item {
            val done = d.labs.count { latest[it.id]?.status == "done" }
            val going = d.labs.count { latest[it.id]?.status == "in_progress" }
            MonoLabel("// $done done · $going in progress · ${d.labs.size} labs")
        }
        items(d.labs, key = { it.id }) { l ->
            var open by remember(l.id) { mutableStateOf(false) }
            val run = latest[l.id]
            AppCard(onClick = { open = !open }, padding = Sp.m) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(l.name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    l.module_code?.takeIf { it.isNotBlank() }?.let { Pill(it, cs.primary) }
                }
                MonoLabel(
                    labStatusLabel(run),
                    color = when (run?.status) {
                        "done" -> cs.primary
                        "blocked" -> cs.error
                        else -> cs.onSurfaceVariant
                    }
                )
                l.environment?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                        maxLines = if (open) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis
                    )
                }
                if (open) {
                    run?.let { r ->
                        listOfNotNull(r.evidence, r.evidence_url, r.notes).filter { it.isNotBlank() }.forEach {
                            Spacer(Modifier.height(Sp.s))
                            Text("Evidence", style = MaterialTheme.typography.labelMedium, color = cs.primary)
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    l.scope_note?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(Sp.s))
                        Text("Scope", style = MaterialTheme.typography.labelMedium, color = cs.primary)
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                    l.safety_notes?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(Sp.s))
                        Text("Safety", style = MaterialTheme.typography.labelMedium, color = cs.error)
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(Sp.xxl)) }
    }
}
