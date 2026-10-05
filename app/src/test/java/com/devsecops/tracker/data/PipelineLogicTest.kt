package com.devsecops.tracker.data

import com.devsecops.tracker.ui.SessionNode
import com.devsecops.tracker.ui.Stat
import com.devsecops.tracker.ui.chatStat
import com.devsecops.tracker.ui.nextDayNumber
import com.devsecops.tracker.ui.pipelineStats
import com.devsecops.tracker.ui.sessionNodes
import org.junit.Assert.assertEquals
import org.junit.Test

class PipelineLogicTest {

    @Test
    fun pipeline_firstUndoneIsRunRestQueued() {
        assertEquals(listOf(Stat.RUN, Stat.QUEUED, Stat.QUEUED), pipelineStats(listOf(false, false, false)))
    }

    @Test
    fun pipeline_doneStagesPassEvenWhenOutOfOrder() {
        assertEquals(listOf(Stat.PASS, Stat.RUN, Stat.PASS), pipelineStats(listOf(true, false, true)))
        assertEquals(listOf(Stat.PASS, Stat.PASS, Stat.PASS), pipelineStats(listOf(true, true, true)))
    }

    private fun view(no: String, vararg status: String?): SessionView {
        val s = TeachingSession(id = no.hashCode().toLong(), module_code = "M0.2", session_no = no, title = "t")
        val items = status.mapIndexed { i, st ->
            val it = MustCoverItem(id = (no.hashCode() * 10 + i).toLong(), session_id = s.id, item_no = i + 1, item = "x")
            it to st?.let { v -> CoverageEntry(id = i.toLong(), must_cover_id = it.id, status = v) }
        }
        return SessionView(s, items)
    }

    @Test
    fun sessionNodes_passFailPendingRunQueued() {
        val nodes = sessionNodes(
            listOf(
                view("1", "verified", "verified"),   // all verified
                view("4", "verified", null),         // partial
                view("6", "redo", "verified"),       // redo open
                view("6b", null, null),              // first untouched
                view("7", null)                      // later untouched
            )
        )
        assertEquals(
            listOf(
                SessionNode("1", Stat.PASS), SessionNode("4", Stat.PENDING), SessionNode("6", Stat.FAIL),
                SessionNode("6b", Stat.RUN), SessionNode("7", Stat.QUEUED)
            ),
            nodes
        )
    }

    @Test
    fun sessionNodes_emptyItemsIsNotPass() {
        val nodes = sessionNodes(listOf(view("1")))
        assertEquals(Stat.RUN, nodes[0].stat)
    }

    @Test
    fun nextDay_incrementsOnlyWhenDone() {
        assertEquals(11, nextDayNumber("Done", 10))
        assertEquals(10, nextDayNumber("In progress", 10))
        assertEquals(10, nextDayNumber(null, 10))
    }

    @Test
    fun sessionNodes_sortsByNumberAndMarksSkippedUntouchedAsPending() {
        val nodes = sessionNodes(
            listOf(
                view("6b", "verified", null),   // touched
                view("1", null, null),          // untouched but before a touched one: skipped
                view("8", null),                // first untouched after the last touched: RUN
                view("4", "verified", "verified"),
                view("9", null)
            )
        )
        assertEquals(
            listOf(
                SessionNode("1", Stat.PENDING), SessionNode("4", Stat.PASS), SessionNode("6b", Stat.PENDING),
                SessionNode("8", Stat.RUN), SessionNode("9", Stat.QUEUED)
            ),
            nodes
        )
    }

    @Test
    fun chatStat_mapsPathStatesToPipelineVocabulary() {
        assertEquals(Stat.PASS, chatStat(ChatState.DONE))
        assertEquals(Stat.RUN, chatStat(ChatState.CURRENT))
        assertEquals(Stat.QUEUED, chatStat(ChatState.UPCOMING))
        assertEquals(Stat.LOCKED, chatStat(ChatState.LOCKED))
    }
}
