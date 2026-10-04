package com.devsecops.tracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devsecops.tracker.data.Repository
import com.devsecops.tracker.data.RevisionCardInsert
import kotlinx.coroutines.launch

// Your own cards, alongside the seeded question bank. Same rotation, same
// scheduling — it becomes due once you've studied its topic.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCardScreen(onSaved: () -> Unit) {
    val loader = rememberTopicLoader()
    var selectedTopicId by rememberSaveable { mutableStateOf(-1) }
    val selectedTopic = loader.topics.firstOrNull { it.id == selectedTopicId }
    var question by rememberSaveable { mutableStateOf("") }
    var answer by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Write the question so it forces recall, not recognition — \"explain X\" beats \"what is X?\".",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        TopicPicker(loader, selectedTopic) { selectedTopicId = it?.id ?: -1 }

        OutlinedTextField(
            value = question,
            onValueChange = { question = it },
            label = { Text("Question") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = answer,
            onValueChange = { answer = it },
            label = { Text("Answer") },
            minLines = 4,
            modifier = Modifier.fillMaxWidth()
        )

        errorMsg?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Button(
            enabled = selectedTopic != null && question.isNotBlank() && answer.isNotBlank() && !saving,
            onClick = {
                val topic = selectedTopic ?: return@Button
                saving = true
                errorMsg = null
                scope.launch {
                    catching {
                        Repository.addRevisionCard(
                            RevisionCardInsert(
                                topic_id = topic.id,
                                question = question.trim(),
                                answer = answer.trim()
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
