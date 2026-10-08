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
            v.isDone -> Stat.PASS
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

// Evidence level "L1".."L5" -> number (0 if unknown).
fun levelNumber(level: String?): Int = level?.drop(1)?.toIntOrNull() ?: 0

// Topic status in the module hub: L4+ = independent = PASS; L1-L3 = evidence
// exists but not yet independent = PENDING; studied without a record = RUN
// (assess it next); nothing yet = QUEUED.
fun topicStat(level: String?, studied: Boolean): Stat = when {
    levelNumber(level) >= 4 -> Stat.PASS
    levelNumber(level) in 1..3 -> Stat.PENDING
    studied -> Stat.RUN
    else -> Stat.QUEUED
}

// ---- Note reader: outline + find ------------------------------------------

data class NoteSection(val heading: String, val body: String)

// Splits note Markdown at "## " headings (ignoring "## " lines inside ``` fences).
// Text before the first heading becomes a section with an empty heading.
fun splitSections(md: String): List<NoteSection> {
    val out = mutableListOf<NoteSection>()
    var head = ""
    val buf = StringBuilder()
    var inFence = false
    fun flush() {
        val text = buf.toString().trimEnd('\n')
        if (text.isNotBlank()) out.add(NoteSection(head, text))
        buf.clear()
    }
    for (line in md.lines()) {
        if (line.trimStart().startsWith("```")) inFence = !inFence
        if (!inFence && line.startsWith("## ")) {
            flush()
            head = line.removePrefix("## ").trim()
        }
        buf.append(line).append('\n')
    }
    flush()
    return out
}

// Section body without its own "## heading" line (the card header shows the heading instead).
fun sectionBody(s: NoteSection): String =
    if (s.heading.isBlank()) s.body else s.body.lines().drop(1).joinToString("\n").trim('\n')

// 1-based number for each headed section, 0 for the unheaded preamble.
fun sectionNumbers(sections: List<NoteSection>): List<Int> {
    var c = 0
    return sections.map { if (it.heading.isBlank()) 0 else ++c }
}

// Short Hinglish-neutral label shown on top of each diagram.
fun diagramKindLabel(kind: String): String = when (kind) {
    "stack" -> "layers"
    "nest" -> "containment"
    "steps" -> "steps"
    "seq" -> "sequence"
    "compare" -> "compare"
    "bits" -> "bit layout"
    "annot" -> "real output"
    "path" -> "journey"
    "flow" -> "decision"
    else -> kind
}

// Indexes of sections whose heading or body contains the query (case-insensitive).
// Fewer than 2 characters matches nothing.
fun sectionMatches(sections: List<NoteSection>, query: String): List<Int> {
    val q = query.trim()
    if (q.length < 2) return emptyList()
    return sections.mapIndexedNotNull { i, s ->
        if (s.heading.contains(q, true) || s.body.contains(q, true)) i else null
    }
}


// ---- Learn Engine (drills + study packet) ---------------------------------

// The session to study now: the first one with must-cover items not all
// verified; if every session is done, the last one; -1 when there are none.
fun currentSessionIndex(sessions: List<SessionView>): Int {
    if (sessions.isEmpty()) return -1
    val i = sessions.indexOfFirst { it.total > 0 && !it.isDone }
    return if (i >= 0) i else sessions.lastIndex
}

private val MODULE_RX = Regex("M(\\d+)\\.(\\d+)")

// Lab codes look like "M0.2", "M0.2\u2013M0.4" (range) or are empty.
fun labMatchesModule(labCode: String?, moduleCode: String?): Boolean {
    if (labCode.isNullOrBlank() || moduleCode.isNullOrBlank()) return false
    val mod = MODULE_RX.find(moduleCode) ?: return false
    val major = mod.groupValues[1].toInt()
    val minor = mod.groupValues[2].toInt()
    val found = MODULE_RX.findAll(labCode).map { it.groupValues[1].toInt() to it.groupValues[2].toInt() }.toList()
    return when (found.size) {
        0 -> false
        1 -> found[0] == major to minor
        else -> {
            val (aM, aN) = found.first()
            val (bM, bN) = found.last()
            major in aM..bM && (major != aM || minor >= aN) && (major != bM || minor <= bN)
        }
    }
}

// Text the learner can paste into the tutor chat after a drill. known[i] is
// true when the item was recalled without looking.
fun drillSummary(title: String, items: List<String>, known: List<Boolean>): String {
    val k = known.count { it }
    val lines = items.mapIndexed { i, t -> (if (known.getOrNull(i) == true) "OK  " else "REDO") + " " + (i + 1) + ". " + t }
    return "Drill: $title - $k/${items.size} recalled\n" + lines.joinToString("\n")
}
