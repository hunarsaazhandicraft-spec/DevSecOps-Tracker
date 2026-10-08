package com.devsecops.tracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// Shared frame for the small tool screens: compact title bar + padded column.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolScaffold(
    title: String,
    onBack: () -> Unit,
    scroll: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    Scaffold(topBar = { AppTitleBar(title = title, onBack = onBack, actions = actions) }) { padding ->
        val base = Modifier.fillMaxSize().padding(padding)
        Column(
            (if (scroll) base.verticalScroll(rememberScrollState()) else base).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

private data class ToolEntry(val route: String, val title: String, val subtitle: String)

private val TOOL_ENTRIES = listOf(
    ToolEntry("planner", "Plan my day", "Pick the time you have, get an ordered plan"),
    ToolEntry("focus", "Focus timer", "Pomodoro that saves a study session"),
    ToolEntry("insights", "Insights & coach", "Patterns in your mistakes, decay, skills by phase"),
    ToolEntry("search", "Search everything", "Topics, glossary, commands, cards"),
    ToolEntry("glossary", "Glossary", "Terms and your own explanations"),
    ToolEntry("interview", "Interview practice", "Question, think, reveal, self-rate"),
    ToolEntry("projects", "Projects P01-P09", "Status, repo link, rubric"),
    ToolEntry("commands", "Command vault", "Commands you want to remember"),
    ToolEntry("bandit", "Bandit tracker", "OverTheWire Bandit levels 0-33")
)

@Composable
fun ToolsScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    ToolScaffold(title = "More tools", onBack = onBack) {
        TOOL_ENTRIES.forEach { e ->
            AppCard(onClick = { onOpen(e.route) }, padding = 0.dp) {
                Column(Modifier.padding(16.dp)) {
                    Text(e.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        e.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        DrillLinkCard()
    }
}
