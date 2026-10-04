package com.devsecops.tracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devsecops.tracker.data.MasteryRecordInsert
import com.devsecops.tracker.data.Repository
import kotlinx.coroutines.launch

private val LEVELS = listOf("L1", "L2", "L3", "L4", "L5")
private val HINTS = listOf(1, 2, 3, 4, 5)

// Every save here needs a real quote from the conversation — that's the
// rule the project enforces (record only what the evidence shows). The
// Save button won't enable without one, and the DB rejects a blank quote
// too, so this can't be bypassed from the app even by accident.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordMasteryScreen(onSaved: () -> Unit) {
    val loader = rememberTopicLoader()
    var selectedTopicId by rememberSaveable { mutableStateOf(-1) }
    val selectedTopic = loader.topics.firstOrNull { it.id == selectedTopicId }
    var level by rememberSaveable { mutableStateOf<String?>(null) }
    var hintUsed by rememberSaveable { mutableStateOf<Int?>(null) }
    var evidenceQuote by rememberSaveable { mutableStateOf("") }
    var evidenceUrl by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "This becomes a permanent record — it can't be edited or deleted later, only added to.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        TopicPicker(loader, selectedTopic) { selectedTopicId = it?.id ?: -1 }

        Text("Level", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LEVELS.forEach { l ->
                FilterChip(
                    selected = level == l,
                    onClick = { level = l },
                    label = { Text(l) }
                )
            }
        }

        Text("Hint used (optional — leave unselected if independent)", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HINTS.forEach { h ->
                FilterChip(
                    selected = hintUsed == h,
                    onClick = { hintUsed = if (hintUsed == h) null else h },
                    label = { Text("$h") }
                )
            }
        }

        OutlinedTextField(
            value = evidenceQuote,
            onValueChange = { evidenceQuote = it },
            label = { Text("Evidence quote (required)") },
            placeholder = { Text("What the learner actually said/did, in their words") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = evidenceUrl,
            onValueChange = { evidenceUrl = it },
            label = { Text("Evidence link (optional: repo, gist, screenshot URL)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        errorMsg?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Button(
            enabled = selectedTopic != null && level != null && evidenceQuote.isNotBlank() && !saving,
            onClick = {
                val topic = selectedTopic ?: return@Button
                val lvl = level ?: return@Button
                saving = true
                errorMsg = null
                scope.launch {
                    catching {
                        Repository.recordMastery(
                            MasteryRecordInsert(
                                topic_id = topic.id,
                                level = lvl,
                                evidence_quote = evidenceQuote.trim(),
                                hint_used = hintUsed,
                                evidence_url = evidenceUrl.trim().ifBlank { null }
                            )
                        )
                    }.onSuccess {
                        saving = false
                        onSaved()
                    }.onFailure {
                        saving = false
                        errorMsg = it.message ?: "Save failed"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (saving) "Saving..." else "Save")
        }
    }
}

