package com.devsecops.tracker.data

import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RevisionLogicTest {

    private val md = """
## Short mein
```callout recap
- Pehla point
- Doosra point
```

## Kaise kaam karta hai
```steps Ek flow
Pehla | detail
Doosra | detail
Teesra | detail
```

## Dikhta hai → matlab
| Dikhta hai | Matlab |
|---|---|
| `refused` | port par koi nahi |
| `404` | path nahi mila |
| `500` | server toota |
| `301` | jagah badli |

## Cheat sheet
| Command | Kaam |
|---|---|
| `pwd` | abhi kahan hu |
| `ls` | list |
| `cd` | folder badlo |
""".trimIndent()

    @Test fun recapAndStepsAreExtracted() {
        val r = buildRevision(md, 1)
        assertEquals(listOf("Pehla point", "Doosra point"), r.recap)
        assertEquals(listOf("Pehla", "Doosra", "Teesra"), r.order?.steps)
        assertEquals("Ek flow", r.order?.title)
    }

    @Test fun mcqsHaveTheCorrectAnswerAndDistinctOptions() {
        val r = buildRevision(md, 7)
        assertTrue(r.mcqs.isNotEmpty() && r.mcqs.size <= 4)
        r.mcqs.forEach { q ->
            assertEquals(q.options.size, q.options.distinct().size)
            assertTrue(q.correct in q.options.indices)
            assertTrue(q.why.contains(q.options[q.correct]))
        }
        assertTrue(r.mcqs.first().prompt.startsWith("Ye dikhe"))
    }

    @Test fun sameSeedGivesSameQuestions() {
        assertEquals(buildRevision(md, 5), buildRevision(md, 5))
    }

    @Test fun noteWithoutTablesOrStepsStillWorks() {
        val r = buildRevision("## Ek line mein\nbas ek line", 1)
        assertTrue(r.mcqs.isEmpty())
        assertNull(r.order)
        assertTrue(r.recap.isEmpty())
    }

    @Test fun tooFewRowsMakeNoQuestions() {
        val r = buildRevision("## X\n| a | b |\n|---|---|\n| 1 | 2 |\n| 3 | 4 |", 1)
        assertTrue(r.mcqs.isEmpty())
    }

    private fun note(id: Long, created: String) = NoteMeta(id = id, module_id = 1, title = "n$id", created_at = created)
    private fun done(noteId: Long, rung: Int) = NoteRevisionRow(note_id = noteId, rung = rung)
    private val today = LocalDate.parse("2026-10-09")

    @Test fun noteIsDueOneDayAfterItIsWritten() {
        val due = dueNotes(listOf(note(1, "2026-10-08T10:00:00+00:00")), emptyList(), today)
        assertEquals(1, due.size)
        assertEquals(1, due[0].rung)
        assertTrue(dueNotes(listOf(note(1, "2026-10-09T01:00:00+00:00")), emptyList(), today).isEmpty() ||
            dueNotes(listOf(note(1, "2026-10-09T01:00:00+00:00")), emptyList(), today)[0].dueOn <= today)
    }

    @Test fun ladderMovesUpAfterEachRevision() {
        val n = listOf(note(1, "2026-10-01T00:00:00+00:00"))
        assertEquals(1, dueNotes(n, emptyList(), today)[0].rung)
        assertEquals(3, dueNotes(n, listOf(done(1, 1)), today)[0].rung)
        assertEquals(7, dueNotes(n, listOf(done(1, 1), done(1, 3)), today)[0].rung)
    }

    @Test fun ladderDoesNotDuePreMaturely() {
        val n = listOf(note(1, "2026-10-08T05:00:00+00:00"))
        assertTrue(dueNotes(n, listOf(done(1, 1)), today).isEmpty())   // rung 3 due on 11 Oct
    }

    @Test fun allRungsDoneMeansNothingDue() {
        val n = listOf(note(1, "2026-08-01T00:00:00+00:00"))
        val all = REVISION_RUNGS.map { done(1, it) }
        assertTrue(dueNotes(n, all, today).isEmpty())
    }

    @Test fun dailyCapAndNewestFirst() {
        val notes = (1L..6L).map { note(it, "2026-10-0${it}T00:00:00+00:00") }
        val due = dueNotes(notes, emptyList(), today)
        assertEquals(3, due.size)
        assertEquals(listOf(6L, 5L, 4L), due.map { it.note.id })
        assertEquals(1, dueNotes(notes, emptyList(), today, doneTodayCount = 2).size)
        assertTrue(dueNotes(notes, emptyList(), today, doneTodayCount = 3).isEmpty())
        assertEquals(6, dueNotesTotal(notes, emptyList(), today))
    }

    @Test fun notesWithoutCreatedAtAreSkipped() {
        assertFalse(dueNotes(listOf(NoteMeta(id = 1, module_id = 1, title = "x")), emptyList(), today).isNotEmpty())
        assertNotNull(note(1, "2026-10-01T00:00:00+00:00"))
    }
}
