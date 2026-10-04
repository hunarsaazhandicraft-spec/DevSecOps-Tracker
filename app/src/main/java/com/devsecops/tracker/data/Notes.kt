package com.devsecops.tracker.data

import android.content.Context
import com.devsecops.tracker.Supa
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

// =========================================================================
// Topic notes: in-depth notes the tutor writes into Supabase (topic_notes).
// The app reads them and only edits "my_words" and the draft/reviewed status
// (column-level grant). Last notes opened are cached on disk for offline.
// =========================================================================

@Serializable
data class NoteMeta(
    val id: Long,
    val module_id: Int,
    val topic_id: Int? = null,
    val part: Int = 1,
    val title: String,
    val status: String = "draft",
    val source: String = "tutor-chat",
    val updated_at: String? = null
)

@Serializable
data class NoteFull(
    val id: Long,
    val module_id: Int,
    val topic_id: Int? = null,
    val part: Int = 1,
    val title: String,
    val content_md: String,
    val my_words: String? = null,
    val status: String = "draft",
    val source: String = "tutor-chat",
    val updated_at: String? = null
)

@Serializable private data class NoteReviewUp(val my_words: String, val status: String)

object Notes {
    private const val MAX_CACHED = 30
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val META_COLS = Columns.list("id", "module_id", "topic_id", "part", "title", "status", "source", "updated_at")

    // ---- network ----------------------------------------------------------

    suspend fun list(): List<NoteMeta> =
        Fetch.pageAll { from, to ->
            Supa.client.from("topic_notes")
                .select(META_COLS) {
                    order("module_id", Order.ASCENDING)
                    order("topic_id", Order.ASCENDING)
                    order("part", Order.ASCENDING)
                    range(from, to)
                }.decodeList<NoteMeta>()
        }

    suspend fun get(id: Long): NoteFull =
        Supa.client.from("topic_notes").select { filter { eq("id", id) }; limit(1) }
            .decodeList<NoteFull>().firstOrNull() ?: error("Note not found")

    suspend fun forModule(moduleId: Int): List<NoteFull> =
        Supa.client.from("topic_notes").select {
            filter { eq("module_id", moduleId) }
            order("topic_id", Order.ASCENDING)
            order("part", Order.ASCENDING)
        }.decodeList<NoteFull>()

    // Ids of notes whose title or body contains the text (server side).
    suspend fun searchIds(q: String): Set<Long> {
        val clean = q.filter { it.isLetterOrDigit() || it == ' ' || it == '-' || it == '/' }.trim()
        if (clean.length < 3) return emptySet()
        val p = "%$clean%"
        return Supa.client.from("topic_notes").select(Columns.list("id")) {
            filter { or { ilike("title", p); ilike("content_md", p); ilike("my_words", p) } }
        }.decodeList<IdOnly>().map { it.id }.toSet()
    }

    @Serializable private data class IdOnly(val id: Long)

    // Saves "my words". Blank words always means draft (DB rule: reviewed needs words).
    suspend fun saveMyWords(id: Long, words: String, wantReviewed: Boolean) {
        val text = words.trim()
        val status = if (wantReviewed && text.isNotEmpty()) "reviewed" else "draft"
        Supa.client.from("topic_notes").update(NoteReviewUp(text, status)) { filter { eq("id", id) } }
        Fetch.invalidate()
    }

    // ---- offline cache ----------------------------------------------------

    private fun dir(ctx: Context) = File(ctx.filesDir, "notes_cache").apply { mkdirs() }

    suspend fun cachedIndex(ctx: Context): List<NoteMeta> = withContext(Dispatchers.IO) {
        runCatching {
            val f = File(dir(ctx), "index.json")
            if (f.exists()) json.decodeFromString<List<NoteMeta>>(f.readText()) else emptyList()
        }.getOrDefault(emptyList())
    }

    suspend fun saveIndex(ctx: Context, list: List<NoteMeta>) = withContext(Dispatchers.IO) {
        runCatching { File(dir(ctx), "index.json").writeText(json.encodeToString(list)) }
        Unit
    }

    suspend fun cachedNote(ctx: Context, id: Long): NoteFull? = withContext(Dispatchers.IO) {
        runCatching {
            val f = File(dir(ctx), "note_$id.json")
            if (f.exists()) json.decodeFromString<NoteFull>(f.readText()) else null
        }.getOrNull()
    }

    suspend fun saveNote(ctx: Context, note: NoteFull) = withContext(Dispatchers.IO) {
        runCatching {
            val d = dir(ctx)
            File(d, "note_${note.id}.json").writeText(json.encodeToString(note))
            val files = d.listFiles { f -> f.name.startsWith("note_") }.orEmpty()
                .sortedByDescending { it.lastModified() }
            files.drop(MAX_CACHED).forEach { it.delete() }
        }
        Unit
    }

    // Share text for one or more notes.
    fun shareText(notes: List<NoteFull>): String = notes.joinToString("\n\n---\n\n") { n ->
        buildString {
            append("# ").append(n.title).append("\n\n").append(n.content_md.trim())
            if (!n.my_words.isNullOrBlank()) append("\n\n## Mere shabdon mein\n").append(n.my_words.trim())
        }
    }
}
