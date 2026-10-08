package com.devsecops.tracker.data

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.random.Random

// =========================================================================
// Note-based daily revision. Pure logic (no network, no UI) so it is tested.
//  - dueNotes(): which notes to revise today (1, 3, 7, 14, 30 days after the
//    note was written, newest first, max 3 a day).
//  - buildRevision(): turns a note's Markdown into a recap + MCQs + one
//    "put the steps in order" question. No AI: questions come from the
//    note's own tables and step diagrams.
// =========================================================================

val REVISION_RUNGS = listOf(1, 3, 7, 14, 30)
const val MAX_NOTES_PER_DAY = 3

data class NoteDue(val note: NoteMeta, val rung: Int, val dueOn: LocalDate, val noteDay: LocalDate)

fun dueNotes(
    notes: List<NoteMeta>,
    done: List<NoteRevisionRow>,
    today: LocalDate,
    doneTodayCount: Int = 0,
    maxPerDay: Int = MAX_NOTES_PER_DAY
): List<NoteDue> {
    val highest = done.groupBy { it.note_id }.mapValues { e -> e.value.maxOf { it.rung } }
    val all = notes.mapNotNull { n ->
        val day = localDateOf(n.created_at) ?: return@mapNotNull null
        val rung = REVISION_RUNGS.firstOrNull { it > (highest[n.id] ?: 0) } ?: return@mapNotNull null
        val dueOn = day.plus(DatePeriod(days = rung))
        if (dueOn > today) null else NoteDue(n, rung, dueOn, day)
    }
    val sorted = all.sortedWith(compareBy<NoteDue> { it.rung }.thenByDescending { it.noteDay }.thenBy { it.note.id })
    return sorted.take((maxPerDay - doneTodayCount).coerceAtLeast(0))
}

// How many notes are due in total (before the daily cap).
fun dueNotesTotal(notes: List<NoteMeta>, done: List<NoteRevisionRow>, today: LocalDate): Int =
    dueNotes(notes, done, today, 0, Int.MAX_VALUE).size

data class Mcq(val prompt: String, val options: List<String>, val correct: Int, val why: String)
data class OrderQ(val title: String, val steps: List<String>)   // steps in the correct order
data class NoteRevision(val recap: List<String>, val mcqs: List<Mcq>, val order: OrderQ?)

private fun clean(t: String) = t.replace("**", "").replace("`", "").replace("\\|", "/").trim()

private data class Tbl(val heading: String, val rows: List<Pair<String, String>>)
private data class Blk(val kind: String, val caption: String, val lines: List<String>)

private fun parse(md: String): Pair<List<Tbl>, List<Blk>> {
    val tables = mutableListOf<Tbl>()
    val blocks = mutableListOf<Blk>()
    var heading = ""
    var inFence = false
    var kind = ""
    var caption = ""
    val buf = mutableListOf<String>()
    val rows = mutableListOf<List<String>>()
    fun flushTable() {
        if (rows.size >= 3) {                       // header + separator + >=1 row
            val body = rows.drop(2).filter { it.size >= 2 }
                .map { clean(it[0]) to clean(it[1]) }
                .filter { (a, b) -> a.isNotBlank() && b.isNotBlank() && a != b && a.length <= 90 && b.length <= 110 }
            if (body.size >= 3) tables.add(Tbl(heading, body))
        }
        rows.clear()
    }
    for (raw in md.lines()) {
        val line = raw.trimEnd()
        if (line.trimStart().startsWith("```")) {
            if (!inFence) {
                flushTable()
                inFence = true
                val info = line.trim().removePrefix("```").trim()
                kind = info.substringBefore(' ')
                caption = info.substringAfter(' ', "").trim()
                buf.clear()
            } else {
                inFence = false
                blocks.add(Blk(kind, caption, buf.toList()))
            }
            continue
        }
        if (inFence) { buf.add(line); continue }
        if (line.startsWith("## ")) { flushTable(); heading = line.removePrefix("## ").trim(); continue }
        val t = line.trim()
        if (t.startsWith("|") && t.endsWith("|") && t.length > 2) {
            rows.add(t.trim('|').split("|").map { it.trim() })
        } else flushTable()
    }
    flushTable()
    return tables to blocks
}

fun buildRevision(md: String, seed: Long, maxMcq: Int = 4): NoteRevision {
    val (tables, blocks) = parse(md)
    val rnd = Random(seed)

    val recap = blocks.firstOrNull { it.kind == "callout" && it.caption.startsWith("recap") }
        ?.lines?.filter { it.trimStart().startsWith("- ") }
        ?.map { clean(it.trimStart().removePrefix("- ")) }
        ?.filter { it.isNotBlank() } ?: emptyList()

    // Questions from tables: "Dikhta hai" first, then the rest.
    val ordered = tables.sortedBy { if (it.heading.startsWith("Dikhta")) 0 else 1 }
    val mcqs = mutableListOf<Mcq>()
    val perTable = if (ordered.size <= 1) maxMcq else 2
    for (t in ordered) {
        val picks = t.rows.shuffled(rnd).take(perTable)
        for ((left, right) in picks) {
            if (mcqs.size >= maxMcq) break
            val others = t.rows.map { it.second }.filter { it != right }.distinct().shuffled(rnd).take(3)
            if (others.size < 2) continue
            val options = (others + right).shuffled(rnd)
            val prompt = if (t.heading.startsWith("Dikhta")) "Ye dikhe: $left. Iska matlab?" else "$left: kya hai / kya karta hai?"
            mcqs.add(Mcq(prompt, options, options.indexOf(right), "$left = $right"))
        }
        if (mcqs.size >= maxMcq) break
    }

    // One ordering question from a steps/path diagram.
    val order = blocks.firstOrNull { it.kind == "steps" || it.kind == "path" }
        ?.let { b ->
            val steps = b.lines.map { clean(it.substringBefore(" | ")) }.filter { it.isNotBlank() }
            if (steps.size in 3..8 && steps.distinct().size == steps.size) OrderQ(b.caption.ifBlank { "Steps ko sahi order me lagao" }, steps) else null
        }

    return NoteRevision(recap, mcqs.take(maxMcq), order)
}
