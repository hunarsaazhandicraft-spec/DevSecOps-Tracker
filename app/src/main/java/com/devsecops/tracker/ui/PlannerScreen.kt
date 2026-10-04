package com.devsecops.tracker.ui

import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devsecops.tracker.data.Extras
import com.devsecops.tracker.data.HomeDashboard
import com.devsecops.tracker.data.Repository
import kotlinx.coroutines.launch

// The stored daily target can just say "Revise N cards", which the plan already
// lists as its own step, so fall back to a study prompt in that case.
private fun studyText(target: String?): String =
    if (target.isNullOrBlank() || target.startsWith("Revise", ignoreCase = true))
        "New topic from your Learn tab, then log it"
    else target

private data class PlanStep(val minutes: Int, val title: String, val detail: String)

// "I have N minutes today" -> an ordered plan built from what is actually
// due: revision first (cheap and spaced), then overdue weak spots, then new
// study with whatever time is left.
@Composable
fun PlannerScreen(onBack: () -> Unit) {
    var d by remember { mutableStateOf<HomeDashboard?>(null) }
    var budget by rememberSaveable { mutableStateOf(60) }
    var loadFailed by remember { mutableStateOf(false) }
    var loadTick by remember { mutableStateOf(0) }
    var msg by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(loadTick) {
        loadFailed = false
        catching { Repository.loadHomeDashboard() }
            .onSuccess { d = it }
            .onFailure { loadFailed = true }
    }

    ToolScaffold(title = "Plan my day", onBack = onBack) {
        Text("How much time do you have?", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(30, 60, 90, 120).forEach { m ->
                FilterChip(selected = budget == m, onClick = { budget = m }, label = { Text("$m min") })
            }
        }
        val dash = d
        if (dash == null && loadFailed) {
            ErrorBlock("Couldn't load your plan data", { loadTick += 1 })
        } else if (dash == null) {
            CircularProgressIndicator()
        } else {
            val steps = mutableListOf<PlanStep>()
            var left = budget
            if (dash.dueRevisionCount > 0) {
                val m = minOf(left, maxOf(5, dash.dueRevisionCount * 2))
                steps += PlanStep(m, "Revision", "${dash.dueRevisionCount} cards due")
                left -= m
            }
            dash.topWeakSpots.filter { isOverdue(it.revisit_date) }.take(2).forEach { w ->
                if (left >= 10) {
                    steps += PlanStep(10, "Weak spot", cleanTitle(w.title ?: w.reason))
                    left -= 10
                }
            }
            if (left >= 15) {
                steps += PlanStep(left, "Study", studyText(dash.todaysTarget))
                left = 0
            }
            if (steps.isEmpty()) steps += PlanStep(budget, "Study", studyText(dash.todaysTarget))

            steps.forEach { s ->
                AppCard(padding = 0.dp) {
                    Row(Modifier.padding(14.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.title, fontWeight = FontWeight.Bold)
                            Text(s.detail, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("${s.minutes} min", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val start = System.currentTimeMillis() + 30 * 60_000L
                    val intent = Intent(Intent.ACTION_INSERT).apply {
                        data = CalendarContract.Events.CONTENT_URI
                        putExtra(CalendarContract.Events.TITLE, "DevSecOps study block")
                        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
                        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, start + budget * 60_000L)
                    }
                    runCatching { context.startActivity(intent) }
                        .onFailure { msg = "No calendar app found." }
                }
            ) { Text("Add a $budget-min block to my calendar") }
        }

        HorizontalDivider()
        Text("Not today?", style = MaterialTheme.typography.labelLarge)
        Text(
            "One planned rest day per week keeps your streak alive.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                scope.launch {
                    msg = catching { Extras.takeRestDay() }
                        .getOrElse { it.message ?: "Failed" } ?: "Rest day saved. Your streak is safe."
                }
            }
        ) { Text("Take a rest day") }
        msg?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}
