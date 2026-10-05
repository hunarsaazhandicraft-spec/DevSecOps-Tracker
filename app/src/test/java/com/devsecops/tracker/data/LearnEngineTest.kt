package com.devsecops.tracker.data

import com.devsecops.tracker.ui.currentSessionIndex
import com.devsecops.tracker.ui.drillSummary
import com.devsecops.tracker.ui.labMatchesModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LearnEngineTest {
    private fun sv(id: Long, verified: Int, total: Int): SessionView {
        val s = TeachingSession(id = id, module_code = "M0.2", session_no = id.toString(), title = "S$id")
        val items = (1..total).map { n ->
            val m = MustCoverItem(id = id * 100 + n, session_id = id, item_no = n, item = "item $n")
            val c = if (n <= verified) CoverageEntry(id = n.toLong(), must_cover_id = m.id, status = "verified") else null
            m to c
        }
        return SessionView(s, items)
    }

    @Test
    fun currentSession_firstUnfinished_elseLast_elseMinusOne() {
        assertEquals(1, currentSessionIndex(listOf(sv(1, 3, 3), sv(2, 1, 3), sv(3, 0, 3))))
        assertEquals(2, currentSessionIndex(listOf(sv(1, 2, 2), sv(2, 2, 2), sv(3, 2, 2))))
        assertEquals(-1, currentSessionIndex(emptyList()))
    }

    @Test
    fun labMatchesModule_singleAndRange() {
        assertTrue(labMatchesModule("M0.2", "M0.2"))
        assertFalse(labMatchesModule("M0.2", "M0.3"))
        assertTrue(labMatchesModule("M0.2–M0.4", "M0.3"))
        assertTrue(labMatchesModule("M0.2–M0.4", "M0.4"))
        assertFalse(labMatchesModule("M0.2–M0.4", "M0.5"))
        assertFalse(labMatchesModule(null, "M0.2"))
        assertFalse(labMatchesModule("M0.2", null))
    }

    @Test
    fun drillSummary_countsAndMarksRedo() {
        val t = drillSummary("DNS", listOf("a", "b"), listOf(true, false))
        assertEquals("Drill: DNS - 1/2 recalled\nOK   1. a\nREDO 2. b", t)
    }
}
