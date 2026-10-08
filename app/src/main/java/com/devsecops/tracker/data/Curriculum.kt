package com.devsecops.tracker.data

import com.devsecops.tracker.Supa
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.Serializable

// =========================================================================
// Curriculum — the read-only course skeleton the tutor seeded into Supabase
// (Phase A/B, 2026-09-30) plus the tutor-written coverage table.
// The app never writes these tables (RLS: SELECT only).
// =========================================================================

@Serializable
data class ChatPlanRow(
    val chat_no: Int,
    val title: String,
    val module_code: String? = null,
    val phase_id: Int? = null,
    val prerequisite: String? = null,
    val hours: Double? = null,
    val teaching_file_status: String = "planned",
    val teaching_file_ref: String? = null,
    val deliverable: String? = null,
    val start_message: String? = null,
    val lock_reason: String? = null
)

@Serializable
data class TeachingSession(
    val id: Long,
    val module_code: String,
    val session_no: String,
    val title: String,
    val source_path: String? = null,
    val min_minutes: Int? = null,
    val goal: String? = null,
    val done_when: String? = null
)

@Serializable
data class MustCoverItem(
    val id: Long,
    val session_id: Long,
    val item_no: Int,
    val item: String,
    val what_breaks: String? = null,
    val attacker_view: String? = null,
    val defender_view: String? = null,
    val misconception: String? = null,
    val tier: String? = null          // CORE | WORKING | AWARENESS; null until the tutor fills it (treated as CORE)
)

@Serializable
data class CoverageEntry(
    val id: Long,
    val must_cover_id: Long,
    val status: String,              // verified | guided | redo | not_taught
    val learner_quote: String? = null,
    val highest_hint: Int? = null,
    val recorded_at: String? = null
)

@Serializable
data class DocMeta(
    val path: String,
    val title: String,
    val kind: String = "other",
    val status: String = "authoritative",
    val stale_notion_ref: Boolean = false,
    val updated_at: String? = null
)

@Serializable
data class DocBody(val path: String, val title: String = "", val content: String = "")

@Serializable
data class CurriculumBlocker(
    val id: Long,
    val code: String? = null,
    val title: String,
    val detail: String? = null,
    val status: String = "open",
    val found_in: String? = null
)

@Serializable
data class LogHead(
    val study_day: Int,
    val log_date: String,
    val status: String? = null,
    val chapter_topic: String? = null,
    val next_action: String? = null,
    val module_id: Int? = null
)

@Serializable
data class GateRow(
    val id: String,
    val kind: String = "other",
    val name: String,
    val phase_id: Int? = null,
    val module_code: String? = null,
    val description: String? = null
)

@Serializable
data class GateCheckRow(
    val id: Long,
    val gate_id: String,
    val check_no: Int = 0,
    val check_text: String,
    val requires_doing: Boolean = false,
    val min_level: String? = null
)

@Serializable
data class LabRow(
    val id: String,
    val name: String,
    val module_code: String? = null,
    val environment: String? = null,
    val scope_note: String? = null,
    val safety_notes: String? = null
)

// Tutor-written lab progress (table lab_runs, SELECT only for the app).
@Serializable
data class LabRun(
    val id: Long,
    val lab_id: String,
    val status: String = "in_progress",   // in_progress | done | blocked
    val level: String? = null,
    val evidence: String? = null,
    val evidence_url: String? = null,
    val notes: String? = null,
    val run_date: String? = null
)

// Newest run per lab (by run_date, then id).
fun latestLabRuns(runs: List<LabRun>): Map<String, LabRun> =
    runs.groupBy { it.lab_id }.mapValues { (_, v) ->
        v.maxWith(compareBy<LabRun>({ it.run_date ?: "" }, { it.id }))
    }

fun labStatusLabel(run: LabRun?): String = when (run?.status) {
    null -> "Not started"
    "done" -> if (run.level.isNullOrBlank()) "Done" else "Done · ${run.level}"
    "blocked" -> "Blocked"
    else -> "In progress"
}

// Status of a module gate, derived from the tutor-written progress_items.
data class GateStatus(val label: String, val passed: Boolean, val bestLevel: String?)

@Serializable private data class FeedLog(val created_at: String, val study_day: Int, val status: String? = null)
@Serializable private data class FeedProgress(val created_at: String, val item: String, val status: String? = null, val mastery_level: String? = null)
@Serializable private data class FeedWeak(val opened_at: String, val title: String? = null, val reason: String = "", val cleared_at: String? = null)
@Serializable private data class FeedMastery(val recorded_at: String, val level: String, val topic_id: Int)
@Serializable private data class FeedCoverage(val recorded_at: String, val status: String, val must_cover_id: Long)

// One line in the "tutor wrote to Supabase" feed.
data class FeedEvent(val at: String, val kind: String, val text: String)

// A session with its must-cover rows and each row's latest coverage entry.
data class SessionView(
    val session: TeachingSession,
    val items: List<Pair<MustCoverItem, CoverageEntry?>>
) {
    val verified: Int get() = items.count { it.second?.status == "verified" }
    val guided: Int get() = items.count { it.second?.status == "guided" }
    val total: Int get() = items.size

    // Done-definition (05_MASTERY_SYSTEM): every CORE verified, at least 80% of
    // WORKING verified, every AWARENESS recognized (verified). Unknown tier = CORE.
    val isDone: Boolean get() {
        if (total == 0) return false
        fun ver(l: List<Pair<MustCoverItem, CoverageEntry?>>) = l.count { it.second?.status == "verified" }
        val core = items.filter { (it.first.tier ?: "CORE") == "CORE" }
        val working = items.filter { it.first.tier == "WORKING" }
        val aware = items.filter { it.first.tier == "AWARENESS" }
        return ver(core) == core.size && ver(aware) == aware.size &&
            (working.isEmpty() || ver(working) * 5 >= working.size * 4)
    }
}

enum class ChatState { DONE, CURRENT, UPCOMING, LOCKED }

data class PathChat(val chat: ChatPlanRow, val state: ChatState)

@Serializable private data class ModCode(val id: Int, val code: String? = null)

// Extras for the study packet of one chat (all read-only).
data class PacketExtras(
    val notes: List<NoteMeta>,
    val labs: List<LabRow>
)

object Curriculum {

    suspend fun packetExtras(moduleCode: String?): PacketExtras {
        if (moduleCode.isNullOrBlank()) return PacketExtras(emptyList(), emptyList())
        val modId = runCatching {
            Supa.client.from("modules").select(Columns.list("id", "code")) {
                filter { eq("code", moduleCode) }
            }.decodeList<ModCode>().firstOrNull()?.id
        }.getOrNull()
        val notes = if (modId == null) emptyList() else
            runCatching { Notes.list().filter { it.module_id == modId } }.getOrDefault(emptyList())
        val labs = runCatching { labs().filter { com.devsecops.tracker.ui.labMatchesModule(it.module_code, moduleCode) } }
            .getOrDefault(emptyList())
        return PacketExtras(notes, labs)
    }

    suspend fun gates(): List<GateRow> =
        Supa.client.from("gates").select { order("id", Order.ASCENDING) }.decodeList()

    suspend fun gateChecks(): List<GateCheckRow> =
        Supa.client.from("gate_checks").select {
            order("gate_id", Order.ASCENDING)
            order("check_no", Order.ASCENDING)
        }.decodeList()

    suspend fun labs(): List<LabRow> =
        Supa.client.from("labs").select { order("id", Order.ASCENDING) }.decodeList()

    // A missing table (DDL not run yet) or a failed read must not break the Labs list.
    suspend fun labRuns(): List<LabRun> = runCatching {
        Supa.client.from("lab_runs").select {
            order("run_date", Order.DESCENDING)
            order("id", Order.DESCENDING)
        }.decodeList<LabRun>()
    }.getOrDefault(emptyList())

    private fun levelNum(l: String?): Int = l?.trim()?.removePrefix("L")?.toIntOrNull() ?: 0

    // Module gate status from progress_items (same matching as the Path screen).
    fun moduleGateStatus(progress: List<ProgressItem>, code: String?): GateStatus {
        if (code.isNullOrBlank()) return GateStatus("Tutor records this", false, null)
        val rx = Regex("^" + Regex.escape(code) + "(?![0-9A-Za-z.])")
        val rows = progress.filter { !it.superseded && rx.containsMatchIn(it.item) }
        val best = rows.mapNotNull { it.mastery_level }.maxByOrNull { levelNum(it) }
        val passed = isPassed(progress, code)
        val label = when {
            passed -> "Passed"
            rows.isEmpty() -> "Not started"
            rows.any { it.status.equals("Mastered", true) } -> "Mastered, gate pending"
            else -> rows.firstOrNull { !it.status.isNullOrBlank() }?.status ?: "In progress"
        }
        return GateStatus(label, passed, best)
    }

    suspend fun chats(): List<ChatPlanRow> =
        Supa.client.from("chat_plan").select { order("chat_no", Order.ASCENDING) }.decodeList()

    suspend fun blockers(): List<CurriculumBlocker> =
        Supa.client.from("curriculum_blockers").select {
            filter { eq("status", "open") }
            order("id", Order.ASCENDING)
        }.decodeList()

    suspend fun docs(): List<DocMeta> =
        Supa.client.from("project_docs")
            .select(Columns.list("path", "title", "kind", "status", "stale_notion_ref", "updated_at")) {
                order("path", Order.ASCENDING)
            }.decodeList()

    suspend fun doc(path: String): DocBody? =
        Supa.client.from("project_docs")
            .select(Columns.list("path", "title", "content")) {
                filter { eq("path", path) }
                limit(1)
            }.decodeList<DocBody>().firstOrNull()

    suspend fun hoursRows(): List<HoursRow> =
        Supa.client.from("daily_logs")
            .select(Columns.list("log_date", "planned_hours", "actual_hours")) {
                order("log_date", Order.DESCENDING)
                limit(21)
            }.decodeList<HoursRow>()

    suspend fun latestLog(): LogHead? =
        Supa.client.from("daily_logs")
            .select(Columns.list("study_day", "log_date", "status", "chapter_topic", "next_action", "module_id")) {
                order("study_day", Order.DESCENDING)
                limit(1)
            }.decodeList<LogHead>().firstOrNull()

    // True when a non-superseded progress item for this module code
    // ("M0.1 ...", "M0.1: ...") is Mastered with a passing gate result.
    fun isPassed(progress: List<ProgressItem>, code: String?): Boolean {
        if (code.isNullOrBlank()) return false
        val rx = Regex("^" + Regex.escape(code) + "(?![0-9A-Za-z.])")
        return progress.any {
            !it.superseded && rx.containsMatchIn(it.item) &&
                it.status.equals("Mastered", true) &&
                it.gate_result?.trim()?.lowercase()?.startsWith("pass") == true
        }
    }

    // A chat is done when the tutor recorded its module as Mastered with a
    // passed gate (the protocol's NEXT_CHAT rule), or the plan itself says ✅.
    suspend fun path(): List<PathChat> {
        val chats = chats()
        val progress = Fetch.progressItems()   // errors propagate: never show "no progress" on a failed fetch
        fun passed(code: String?): Boolean = isPassed(progress, code)
        val done = chats.map { passed(it.module_code) || it.prerequisite?.trimStart()?.startsWith("✅") == true }
        val current = done.indexOfFirst { !it }
        return chats.mapIndexed { i, c ->
            val s = when {
                done[i] -> ChatState.DONE
                i == current -> ChatState.CURRENT
                c.teaching_file_status == "locked" -> ChatState.LOCKED
                else -> ChatState.UPCOMING
            }
            PathChat(c, s)
        }
    }

    // Sessions for a chat: same module code, or taught from the same file
    // (the M0.2 file also carries M0.3's sessions).
    suspend fun sessionsFor(chat: ChatPlanRow): List<SessionView> {
        val all: List<TeachingSession> = Supa.client.from("teaching_sessions")
            .select { order("id", Order.ASCENDING) }.decodeList()
        val mine = all.filter {
            it.module_code == chat.module_code ||
                (chat.teaching_file_ref != null && it.source_path == chat.teaching_file_ref &&
                    chat.module_code?.startsWith("M") == true)
        }
        if (mine.isEmpty()) return emptyList()
        val items: List<MustCoverItem> = Supa.client.from("must_cover_items").select {
            filter { isIn("session_id", mine.map { it.id }) }
            order("item_no", Order.ASCENDING)
        }.decodeList()
        val coverage: List<CoverageEntry> = if (items.isEmpty()) emptyList() else
            Supa.client.from("session_coverage").select {
                filter { isIn("must_cover_id", items.map { it.id }) }
                order("recorded_at", Order.DESCENDING)
            }.decodeList()
        val latest = coverage.groupBy { it.must_cover_id }.mapValues { it.value.first() }
        return mine
            .sortedWith(compareBy<TeachingSession>({ it.module_code }, { sessionOrder(it.session_no) }))
            .map { s ->
                SessionView(
                    s,
                    items.filter { it.session_id == s.id }.sortedBy { it.item_no }.map { it to latest[it.id] }
                )
            }
    }

    private fun sessionOrder(no: String): Double {
        val digits = no.takeWhile { it.isDigit() }
        val n = digits.toDoubleOrNull() ?: 99.0
        return if (no.length > digits.length) n + 0.5 else n
    }

    // Most recent tutor writes across the progress tables, newest first.
    suspend fun feed(limit: Long = 8): List<FeedEvent> {
        val out = ArrayList<FeedEvent>()
        Fetch.orDefault(emptyList<FeedLog>()) {
            Supa.client.from("daily_logs").select(Columns.list("created_at", "study_day", "status")) {
                order("created_at", Order.DESCENDING); limit(limit)
            }.decodeList()
        }.forEach { out += FeedEvent(it.created_at, "Daily log", "Day ${it.study_day}" + (it.status?.let { s -> " · $s" } ?: "")) }
        Fetch.orDefault(emptyList<FeedProgress>()) {
            Supa.client.from("progress_items").select(Columns.list("created_at", "item", "status", "mastery_level")) {
                order("created_at", Order.DESCENDING); limit(limit)
            }.decodeList()
        }.forEach {
            val tail = listOfNotNull(it.status, it.mastery_level).joinToString(" · ")
            out += FeedEvent(it.created_at, "Progress", if (tail.isBlank()) it.item else "${it.item} — $tail")
        }
        Fetch.orDefault(emptyList<FeedWeak>()) {
            Supa.client.from("weak_spots").select(Columns.list("opened_at", "title", "reason", "cleared_at")) {
                order("opened_at", Order.DESCENDING); limit(limit)
            }.decodeList()
        }.forEach {
            out += FeedEvent(it.opened_at, if (it.cleared_at == null) "Weak spot opened" else "Weak spot (now cleared)", it.title ?: it.reason.take(80))
        }
        Fetch.orDefault(emptyList<FeedMastery>()) {
            Supa.client.from("mastery_records").select(Columns.list("recorded_at", "level", "topic_id")) {
                order("recorded_at", Order.DESCENDING); limit(limit)
            }.decodeList()
        }.forEach { out += FeedEvent(it.recorded_at, "Mastery", "${it.level} on topic #${it.topic_id}") }
        Fetch.orDefault(emptyList<FeedCoverage>()) {
            Supa.client.from("session_coverage").select(Columns.list("recorded_at", "status", "must_cover_id")) {
                order("recorded_at", Order.DESCENDING); limit(limit * 4)
            }.decodeList()
        }.groupBy { it.recorded_at.take(16) }.forEach { (_, rows) ->
            val v = rows.count { it.status == "verified" }
            out += FeedEvent(rows.first().recorded_at, "Coverage", "${rows.size} must-cover items checked · $v ✅")
        }
        return out.sortedByDescending { it.at }.take(limit.toInt())
    }
}
