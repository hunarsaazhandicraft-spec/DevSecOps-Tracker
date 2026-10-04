package com.devsecops.tracker.data

import com.devsecops.tracker.Supa
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable

// =========================================================================
// Attendance (Phase E): Start/End day from the app, confidence, breaks,
// Present / Leave. "Absent" is never stored: a past day inside the tracked
// range with neither a present nor a leave record is shown as absent.
// Present also upserts streak_days (studied) and Leave upserts it as rest,
// so the existing streak logic keeps working unchanged.
// =========================================================================

@Serializable
data class AttendanceRow(
    val day: String,
    val status: String = "present",          // present | leave
    val started_at: String? = null,
    val ended_at: String? = null,
    val confidence: Int? = null,
    val note: String? = null,
    val leave_reason: String? = null
)

@Serializable
data class BreakRow(
    val id: Long,
    val day: String,
    val started_at: String,
    val ended_at: String? = null,
    val reason: String? = null
)

// Insert / update payloads: only non-null fields so nothing is overwritten
// with an explicit null.
@Serializable private data class AttStartIns(val day: String, val status: String, val started_at: String)
@Serializable private data class AttLeaveUp(val day: String, val status: String, val leave_reason: String)
@Serializable private data class AttEndUp(val ended_at: String, val confidence: Int, val note: String)
@Serializable private data class AttEndUpNoNote(val ended_at: String, val confidence: Int)
@Serializable private data class BreakIns(val day: String)
@Serializable private data class BreakEndUp(val ended_at: String)
@Serializable private data class LogIdRow(val study_day: Int)
@Serializable private data class LogHoursUp(val actual_hours: Double, val confidence: Int)

enum class DayMark { PRESENT, LEAVE, ABSENT, TODAY_OPEN, UNTRACKED }

data class DayRow(
    val date: String,
    val mark: DayMark,
    val hours: Double? = null,
    val confidence: Int? = null,
    val reason: String? = null
)

data class TodayAttendance(
    val row: AttendanceRow?,
    val openBreak: BreakRow?,
    val breaks: List<BreakRow>
) {
    val started: Boolean get() = row?.started_at != null
    val ended: Boolean get() = row?.ended_at != null
    val onLeave: Boolean get() = row?.status == "leave"
}

object Attendance {

    private fun nowIso(): String = Clock.System.now().toString()
    private fun today(): String = localTodayDate().toString()

    private fun millis(iso: String?): Long? =
        iso?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() }

    // Worked minutes = (end or now) - start - breaks (an open break counts up to "now").
    fun workedMinutes(row: AttendanceRow?, breaks: List<BreakRow>, nowMs: Long): Long {
        val start = millis(row?.started_at) ?: return 0
        val end = millis(row?.ended_at) ?: nowMs
        var total = end - start
        for (b in breaks) {
            val bs = millis(b.started_at) ?: continue
            val be = millis(b.ended_at) ?: end
            total -= (be.coerceAtMost(end) - bs.coerceAtLeast(start)).coerceAtLeast(0)
        }
        return (total / 60_000).coerceAtLeast(0)
    }

    fun breakMinutes(breaks: List<BreakRow>, nowMs: Long): Long = breaks.sumOf { b ->
        val bs = millis(b.started_at) ?: return@sumOf 0L
        val be = millis(b.ended_at) ?: nowMs
        ((be - bs) / 60_000).coerceAtLeast(0)
    }

    suspend fun loadToday(): TodayAttendance = loadDay(today())

    suspend fun loadDay(day: String): TodayAttendance {
        val row = Supa.client.from("attendance").select { filter { eq("day", day) }; limit(1) }
            .decodeList<AttendanceRow>().firstOrNull()
        val breaks = Supa.client.from("breaks").select {
            filter { eq("day", day) }
            order("started_at", Order.ASCENDING)
        }.decodeList<BreakRow>()
        return TodayAttendance(row, breaks.firstOrNull { it.ended_at == null }, breaks)
    }

    // ---- writes -----------------------------------------------------------

    suspend fun startDay() {
        val day = today()
        val cur = loadDay(day)
        if (cur.row == null) {
            Supa.client.from("attendance").insert(AttStartIns(day, "present", nowIso()))
        } else if (cur.onLeave || cur.row.started_at == null) {
            // Was marked leave (or has no start yet): turn into a present day.
            Supa.client.from("attendance").update(AttStartIns(day, "present", nowIso())) {
                filter { eq("day", day) }
            }
        }
        Supa.client.from("streak_days").upsert(StreakDay(day = day, studied = true, rest = false))
        Fetch.invalidate()
    }

    suspend fun startBreak() {
        val day = today()
        Supa.client.from("breaks").insert(BreakIns(day))
        Fetch.invalidate()
    }

    suspend fun endBreak() {
        val open = loadDay(today()).openBreak ?: return
        Supa.client.from("breaks").update(BreakEndUp(nowIso())) { filter { eq("id", open.id) } }
        Fetch.invalidate()
    }

    data class EndResult(val hours: Double, val loggedToTutor: Boolean)

    // Closes any open break, stores end + confidence, and copies hours and
    // confidence into today's tutor daily_logs row when one exists.
    // Throws if the day was never started; reports (never hides) whether the
    // daily_logs copy happened.
    suspend fun endDay(confidence: Int, note: String): EndResult {
        val day = today()
        val cur = loadDay(day)
        if (cur.row?.started_at == null) error("Day was not started - nothing to end.")
        val now = nowIso()
        cur.openBreak?.let { b ->
            Supa.client.from("breaks").update(BreakEndUp(now)) { filter { eq("id", b.id) } }
        }
        if (note.isBlank()) {
            Supa.client.from("attendance").update(AttEndUpNoNote(now, confidence)) { filter { eq("day", day) } }
        } else {
            Supa.client.from("attendance").update(AttEndUp(now, confidence, note.trim())) { filter { eq("day", day) } }
        }
        val closed = cur.breaks.map { if (it.ended_at == null) it.copy(ended_at = now) else it }
        val nowMs = Clock.System.now().toEpochMilliseconds()
        val hours = Math.round(workedMinutes(cur.row, closed, nowMs) / 60.0 * 100) / 100.0
        val logged = try {
            val rows = Supa.client.from("daily_logs").select(Columns.list("study_day")) {
                filter { eq("log_date", day) }
            }.decodeList<LogIdRow>()
            if (rows.isEmpty()) false else {
                Supa.client.from("daily_logs").update(LogHoursUp(hours, confidence)) { filter { eq("log_date", day) } }
                true
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { false }
        Fetch.invalidate()
        return EndResult(hours, logged)
    }

    suspend fun markLeave(day: String, reason: String) {
        val hasStudy = Supa.client.from("streak_days").select { filter { eq("day", day) } }
            .decodeList<StreakDay>().any { it.studied }
        if (hasStudy) error("That day already has study recorded - it is a present day.")
        Supa.client.from("attendance").upsert(AttLeaveUp(day, "leave", reason.trim().ifBlank { "Leave" }))
        Supa.client.from("streak_days").upsert(StreakDay(day = day, studied = false, rest = true))
        Fetch.invalidate()
    }

    suspend fun clearLeave(day: String) {
        val wasLeave = Supa.client.from("attendance").select { filter { eq("day", day); eq("status", "leave") } }
            .decodeList<AttendanceRow>().isNotEmpty()
        if (!wasLeave) return
        Supa.client.from("attendance").delete { filter { eq("day", day); eq("status", "leave") } }
        Supa.client.from("streak_days").delete { filter { eq("day", day); eq("rest", true) } }
        Fetch.invalidate()
    }

    // ---- history ----------------------------------------------------------

    // Newest first. Days before the first tracked day are UNTRACKED, not absent.
    suspend fun history(days: Int = 35): List<DayRow> {
        val from = localTodayDate().minus(DatePeriod(days = days)).toString()
        val att = Supa.client.from("attendance").select {
            filter { gte("day", from) }
            order("day", Order.DESCENDING)
        }.decodeList<AttendanceRow>()
        val streak = Fetch.streakDays()   // errors propagate: never show every day as absent
        val brk = Supa.client.from("breaks").select { filter { gte("day", from) } }
            .decodeList<BreakRow>().groupBy { it.day }
        val attBy = att.associateBy { it.day }
        val studied = streak.filter { it.studied }.map { it.day }.toSet()
        val rest = streak.filter { it.rest }.map { it.day }.toSet()
        val firstTracked = (att.map { it.day } + studied + rest).minOrNull()
        val today = localTodayDate()
        val nowMs = Clock.System.now().toEpochMilliseconds()
        return (0 until days).map { i ->
            val d = today.minus(DatePeriod(days = i)).toString()
            val a = attBy[d]
            val hrs = a?.let { workedMinutes(it, brk[d].orEmpty(), nowMs) / 60.0 }?.takeIf { a.started_at != null }
            val mark = when {
                a?.status == "leave" || (a == null && d in rest) -> DayMark.LEAVE
                a != null || d in studied -> if (d == today.toString() && a?.ended_at == null) DayMark.TODAY_OPEN else DayMark.PRESENT
                d == today.toString() -> DayMark.TODAY_OPEN
                firstTracked == null || d < firstTracked -> DayMark.UNTRACKED
                else -> DayMark.ABSENT
            }
            DayRow(d, mark, hrs, a?.confidence, a?.leave_reason)
        }
    }

    // Full tables for the JSON backup (paged, stable order).
    suspend fun exportAttendance(): List<AttendanceRow> = Fetch.pageAll { from, to ->
        Supa.client.from("attendance").select { order("day", Order.ASCENDING); range(from, to) }.decodeList<AttendanceRow>()
    }

    suspend fun exportBreaks(): List<BreakRow> = Fetch.pageAll { from, to ->
        Supa.client.from("breaks").select { order("id", Order.ASCENDING); range(from, to) }.decodeList<BreakRow>()
    }
}
