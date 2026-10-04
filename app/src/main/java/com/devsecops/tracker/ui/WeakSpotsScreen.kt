package com.devsecops.tracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devsecops.tracker.data.Extras
import com.devsecops.tracker.data.Repository
import com.devsecops.tracker.data.WeakSpot
import kotlinx.coroutines.launch

// Things that need another pass. Open ones first (with what to do about
// them), cleared ones kept as history.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeakSpotsScreen(onBack: () -> Unit, onAddWeakSpot: () -> Unit, showBackButton: Boolean = true) {
    var all by remember { mutableStateOf<List<WeakSpot>>(emptyList()) }
    var topicNames by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var moduleNames by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var tab by rememberSaveable { mutableStateOf(0) }
    var confirmId by rememberSaveable { mutableStateOf<Long?>(null) }
    var actionErr by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun refresh(silent: Boolean = false) {
        if (!silent) loading = true
        error = null
        catching {
            Triple(Extras.listAllWeakSpots(), Repository.listTopics(), Repository.listModules())
        }.onSuccess { (spots, topics, mods) ->
            all = spots
            topicNames = topics.associate { it.id to it.name }
            moduleNames = mods.associate { it.id to it.name.substringBefore(' ') }
        }.onFailure { if (!silent) error = it.message ?: "Load failed" }
        loading = false
    }

    LaunchedEffect(Unit) { refresh() }
    OnResume { scope.launch { refresh(silent = true) } }

    val open = all.filter { it.cleared_at == null }
    val cleared = all.filter { it.cleared_at != null }
    val overdue = open.count { isOverdue(it.revisit_date) }

    confirmId?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmId = null },
            title = { Text("Mark as cleared?") },
            text = { Text("Mark this weak spot as cleared? Only do this after an unaided re-check.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmId = null
                    actionErr = null
                    scope.launch {
                        catching { Repository.clearWeakSpot(id) }
                            .onSuccess { refresh() }
                            .onFailure { actionErr = "Couldn't clear it: " + (it.message ?: "unknown error") }
                    }
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmId = null }) { Text("Cancel") } }
        )
    }

    Scaffold(
        topBar = {
            AppTitleBar(
                title = "Weak spots",
                subtitle = if (loading) null
                else "${open.size} open" + if (overdue > 0) " · $overdue overdue" else "",
                onBack = if (showBackButton) onBack else null
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("Add") },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                onClick = onAddWeakSpot
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SegTabs(
                listOf("Open · ${open.size}", "Cleared · ${cleared.size}"),
                tab, { tab = it },
                Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
            actionErr?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            when {
                loading -> SkeletonList(3)
                error != null -> ErrorBlock(error ?: "", { scope.launch { refresh() } }, Modifier.padding(16.dp))
                else -> {
                    val list = if (tab == 0) open else cleared
                    if (list.isEmpty()) {
                        EmptyBlock(
                            Icons.Filled.Check,
                            if (tab == 0) "Nothing weak right now" else "Nothing cleared yet",
                            if (tab == 0) "Weak spots appear here when a topic needs another pass." else "Cleared weak spots stay here as history."
                        )
                    } else LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 88.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(list, key = { _, it -> it.id }) { idx, ws ->
                            WeakSpotCard(
                                ws,
                                modifier = Modifier.appear(idx),
                                moduleLabel = (ws.module_id?.let { moduleNames[it] } ?: "") +
                                    (topicNames[ws.topic_id]?.let { " · $it" } ?: ""),
                                onClear = { confirmId = ws.id }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeakSpotCard(ws: WeakSpot, moduleLabel: String, modifier: Modifier = Modifier, onClear: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val cleared = ws.cleared_at != null
    val late = !cleared && isOverdue(ws.revisit_date)
    AppCard(modifier = modifier, padding = 18.dp) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            if (late) Pill("Overdue", cs.error)
            else if (cleared) Pill("Cleared", cs.primary)
            else {
                val sev = ws.severity ?: "Open"
                val sevColor = when (sev.lowercase()) {
                    "high", "critical", "severe" -> cs.error
                    "medium", "med", "moderate" -> cs.tertiary
                    "low", "minor" -> cs.secondary
                    else -> cs.tertiary
                }
                Pill(sev, sevColor)
            }
            Spacer(Modifier.width(8.dp))
            Text(moduleLabel, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1)
        }
        Spacer(Modifier.height(10.dp))
        Text(cleanTitle(ws.title ?: ws.reason), style = MaterialTheme.typography.titleMedium)
        if (ws.title != null && ws.reason.isNotBlank() && ws.reason != ws.title) {
            Spacer(Modifier.height(4.dp))
            Text(ws.reason, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        if (!ws.required_action.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            SectionLabel("Do this")
            Text(ws.required_action, style = MaterialTheme.typography.bodyMedium)
        }
        if (!ws.practice_needed.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            SectionLabel("Practice")
            Text(ws.practice_needed, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(
                if (cleared) "Cleared ${friendlyLocal(ws.cleared_at)}"
                else "Revisit ${friendlyDate(ws.revisit_date)} · opened ${friendlyLocal(ws.opened_at)}",
                style = MaterialTheme.typography.bodySmall,
                color = if (late) cs.error else cs.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (!cleared) FilledTonalButton(onClick = onClear) { Text("Mark cleared") }
        }
    }
}

// Timestamps are UTC in the database; show the date as it was on this device.
private fun friendlyLocal(ts: String?): String = friendlyDate(localDateString(ts))
