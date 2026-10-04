package com.devsecops.tracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devsecops.tracker.data.Extras
import com.devsecops.tracker.data.MasteryRecord
import com.devsecops.tracker.data.Module
import com.devsecops.tracker.data.Prereqs
import com.devsecops.tracker.data.Repository
import com.devsecops.tracker.data.Topic
import com.devsecops.tracker.data.WeakSpot
import kotlinx.coroutines.launch

// The roadmap: every module of the curriculum, grouped by phase, each with
// where you stand. Tap a module to open its chapter (days, topics, weak
// spots, cards and the mastery gate all live there).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MasteryScreen(
    onBack: () -> Unit,
    onRecordEvidence: () -> Unit,
    onOpenModule: (Int) -> Unit,
    showBackButton: Boolean = true
) {
    var modules by remember { mutableStateOf<List<Module>>(emptyList()) }
    var topics by remember { mutableStateOf<List<Topic>>(emptyList()) }
    var records by remember { mutableStateOf<List<MasteryRecord>>(emptyList()) }
    var statuses by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var weak by remember { mutableStateOf<List<WeakSpot>>(emptyList()) }
    var phases by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var phaseSel by rememberSaveable { mutableStateOf<Int?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun refresh(silent: Boolean = false) {
        if (!silent) loading = true
        error = null
        catching { Repository.loadMasteryView() }
            .onSuccess { (m, t, r) -> modules = m; topics = t; records = r }
            .onFailure { if (!silent) error = it.message ?: "Load failed" }
        statuses = catching { Repository.listModuleStatuses() }.getOrDefault(statuses)
        weak = catching { Repository.listOpenWeakSpots() }.getOrDefault(weak)
        if (phases.isEmpty()) phases = catching { Extras.listPhases() }.getOrDefault(phases)
        loading = false
    }

    LaunchedEffect(Unit) { refresh() }
    OnResume { scope.launch { refresh(silent = true) } }

    val assessedTopics = remember(records) { records.map { it.topic_id }.toSet() }

    Scaffold(
        topBar = {
            AppTitleBar(
                title = "Learn",
                subtitle = if (modules.isEmpty()) null
                else "${modules.size} modules · ${topics.count { it.id in assessedTopics }} of ${topics.size} topics have evidence",
                onBack = if (showBackButton) onBack else null,
                actions = {
                    FilledTonalIconButton(onClick = onRecordEvidence) {
                        Icon(Icons.Filled.Add, contentDescription = "Record evidence")
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loading -> SkeletonList()
                error != null -> ErrorBlock(error ?: "", { scope.launch { refresh() } }, Modifier.padding(16.dp))
                modules.isEmpty() -> EmptyBlock(Icons.Filled.MenuBook, "No curriculum yet", "Modules appear here once the curriculum is loaded.")
                else -> {
                    val phaseIds = modules.map { it.phase_id }.distinct()
                    fun phaseShort(id: Int) = (phases[id] ?: "Phase $id").substringBefore(" —")
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(Sp.l, 0.dp, Sp.l, Sp.xl),
                        verticalArrangement = Arrangement.spacedBy(Sp.s)
                    ) {
                        val hero = modules.firstOrNull { statusIs(statuses[it.id], "In Progress") }
                            ?: modules.firstOrNull { m ->
                                statuses[m.id] == null &&
                                    (Prereqs.EDGES[m.id] ?: emptyList()).all { statusIs(statuses[it], "Mastered") }
                            }
                        if (hero != null) {
                            item(key = "hero") {
                                val ht = topics.filter { it.module_id == hero.id }
                                val hAssessed = ht.count { it.id in assessedTopics }
                                val next = ht.firstOrNull { it.id !in assessedTopics }
                                HeroCard(hero, statuses[hero.id], hAssessed, ht.size, next?.name) { onOpenModule(hero.id) }
                            }
                        }
                        item {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                item {
                                    FilterChip(selected = phaseSel == null, onClick = { phaseSel = null }, label = { Text("All") })
                                }
                                items(phaseIds) { id ->
                                    FilterChip(
                                        selected = phaseSel == id,
                                        onClick = { phaseSel = if (phaseSel == id) null else id },
                                        label = { Text(phaseShort(id)) }
                                    )
                                }
                            }
                        }
                        phaseIds.filter { phaseSel == null || it == phaseSel }.forEach { pid ->
                            val mods = modules.filter { it.phase_id == pid }
                            val mastered = mods.count { statusIs(statuses[it.id], "Mastered") }
                            val now = mods.firstOrNull { statusIs(statuses[it.id], "In Progress") }
                            item(key = "h$pid") {
                                GroupHeader(
                                    title = phases[pid] ?: "Phase $pid",
                                    subtitle = "$mastered of ${mods.size} modules mastered" +
                                        (now?.let { " · now on ${it.name.substringBefore(' ')}" } ?: "")
                                )
                            }
                            itemsIndexed(mods, key = { _, it -> "m${it.id}" }) { idx, m ->
                                val mt = topics.filter { it.module_id == m.id }
                                val assessed = mt.count { it.id in assessedTopics }
                                val weakCount = weak.count { it.module_id == m.id || mt.any { t -> t.id == it.topic_id } }
                                val st = statuses[m.id]
                                val locked = st == null && (Prereqs.EDGES[m.id] ?: emptyList()).any { !statusIs(statuses[it], "Mastered") }
                                ModuleRow(m, st, locked, assessed, mt.size, weakCount, Modifier.appear(idx)) { onOpenModule(m.id) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModuleRow(m: Module, status: String?, locked: Boolean, assessed: Int, total: Int, weak: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val mastered = statusIs(status, "Mastered")
    val inProgress = statusIs(status, "In Progress")
    // Locked rows keep readable text: dim by using softer colours, not by fading the whole row.
    val titleColor = if (locked) cs.onSurfaceVariant else cs.onSurface
    val semanticsMod = if (locked) modifier.semantics {
        disabled()
        contentDescription = "${m.name}, locked until earlier modules are mastered"
    } else modifier
    AppCard(modifier = semanticsMod, onClick = onClick, padding = Sp.m) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(m.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, color = titleColor)
                Text(
                    "$assessed / $total with evidence" + if (weak > 0) " · $weak weak spot${if (weak == 1) "" else "s"}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (weak > 0) cs.error else cs.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(Sp.m))
            when {
                mastered -> Pill("Mastered", cs.primary)
                inProgress -> Pill("In progress", cs.secondary)
                status != null -> Pill(status, cs.tertiary)
                locked -> Pill("Locked", cs.onSurfaceVariant)
                else -> Pill("Not started", cs.onSurfaceVariant)
            }
        }
        if (total > 0 || mastered) {
            Spacer(Modifier.height(Sp.s))
            val frac = if (mastered) 1f else assessed.toFloat() / total
            ThinBar(frac, if (mastered) cs.primary else if (locked) cs.outline else cs.secondary)
        }
    }
}

@Composable
private fun HeroCard(m: Module, status: String?, assessed: Int, total: Int, next: String?, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    AppCard(modifier = Modifier.appear(0), onClick = onClick, padding = Sp.l) {
        Text("You are here", style = MaterialTheme.typography.labelMedium, color = cs.primary)
        Spacer(Modifier.height(Sp.xs))
        Text(m.name, style = MaterialTheme.typography.titleLarge, maxLines = 2)
        Spacer(Modifier.height(Sp.m))
        if (total > 0) {
            ThinBar(assessed.toFloat() / total, cs.primary)
            Spacer(Modifier.height(Sp.xs))
        }
        Text(
            "$assessed / $total topics with evidence" + if (status == null) " \u00B7 ready to start" else "",
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant
        )
        if (next != null) {
            Spacer(Modifier.height(Sp.xs))
            Text("next: $next", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
