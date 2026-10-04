package com.devsecops.tracker.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devsecops.tracker.data.Repository
import com.devsecops.tracker.data.StudySessionInsert
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Countdown based on the wall clock, so it stays right if the app is
// backgrounded. When it ends (or you stop early) the minutes really spent
// can be saved as a study session on a topic.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("focus_prefs", Context.MODE_PRIVATE) }
    var minutes by rememberSaveable { mutableStateOf(prefs.getInt("minutes", 25)) }
    var endAt by remember {
        mutableStateOf<Long?>(
            if (prefs.getBoolean("running", false)) prefs.getLong("end_at", 0L).takeIf { it > 0L } else null
        )
    }
    var remainingMs by remember {
        mutableStateOf(maxOf(0L, (endAt ?: 0L) - System.currentTimeMillis()))
    }
    var spentMin by rememberSaveable { mutableStateOf<Int?>(null) }
    val loader = rememberTopicLoader()
    var topicId by rememberSaveable { mutableStateOf(-1) }
    val topic = loader.topics.firstOrNull { it.id == topicId }
    var msg by remember { mutableStateOf<String?>(null) }
    var msgIsError by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    fun clearRun() {
        prefs.edit().putBoolean("running", false).remove("end_at").apply()
    }

    DisposableEffect(endAt != null) {
        view.keepScreenOn = endAt != null
        onDispose { view.keepScreenOn = false }
    }

    LaunchedEffect(endAt) {
        val e = endAt ?: return@LaunchedEffect
        while (true) {
            val r = e - System.currentTimeMillis()
            if (r <= 0L) {
                remainingMs = 0L
                spentMin = minutes
                endAt = null
                clearRun()
                break
            }
            remainingMs = r
            delay(500)
        }
    }

    ToolScaffold(title = "Focus timer", onBack = onBack) {
        val running = endAt != null
        val secs = (remainingMs / 1000).toInt()
        Text(
            if (running) "%02d:%02d".format(secs / 60, secs % 60) else "%02d:00".format(minutes),
            fontSize = 64.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        if (!running && spentMin == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.align(Alignment.CenterHorizontally)) {
                listOf(15, 25, 50).forEach { m ->
                    FilterChip(
                        selected = minutes == m,
                        onClick = {
                            minutes = m
                            prefs.edit().putInt("minutes", m).apply()
                        },
                        label = { Text("$m min") }
                    )
                }
            }
            Button(
                onClick = {
                    msg = null
                    val end = System.currentTimeMillis() + minutes * 60_000L
                    prefs.edit().putBoolean("running", true).putLong("end_at", end).putInt("minutes", minutes).apply()
                    endAt = end
                    remainingMs = minutes * 60_000L
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Start") }
        }
        if (running) {
            OutlinedButton(
                onClick = {
                    val spent = ((minutes * 60_000L - remainingMs) / 60_000L).toInt()
                    endAt = null
                    clearRun()
                    spentMin = spent
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Stop and keep the time so far") }
        }
        spentMin?.let { spent ->
            Text(
                if (spent >= 1) "You focused for $spent min. Save it as a study session?"
                else "Less than a minute - nothing to save.",
                style = MaterialTheme.typography.bodyMedium
            )
            if (spent >= 1) {
                TopicPicker(loader, topic) { topicId = it?.id ?: -1 }
                Button(
                    enabled = topic != null && !saving,
                    onClick = {
                        val t = topic ?: return@Button
                        saving = true
                        scope.launch {
                            catching {
                                Repository.logStudySession(StudySessionInsert(t.id, spent, "learning", "Focus timer"))
                            }.onSuccess { msg = "Saved."; msgIsError = false; spentMin = null; topicId = -1 }
                                .onFailure { msg = it.message ?: "Save failed"; msgIsError = true }
                            saving = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (saving) "Saving..." else "Save session") }
            }
            TextButton(onClick = { spentMin = null }) { Text("Discard") }
        }
        msg?.let { Text(it, color = if (msgIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) }
    }
}
