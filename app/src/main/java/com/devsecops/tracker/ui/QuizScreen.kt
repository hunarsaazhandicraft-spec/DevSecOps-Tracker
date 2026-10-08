package com.devsecops.tracker.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devsecops.tracker.data.DueCard
import com.devsecops.tracker.data.Repository
import kotlinx.coroutines.launch

// Daily revision. Question first, answer hidden — the whole point is to try
// to recall before seeing it. Your own honesty about how it went is what
// drives the schedule, so the four buttons matter more than they look.
private val RESULTS = listOf(
    "again" to "Again",
    "hard" to "Hard",
    "good" to "Good",
    "easy" to "Easy"
)

private val CONFIDENCE = listOf("guess" to "Guess", "sure" to "Sure", "certain" to "Certain")
private val ERROR_TAGS = listOf(
    "concept" to "Concept",
    "recall" to "Didn't recall",
    "careless" to "Careless",
    "vocab" to "Vocabulary"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(onBack: () -> Unit, onAddCard: () -> Unit, showBackButton: Boolean = true) {
    var queue by remember { mutableStateOf<List<DueCard>>(emptyList()) }
    var overdue by remember { mutableStateOf(0) }
    var index by remember { mutableStateOf(0) }
    var revealed by rememberSaveable { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var submitError by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<Triple<String, String?, String?>?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun load(keepReveal: Boolean = false) {
        loading = true
        error = null
        submitError = null
        index = 0
        if (!keepReveal) revealed = false
        catching { Repository.loadRevise() }
            .onSuccess { queue = it.queue; overdue = it.overdue }
            .onFailure { error = it.message ?: "Load failed" }
        loading = false
    }

    // A failed rating keeps the queue and the current card; only this banner appears.
    fun submit(current: DueCard, result: String, confidence: String?, tag: String?) {
        saving = true
        submitError = null
        pending = Triple(result, confidence, tag)
        scope.launch {
            catching { Repository.submitReview(current, result, confidence, tag) }
                .onSuccess {
                    saving = false
                    revealed = false
                    pending = null
                    index += 1
                }
                .onFailure {
                    saving = false
                    submitError = "Couldn't save that review: " + (it.message ?: "unknown error")
                }
        }
    }

    LaunchedEffect(Unit) { load(keepReveal = true) }

    Scaffold(
        topBar = {
            AppTitleBar(
                title = "Revise",
                subtitle = when {
                    loading || error != null -> null
                    queue.isEmpty() -> "Nothing due"
                    index >= queue.size -> "All ${queue.size} done"
                    else -> "Card ${index + 1} of ${queue.size} \u00B7 ${queue.size - index} left"
                },
                onBack = if (showBackButton) onBack else null,
                actions = {
                    FilledTonalIconButton(onClick = onAddCard) {
                        Icon(Icons.Filled.Add, contentDescription = "Add card")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!loading && error == null && overdue > 0) {
                val debt = com.devsecops.tracker.data.DueRules.isDebt(overdue)
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (debt) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        if (debt) "Review debt: $overdue cards overdue" else "$overdue cards overdue",
                        style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold
                    )
                    Text(
                        (if (debt) "${com.devsecops.tracker.data.DueRules.DEBT_THRESHOLD}+ overdue = naya CORE content tab tak block: pehle cards saaf karo. " else "") +
                            "Aaj ka cap ${com.devsecops.tracker.data.DueRules.MAX_REVIEWS_PER_DAY} cards, sabse purane pehle.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            if (!loading && error == null && queue.isNotEmpty()) {
                ThinBar(
                    (index.coerceAtMost(queue.size)).toFloat() / queue.size,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }
            when {
                loading -> SkeletonList(2)

                error != null -> ErrorBlock(error ?: "", { scope.launch { load() } }, Modifier.padding(16.dp))

                queue.isEmpty() -> EmptyBlock(
                    icon = Icons.Filled.Check,
                    title = "Nothing due today",
                    body = "Cards enter the rotation once you've logged study on their topic. Study something and they'll show up here.",
                    actionLabel = "Check again",
                    onAction = { scope.launch { load() } }
                )

                index >= queue.size -> EmptyBlock(
                    icon = Icons.Filled.Check,
                    title = "Done \u2014 ${queue.size} card${if (queue.size == 1) "" else "s"} reviewed",
                    body = "Each one is scheduled to come back based on how you rated it.",
                    actionLabel = "Check again",
                    onAction = { scope.launch { load() } }
                )

                else -> AnimatedContent(
                    targetState = queue[index],
                    modifier = Modifier.fillMaxSize(),
                    contentKey = { it.card.id },
                    transitionSpec = {
                        (slideInHorizontally(tween(250)) { it / 6 } + fadeIn(tween(250))) togetherWith
                            (slideOutHorizontally(tween(250)) { -it / 6 } + fadeOut(tween(150)))
                    },
                    label = "cardSwap"
                ) { due ->
                    CardView(
                        due = due,
                        revealed = revealed,
                        saving = saving,
                        onReveal = { revealed = true },
                        submitError = submitError,
                        onRetry = {
                            val pr = pending
                            if (pr != null && index < queue.size) submit(queue[index], pr.first, pr.second, pr.third)
                        },
                        onRate = { result, confidence, tag ->
                            if (index < queue.size) submit(queue[index], result, confidence, tag)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CardView(
    due: DueCard,
    revealed: Boolean,
    saving: Boolean,
    submitError: String?,
    onRetry: () -> Unit,
    onReveal: () -> Unit,
    onRate: (String, String?, String?) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var confidence by rememberSaveable(due.card.id) { mutableStateOf<String?>(null) }
    var errorTag by rememberSaveable(due.card.id) { mutableStateOf<String?>(null) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (submitError != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(cs.errorContainer)
                    .padding(start = 14.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    submitError,
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onErrorContainer,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onRetry, enabled = !saving) { Text("Retry") }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(due.topicName, cs.primary, Modifier.weight(1f, fill = false))
            if (due.lapses >= 3) Pill("Leech \u00B7 Again \u00D7${due.lapses}", cs.error)
        }

        GlowCard(Modifier, glow = !revealed, padding = 22.dp) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp)
                    .animateContentSize(tween(240))
            ) {
                SectionLabel("Question")
                Spacer(Modifier.height(8.dp))
                Text(due.card.question, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                AnimatedVisibility(
                    visible = revealed,
                    enter = fadeIn(tween(240)) + expandVertically(tween(240))
                ) {
                    Column {
                        Spacer(Modifier.height(18.dp))
                        HorizontalDivider(color = cs.outlineVariant)
                        Spacer(Modifier.height(14.dp))
                        SectionLabel("Answer")
                        Spacer(Modifier.height(6.dp))
                        Text(due.card.answer, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        if (!revealed) {
            Text("How sure are you? (optional)", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CONFIDENCE.forEach { (value, label) ->
                    FilterChip(
                        selected = confidence == value,
                        onClick = { confidence = if (confidence == value) null else value },
                        label = { Text(label) }
                    )
                }
            }
            Button(onClick = onReveal, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(28.dp)) {
                Text("Show answer", style = MaterialTheme.typography.labelLarge)
            }
            Text(
                "Say the answer out loud first \u2014 recalling it is what makes it stick.",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant
            )
        } else {
            Text("How did that go?", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RESULTS.forEach { (value, label) ->
                    val tone = when (value) {
                        "again" -> cs.error
                        "hard" -> cs.tertiary
                        "good" -> cs.primary
                        else -> cs.secondary
                    }
                    Column(
                        Modifier
                            .weight(1f)
                            .clickableScale(enabled = !saving) { onRate(value, confidence, errorTag) }
                            .clip(RoundedCornerShape(20.dp))
                            .background(tone.copy(alpha = if (saving) 0.08f else 0.18f))
                            .padding(vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(label, style = MaterialTheme.typography.labelLarge, color = tone)
                        Text(
                            "${Repository.previewDays(value, due.lastReview)}d",
                            style = MaterialTheme.typography.labelMedium,
                            color = cs.onSurfaceVariant
                        )
                    }
                }
            }
            Text("If it went badly, why? (optional)", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ERROR_TAGS.forEach { (value, label) ->
                    FilterChip(
                        selected = errorTag == value,
                        onClick = { errorTag = if (errorTag == value) null else value },
                        label = { Text(label) }
                    )
                }
            }
        }
    }
}
