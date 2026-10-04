package com.devsecops.tracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.devsecops.tracker.data.FadingItem
import com.devsecops.tracker.data.Memory
import com.devsecops.tracker.data.StrengthSummary
import kotlinx.coroutines.delay

// ---- helpers ------------------------------------------------------------

@Composable
private fun strengthColor(r: Double): Color = when {
    r >= 0.85 -> MaterialTheme.colorScheme.primary
    r >= 0.70 -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.error
}

private fun pct(r: Double): String = "${Math.round(r * 100.0)}%"

private fun normalizeAnswer(s: String): String =
    s.trim().lowercase().replace(Regex("\\s+"), " ").trimEnd('.', '!', '?', ',', ';', ':')

private fun fmtHours(h: Double): String = when {
    h <= 0.5 -> "now"
    h < 24.0 -> "in ${Math.round(h)}h"
    else -> "in ${Math.round(h / 24.0)}d"
}

// ---- Recall warm-up -----------------------------------------------------

private const val WARMUP_SECONDS = 90

@Composable
fun RecallWarmup(
    topicTitle: String,
    checklist: List<String>,
    onDone: (selfScore: Int, total: Int) -> Unit,
    onBack: () -> Unit
) {
    var step by remember { mutableStateOf(0) }        // 0 recall, 1 checklist, 2 result
    var text by remember { mutableStateOf("") }
    var secondsLeft by remember { mutableStateOf(WARMUP_SECONDS) }
    val ticks = remember(checklist) { mutableStateListOf<Boolean>().also { l -> repeat(checklist.size) { l.add(false) } } }

    LaunchedEffect(step) {
        if (step == 0) {
            while (secondsLeft > 0) {
                delay(1000)
                secondsLeft -= 1
            }
        }
    }

    val score = ticks.count { it }
    val total = checklist.size

    Scaffold(
        topBar = {
            AppTitleBar(
                title = "Recall warm-up",
                subtitle = topicTitle.ifBlank { "Untitled topic" },
                onBack = onBack
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (step) {
                0 -> {
                    AppCard {
                        Text("Blank page", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Write everything you remember about this topic. No peeking, no grading. Getting it wrong is the point: it makes the next look stick.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val mm = secondsLeft / 60
                    val ss = secondsLeft % 60
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Pill(
                            if (secondsLeft > 0) "%d:%02d left".format(mm, ss) else "Time's up (soft)",
                            color = if (secondsLeft > 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary
                        )
                        Text("Timer is a guide only", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    ThinBar((secondsLeft.toFloat() / WARMUP_SECONDS).coerceIn(0f, 1f), color = MaterialTheme.colorScheme.secondary)
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp),
                        placeholder = { Text("Start writing from memory...") }
                    )
                    Button(onClick = { step = 1 }, modifier = Modifier.fillMaxWidth()) {
                        Text("Done - show checklist")
                    }
                }

                1 -> {
                    if (checklist.isEmpty()) {
                        EmptyBlock(
                            icon = Icons.Filled.Check,
                            title = "No checklist for this topic",
                            body = "Compare your page with your notes, then finish.",
                            actionLabel = "Finish",
                            onAction = { step = 2 }
                        )
                    } else {
                        GroupHeader("Tick what you recalled", "Be honest: only tick points you wrote down yourself.")
                        checklist.forEachIndexed { i, item ->
                            AppCard(onClick = { ticks[i] = !ticks[i] }, padding = 12.dp) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = ticks[i], onCheckedChange = { ticks[i] = it })
                                    Text(item, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                        if (text.isNotBlank()) {
                            GroupHeader("Your page")
                            AppCard { Text(text, style = MaterialTheme.typography.bodyMedium) }
                        }
                        Button(onClick = { step = 2 }, modifier = Modifier.fillMaxWidth()) {
                            Text("See result ($score of $total)")
                        }
                    }
                }

                else -> {
                    val frac = if (total == 0) 0f else score.toFloat() / total
                    AppCard {
                        Text("Warm-up result", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        if (total == 0) {
                            Text("Recall attempt complete.", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            Text("$score of $total recalled", style = MaterialTheme.typography.headlineSmall)
                            Spacer(Modifier.height(8.dp))
                            ThinBar(frac, color = strengthColor(frac.toDouble()))
                            Spacer(Modifier.height(8.dp))
                            Text(
                                if (frac >= 0.8f) "Strong start. Now the new material will attach to something."
                                else "Gaps found. That is useful: they show what to look for today.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Button(onClick = { onDone(score, total) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Continue")
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ---- Memory strength card (Home / Progress) ------------------------------

@Composable
fun MemoryStrengthCard(onOpenFading: () -> Unit, modifier: Modifier = Modifier) {
    var summary by remember { mutableStateOf<StrengthSummary?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        summary = runCatching { Memory.strengthSummary() }.getOrNull()
        loading = false
    }

    val s = summary
    AppCard(modifier = modifier, onClick = if (s != null && s.reviewedCards > 0) onOpenFading else null) {
        Text("Memory strength", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        when {
            loading -> Text("Calculating...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            s == null || s.reviewedCards == 0 -> Text(
                "Review a few cards and your memory strength will appear here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            else -> {
                val avg = s.averageRetrievability
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(pct(avg), style = MaterialTheme.typography.headlineSmall, color = strengthColor(avg))
                    Text(
                        "average recall across ${s.reviewedCards} card${if (s.reviewedCards == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                ThinBar(avg.toFloat(), color = strengthColor(avg))
                Spacer(Modifier.height(10.dp))
                if (s.fadingSoonCount > 0) {
                    Pill("${s.fadingSoonCount} fading soon", color = MaterialTheme.colorScheme.tertiary)
                } else {
                    Pill("Nothing fading soon", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

// ---- Fading soon list ------------------------------------------------------

@Composable
fun FadingSoonScreen(onBack: () -> Unit, onOpenCard: (FadingItem) -> Unit) {
    var fading by remember { mutableStateOf<List<FadingItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(reload) {
        loading = true
        error = null
        runCatching { Memory.fadingSoon(100) }
            .onSuccess { fading = it }
            .onFailure { error = it.message ?: "Load failed" }
        loading = false
    }

    Scaffold(
        topBar = {
            AppTitleBar(
                title = "Fading soon",
                subtitle = if (loading || error != null) null else "${fading.size} card${if (fading.size == 1) "" else "s"} slipping",
                onBack = onBack
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loading -> SkeletonList(3)
                error != null -> ErrorBlock(error ?: "", { reload += 1 }, Modifier.padding(16.dp))
                fading.isEmpty() -> EmptyBlock(
                    icon = Icons.Filled.Check,
                    title = "Nothing fading",
                    body = "No reviewed card is predicted to drop under 85% in the next 3 days.",
                    actionLabel = "Refresh",
                    onAction = { reload += 1 }
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(fading, key = { it.cardId }) { item ->
                        AppCard(onClick = { onOpenCard(item) }, padding = 14.dp) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Pill(pct(item.retrievabilityNow), color = strengthColor(item.retrievabilityNow))
                                Text(
                                    item.topicName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "under 80% ${fmtHours(item.hoursUntilBelow80)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(item.question, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
                        }
                    }
                }
            }
        }
    }
}

// ---- Card types for Quiz -----------------------------------------------------

private val CLOZE = Regex("\\{\\{c\\d+::(.*?)(?:::(.*?))?\\}\\}")

// Text with {{c1::answer}} (optional {{c1::answer::hint}}) markers. Tap to reveal.
@Composable
fun ClozeCardBody(text: String, modifier: Modifier = Modifier) {
    var revealed by remember(text) { mutableStateOf(false) }
    val hasBlanks = remember(text) { CLOZE.containsMatchIn(text) }
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary

    val annotated = remember(text, revealed, primary, secondary) {
        buildAnnotatedString {
            var last = 0
            for (m in CLOZE.findAll(text)) {
                append(text.substring(last, m.range.first))
                val answer = m.groupValues[1]
                val hint = m.groupValues.getOrNull(2).orEmpty()
                if (revealed) {
                    withStyle(SpanStyle(color = primary, fontWeight = FontWeight.Bold)) { append(answer) }
                } else {
                    withStyle(SpanStyle(color = secondary, fontWeight = FontWeight.Bold)) {
                        append(if (hint.isNotBlank()) "[ $hint ]" else "[ ... ]")
                    }
                }
                last = m.range.last + 1
            }
            if (last < text.length) append(text.substring(last))
        }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (text.isBlank()) {
            Text("This card has no text.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(
                annotated,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth().clickable(enabled = hasBlanks) { revealed = !revealed }
            )
            if (hasBlanks) {
                Text(
                    if (revealed) "Tap to hide" else "Tap the text to reveal",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// Type the answer from memory. Compare ignores case, extra spaces, trailing punctuation.
@Composable
fun TypeAnswerCardBody(
    expected: String,
    onResult: (correct: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var input by remember(expected) { mutableStateOf("") }
    var checked by remember(expected) { mutableStateOf<Boolean?>(null) }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = input,
            onValueChange = { if (checked == null) input = it },
            modifier = Modifier.fillMaxWidth(),
            enabled = checked == null,
            singleLine = true,
            placeholder = { Text("Type your answer") }
        )
        val result = checked
        if (result == null) {
            Button(
                onClick = {
                    val ok = normalizeAnswer(input) == normalizeAnswer(expected)
                    checked = ok
                    onResult(ok)
                },
                enabled = input.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Check") }
        } else {
            val c = if (result) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            AppCard(container = c.copy(alpha = 0.12f), padding = 12.dp) {
                Text(if (result) "Correct" else "Not quite", color = c, style = MaterialTheme.typography.titleSmall)
                if (!result) {
                    Spacer(Modifier.height(4.dp))
                    Text("Expected: $expected", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

// Tap items in the order you think is right. Tap a placed item to take it back.
@Composable
fun OrderingCardBody(
    items: List<String>,
    onResult: (correct: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) {
        Text("This card has no items.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    // Shuffled display order (indices into items); never identical to the answer.
    val shuffled = remember(items) {
        val idx = items.indices.toList().shuffled(java.util.Random(items.hashCode().toLong()))
        if (items.size > 1 && idx.map { items[it] } == items) idx.reversed() else idx
    }
    val chosen = remember(items) { mutableStateListOf<Int>() }
    var result by remember(items) { mutableStateOf<Boolean?>(null) }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Your order", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (chosen.isEmpty()) {
            Text("Tap items below to place them.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        chosen.forEachIndexed { pos, idx ->
            AppCard(
                onClick = if (result == null) ({ chosen.remove(idx) }) else null,
                container = MaterialTheme.colorScheme.primaryContainer,
                padding = 10.dp
            ) {
                Text("${pos + 1}. ${items[idx]}", style = MaterialTheme.typography.bodyMedium)
            }
        }
        val remaining = shuffled.filter { it !in chosen }
        if (remaining.isNotEmpty()) {
            Text("Remaining", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            remaining.forEach { idx ->
                AppCard(
                    onClick = if (result == null) ({ chosen.add(idx) }) else null,
                    padding = 10.dp
                ) { Text(items[idx], style = MaterialTheme.typography.bodyMedium) }
            }
        }
        val r = result
        if (r == null) {
            Button(
                onClick = {
                    val ok = chosen.map { items[it] } == items
                    result = ok
                    onResult(ok)
                },
                enabled = chosen.size == items.size,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Check order") }
        } else {
            val c = if (r) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            AppCard(container = c.copy(alpha = 0.12f), padding = 12.dp) {
                Text(if (r) "Correct order" else "Not quite. Correct order:", color = c, style = MaterialTheme.typography.titleSmall)
                if (!r) {
                    Spacer(Modifier.height(4.dp))
                    items.forEachIndexed { i, s ->
                        Text("${i + 1}. $s", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

// The learner's own mnemonic ("my hook"). Callback only; caller persists.
@Composable
fun MyHookField(initial: String?, onSave: (String) -> Unit, modifier: Modifier = Modifier) {
    var value by remember(initial) { mutableStateOf(initial.orEmpty()) }
    var savedValue by remember(initial) { mutableStateOf(initial.orEmpty()) }
    val dirty = value.trim() != savedValue.trim()

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("My hook") },
            placeholder = { Text("A picture, rhyme or story that makes this stick") }
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = {
                    val v = value.trim()
                    savedValue = v
                    onSave(v)
                },
                enabled = dirty
            ) { Text("Save hook") }
            if (!dirty && savedValue.isNotBlank()) {
                Text("Saved", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
