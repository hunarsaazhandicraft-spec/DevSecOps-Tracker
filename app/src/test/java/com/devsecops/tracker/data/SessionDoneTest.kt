package com.devsecops.tracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionDoneTest {

    private fun view(vararg row: Pair<String?, String?>): SessionView {
        val s = TeachingSession(id = 1, module_code = "M0.2", session_no = "1", title = "t")
        val items = row.mapIndexed { i, (tier, st) ->
            val it = MustCoverItem(id = i + 1L, session_id = 1, item_no = i + 1, item = "x", tier = tier)
            it to st?.let { v -> CoverageEntry(id = i.toLong(), must_cover_id = it.id, status = v) }
        }
        return SessionView(s, items)
    }

    @Test fun unknownTierIsTreatedAsCore() {
        assertTrue(view(null to "verified", null to "verified").isDone)
        assertFalse(view(null to "verified", null to "guided").isDone)
    }

    @Test fun coreMustAllBeVerified() {
        assertFalse(view("CORE" to "verified", "CORE" to "redo").isDone)
        assertTrue(view("CORE" to "verified", "CORE" to "verified").isDone)
    }

    @Test fun workingNeedsEightyPercent() {
        val four = arrayOf("WORKING" to "verified", "WORKING" to "verified", "WORKING" to "verified", "WORKING" to "verified", "WORKING" to "guided")
        assertTrue(view("CORE" to "verified", *four).isDone)
        assertFalse(view("CORE" to "verified", "WORKING" to "verified", "WORKING" to "guided").isDone)
    }

    @Test fun awarenessMustBeRecognized() {
        assertFalse(view("CORE" to "verified", "AWARENESS" to null).isDone)
        assertTrue(view("CORE" to "verified", "AWARENESS" to "verified").isDone)
    }

    @Test fun guidedIsCountedButNotDone() {
        val v = view("CORE" to "guided", "CORE" to "verified")
        assertEquals(1, v.guided)
        assertEquals(1, v.verified)
        assertFalse(v.isDone)
    }

    @Test fun emptySessionIsNotDone() {
        assertFalse(view().isDone)
    }
}
