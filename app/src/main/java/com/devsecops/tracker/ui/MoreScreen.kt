package com.devsecops.tracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// "Aur": everything that is not the daily loop (Today, Revise, Notes).
// Read-only mirrors and reference lists live here, one line each, so the
// main tabs stay about what you do every day.
private data class MoreRow(val route: String, val title: String, val line: String)

private val MORE_GROUPS: List<Pair<String, List<MoreRow>>> = listOf(
    "Course" to listOf(
        MoreRow("path", "Path", "Chats aur sessions, tutor ke hisaab se"),
        MoreRow("weak_spots", "Weak spots", "Jo gaps tutor ne pakde"),
        MoreRow("mastery", "Mastery", "Module-wise L1 se L5 ka saboot"),
        MoreRow("gates", "Gates aur labs", "Mastery gates aur lab setups")
    ),
    "Reference" to listOf(
        MoreRow("library", "Project docs", "Curriculum aur rules ke documents"),
        MoreRow("resources", "Resources", "Courses, docs, books, tools"),
        MoreRow("glossary", "Glossary", "Terms ke matlab"),
        MoreRow("interview", "Interview bank", "Interview ke sawal"),
        MoreRow("projects", "Projects", "P01 se P09 ka status")
    ),
    "Tools" to listOf(
        MoreRow("progress", "Progress overview", "Hours, phases, memory (sirf dekhne ke liye)"),
        MoreRow("focus", "Focus timer", "Pomodoro"),
        MoreRow("planner", "Plan my day", "Time ke hisaab se order"),
        MoreRow("commands", "Command vault", "Saved commands"),
        MoreRow("bandit", "Bandit tracker", "Bandit levels")
    ),
    "App" to listOf(
        MoreRow("attendance", "Attendance", "Din ka record, leave, breaks"),
        MoreRow("settings", "Settings", "Account, reminders")
    )
)

@Composable
fun MoreScreen(onOpen: (String) -> Unit) {
    Scaffold(topBar = { AppTitleBar(title = "Aur", subtitle = "Roz ka kaam Today, Revise, Notes me hai") }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MORE_GROUPS.forEach { (group, rows) ->
                GroupHeader(group)
                rows.forEach { r ->
                    AppCard(onClick = { onOpen(r.route) }, padding = 16.dp) {
                        Text(r.title, style = MaterialTheme.typography.titleSmall)
                        Text(r.line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
