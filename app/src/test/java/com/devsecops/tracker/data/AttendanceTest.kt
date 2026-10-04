package com.devsecops.tracker.data

import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AttendanceTest {

    private fun ms(iso: String) = Instant.parse(iso).toEpochMilliseconds()
    private val start = "2026-10-04T08:00:00Z"

    private fun row(end: String? = null) = AttendanceRow(day = "2026-10-04", started_at = start, ended_at = end)
    private fun brk(a: String, b: String?) = BreakRow(id = 1, day = "2026-10-04", started_at = a, ended_at = b)

    @Test fun noBreaks_isEndMinusStart() {
        assertEquals(90L, Attendance.workedMinutes(row("2026-10-04T09:30:00Z"), emptyList(), 0))
    }

    @Test fun closedBreak_isSubtracted() {
        val b = brk("2026-10-04T08:15:00Z", "2026-10-04T08:20:00Z")
        assertEquals(55L, Attendance.workedMinutes(row("2026-10-04T09:00:00Z"), listOf(b), 0))
    }

    @Test fun openBreak_countsUntilNow() {
        val b = brk("2026-10-04T08:30:00Z", null)
        assertEquals(30L, Attendance.workedMinutes(row(), listOf(b), ms("2026-10-04T09:00:00Z")))
    }

    @Test fun notStarted_isZero() {
        assertEquals(0L, Attendance.workedMinutes(null, emptyList(), ms("2026-10-04T09:00:00Z")))
    }

    @Test fun breakOutsideWindow_isIgnored() {
        val b = brk("2026-10-04T06:00:00Z", "2026-10-04T07:00:00Z")
        assertEquals(60L, Attendance.workedMinutes(row("2026-10-04T09:00:00Z"), listOf(b), 0))
    }

    @Test fun breakMinutes_sumsClosedBreaks() {
        val l = listOf(brk("2026-10-04T08:00:00Z", "2026-10-04T08:05:00Z"), brk("2026-10-04T09:00:00Z", "2026-10-04T09:10:00Z"))
        assertEquals(15L, Attendance.breakMinutes(l, 0))
    }

    private fun item(name: String, status: String = "Mastered", gate: String? = "Pass", sup: Boolean = false) =
        ProgressItem(id = 1, item = name, status = status, gate_result = gate, superseded = sup)

    @Test fun path_matchesCodeWithSpaceOrColon() {
        assertTrue(Curriculum.isPassed(listOf(item("M0.1 Foundations")), "M0.1"))
        assertTrue(Curriculum.isPassed(listOf(item("M0.1: Foundations")), "M0.1"))
    }

    @Test fun path_doesNotConfuseM01WithM010() {
        assertFalse(Curriculum.isPassed(listOf(item("M0.10 Other")), "M0.1"))
    }

    @Test fun path_requiresMasteredAndPass_andIgnoresSuperseded() {
        assertFalse(Curriculum.isPassed(listOf(item("M0.1 X", status = "Needs Review")), "M0.1"))
        assertFalse(Curriculum.isPassed(listOf(item("M0.1 X", gate = null)), "M0.1"))
        assertFalse(Curriculum.isPassed(listOf(item("M0.1 X", sup = true)), "M0.1"))
    }
}
