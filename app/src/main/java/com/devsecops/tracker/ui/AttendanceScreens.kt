package com.devsecops.tracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devsecops.tracker.data.Attendance
import com.devsecops.tracker.data.DayMark
import com.devsecops.tracker.data.DayRow
import com.devsecops.tracker.data.Fetch
import com.devsecops.tracker.data.TodayAttendance
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun fmtDur(min: Long): String = if (min >= 60) "${min / 60}h ${min % 60}m" else "${min}m"

private fun nowMs(): Long = System.currentTimeMillis()

@Composable
private fun ConfidencePicker(value: Int?, onPick: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Sp.s)) {
        (1..5).forEach { n ->
            FilterChip(selected = value == n, onClick = { onPick(n) }, label = { Text("$n") })
        }
    }
}

@Composable
private fun EndDayDialog(onDismiss: () -> Unit, onSave: (Int, String) -> Unit) {
    var conf by remember { mutableStateOf<Int?>(null) }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("End day") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Sp.m)) {
                Text("Aaj ka confidence kitna tha? (1 = bilkul nahi, 5 = pura clear)")
                ConfidencePicker(conf) { conf = it }
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text("Note (optional)") }, modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = { TextButton(enabled = conf != null, onClick = { onSave(conf!!, note) }) { Text("Save & end") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun LeaveDialog(day: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Leave: $day") },
        text = {
            OutlinedTextField(
                value = reason, onValueChange = { reason = it },
                label = { Text("Reason (e.g. travel, sick, exam)") }, modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton(onClick = { onSave(reason) }) { Text("Mark leave") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun AttendanceScreen(onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var today by remember { mutableStateOf<TodayAttendance?>(null) }
    var hist by remember { mutableStateOf<List<DayRow>>(emptyList()) }
    var err by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(nowMs()) }
    var showEnd by remember { mutableStateOf(false) }
    var leaveDay by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        Fetch.safeCall { Attendance.loadToday() to Attendance.history() }
            .onSuccess { today = it.first; hist = it.second; err = null }
            .onFailure { err = it.message ?: "Could not load attendance" }
    }
    fun act(block: suspend () -> Unit) {
        if (busy) return
        scope.launch {
            busy = true
            Fetch.safeCall { block() }.onFailure { msg = it.message ?: "Failed" }
            reload()
            busy = false
        }
    }
    LaunchedEffect(Unit) { reload() }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = nowMs() } }
    OnResume { scope.launch { reload() } }

    if (showEnd) EndDayDialog(onDismiss = { showEnd = false }) { c, n ->
        showEnd = false
        act {
            val r = Attendance.endDay(c, n)
            msg = "Day ended: ${"%.2f".format(r.hours)} h, confidence $c/5" +
                if (r.loggedToTutor) " (also saved to today's daily log)"
                else " - NOT copied to the tutor daily log (no log row for today yet, or the write failed)"
        }
    }
    leaveDay?.let { d ->
        LeaveDialog(d, onDismiss = { leaveDay = null }) { r ->
            leaveDay = null
            act { Attendance.markLeave(d, r); msg = "Leave saved for $d" }
        }
    }

    Column(Modifier.fillMaxSize()) {
        AppTitleBar(title = "Attendance", onBack = onBack, subtitle = "Start / break / end, present or leave")
        val t = today
        when {
            err != null && t == null -> ErrorBlock(err!!, onRetry = { scope.launch { reload() } })
            t == null -> SkeletonList()
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = Sp.l, vertical = Sp.s),
                verticalArrangement = Arrangement.spacedBy(Sp.m)
            ) {
                item {
                    AppCard {
                        Text("Today", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(Sp.s))
                        val worked = Attendance.workedMinutes(t.row, t.breaks, now)
                        val brk = Attendance.breakMinutes(t.breaks, now)
                        when {
                            t.onLeave -> {
                                Pill("On leave", cs.tertiary)
                                t.row?.leave_reason?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                                Spacer(Modifier.height(Sp.s))
                                OutlinedButton(onClick = { act { Attendance.clearLeave(t.row!!.day) } }, enabled = !busy) { Text("Cancel leave") }
                                Spacer(Modifier.height(Sp.xs))
                                Button(onClick = { act { Attendance.startDay() } }, enabled = !busy) { Text("I'm studying today - Start day") }
                            }
                            !t.started -> {
                                Text("Day shuru nahi hua.", color = cs.onSurfaceVariant)
                                Spacer(Modifier.height(Sp.s))
                                Button(onClick = { act { Attendance.startDay() } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Start day") }
                                Spacer(Modifier.height(Sp.xs))
                                OutlinedButton(
                                    onClick = { leaveDay = com.devsecops.tracker.data.localTodayDate().toString() },
                                    enabled = !busy, modifier = Modifier.fillMaxWidth()
                                ) { Text("Mark today as leave") }
                            }
                            t.ended -> {
                                Pill("Day ended", cs.primary)
                                Text(fmtDur(worked) + " worked - confidence ${t.row?.confidence ?: "-"}/5",
                                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                                if (brk > 0) Text("Breaks: ${fmtDur(brk)}", color = cs.onSurfaceVariant)
                                t.row?.note?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant) }
                            }
                            else -> {
                                Pill(if (t.openBreak != null) "On break" else "Studying", if (t.openBreak != null) cs.tertiary else cs.primary)
                                Text(fmtDur(worked), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
                                Text("worked so far" + if (brk > 0) " - breaks ${fmtDur(brk)}" else "", color = cs.onSurfaceVariant)
                                Spacer(Modifier.height(Sp.m))
                                Row(horizontalArrangement = Arrangement.spacedBy(Sp.s)) {
                                    if (t.openBreak == null)
                                        OutlinedButton(onClick = { act { Attendance.startBreak() } }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Take break") }
                                    else
                                        Button(onClick = { act { Attendance.endBreak() } }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Resume") }
                                    Button(onClick = { showEnd = true }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("End day") }
                                }
                            }
                        }
                    }
                }
                msg?.let { item { Text(it, style = MaterialTheme.typography.bodyMedium, color = cs.primary) } }
                item {
                    val tracked = hist.filter { it.mark != DayMark.UNTRACKED }
                    GroupHeader(
                        "Last 35 days",
                        "${tracked.count { it.mark == DayMark.PRESENT }} present - " +
                            "${tracked.count { it.mark == DayMark.LEAVE }} leave - " +
                            "${tracked.count { it.mark == DayMark.ABSENT }} absent"
                    )
                }
                items(hist.filter { it.mark != DayMark.UNTRACKED }) { r ->
                    val (label, color) = when (r.mark) {
                        DayMark.PRESENT -> "Present" to cs.primary
                        DayMark.LEAVE -> "Leave" to cs.tertiary
                        DayMark.ABSENT -> "Absent" to cs.error
                        else -> "Today" to cs.secondary
                    }
                    AppCard(
                        onClick = if (r.mark == DayMark.ABSENT) ({ leaveDay = r.date }) else null,
                        padding = Sp.m
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(r.date, style = MaterialTheme.typography.titleSmall)
                                val extra = listOfNotNull(
                                    r.hours?.let { "%.1f h".format(it) },
                                    r.confidence?.let { "conf $it/5" },
                                    r.reason
                                ).joinToString(" - ")
                                if (extra.isNotBlank()) Text(extra, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                                if (r.mark == DayMark.ABSENT) Text("Tap to mark as leave", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                            }
                            Pill(label, color)
                        }
                    }
                }
                item { Spacer(Modifier.height(Sp.xxl)) }
            }
        }
    }
}

// Compact card for Today: status + one primary action, full screen on tap.
@Composable
fun AttendanceCard(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var t by remember { mutableStateOf<TodayAttendance?>(null) }
    var now by remember { mutableLongStateOf(nowMs()) }
    var busy by remember { mutableStateOf(false) }

    suspend fun reload() { Fetch.safeCall { Attendance.loadToday() }.onSuccess { t = it } }
    LaunchedEffect(Unit) { reload() }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = nowMs() } }
    OnResume { scope.launch { reload() } }

    val a = t ?: return
    AppCard(modifier, onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Attendance", style = MaterialTheme.typography.titleMedium)
                val w = Attendance.workedMinutes(a.row, a.breaks, now)
                Text(
                    when {
                        a.onLeave -> "On leave today"
                        !a.started -> "Day not started"
                        a.ended -> "Done - ${fmtDur(w)}, confidence ${a.row?.confidence ?: "-"}/5"
                        a.openBreak != null -> "On break - ${fmtDur(w)} worked"
                        else -> "Studying - ${fmtDur(w)} worked"
                    },
                    style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant
                )
            }
            if (!a.started && !a.onLeave) {
                Button(enabled = !busy, onClick = {
                    scope.launch { busy = true; Fetch.safeCall { Attendance.startDay() }; reload(); busy = false }
                }) { Text("Start") }
            }
        }
    }
}
