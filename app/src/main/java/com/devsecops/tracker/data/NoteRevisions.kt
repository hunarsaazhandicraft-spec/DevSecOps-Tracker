package com.devsecops.tracker.data

import com.devsecops.tracker.Supa
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.Serializable

// One finished revision of one note (app writes it, the tutor reads it at DAY).
@Serializable
data class NoteRevisionRow(
    val id: Long = 0,
    val note_id: Long,
    val rung: Int,
    val done_at: String? = null,
    val score: Int? = null,
    val total: Int? = null,
    val recall_hits: Int? = null,
    val recall_total: Int? = null,
    val brain_dump: String? = null
)

@Serializable
data class NoteRevisionInsert(
    val note_id: Long,
    val rung: Int,
    val score: Int,
    val total: Int,
    val recall_hits: Int?,
    val recall_total: Int?,
    val brain_dump: String?
)

object NoteRevisions {
    suspend fun list(): List<NoteRevisionRow> =
        Fetch.pageAll { from, to ->
            Supa.client.from("note_revisions").select {
                order("done_at", Order.DESCENDING)
                range(from, to)
            }.decodeList<NoteRevisionRow>()
        }

    suspend fun save(row: NoteRevisionInsert) {
        Supa.client.from("note_revisions").insert(row)
        Fetch.invalidate()
    }
}
