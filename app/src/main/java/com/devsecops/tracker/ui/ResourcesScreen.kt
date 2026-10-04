package com.devsecops.tracker.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.devsecops.tracker.data.Fetch
import com.devsecops.tracker.data.ResourceRow
import com.devsecops.tracker.data.Resources
import kotlinx.coroutines.launch

private val SLOT_ORDER = listOf("course", "book", "video", "doc", "tool")

private fun slotTitle(s: String?) = when (s) {
    "course" -> "Courses"
    "book" -> "Books"
    "video" -> "Videos"
    "doc" -> "Docs and standards"
    "tool" -> "Tools and labs"
    else -> "Other"
}

// The seeded notes start with a legacy tag like "[M06 HTTP and web architecture] ".
private fun cleanNote(n: String?): String? =
    n?.replace(Regex("^\\[[^\\]]*\\]\\s*"), "")?.takeIf { it.isNotBlank() }

@Composable
fun ResourcesScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<ResourceRow>?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var kind by remember { mutableIntStateOf(0) }   // 0 all, 1 primary, 2 official, 3 secondary

    suspend fun load() {
        Fetch.safeCall { Resources.all() }
            .onSuccess { items = it; err = null }
            .onFailure { err = it.message ?: "Could not load resources" }
    }
    LaunchedEffect(Unit) { load() }

    Column(Modifier.fillMaxSize()) {
        AppTitleBar(title = "Resources", onBack = onBack,
            subtitle = items?.let { "${it.size} curated links (read-only)" })
        val all = items
        when {
            err != null && all == null -> ErrorBlock(err!!, onRetry = { scope.launch { load() } })
            all == null -> SkeletonList()
            else -> {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    label = { Text("Search title or note") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Sp.l)
                )
                SegTabs(listOf("All", "Primary", "Official", "Extra"), kind, { kind = it },
                    Modifier.padding(horizontal = Sp.l, vertical = Sp.s))
                val q = query.trim().lowercase()
                val shown = all.filter { r ->
                    val okKind = when (kind) { 1 -> r.kind == "primary"; 2 -> r.kind == "official"; 3 -> r.kind == "secondary"; else -> true }
                    val okQ = q.isEmpty() || r.title.lowercase().contains(q) || (r.note?.lowercase()?.contains(q) == true)
                    okKind && okQ
                }
                val grouped = shown.groupBy { it.slot ?: "other" }
                val slots = SLOT_ORDER.filter { grouped.containsKey(it) } + grouped.keys.filter { it !in SLOT_ORDER }
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = Sp.l, vertical = Sp.s),
                    verticalArrangement = Arrangement.spacedBy(Sp.s)
                ) {
                    if (shown.isEmpty()) item { Text("Koi resource nahi mila.", color = cs.onSurfaceVariant) }
                    slots.forEach { slot ->
                        val list = grouped[slot].orEmpty()
                        item(key = "h-$slot") { GroupHeader(slotTitle(slot), "${list.size}") }
                        items(list, key = { it.id }) { r ->
                            val link = Resources.openableUrl(r.url)
                            AppCard(
                                onClick = link?.let { u ->
                                    {
                                        runCatching {
                                            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                        }
                                    }
                                },
                                padding = Sp.m
                            ) {
                                Text(r.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                cleanNote(r.note)?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                                        maxLines = 3, overflow = TextOverflow.Ellipsis)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(Sp.xs)) {
                                    Pill(r.kind, when (r.kind) { "primary" -> cs.primary; "official" -> cs.tertiary; else -> cs.onSurfaceVariant })
                                    Pill(if (link != null) "link" else "no link", if (link != null) cs.primary else cs.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(Sp.xxl)) }
                }
            }
        }
    }
}
