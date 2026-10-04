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
import com.devsecops.tracker.data.WeakSpotInsert
import kotlinx.coroutines.launch

// A weak spot is a thing you got wrong or needed help on. Writing down what
// specifically broke — not just the topic — is what makes it useful later.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddWeakSpotScreen(onSaved: () -> Unit) {
    val loader = rememberTopicLoader()
    var selectedTopicId by rememberSaveable { mutableStateOf(-1) }
    val selectedTopic = loader.topics.firstOrNull { it.id == selectedTopicId }
    var reason by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "It stays open until you clear it, and shows on your dashboard while it does.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        TopicPicker(loader, selectedTopic) { selectedTopicId = it?.id ?: -1 }

        OutlinedTextField(
            value = reason,
            onValueChange = { reason = it },
            label = { Text("What exactly went wrong?") },
            placeholder = { Text("e.g. confused setgid with the sticky bit on shared dirs") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth()
        )

        errorMsg?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Button(
            enabled = selectedTopic != null && reason.isNotBlank() && !saving,
            onClick = {
                val topic = selectedTopic ?: return@Button
                saving = true
                errorMsg = null
                scope.launch {
                    catching {
                        Repository.addWeakSpot(
                            WeakSpotInsert(topic_id = topic.id, reason = reason.trim())
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
