package com.devsecops.tracker.ui

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

// PASS = every must-cover item verified by the tutor. FAIL = a redo is open.
// PENDING = started but not finished. The first untouched session is RUN
// ("agla"); untouched ones after it are QUEUED.
fun sessionNodes(views: List<SessionView>): List<SessionNode> {
    var runUsed = false
    return views.map { v ->
        val redo = v.items.any { it.second?.status == "redo" }
        val touched = v.items.any { it.second != null }
        val stat = when {
            v.total > 0 && v.verified == v.total -> Stat.PASS
            redo -> Stat.FAIL
            touched -> Stat.PENDING
            !runUsed -> { runUsed = true; Stat.RUN }
            else -> Stat.QUEUED
        }
        SessionNode(v.session.session_no, stat)
    }
}

// The tutor chat command for the next study day.
fun nextDayNumber(status: String?, studyDay: Int): Int =
    if (status.equals("Done", ignoreCase = true)) studyDay + 1 else studyDay
