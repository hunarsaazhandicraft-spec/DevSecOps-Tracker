package com.devsecops.tracker.data

import com.devsecops.tracker.Supa
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.Serializable

// Curated study resources the tutor seeded (table resources, SELECT only).

@Serializable
data class ResourceRow(
    val id: Long,
    val module_code: String? = null,
    val title: String,
    val url: String? = null,
    val kind: String = "secondary",   // primary | official | secondary
    val slot: String? = null,         // course | doc | tool | book | video
    val note: String? = null,
    val source_path: String? = null
)

object Resources {
    suspend fun all(): List<ResourceRow> =
        Fetch.pageAll { from, to ->
            Supa.client.from("resources").select {
                order("id", Order.ASCENDING)
                range(from, to)
            }.decodeList<ResourceRow>()
        }

    // Only open real web links.
    fun openableUrl(url: String?): String? =
        url?.trim()?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
}
