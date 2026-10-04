package com.devsecops.tracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import com.devsecops.tracker.data.Repository
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devsecops.tracker.data.Module
import com.devsecops.tracker.data.Topic

// Loads topics + modules for a form, without ever crashing when offline.
// Screens keep only the selected topic's id (saveable) and look it up here.
internal class TopicLoader {
    var topics by mutableStateOf<List<Topic>>(emptyList())
    var modules by mutableStateOf<List<Module>>(emptyList())
    var loading by mutableStateOf(true)
    var failed by mutableStateOf(false)
    var tick by mutableStateOf(0)
    fun retry() { tick += 1 }
}

@Composable
internal fun rememberTopicLoader(): TopicLoader {
    val l = remember { TopicLoader() }
    LaunchedEffect(l.tick) {
        l.loading = true
        l.failed = false
        catching { Repository.listTopics() to Repository.listModules() }
            .onSuccess { (t, m) -> l.topics = t; l.modules = m }
            .onFailure { l.failed = true }
        l.loading = false
    }
    return l
}

@Composable
internal fun TopicPicker(loader: TopicLoader, selected: Topic?, onSelect: (Topic?) -> Unit) {
    when {
        loader.loading -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Text("Loading topics\u2026", style = MaterialTheme.typography.bodyMedium)
        }
        loader.failed -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Couldn't load topics",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
            TextButton(onClick = { loader.retry() }) { Text("Retry") }
        }
        else -> TopicPicker(loader.topics, loader.modules, selected, onSelect)
    }
}

// There are ~174 topics in the full curriculum, so a plain dropdown is
// useless — you type to narrow it down, and each result shows which module
// it belongs to (several modules have similarly-named topics).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicPicker(
    topics: List<Topic>,
    modules: List<Module>,
    selected: Topic?,
    onSelect: (Topic?) -> Unit
) {
    var query by rememberSaveable { mutableStateOf(selected?.name ?: "") }
    var expanded by remember { mutableStateOf(false) }

    val moduleNames = remember(modules) { modules.associate { it.id to it.name } }

    val filtered = remember(query, topics) {
        if (query.isBlank()) {
            topics
        } else {
            topics.filter { it.name.contains(query, ignoreCase = true) }
        }
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                expanded = true
                // Typing past a chosen topic means it's no longer chosen.
                if (selected != null && it != selected.name) onSelect(null)
            },
            label = { Text("Topic") },
            placeholder = { Text("Type to search ${topics.size} topics") },
            supportingText = selected?.let { topic ->
                { Text(moduleNames[topic.module_id] ?: "") }
            },
            singleLine = true,
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 320.dp)
        ) {
            when {
                topics.isEmpty() -> DropdownMenuItem(
                    text = { Text("No topics yet") },
                    onClick = {}
                )
                filtered.isEmpty() -> DropdownMenuItem(
                    text = { Text("No topic matches \"$query\"") },
                    onClick = {}
                )
                else -> filtered.take(40).forEach { topic ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(topic.name, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    moduleNames[topic.module_id] ?: "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        onClick = {
                            onSelect(topic)
                            query = topic.name
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
