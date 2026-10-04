package com.devsecops.tracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devsecops.tracker.data.Repository
import com.devsecops.tracker.data.StudySessionInsert
import kotlinx.coroutines.launch

private val ACTIVITY_TYPES = listOf("learning", "practice", "lab", "assessment")

// + Log Study -> topic -> duration -> activity type -> save.
// Fast on purpose: this is the form you'll use most, every day.
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StudyLogScreen(onSaved: () -> Unit) {
    val loader = rememberTopicLoader()
    var selectedTopicId by rememberSaveable { mutableStateOf(-1) }
    val selectedTopic = loader.topics.firstOrNull { it.id == selectedTopicId }
    var duration by rememberSaveable { mutableStateOf("") }
    var activityType by rememberSaveable { mutableStateOf(ACTIVITY_TYPES[0]) }
    var notes by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        TopicPicker(loader, selectedTopic) { selectedTopicId = it?.id ?: -1 }

        OutlinedTextField(
            value = duration,
            onValueChange = { duration = it.filter(Char::isDigit) },
            label = { Text("Duration (minutes)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth()
        )

        Text("Activity type", style = MaterialTheme.typography.labelLarge)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ACTIVITY_TYPES.forEach { type ->
                FilterChip(
                    selected = activityType == type,
                    onClick = { activityType = type },
                    label = { Text(type.replaceFirstChar { it.uppercase() }) }
                )
            }
        }

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notes (optional)") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth()
        )

        errorMsg?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Button(
            enabled = selectedTopic != null && duration.isNotBlank() && !saving,
            onClick = {
                val topic = selectedTopic ?: return@Button
                val mins = duration.toIntOrNull() ?: return@Button
                saving = true
                errorMsg = null
                scope.launch {
                    catching {
                        Repository.logStudySession(
                            StudySessionInsert(
                                topic_id = topic.id,
                                duration_min = mins,
                                activity_type = activityType,
                                notes = notes.ifBlank { null }
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

