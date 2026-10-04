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
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// All reads/writes go through here. Screens stay dumb; the rules about what
// counts as "due", what a streak is, and what today's target should be all
// live in one place.
//
// Performance note: every screen load used to fire its queries one after
// another — Home alone was ~14 sequential round trips, with topics fetched
// three times. Now independent queries go out together and the curriculum
// tables (which only change when new content is seeded) are cached for the
// life of the process.
object Repository {

    private val backupJson = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    // Device-local date (Asia/Kolkata on the phone) — never the UTC date.
    private fun today(): String = localTodayString()

    // Every write ends here: drop the shared read cache (and the Learner
    // model cache) so the next screen sees the new data.
    private fun afterWrite() {
        Fetch.invalidate()
        Learner.invalidate()
    }

    // ---- Reference data cache ---------------------------------------------
    // topics and modules are curriculum structure: 174 + 23 rows that don't
    // change while the app is open. Re-downloading them on every screen was
    // most of the perceived lag.

    @Volatile private var cachedTopics: List<Topic>? = null
    @Volatile private var cachedModules: List<Module>? = null

    // Throws on failure (no more "empty list" masquerading as data); only a
    // non-empty result is cached for the life of the process.
    suspend fun listTopics(): List<Topic> {
        cachedTopics?.let { return it }
        val fetched = Fetch.topics()
        if (fetched.isNotEmpty()) cachedTopics = fetched
        return fetched
    }

    suspend fun listModules(): List<Module> {
        cachedModules?.let { return it }
        val fetched = Fetch.modules()
        if (fetched.isNotEmpty()) cachedModules = fetched
        return fetched
    }

    // Called after anything that could change the curriculum itself.
    fun invalidateReferenceCache() {
        cachedTopics = null
        cachedModules = null
    }

    // ---- Raw fetches ------------------------------------------------------
    // Deliberately small and side-effect free so callers can run them in
    // parallel and share the results.

    // Thin wrappers over Fetch (paged, cached 20 s, THROW on failure).

    private suspend fun fetchSessions(): List<StudySessionProgress> = Fetch.sessionProgress()

    private suspend fun fetchMasteryTopicIds(): List<MasteryTopicIdOnly> =
        Fetch.masteryRecords().map { MasteryTopicIdOnly(it.topic_id) }

    private suspend fun fetchCards(): List<RevisionCard> = Fetch.cards()

    private suspend fun fetchReviews(): List<RevisionReview> = Fetch.reviews()

    private suspend fun fetchOpenWeakSpots(): List<WeakSpot> = Fetch.openWeakSpots()

    private suspend fun fetchLogTopics(): List<DailyLogTopic> = Fetch.logTopics()

    private suspend fun fetchDailyLogs(): List<DailyLog> = Fetch.dailyLogs()

    private suspend fun fetchProgressItems(): List<ProgressItem> = Fetch.progressItems()

    private suspend fun fetchAllWeakSpots(): List<WeakSpot> = Fetch.weakSpots()

    // Genuinely optional: a missing/failed target just falls back to the
    // derived one. (Cancellation is still rethrown by orDefault.)
    private suspend fun fetchTodaysTarget(todayStr: String): DailyTarget? =
        Fetch.orDefault<DailyTarget?>(null) {
            Supa.client.from("daily_targets")
                .select() { filter { eq("target_date", todayStr) } }
                .decodeSingleOrNull<DailyTarget>()
        }

    // ---- Home dashboard ---------------------------------------------------

    suspend fun loadHomeDashboard(): HomeDashboard = coroutineScope {
        val todayStr = today()
        val todayDate = LocalDate.parse(todayStr)
        val weekStart = todayDate.minus(DatePeriod(days = 6)).toString()

        // All eight of these leave at once instead of queueing behind
        // each other.
        val targetJob = async { fetchTodaysTarget(todayStr) }
        val cardsJob = async { fetchCards() }
        val reviewsJob = async { fetchReviews() }
        val sessionsJob = async { fetchSessions() }
        val masteryJob = async { fetchMasteryTopicIds() }
        val weakSpotsJob = async { fetchOpenWeakSpots() }
        val streakJob = async { Fetch.streakDays() }
        val topicsJob = async { listTopics() }
        val modulesJob = async { listModules() }
        val logTopicsJob = async { fetchLogTopics() }

        val sessions = sessionsJob.await()
        val masteryTopicIds = masteryJob.await()
        val topics = topicsJob.await()

        val studiedSet = studiedTopicIds(sessions, masteryTopicIds, logTopicsJob.await())
        val dueCards = assembleDueCards(
            cards = cardsJob.await(),
            reviews = reviewsJob.await(),
            studiedTopicIds = studiedSet,
            topics = topics,
            todayStr = todayStr
        )

        val streak = streakJob.await()
        val studiedDates = streak.filter { it.studied }.map { it.day }.toSet()
        val restDates = streak.filter { it.rest }.map { it.day }.toSet()
        val weekSessions = sessions.filter { it.session_date >= weekStart }

        // Overdue is computed over the FULL open list, before take(3).
        val openWeak = weakSpotsJob.await()
        val overdueWeak = openWeak
            .filter { w -> w.revisit_date?.let { it.isNotBlank() && it <= todayStr } == true }
            .sortedBy { it.revisit_date }

        HomeDashboard(
            todaysTarget = resolveDailyTarget(
                storedTarget = targetJob.await()?.target_text,
                dueCount = dueCards.size,
                topics = topics,
                modules = modulesJob.await(),
                studiedTopicIds = studiedSet
            ),
            dueRevisionCount = dueCards.size,
            nextRevision = dueCards.firstOrNull()?.let { due ->
                NextRevision(
                    topicName = due.topicName,
                    question = due.card.question,
                    dueDate = due.lastReview?.next_review_date ?: todayStr
                )
            },
            currentStreak = computeStreak(studiedDates, todayDate, restDates),
            last7Days = buildLastNDays(todayDate, studiedDates, 7),
            weekMinutes = weekSessions.sumOf { it.duration_min },
            weekSessionCount = weekSessions.size,
            topWeakSpots = openWeak.take(3),
            mastery = MasterySummary(
                assessedCount = masteryTopicIds.map { it.topic_id }.distinct().size,
                totalCount = topics.size
            ),
            overdueWeakSpotCount = overdueWeak.size,
            overdueWeakSpot = overdueWeak.firstOrNull()
        )
    }

    // ---- Revision / spaced repetition -------------------------------------
    //
    // A card only enters rotation once its topic has actually been studied —
    // a study session or a mastery record on it. Without that rule the whole
    // seeded question bank would come due on day one.

    suspend fun loadDueCards(): List<DueCard> = coroutineScope {
        val todayStr = today()

        val cardsJob = async { fetchCards() }
        val reviewsJob = async { fetchReviews() }
        val sessionsJob = async { fetchSessions() }
        val masteryJob = async { fetchMasteryTopicIds() }
        val topicsJob = async { listTopics() }
        val logTopicsJob = async { fetchLogTopics() }

        assembleDueCards(
            cards = cardsJob.await(),
            reviews = reviewsJob.await(),
            studiedTopicIds = studiedTopicIds(
                sessionsJob.await(), masteryJob.await(), logTopicsJob.await()
            ),
            topics = topicsJob.await(),
            todayStr = todayStr
        )
    }

    private fun studiedTopicIds(
        sessions: List<StudySessionProgress>,
        mastery: List<MasteryTopicIdOnly>,
        logTopics: List<DailyLogTopic>
    ): Set<Int> = (
        sessions.map { it.topic_id } +
            mastery.map { it.topic_id } +
            logTopics.map { it.topic_id }
        ).toSet()

    // At most this many never-reviewed cards join the queue per day, so a
    // freshly imported bank (or a big study day) can't bury you in 40 cards.
    private const val MAX_NEW_CARDS_PER_DAY = 5

    private fun assembleDueCards(
        cards: List<RevisionCard>,
        reviews: List<RevisionReview>,
        studiedTopicIds: Set<Int>,
        topics: List<Topic>,
        todayStr: String
    ): List<DueCard> {
        if (cards.isEmpty()) return emptyList()
        val topicNames = topics.associate { it.id to it.name }
        val latestByCard = reviews
            .groupBy { it.card_id }
            .mapValues { (_, list) -> list.maxByOrNull { it.reviewed_at } }

        val lapsesByCard = reviews.filter { it.result == "again" }
            .groupingBy { it.card_id }.eachCount()

        val due = cards
            .filter { it.topic_id in studiedTopicIds }
            .mapNotNull { card ->
                val last = latestByCard[card.id]
                val isDue = if (last != null) last.next_review_date <= todayStr
                else (card.due_date ?: todayStr) <= todayStr
                if (!isDue) null
                else DueCard(
                    card = card,
                    topicName = topicNames[card.topic_id] ?: "Topic #${card.topic_id}",
                    lastReview = last,
                    lapses = lapsesByCard[card.id] ?: 0
                )
            }

        // Cards already in rotation first (oldest due first), then a capped
        // number of brand-new ones.
        val reviewed = due.filter { it.lastReview != null }
            .sortedBy { it.lastReview!!.next_review_date }
        // The daily cap counts cards whose FIRST review happened today: once
        // a new card has been reviewed it is no longer "fresh", so without
        // this the cap refilled after every review (Revise showed 6 again
        // right after 4 new cards were done).
        val introducedToday = reviews
            .groupBy { it.card_id }
            .count { (_, list) ->
                val first = list.minByOrNull { it.reviewed_at } ?: return@count false
                localDateStringOf(first.reviewed_at) == todayStr
            }
        val newAllowed = (MAX_NEW_CARDS_PER_DAY - introducedToday).coerceAtLeast(0)
        val fresh = due.filter { it.lastReview == null }
            .sortedBy { it.card.due_date ?: "" }
            .take(newAllowed)
        return reviewed + fresh
    }

    // Simplified SM-2. The previous interval is recovered from the last
    // review, so nothing extra has to be stored on the card.
    fun nextReviewDate(result: String, lastReview: RevisionReview?): String {
        val todayDate = LocalDate.parse(today())

        // reviewed_at is stored UTC; the interval was originally measured
        // from the local date, so convert before subtracting. Taking the
        // first 10 chars would inflate every interval by a day for anyone
        // reviewing after midnight local time.
        val prevInterval = if (lastReview == null) 0 else {
            runCatching {
                val reviewedLocal = localDateOf(lastReview.reviewed_at)!!
                val next = LocalDate.parse(lastReview.next_review_date)
                next.toEpochDays() - reviewedLocal.toEpochDays()
            }.getOrDefault(0)
        }

        val days = when (result) {
            "again" -> 1
            "hard" -> maxOf(1, (prevInterval * 1.2).toInt())
            "good" -> if (prevInterval <= 0) 3 else (prevInterval * 2.5).toInt()
            "easy" -> if (prevInterval <= 0) 5 else (prevInterval * 4).toInt()
            else -> 1
        }.coerceIn(1, 180)

        return todayDate.plus(DatePeriod(days = days)).toString()
    }

    // Days until this card would come back if rated `result` right now
    // (shown on the rating buttons).
    fun previewDays(result: String, lastReview: RevisionReview?): Int =
        LocalDate.parse(nextReviewDate(result, lastReview)).toEpochDays() -
            LocalDate.parse(today()).toEpochDays()

    suspend fun submitReview(
        card: DueCard,
        result: String,
        confidence: String? = null,
        errorTag: String? = null
    ) {
        Supa.client.from("revision_reviews").insert(
            RevisionReviewInsert(
                card_id = card.card.id,
                result = result,
                next_review_date = nextReviewDate(result, card.lastReview),
                confidence = confidence,
                // Only meaningful when it went badly.
                error_tag = if (result == "again" || result == "hard") errorTag else null
            )
        )
        afterWrite()
    }

    suspend fun addRevisionCard(entry: RevisionCardInsert) {
        Supa.client.from("revision_cards").insert(entry)
        afterWrite()
    }

    suspend fun addWeakSpot(entry: WeakSpotInsert) {
        Supa.client.from("weak_spots").insert(entry)
        afterWrite()
    }

    // ---- Daily target -----------------------------------------------------
    // Whatever is stored for today wins, so it can still be set deliberately
    // during a tutoring session; otherwise it's derived from real state.

    private fun resolveDailyTarget(
        storedTarget: String?,
        dueCount: Int,
        topics: List<Topic>,
        modules: List<Module>,
        studiedTopicIds: Set<Int>
    ): String {
        if (storedTarget != null) return storedTarget
        if (dueCount > 0) return "Revise $dueCount card${if (dueCount == 1) "" else "s"}"

        // Roadmap order = phase first, then module inside the phase.
        val moduleOrder = modules.associate { it.id to it.phase_id * 100 + it.order_num }
        val next = topics
            .filter { it.id !in studiedTopicIds }
            .minByOrNull { (moduleOrder[it.module_id] ?: 9999) * 10000 + it.id }

        return next?.let { "Study: ${it.name}" } ?: "Everything assessed — pick a project"
    }

    // ---- Streak -----------------------------------------------------------

    // Rest days (a planned day off) neither add to nor break the streak.
    // If today has not been studied yet the streak is still alive: counting
    // starts from yesterday, so it never drops to 0 every morning. Home and
    // Progress both call this with Fetch.streakDays() (all rows, no cap).
    fun computeStreak(
        studiedDates: Set<String>,
        todayDate: LocalDate,
        restDates: Set<String> = emptySet()
    ): Int {
        var count = 0
        var cursor = todayDate
        if (!studiedDates.contains(cursor.toString())) {
            cursor = cursor.minus(DatePeriod(days = 1))
        }
        while (true) {
            val key = cursor.toString()
            if (studiedDates.contains(key)) count++
            else if (!restDates.contains(key)) break
            cursor = cursor.minus(DatePeriod(days = 1))
        }
        return count
    }

    private fun buildLastNDays(todayDate: LocalDate, studiedDates: Set<String>, n: Int): List<DayStatus> =
        (n - 1 downTo 0).map { offset ->
            val d = todayDate.minus(DatePeriod(days = offset))
            val dateStr = d.toString()
            DayStatus(
                date = dateStr,
                label = d.dayOfWeek.name.take(2),
                studied = studiedDates.contains(dateStr),
                isToday = offset == 0
            )
        }

    // ---- Writes -----------------------------------------------------------

    // Two non-atomic writes, so the ORDER matters: the streak upsert is
    // idempotent (same day key), the session insert is not. Doing the upsert
    // first means a failure/retry can never leave a duplicated session with a
    // missing streak day: if the insert fails after the upsert, retrying just
    // re-upserts the same row and inserts the session once.
    suspend fun logStudySession(entry: StudySessionInsert) {
        val day = today()
        Supa.client.from("streak_days").upsert(StreakDay(day = day, studied = true, rest = false))
        Supa.client.from("study_sessions").insert(entry.copy(session_date = day))
        afterWrite()
    }

    suspend fun listOpenWeakSpots(): List<WeakSpot> = fetchOpenWeakSpots()

    suspend fun clearWeakSpot(id: Long) {
        Supa.client.from("weak_spots")
            .update(ClearWeakSpotPayload(cleared_at = Clock.System.now().toString())) {
                filter { eq("id", id) }
            }
        afterWrite()
    }

    // mastery_records is append-only by design (the DB grants SELECT +
    // INSERT only) — there is deliberately no update or delete here.
    suspend fun listMasteryRecords(): List<MasteryRecord> = Fetch.masteryRecords()

    suspend fun recordMastery(entry: MasteryRecordInsert) {
        Supa.client.from("mastery_records").insert(entry)
        afterWrite()
    }

    // ---- Mastery screen ---------------------------------------------------

    suspend fun loadMasteryView(): Triple<List<Module>, List<Topic>, List<MasteryRecord>> =
        coroutineScope {
            val modulesJob = async { listModules() }
            val topicsJob = async { listTopics() }
            val recordsJob = async { listMasteryRecords() }
            Triple(modulesJob.await(), topicsJob.await(), recordsJob.await())
        }

    // module id -> status ("Mastered", "In progress", ...) from progress_items,
    // ignoring rows the Notion import marked superseded.
    suspend fun listModuleStatuses(): Map<Int, String> =
        fetchProgressItems()
            .filter { !it.superseded && it.module_id != null && !it.status.isNullOrBlank() }
            .associate { it.module_id!! to it.status!! }

    // ---- Chapter detail ---------------------------------------------------

    suspend fun loadModuleDetail(moduleId: Int): ModuleDetail? = coroutineScope {
        val modulesJob = async { listModules() }
        val topicsJob = async { listTopics() }
        val logsJob = async { fetchDailyLogs() }
        val logTopicsJob = async { fetchLogTopics() }
        val recordsJob = async { listMasteryRecords() }
        val weakJob = async { fetchAllWeakSpots() }
        val cardsJob = async { fetchCards() }
        val reviewsJob = async { fetchReviews() }
        val progressJob = async { fetchProgressItems() }
        val sessionsJob = async { fetchSessions() }

        val module = modulesJob.await().firstOrNull { it.id == moduleId }
            ?: return@coroutineScope null
        val topics = topicsJob.await().filter { it.module_id == moduleId }
        val topicIds = topics.map { it.id }.toSet()
        val allLogs = logsJob.await()
        val logTopics = logTopicsJob.await().filter { it.topic_id in topicIds }
        val logsById = allLogs.associateBy { it.id }

        val moduleLogIds = logTopics.map { it.daily_log_id }.toSet()
        val logs = allLogs.filter { it.module_id == moduleId || it.id in moduleLogIds }

        val coveredNotes = logTopics
            .groupBy { it.topic_id }
            .mapValues { (_, list) ->
                list.mapNotNull { lt ->
                    val day = logsById[lt.daily_log_id]?.study_day ?: return@mapNotNull null
                    "Day $day" + (lt.covered_note?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: "")
                }
            }

        val latestByCard = reviewsJob.await()
            .groupBy { it.card_id }
            .mapValues { (_, l) -> l.maxByOrNull { it.reviewed_at } }
        val topicNames = topics.associate { it.id to it.name }
        val cards = cardsJob.await()
            .filter { it.topic_id in topicIds }
            .map { c ->
                val last = latestByCard[c.id]
                CardStatus(
                    card = c,
                    topicName = topicNames[c.topic_id] ?: "",
                    lastResult = last?.result,
                    nextDue = last?.next_review_date ?: c.due_date
                )
            }

        ModuleDetail(
            module = module,
            progress = progressJob.await()
                .filter { it.module_id == moduleId && !it.superseded }
                .maxByOrNull { if (it.item_type == "Module") 1 else 0 },
            logs = logs,
            topics = topics,
            coveredNotes = coveredNotes,
            coveredTopicIds = logTopics.map { it.topic_id }.toSet(),
            records = recordsJob.await().filter { it.topic_id in topicIds },
            weakSpots = weakJob.await().filter { it.topic_id in topicIds },
            cards = cards,
            minutes = sessionsJob.await().filter { it.topic_id in topicIds }.sumOf { it.duration_min }
        )
    }

    // ---- Progress screen --------------------------------------------------

    suspend fun loadProgressData(): ProgressData = coroutineScope {
        val todayStr = today()
        val todayDate = LocalDate.parse(todayStr)

        val sessionsJob = async { fetchSessions() }
        val streakJob = async { Fetch.streakDays() }
        val topicsJob = async { listTopics() }

        val sessions = sessionsJob.await()
        val topicNameById = topicsJob.await().associate { it.id to it.name }

        val timeByTopic = sessions
            .groupBy { it.topic_id }
            .map { (topicId, list) ->
                TopicTime(
                    topicName = topicNameById[topicId] ?: "Topic #$topicId",
                    minutes = list.sumOf { it.duration_min }
                )
            }
            .sortedByDescending { it.minutes }

        val streakRows = streakJob.await()
        val studiedDates = streakRows.filter { it.studied }.map { it.day }.toSet()
        val restDates = streakRows.filter { it.rest }.map { it.day }.toSet()

        ProgressData(
            totalMinutes = sessions.sumOf { it.duration_min },
            totalSessions = sessions.size,
            currentStreak = computeStreak(studiedDates, todayDate, restDates),
            last30Days = buildLastNDays(todayDate, studiedDates, 30),
            timeByTopic = timeByTopic
        )
    }

    // ---- Backup -----------------------------------------------------------

    // Full backup. Reads bypass the cache and page through every table; any
    // failure throws, so a partial/empty backup is never written silently.
    suspend fun exportAll(): String = coroutineScope {
        Fetch.invalidate()
        val topicsJob = async { Fetch.topics() }
        val sessionsJob = async { Fetch.sessions().sortedByDescending { it.session_date } }
        val weakSpotsJob = async { Fetch.weakSpots() }
        val masteryJob = async { Fetch.masteryRecords() }
        val streakJob = async { Fetch.streakDays() }
        val cardsJob = async { Fetch.cards() }
        val reviewsJob = async { Fetch.reviews() }
        val logsJob = async { Fetch.dailyLogs() }
        val logTopicsJob = async { Fetch.logTopics() }
        val progressJob = async { Fetch.progressItems() }
        val glossaryJob = async { Fetch.glossary() }
        val commandsJob = async { Fetch.commands() }
        val banditJob = async { Fetch.banditLevels() }
        val projectsJob = async { Fetch.projects() }
        val interviewJob = async { Fetch.interviewQuestions() }

        backupJson.encodeToString(
            BackupBundle(
                exported_at = Clock.System.now().toString(),
                topics = topicsJob.await(),
                study_sessions = sessionsJob.await(),
                weak_spots = weakSpotsJob.await(),
                mastery_records = masteryJob.await(),
                streak_days = streakJob.await(),
                revision_cards = cardsJob.await(),
                revision_reviews = reviewsJob.await(),
                daily_logs = logsJob.await(),
                daily_log_topics = logTopicsJob.await(),
                progress_items = progressJob.await(),
                glossary_terms = glossaryJob.await(),
                commands = commandsJob.await(),
                bandit_levels = banditJob.await(),
                projects_tracker = projectsJob.await(),
                interview_questions = interviewJob.await()
            )
        )
    }
}

@Serializable
private data class ClearWeakSpotPayload(val cleared_at: String)
