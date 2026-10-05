package com.devsecops.tracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LabRunsTest {
    private fun run(id: Long, lab: String, status: String, date: String, level: String? = null) =
        LabRun(id = id, lab_id = lab, status = status, level = level, run_date = date)

    @Test
    fun latest_picksNewestDateThenHighestId() {
        val m = latestLabRuns(
            listOf(
                run(1, "A", "in_progress", "2026-10-01"),
                run(2, "A", "done", "2026-10-03", "L4"),
                run(3, "B", "blocked", "2026-10-02"),
                run(4, "B", "in_progress", "2026-10-02")
            )
        )
        assertEquals("done", m["A"]?.status)
        assertEquals(4L, m["B"]?.id)
        assertNull(m["C"])
    }

    @Test
    fun statusLabel_coversAllStates() {
        assertEquals("Not started", labStatusLabel(null))
        assertEquals("Done · L4", labStatusLabel(run(1, "A", "done", "d", "L4")))
        assertEquals("Done", labStatusLabel(run(1, "A", "done", "d")))
        assertEquals("Blocked", labStatusLabel(run(1, "A", "blocked", "d")))
        assertEquals("In progress", labStatusLabel(run(1, "A", "in_progress", "d")))
    }
}
