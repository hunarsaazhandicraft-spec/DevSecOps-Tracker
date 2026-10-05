package com.devsecops.tracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.devsecops.tracker.data.ChatState
import com.devsecops.tracker.data.Curriculum
import com.devsecops.tracker.data.Fetch

// Slim bar under the status bar on the top-level tabs: which day, which module
// and which session is "agla". Read-only; tap opens Path.
@Composable
fun SessionBar(refreshKey: Any?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    var day by remember { mutableStateOf<String?>(null) }
    var code by remember { mutableStateOf<String?>(null) }
    var agla by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(refreshKey) {
        Fetch.safeCall {
            val log = Curriculum.latestLog()
            val cur = Curriculum.path().firstOrNull { it.state == ChatState.CURRENT }?.chat
            val nodes = if (cur == null) emptyList() else sessionNodes(Curriculum.sessionsFor(cur))
            Triple(
                log?.let { "day-${nextDayNumber(it.status, it.study_day)}" },
                cur?.module_code,
                nodes.firstOrNull { it.stat == Stat.RUN }?.label
            )
        }.onSuccess { day = it.first; code = it.second; agla = it.third }
    }
    if (day == null && code == null) return
    Row(
        modifier.fillMaxWidth().background(cs.surfaceContainerLow)
            .clickableScale(haptic = false, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        day?.let { Text(it, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = cs.primary) }
        code?.let { MonoLabel(it) }
        Spacer(Modifier.weight(1f))
        agla?.let {
            StatGlyph(Stat.RUN, 14.dp)
            MonoLabel("agla: $it", color = cs.primary)
        }
    }
}
