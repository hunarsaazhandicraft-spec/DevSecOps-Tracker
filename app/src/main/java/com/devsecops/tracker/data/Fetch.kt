package com.devsecops.tracker.data

import com.devsecops.tracker.Supa
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn

// =========================================================================
// Fetch — the ONE place that reads whole tables from Supabase.
//
// PUBLIC API (all suspend, all return the FULL table, all THROW on failure —
// network/401/decoding errors are never turned into an empty list; wrap in
// try/catch, runCatching, or Fetch.safeCall / Fetch.orDefault when a caller
// genuinely treats the data as optional):
//
//   Fetch.cards(): List<RevisionCard>             revision_cards
//   Fetch.reviews(): List<RevisionReview>         revision_reviews (oldest first)
//   Fetch.sessions(): List<StudySessionExport>    study_sessions (id, topic_id, session_date,
//                                                 duration_min, activity_type, notes)
//   Fetch.sessionProgress(): List<StudySessionProgress>   same data, slim model
//   Fetch.sessionActs(): List<SessionAct>                 same data, slim model (Extras)
//   Fetch.topics(): List<Topic>                   curriculum topics
//   Fetch.modules(): List<Module>                 curriculum modules (phase, order sorted)
//   Fetch.logTopics(): List<DailyLogTopic>        daily_log_topics
//   Fetch.dailyLogs(): List<DailyLog>             daily_logs (study_day ascending)
//   Fetch.weakSpots(): List<WeakSpot>             ALL weak spots, newest opened first
//   Fetch.openWeakSpots(): List<WeakSpot>         only cleared_at == null
//   Fetch.masteryRecords(): List<MasteryRecord>   newest recorded first
//   Fetch.streakDays(): List<StreakDay>           ALL streak rows, newest day first
//   Fetch.progressItems(): List<ProgressItem>     progress_items
//   Fetch.glossary() / commands() / banditLevels() / projects() / interviewQuestions()
//                                                 library tables (used by backup)
//
//   Fetch.invalidate()   drop the cache. Call after EVERY write to any table
//                        (Repository does this for its own writes; Extras/other
//                        writers must call it too).
//   Fetch.safeCall { }   Result<T>; rethrows CancellationException.
//   Fetch.orDefault(d) { } value or d on failure; rethrows CancellationException.
//
// Every list is fetched in pages of 1000 rows (PostgREST caps a response at
// ~1000) with a deterministic ORDER BY on the key, until a short page.
// Results are cached in memory for 20 s (per table, single-flight per table)
// so Home/Learner/Memory don't refetch the same table over and over.
// The returned lists are shared: do not mutate them.
//
// Also in this file (top-level, same package):
//   localTodayDate(): LocalDate           device-local today
//   localTodayString(): String            "yyyy-MM-dd" of the device-local today
//   localDateOf(ts: String?): LocalDate?  UTC timestamp / date string -> LOCAL date
//   localDateStringOf(ts: String?): String?
// Use these instead of `.take(10)` on opened_at / recorded_at / reviewed_at.
// =========================================================================

fun localTodayDate(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

fun localTodayString(): String = localTodayDate().toString()

// "2026-09-28T22:30:00.123+00:00" -> device-local calendar date. A plain
// "yyyy-MM-dd" (Postgres `date` column) is already a calendar date and is
// returned as is. Unparseable input -> null.
fun localDateOf(timestamp: String?): LocalDate? {
    if (timestamp.isNullOrBlank()) return null
    if (timestamp.length == 10) return runCatching { LocalDate.parse(timestamp) }.getOrNull()
    return runCatching {
        Instant.parse(timestamp).toLocalDateTime(TimeZone.currentSystemDefault()).date
    }.getOrElse {
        runCatching { LocalDate.parse(timestamp.take(10)) }.getOrNull()
    }
}

fun localDateStringOf(timestamp: String?): String? = localDateOf(timestamp)?.toString()

object Fetch {

    private const val PAGE_SIZE = 1000L
    private const val TTL_MS = 20_000L

    // ---- error handling ---------------------------------------------------

    suspend fun <T> safeCall(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    suspend fun <T> orDefault(default: T, block: suspend () -> T): T =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            default
        }

    // ---- paging -----------------------------------------------------------

    // Calls fetchPage(from, to) (inclusive range) repeatedly until a page
    // comes back shorter than PAGE_SIZE.
    suspend fun <T> pageAll(fetchPage: suspend (from: Long, to: Long) -> List<T>): List<T> {
        val all = ArrayList<T>()
        var from = 0L
        while (true) {
            val page = fetchPage(from, from + PAGE_SIZE - 1)
            all.addAll(page)
            if (page.size < PAGE_SIZE) break
            from += PAGE_SIZE
        }
        return all
    }

    // ---- cache ------------------------------------------------------------

    private class Entry(val at: Long, val value: Any?)

    private val cache = ConcurrentHashMap<String, Entry>()
    private val locks = ConcurrentHashMap<String, Mutex>()
    @Volatile private var generation = 0L

    fun invalidate() {
        generation++
        cache.clear()
        Learner.invalidate()   // one call clears every cache layer
    }

    private fun fresh(key: String): Entry? {
        val e = cache[key] ?: return null
        return if (System.currentTimeMillis() - e.at < TTL_MS) e else null
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> cached(key: String, load: suspend () -> T): T {
        val hit = fresh(key)
        if (hit != null) return hit.value as T
        val mutex = locks.getOrPut(key) { Mutex() }
        return mutex.withLock {
            val again = fresh(key)
            if (again != null) {
                again.value as T
            } else {
                val gen = generation
                val loaded = load()
                // A write that happened while we were loading bumps the
                // generation; don't cache possibly-stale data in that case.
                if (gen == generation) cache[key] = Entry(System.currentTimeMillis(), loaded)
                loaded
            }
        }
    }

    // ---- tables -----------------------------------------------------------

    suspend fun cards(): List<RevisionCard> = cached("revision_cards") {
        pageAll { from, to ->
            Supa.client.from("revision_cards")
                .select {
                    order("id", Order.ASCENDING)
                    range(from, to)
                }
                .decodeList<RevisionCard>()
        }
    }

    suspend fun reviews(): List<RevisionReview> = cached("revision_reviews") {
        pageAll { from, to ->
            Supa.client.from("revision_reviews")
                .select {
                    order("id", Order.ASCENDING)
                    range(from, to)
                }
                .decodeList<RevisionReview>()
        }
    }

    suspend fun sessions(): List<StudySessionExport> = cached("study_sessions") {
        pageAll { from, to ->
            Supa.client.from("study_sessions")
                .select {
                    order("id", Order.ASCENDING)
                    range(from, to)
                }
                .decodeList<StudySessionExport>()
        }
    }

    suspend fun sessionProgress(): List<StudySessionProgress> =
        sessions().map { StudySessionProgress(it.topic_id, it.duration_min, it.session_date) }

    suspend fun sessionActs(): List<SessionAct> =
        sessions().map { SessionAct(it.topic_id, it.activity_type, it.duration_min, it.session_date) }

    suspend fun topics(): List<Topic> = cached("topics") {
        pageAll { from, to ->
            Supa.client.from("topics")
                .select {
                    order("id", Order.ASCENDING)
                    range(from, to)
                }
                .decodeList<Topic>()
        }
    }

    suspend fun modules(): List<Module> = cached("modules") {
        pageAll { from, to ->
            Supa.client.from("modules")
                .select {
                    order("id", Order.ASCENDING)
                    range(from, to)
                }
                .decodeList<Module>()
        }.sortedWith(compareBy({ it.phase_id }, { it.order_num }))
    }

    suspend fun logTopics(): List<DailyLogTopic> = cached("daily_log_topics") {
        pageAll { from, to ->
            Supa.client.from("daily_log_topics")
                .select {
                    order("daily_log_id", Order.ASCENDING)
                    range(from, to)
                }
                .decodeList<DailyLogTopic>()
        }
    }

    suspend fun dailyLogs(): List<DailyLog> = cached("daily_logs") {
        pageAll { from, to ->
            Supa.client.from("daily_logs")
                .select {
                    order("id", Order.ASCENDING)
                    range(from, to)
                }
                .decodeList<DailyLog>()
        }.sortedBy { it.study_day }
    }

    suspend fun weakSpots(): List<WeakSpot> = cached("weak_spots") {
        pageAll { from, to ->
            Supa.client.from("weak_spots")
                .select {
                    order("id", Order.ASCENDING)
                    range(from, to)
                }
                .decodeList<WeakSpot>()
        }.sortedByDescending { it.opened_at }
    }

    suspend fun openWeakSpots(): List<WeakSpot> = weakSpots().filter { it.cleared_at == null }

    suspend fun masteryRecords(): List<MasteryRecord> = cached("mastery_records") {
        pageAll { from, to ->
            Supa.client.from("mastery_records")
                .select {
                    order("id", Order.ASCENDING)
                    range(from, to)
                }
                .decodeList<MasteryRecord>()
        }.sortedByDescending { it.recorded_at }
    }

    suspend fun streakDays(): List<StreakDay> = cached("streak_days") {
        pageAll { from, to ->
            Supa.client.from("streak_days")
                .select {
                    order("day", Order.DESCENDING)
                    range(from, to)
                }
                .decodeList<StreakDay>()
        }
    }

    suspend fun progressItems(): List<ProgressItem> = cached("progress_items") {
        pageAll { from, to ->
            Supa.client.from("progress_items")
                .select {
                    order("id", Order.ASCENDING)
                    range(from, to)
                }
                .decodeList<ProgressItem>()
        }
    }

    suspend fun glossary(): List<GlossaryTerm> = cached("glossary_terms") {
        pageAll { from, to ->
            Supa.client.from("glossary_terms")
                .select {
                    order("id", Order.ASCENDING)
                    range(from, to)
                }
                .decodeList<GlossaryTerm>()
        }
    }

    suspend fun commands(): List<CommandItem> = cached("commands") {
        pageAll { from, to ->
            Supa.client.from("commands")
                .select {
                    order("id", Order.ASCENDING)
                    range(from, to)
                }
                .decodeList<CommandItem>()
        }
    }

    suspend fun banditLevels(): List<BanditLevel> = cached("bandit_levels") {
        pageAll { from, to ->
            Supa.client.from("bandit_levels")
                .select {
                    order("level", Order.ASCENDING)
                    range(from, to)
                }
                .decodeList<BanditLevel>()
        }
    }

    suspend fun projects(): List<ProjectItem> = cached("projects_tracker") {
        pageAll { from, to ->
            Supa.client.from("projects_tracker")
                .select {
                    order("id", Order.ASCENDING)
                    range(from, to)
                }
                .decodeList<ProjectItem>()
        }
    }

    suspend fun interviewQuestions(): List<InterviewQ> = cached("interview_questions") {
        pageAll { from, to ->
            Supa.client.from("interview_questions")
                .select {
                    order("id", Order.ASCENDING)
                    range(from, to)
                }
                .decodeList<InterviewQ>()
        }
    }
}
