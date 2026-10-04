package com.devsecops.tracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devsecops.tracker.data.GateView

// Mastery-gate checklist + prerequisites + live coverage for one module.
// Shown as the "Gate" tab of a chapter. Everything is derived from data
// already in Supabase, so it can't drift from the truth.
@Composable
fun GateContent(v: GateView) {
    val cs = MaterialTheme.colorScheme
    val ready = v.official || v.checks.all { it.ok }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppCard(padding = 16.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (v.readinessText.isNotBlank()) v.readinessText
                    else if (ready) "Ready for the gate check" else "Not ready yet",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (ready) cs.primary else cs.tertiary,
                    modifier = Modifier.weight(1f)
                )
                Pill(v.status ?: "Not started", if (v.status == "Mastered") cs.primary else cs.onSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
            v.notionResult?.let {
                Text(it, style = MaterialTheme.typography.labelLarge, color = if (v.official) cs.primary else cs.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
            }
            Text(
                "The tutor records the gate in Supabase after a graded, hands-on check. This list shows what the data says so far.",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            v.checks.forEach { c ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(c.ok, 22.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(c.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(c.detail, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                }
            }
        }

        AppCard(padding = 16.dp) {
            Text("Prerequisites", style = MaterialTheme.typography.titleMedium)
            Text("Each must be Mastered before this module starts.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            if (v.prereqs.isEmpty()) Text("None. This module has no named prerequisites.", style = MaterialTheme.typography.bodyMedium)
            v.prereqs.forEach { (m, st) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(st == "Mastered", 22.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(m.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(st ?: "not started", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                }
            }
        }

        GroupHeader("Coverage", "${v.rows.size} topics")
        v.rows.forEach { r ->
            AppCard(padding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.topic.name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    val lvlColor = when (r.bestLevel) {
                        null -> cs.onSurfaceVariant
                        "L1", "L2" -> cs.error
                        "L3" -> cs.tertiary
                        else -> cs.primary
                    }
                    Pill(r.bestLevel ?: "no evidence", lvlColor)
                }
                val flags = buildList {
                    if (r.topic.is_core) add("core")
                    if (r.topic.is_critical) add("critical")
                    add(if (r.covered) "taught" else "not taught yet")
                    if (r.openWeak) add("open weak spot")
                    r.staleDays?.let { add("stale ${it}d") }
                    r.lastDate?.let { add("last ${friendlyDate(it)}") }
                }
                Text(
                    flags.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (r.openWeak || r.staleDays != null) cs.error else cs.onSurfaceVariant
                )
                r.quote?.let {
                    Spacer(Modifier.height(4.dp))
                    Text("“${it.take(140)}”", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
                r.evidenceUrl?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = cs.secondary)
                }
            }
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
    )
}
