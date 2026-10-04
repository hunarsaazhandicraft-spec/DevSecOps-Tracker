package com.devsecops.tracker.data

import com.devsecops.tracker.Supa
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import kotlinx.serialization.Serializable

// R25-R29 features. Kept apart from Repository so the core file stays small.

@Serializable
data class PhaseRow(val id: Int, val name: String, val order_num: Int = 0)

data class LibraryCounts(val glossary: Int, val cards: Int, val projects: Int, val interview: Int, val commands: Int, val bandit: Int)

@Serializable
data class BanditLevel(val level: Int, val done: Boolean = true, val note: String? = null)

@Serializable
data class CommandItem(
    val id: Long,
    val title: String,
    val command: String,
    val note: String? = null,
    val tag: String? = null
)

@Serializable
data class CommandInsert(
    val title: String,
    val command: String,
    val note: String? = null,
    val tag: String? = null
)

@Serializable
data class ProjectItem(
    val id: Long,
    val name: String,
    val code: String? = null,
    val status: String,
    val repo_url: String? = null,
    val rubric_passed: Boolean = false,
    val notes: String? = null
)

@Serializable
data class ProjectUpdate(val status: String, val repo_url: String? = null, val rubric_passed: Boolean = false)

@Serializable
data class InterviewQ(
    val id: Long,
    val module_id: Int? = null,
    val question: String,
    val answer: String? = null,
    val difficulty: String? = null,
    val revision_status: String? = null
)

@Serializable
data class InterviewUpdate(val revision_status: String, val last_reviewed: String)

@Serializable
data class GlossaryTerm(
    val id: Long,
    val term: String,
    val definition: String,
    val expansion: String? = null,
    val plain_meaning: String? = null,
    val example: String? = null,
    val my_understanding: String? = null,
    val module_id: Int? = null,
    val topic_id: Int? = null
)

@Serializable
data class UnderstandingUpdate(val my_understanding: String)

@Serializable
data class SessionAct(val topic_id: Int, val activity_type: String, val duration_min: Int, val session_date: String)

@Serializable
data class ReviewLite(
    val card_id: Long,
    val result: String,
    val confidence: String? = null,
    val error_tag: String? = null
)

// ---- Mastery gate / coverage ---------------------------------------------

data class CoverageRow(
    val topic: Topic,
    val bestLevel: String?,
    val lastDate: String?,
    val quote: String?,
    val evidenceUrl: String?,
    val staleDays: Int?,
    val openWeak: Boolean,
    val covered: Boolean
)

data class GateCheck(val label: String, val ok: Boolean, val detail: String)

data class GateView(
    val module: Module,
    val status: String?,
    val rows: List<CoverageRow>,
    val checks: List<GateCheck>,
    val prereqs: List<Pair<Module, String?>>,
    // progress_items (tutor-written in Supabase) is the authoritative gate result. official =
    // status Mastered AND gate_result Pass. gateResultText is a short text for
    // the UI (null = no progress row for this module).
    val gateResultText: String? = null,
    val official: Boolean = false,
    // "Passed (tutor-graded)" | "Ready" | "Not ready"
    val readinessText: String = ""
)

data class InsightsData(
    val coach: List<String>,
    val phaseSkills: List<Pair<String, Float>>,
    val stale: List<Pair<String, Int>>,
    val tagCounts: List<Pair<String, Int>>
)

data class SearchHit(val kind: String, val title: String, val detail: String)

object Prereqs {
    // From curriculum_v2/04_PREREQUISITE_GRAPH.md. A module should not start
    // until every module listed here is Mastered (L4).
    val EDGES: Map<Int, List<Int>> = mapOf(
        2 to listOf(1), 3 to listOf(2), 4 to listOf(1),
        5 to listOf(3, 4), 6 to listOf(5), 7 to listOf(6),
        8 to listOf(7), 9 to listOf(7),
        10 to listOf(8, 9), 11 to listOf(10, 3), 12 to listOf(11, 9), 13 to listOf(12),
        14 to listOf(13), 15 to listOf(14), 16 to listOf(15, 7, 12), 17 to listOf(16),
        18 to listOf(17), 19 to listOf(18), 20 to listOf(19), 21 to listOf(20),
        22 to listOf(21), 23 to listOf(22)
    )
}

object Extras {
    // Small reference table (a handful of rows), read directly. Throws on failure.
    suspend fun listPhases(): Map<Int, String> =
        Supa.client.from("phases").select().decodeList<PhaseRow>().associate { it.id to it.name }

    suspend fun listAllWeakSpots(): List<WeakSpot> = Fetch.weakSpots()

    suspend fun libraryCounts(): LibraryCounts = coroutineScope {
        val g = async { listGlossary().size }
        val c = async { Fetch.cards().size }
        val p = async { listProjects().size }
        val i = async { listInterview().size }
        val m = async { listCommands().size }
        val b = async { listBandit().count { it.done } }
        LibraryCounts(g.await(), c.await(), p.await(), i.await(), m.await(), b.await())
    }

    private const val STALE_AFTER_DAYS = 21

    private fun todayDate(): LocalDate = localTodayDate()

    private fun levelNum(l: String?): Int = l?.drop(1)?.toIntOrNull() ?: 0

    // Whole LOCAL calendar days from the timestamp's local date to today.
    private fun daysSince(iso: String): Int? {
        val d = localDateOf(iso) ?: return null
        return (todayDate().toEpochDays().toLong() - d.toEpochDays().toLong()).toInt()
    }

    // A topic counts as L4+ only if its LATEST record is >= L4 AND its
    // hint_used is null or <= 2. If the latest record is a relapse (lower
    // level) or needed heavy hints (>= 4), the topic is capped at the best
    // level below L4 from its records. No records -> 0.
    internal fun effectiveLevel(recs: List<MasteryRecord>): Int {
        if (recs.isEmpty()) return 0
        val latest = recs.maxByOrNull { it.recorded_at } ?: return 0
        val hint = latest.hint_used
        val latestOk = levelNum(latest.level) >= 4 && (hint == null || hint <= 2)
        return if (latestOk) recs.maxOf { levelNum(it.level) }
        else recs.map { levelNum(it.level) }.filter { it < 4 }.maxOrNull() ?: 0
    }

    // ---- Bandit -----------------------------------------------------------
    suspend fun listBandit(): List<BanditLevel> = Fetch.banditLevels()

    suspend fun setBandit(level: Int, done: Boolean, note: String?) {
        Supa.client.from("bandit_levels").upsert(BanditLevel(level, done, note))
        Fetch.invalidate()
    }

    // ---- Command vault ----------------------------------------------------
    // Newest first (ids are increasing).
    suspend fun listCommands(): List<CommandItem> = Fetch.commands().sortedByDescending { it.id }

    suspend fun addCommand(c: CommandInsert) {
        Supa.client.from("commands").insert(c)
        Fetch.invalidate()
    }

    suspend fun deleteCommand(id: Long) {
        Supa.client.from("commands").delete { filter { eq("id", id) } }
        Fetch.invalidate()
    }

    // ---- Projects ---------------------------------------------------------
    suspend fun listProjects(): List<ProjectItem> =
        Fetch.projects().sortedBy { it.code ?: it.name }

    suspend fun updateProject(id: Long, u: ProjectUpdate) {
        Supa.client.from("projects_tracker").update(u) { filter { eq("id", id) } }
        Fetch.invalidate()
    }

    // ---- Interview --------------------------------------------------------
    suspend fun listInterview(): List<InterviewQ> = Fetch.interviewQuestions()

    suspend fun markInterview(id: Long, status: String) {
        Supa.client.from("interview_questions").update(
            InterviewUpdate(status, Clock.System.now().toString())
        ) { filter { eq("id", id) } }
        Fetch.invalidate()
    }

    // ---- Glossary ---------------------------------------------------------
    suspend fun listGlossary(): List<GlossaryTerm> =
        Fetch.glossary().sortedBy { it.term.lowercase() }

    suspend fun saveUnderstanding(id: Long, text: String) {
        Supa.client.from("glossary_terms").update(UnderstandingUpdate(text)) {
            filter { eq("id", id) }
        }
        Fetch.invalidate()
    }

    // ---- Streak forgiveness ----------------------------------------------
    // One planned rest day per rolling 7 days. Returns null on success or a
    // message explaining why it was refused.
    suspend fun takeRestDay(): String? {
        val rows = Fetch.streakDays()
        val today = todayDate()
        val weekStart = today.minus(DatePeriod(days = 6)).toString()
        if (rows.any { it.rest && it.day >= weekStart }) return "You already took a rest day in the last 7 days."
        if (rows.any { it.day == today.toString() && it.studied }) return "You already studied today - no rest day needed."
        Supa.client.from("streak_days").upsert(StreakDay(day = today.toString(), studied = false, rest = true))
        Fetch.invalidate()
        return null
    }

    // ---- Mastery gate + coverage + prerequisites --------------------------
    suspend fun loadGate(moduleId: Int): GateView? = coroutineScope {
        val modulesJob = async { Repository.listModules() }
        val topicsJob = async { Repository.listTopics() }
        val recordsJob = async { Repository.listMasteryRecords() }
        val weakJob = async { Repository.listOpenWeakSpots() }
        val statusJob = async { Repository.listModuleStatuses() }
        val sessionsJob = async { Fetch.sessionActs() }
        val logTopicsJob = async { Fetch.logTopics() }
        val progressJob = async { Fetch.progressItems() }
        val modules = modulesJob.await()
        val module = modules.firstOrNull { it.id == moduleId } ?: return@coroutineScope null
        val topics = topicsJob.await().filter { it.module_id == moduleId }
        val topicIds = topics.map { it.id }.toSet()
        val records = recordsJob.await().filter { it.topic_id in topicIds }
        val weak = weakJob.await().filter { it.module_id == moduleId || it.topic_id in topicIds }
        val statuses = statusJob.await()
        val sessions = sessionsJob.await().filter { it.topic_id in topicIds }
        val covered = (sessions.map { it.topic_id } + logTopicsJob.await().map { it.topic_id })
            .filter { it in topicIds }.toSet()

        val rows = topics.map { t ->
            val recs = records.filter { it.topic_id == t.id }
            val eff = effectiveLevel(recs)
            val latest = recs.maxByOrNull { it.recorded_at }
            val age = latest?.let { daysSince(it.recorded_at) }
            CoverageRow(
                topic = t,
                bestLevel = if (eff > 0) "L$eff" else null,
                lastDate = localDateStringOf(latest?.recorded_at),
                quote = latest?.evidence_quote,
                evidenceUrl = latest?.evidence_url,
                staleDays = if (age != null && eff >= 3 && age > STALE_AFTER_DAYS) age else null,
                openWeak = weak.any { it.topic_id == t.id },
                covered = t.id in covered
            )
        }

        val core = rows.filter { it.topic.is_core }.ifEmpty { rows }
        val coreOk = core.count { levelNum(it.bestLevel) >= 4 }
        val critical = rows.filter { it.topic.is_critical }
        // Critical topics: level >= 3 evidence required (label unchanged). The
        // module's real gate (L4 on core topics) is the check above.
        val critOk = critical.count { levelNum(it.bestLevel) >= 3 }
        val handsOn = sessions.count { it.activity_type in setOf("lab", "practice", "assessment") }
        val prereqs = (Prereqs.EDGES[moduleId] ?: emptyList()).mapNotNull { pid ->
            modules.firstOrNull { it.id == pid }?.let { it to statuses[pid] }
        }
        val prereqOk = prereqs.all { it.second == "Mastered" }

        val checks = listOf(
            GateCheck("Core topics at L4 or higher", core.isNotEmpty() && coreOk == core.size, "$coreOk / ${core.size}"),
            GateCheck("Critical topics have evidence", critOk == critical.size, "$critOk / ${critical.size}"),
            GateCheck("No open weak spots in this module", weak.isEmpty(), "${weak.size} open"),
            GateCheck("Hands-on work logged (lab / practice / assessment)", handsOn > 0, "$handsOn sessions"),
            GateCheck("Prerequisite modules Mastered", prereqOk, if (prereqs.isEmpty()) "none needed" else "${prereqs.count { it.second == "Mastered" }} / ${prereqs.size}")
        )

        // progress_items (written by the tutor) is authoritative for the gate result.
        val moduleItems = progressJob.await().filter { !it.superseded && it.module_id == moduleId }
        val officialItem = moduleItems.firstOrNull {
            it.status.equals("Mastered", ignoreCase = true) &&
                (it.gate_result?.trim()?.lowercase()?.startsWith("pass") == true)
        }
        val official = officialItem != null
        val shown = officialItem ?: moduleItems.firstOrNull { !it.status.isNullOrBlank() }
        val gateResultText: String? = when {
            official -> "Mastered \u00B7 Gate passed (tutor-graded)"
            shown != null ->
                (shown.status ?: "In progress") + " \u00B7 Gate: " +
                    (shown.gate_result?.takeIf { it.isNotBlank() } ?: "not recorded yet")
            else -> null
        }
        val readinessText = when {
            official -> "Passed (tutor-graded)"
            checks.all { it.ok } -> "Ready"
            else -> "Not ready"
        }
        GateView(module, statuses[moduleId], rows, checks, prereqs, gateResultText, official, readinessText)
    }

    // ---- Insights / coach / decay ----------------------------------------
    suspend fun loadInsights(): InsightsData = coroutineScope {
        val modulesJob = async { Repository.listModules() }
        val topicsJob = async { Repository.listTopics() }
        val recordsJob = async { Repository.listMasteryRecords() }
        val weakJob = async { Repository.listOpenWeakSpots() }
        val reviewsJob = async { Fetch.reviews() }
        val sessionsJob = async { Fetch.sessionActs() }
        val modules = modulesJob.await()
        val topics = topicsJob.await()
        val records = recordsJob.await()
        val reviews = reviewsJob.await()
        val sessions = sessionsJob.await()
        val weak = weakJob.await()

        val moduleById = modules.associateBy { it.id }
        val phaseRegex = Regex("^M(\\d+)\\.")
        fun phaseOf(t: Topic): Int? =
            moduleById[t.module_id]?.name?.let { phaseRegex.find(it)?.groupValues?.get(1)?.toIntOrNull() }

        val bestByTopic = records.groupBy { it.topic_id }
            .mapValues { (_, l) -> effectiveLevel(l) }
        val phaseSkills = topics.groupBy { phaseOf(it) }
            .filterKeys { it != null }
            .entries.sortedBy { it.key ?: 0 }
            .map { (ph, ts) ->
                val ok = ts.count { (bestByTopic[it.id] ?: 0) >= 3 }
                "Phase $ph" to (ok.toFloat() / ts.size.toFloat())
            }

        val topicName = topics.associate { it.id to it.name }
        val latestByTopic = records.groupBy { it.topic_id }
            .mapValues { (_, l) -> l.maxByOrNull { it.recorded_at }!! }
        val stale = latestByTopic.mapNotNull { (tid, r) ->
            val age = daysSince(r.recorded_at)
            if (age != null && age > STALE_AFTER_DAYS && (bestByTopic[tid] ?: 0) >= 3)
                (topicName[tid] ?: "Topic $tid") to age else null
        }.sortedByDescending { it.second }

        val tagCounts = reviews.mapNotNull { it.error_tag }.groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }.map { it.key to it.value }

        val coach = mutableListOf<String>()
        val confident = reviews.filter { it.confidence == "sure" || it.confidence == "certain" }
        val overconfident = confident.count { it.result == "again" || it.result == "hard" }
        if (confident.size >= 5 && overconfident * 4 >= confident.size) {
            coach += "You marked $overconfident of ${confident.size} confident answers as Again/Hard. Slow down and recall before you peek."
        }
        tagCounts.firstOrNull()?.let { (tag, n) ->
            val label = when (tag) {
                "concept" -> "not understanding the idea"
                "recall" -> "not recalling it"
                "careless" -> "careless slips"
                else -> "vocabulary"
            }
            if (n >= 3) coach += "Your most common miss is $label ($n times). Target that first."
        }
        val today = todayDate()
        fun minutesBetween(from: Int, to: Int) = sessions.filter {
            val d = localDateOf(it.session_date)
            d != null && d >= today.minus(DatePeriod(days = to)) && d < today.minus(DatePeriod(days = from))
        }.sumOf { it.duration_min }
        val thisWeek = minutesBetween(-1, 7)
        val lastWeek = minutesBetween(7, 14)
        if (lastWeek > 0 && thisWeek < lastWeek / 2) coach += "Study time dropped: $thisWeek min this week vs $lastWeek min last week."
        if (thisWeek == 0) coach += "No study logged in the last 7 days. One 25-minute session restarts the streak."
        val overdueWeak = weak.count {
            val d = localDateOf(it.revisit_date)
            d != null && d <= today
        }
        if (overdueWeak > 0) coach += "$overdueWeak weak spot(s) are overdue for revisit."
        if (stale.isNotEmpty()) coach += "${stale.size} topic(s) have not been re-checked in over $STALE_AFTER_DAYS days and may have decayed."
        if (coach.isEmpty()) coach += "Nothing alarming. Keep the daily revision habit going."

        InsightsData(coach, phaseSkills, stale, tagCounts)
    }

    // ---- Global search ----------------------------------------------------
    suspend fun search(q: String): List<SearchHit> = coroutineScope {
        val query = q.trim()
        if (query.length < 2) return@coroutineScope emptyList()
        val topicsJob = async { Repository.listTopics() }
        val glossaryJob = async { listGlossary() }
        val commandsJob = async { listCommands() }
        val cardsJob = async { Fetch.cards() }
        val hits = mutableListOf<SearchHit>()
        topicsJob.await().filter { it.name.contains(query, true) }
            .take(10).forEach { hits += SearchHit("Topic", it.name, "") }
        glossaryJob.await().filter {
            it.term.contains(query, true) || it.definition.contains(query, true)
        }.take(10).forEach { hits += SearchHit("Glossary", it.term, it.definition.take(90)) }
        commandsJob.await().filter {
            it.title.contains(query, true) || it.command.contains(query, true)
        }.take(10).forEach { hits += SearchHit("Command", it.title, it.command.take(90)) }
        cardsJob.await().filter {
            it.question.contains(query, true) || it.answer.contains(query, true)
        }.take(10).forEach { hits += SearchHit("Card", it.question.take(90), it.answer.take(90)) }
        hits
    }
}
