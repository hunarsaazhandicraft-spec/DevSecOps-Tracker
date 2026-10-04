package com.devsecops.tracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devsecops.tracker.data.Extras
import com.devsecops.tracker.data.InsightsData

@Composable
fun InsightsScreen(onBack: () -> Unit) {
    ToolScaffold(title = "Insights & coach", onBack = onBack) { InsightsBody() }
}

// Also embedded as the third tab of Progress.
@Composable
fun ColumnScope.InsightsBody() {
    var data by remember { mutableStateOf<InsightsData?>(null) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        data = runCatching { Extras.loadInsights() }.getOrNull()
        loading = false
    }
    run {
        val d = data
        when {
            loading -> SkeletonList(3)
            d == null -> Text("Couldn't load insights.")
            else -> {
                SectionLabel("Coach")
                d.coach.forEach {
                    AppCard(padding = 14.dp) {
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text(
                    "Generated from your own reviews, sessions and mastery records with fixed rules. No AI guesses.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                SectionLabel("Skills by phase (topics at L3+)")
                if (d.phaseSkills.isEmpty()) Text("No mastery records yet.")
                d.phaseSkills.forEach { (label, frac) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, Modifier.width(74.dp), style = MaterialTheme.typography.bodySmall)
                        LinearProgressIndicator(progress = { frac }, modifier = Modifier.weight(1f).height(8.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("${(frac * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                    }
                }

                SectionLabel("Why cards were missed")
                if (d.tagCounts.isEmpty()) Text("No tagged misses yet. Tag a wrong answer in Revise.", style = MaterialTheme.typography.bodySmall)
                d.tagCounts.forEach { (tag, n) ->
                    val label = when (tag) {
                        "concept" -> "Concept"
                        "recall" -> "Didn't recall"
                        "careless" -> "Careless"
                        else -> "Vocabulary"
                    }
                    Row(Modifier.fillMaxWidth()) {
                        Text(label, Modifier.weight(1f)); Text("$n")
                    }
                }

                SectionLabel("Needs a refresh (decay)")
                if (d.stale.isEmpty()) Text("Nothing has gone stale.", style = MaterialTheme.typography.bodySmall)
                d.stale.take(10).forEach { (name, days) ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(name, Modifier.weight(1f)); Text("${days}d ago", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
