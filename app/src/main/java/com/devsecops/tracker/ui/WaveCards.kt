package com.devsecops.tracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devsecops.tracker.data.Curriculum
import com.devsecops.tracker.data.Fetch
import com.devsecops.tracker.data.HoursRow
import com.devsecops.tracker.data.REPLAN_PCT
import com.devsecops.tracker.data.needsReplan
import com.devsecops.tracker.data.paceSummary
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

// Weekly pace: logged hours vs plan (read-only; hours come from daily_logs).
@Composable
fun PaceCard(modifier: Modifier = Modifier, refreshKey: Int = 0) {
    val cs = MaterialTheme.colorScheme
    var rows by remember { mutableStateOf<List<HoursRow>?>(null) }
    LaunchedEffect(refreshKey) {
        Fetch.safeCall { Curriculum.hoursRows() }.onSuccess { rows = it }
    }
    val r = rows ?: return
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    val week = paceSummary(r, today, 7)
    if (week.days == 0) return
    val replan = needsReplan(r, today)
    AppCard(modifier, padding = Sp.m) {
        MonoLabel("// pace · last 7 days")
        Text(
            "%.1f h of %.1f h plan".format(week.actual, week.planned) + (week.pct?.let { " · $it%" } ?: ""),
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold
        )
        Text(
            "${week.days} din ka hours log hua. Plan = daily_logs.planned_hours (khaali ho to 6.5 h).",
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
        )
        if (replan) {
            Spacer(Modifier.height(Sp.xs))
            Text(
                "2 hafte se plan ka $REPLAN_PCT% se kam: tutor se replan karwao.",
                style = MaterialTheme.typography.labelLarge, color = cs.error
            )
        }
    }
}

private val PREFLIGHT = listOf(
    "WSL2 Ubuntu me ho; kaam sirf ~/ ke andar (Windows folder /mnt/c nahi)",
    "Server sirf 127.0.0.1 par bind ho (0.0.0.0 nahi)",
    "Khaali, naye folder me chalao (apni files expose na ho)",
    "Chalane ke baad `ss -tlnp` se bind verify karo"
)

// Phase 0 lab pre-flight (static checklist; ticks are not saved anywhere).
@Composable
fun LabPreflightCard(modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    var open by rememberSaveable { mutableStateOf(false) }
    val ticks = remember { mutableStateListOf(false, false, false, false) }
    AppCard(modifier, onClick = { open = !open }, padding = Sp.m) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Lab pre-flight (WSL2)", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            MonoLabel("${ticks.count { it }}/${PREFLIGHT.size}", color = if (ticks.all { it }) cs.primary else cs.onSurfaceVariant)
        }
        if (open) {
            PREFLIGHT.forEachIndexed { i, t ->
                Row(
                    Modifier.fillMaxWidth().clickable { ticks[i] = !ticks[i] }.padding(top = Sp.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(ticks[i], { ticks[i] = it })
                    Text(t, style = MaterialTheme.typography.bodySmall)
                }
            }
            Text("Sirf yaad dilane ke liye; kuch save nahi hota.", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
        } else {
            Text("Server chalane se pehle 4 checks. Tap karke kholo.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
    }
}

const val DRILL_URL = "https://claude.ai/artifact/JBDqbM5BGntXWRYSkfGyy4"

// Network Drills live in their own artifact database; the app only links to it.
@Composable
fun DrillLinkCard(modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val uri = LocalUriHandler.current
    AppCard(modifier, onClick = { runCatching { uri.openUri(DRILL_URL) } }, padding = Sp.m) {
        Text("Network Drills", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "Practice artifact (browser me khulta hai). Iska data alag hai; app isse nahi padhti.",
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
        )
    }
}

// Shown on the old 52-chat path so it is not mistaken for the current plan.
@Composable
fun StalePlanNote(modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(cs.tertiaryContainer.copy(alpha = 0.7f))
            .padding(horizontal = Sp.m, vertical = Sp.s)
    ) {
        MonoLabel("purana plan", color = cs.tertiary)
        Text(
            "Ye 52-chat plan purana hai (curriculum v2.3 me ~1,825-2,005 h). M0.2/M0.3 ke liye pilot teacher-chats chal rahe hain: ek chat = ek session. Resync ke baad ye list badlegi.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
