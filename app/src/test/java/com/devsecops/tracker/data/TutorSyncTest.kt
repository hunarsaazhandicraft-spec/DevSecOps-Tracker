package com.devsecops.tracker.data

import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorSyncTest {

    @Test
    fun parseNextChat_pilotFormat() {
        val n = parseNextChat("Agla chat: P0 M0.2 S7 HTTP anatomy. Start: DAY. Pehle carry-over cold recheck.")!!
        assertEquals("P0 M0.2 S7 HTTP anatomy", n.name)
        assertEquals("DAY", n.start)
        assertEquals("Pehle carry-over cold recheck.", n.rest)
        assertEquals(emptyList<String>(), n.carry)
    }

    @Test
    fun parseNextChat_carryOverListIsCappedAtEight() {
        val items = (1..10).joinToString("; ") { "item$it" }
        val n = parseNextChat("Agla chat: X. Carry-over: $items")!!
        assertEquals(8, n.carry.size)
        assertEquals("item1", n.carry.first())
        assertNull(n.start)
    }

    @Test
    fun parseNextChat_plainTextOrBlankIsNull() {
        assertNull(parseNextChat("8 Oct: cold re-ask Content-Length wait+timeout."))
        assertNull(parseNextChat("   "))
        assertNull(parseNextChat(null))
    }

    private val today = LocalDate.parse("2026-10-08")

    private fun row(d: String, actual: Double?, plan: Double? = 6.5) = HoursRow(d, plan, actual)

    @Test
    fun pace_sumsLoggedDaysInWindowOnly() {
        val rows = listOf(
            row("2026-10-08", 6.5), row("2026-10-07", 3.25), row("2026-10-06", null),
            row("2026-09-20", 9.0)   // outside the 7-day window
        )
        val p = paceSummary(rows, today, 7)
        assertEquals(9.75, p.actual, 0.001)
        assertEquals(13.0, p.planned, 0.001)
        assertEquals(75, p.pct)
        assertEquals(2, p.days)
    }

    @Test
    fun pace_nullPlanDefaultsToSixPointFive() {
        val p = paceSummary(listOf(row("2026-10-08", 6.5, plan = null)), today, 7)
        assertEquals(DEFAULT_PLAN_HOURS, p.planned, 0.001)
    }

    @Test
    fun replan_needsSevenLoggedDaysUnderSeventyPercent() {
        val low = (1..8).map { row("2026-10-%02d".format(it), 3.0) }       // 3/6.5 = 46%
        assertTrue(needsReplan(low, today))
        assertFalse(needsReplan(low.take(3), today))                         // too few days
        val ok = (1..8).map { row("2026-10-%02d".format(it), 6.0) }
        assertFalse(needsReplan(ok, today))
    }

    @Test
    fun softCap_isStrictlyAboveEightHours() {
        assertFalse(overSoftCap(8.0))
        assertTrue(overSoftCap(8.01))
    }
}
