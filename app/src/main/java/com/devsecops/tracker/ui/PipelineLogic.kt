package com.devsecops.tracker.ui

import com.devsecops.tracker.data.ChatState
import com.devsecops.tracker.data.SessionView

// Pure logic behind the Today "pipeline" and the session strip. No Compose
// here, so it is unit-tested.

enum class Stat(val label: String) {
    PASS("passed"), RUN("running"), QUEUED("queued"), PENDING("pending"), FAIL("failing"), LOCKED("locked")
}

// Done stages are PASS. The first stage that is not done is RUN (the one to do
// now); any later undone stage is QUEUED.
fun pipelineStats(done: List<Boolean>): List<Stat> {
    val firstOpen = done.indexOfFirst { !it }
    return done.mapIndexed { i, d ->
        when {
            d -> Stat.PASS
            i == firstOpen -> Stat.RUN
            else -> Stat.QUEUED
        }
    }
}

data class SessionNode(val label: String, val stat: Stat)

private fun orderKey(no: String): Double {
    val digits = no.takeWhile { it.isDigit() }
    val n = digits.toDoubleOrNull() ?: 99.0
    return if (no.length > digits.length) n + 0.5 else n
}

// Sessions in teaching order (a chat file can carry two modules' sessions, so
// we sort by session number, not by module). PASS = every must-cover item
// verified by the tutor. FAIL = a redo is open. PENDING = started but not
// finished, or skipped (untouched but a later session already has coverage).
// RUN = the first untouched session after the last touched one ("agla");
// untouched ones after it are QUEUED.
fun sessionNodes(views: List<SessionView>): List<SessionNode> {
    val sorted = views.sortedBy { orderKey(it.session.session_no) }
    val touched = sorted.map { v -> v.items.any { it.second != null } }
    val lastTouched = touched.indexOfLast { it }
    var runUsed = false
    return sorted.mapIndexed { i, v ->
        val redo = v.items.any { it.second?.status == "redo" }
        val stat = when {
            v.total > 0 && v.verified == v.total -> Stat.PASS
            redo -> Stat.FAIL
            touched[i] -> Stat.PENDING
            i < lastTouched -> Stat.PENDING
            !runUsed -> { runUsed = true; Stat.RUN }
            else -> Stat.QUEUED
        }
        SessionNode(v.session.session_no, stat)
    }
}

// The tutor chat command for the next study day.
fun nextDayNumber(status: String?, studyDay: Int): Int =
    if (status.equals("Done", ignoreCase = true)) studyDay + 1 else studyDay

// Path trail uses the same status vocabulary as Today.
fun chatStat(state: ChatState): Stat = when (state) {
    ChatState.DONE -> Stat.PASS
    ChatState.CURRENT -> Stat.RUN
    ChatState.UPCOMING -> Stat.QUEUED
    ChatState.LOCKED -> Stat.LOCKED
}
