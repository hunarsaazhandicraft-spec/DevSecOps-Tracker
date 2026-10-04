package com.devsecops.tracker.data

import com.devsecops.tracker.Supa
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

// Learner model + "Continue" policy. Everything is computed on the device
// from rows the app already reads. Nothing is written to the database.

enum class Energy { LOW, OK, HIGH }

enum class ActionKind { REVISE, WEAK_SPOT, STUDY_TOPIC, RECALL_WARMUP, REST }

data class ContinueAction(
    val kind: ActionKind,
    val title: String,
    val minutes: Int,
    // Route string understood by TrackerNavHost: "revise", "weak_spots",
    // "planner", "focus", "module/{id}".
    val route: String,
    val why: String,
    val topicId: Int? = null,
    val cardCount: Int = 0
)

data class CardStat(
    val card: RevisionCard,
    val topicId: Int,
    val topicName: String,
    val reviewCount: Int,
    val lapses: Int,
    val lastResult: String?,
    val lastConfidence: String?,
    val lastReviewedOn: LocalDate?,
    val dueOn: LocalDate,
    val overdueDays: Int,
    val isDue: Boolean,
    val isNew: Boolean,
    // Estimated chance you could recall it today (null = never reviewed).
    val retrievability: Double?,
    val stabilityDays: Double?,
    val leech: Boolean,
    val confidentMisses: Int,
    val lastWasConfidentMiss: Boolean,
    val risk: Double,
    val highRisk: Boolean
)

data class TopicStat(
    val topic: Topic,
    val moduleId: Int,
    val studied: Boolean,
    val level: Int,                       // best recorded level, 0 = none
    val latestLevel: Int,
    val hintAvg: Double?,                 // avg hint_used of latest records, null = unknown
    val retrievability: Double?,          // avg over reviewed cards, null = no reviewed cards
    val cardCount: Int,
    val dueCount: Int,
    val maxOverdueDays: Int,
    val errorTags: Map<String, Int>,
    val confidentMisses: Int,
    val ratedCount: Int,
    val accuracy: Float?,
    val daysSinceTouch: Int?,
    val openWeakSpots: Int,
    val why: String
)

data class CalibrationBucket(
    val key: String,        // guess | sure | certain
    val label: String,      // Low | Medium | High
    val expected: Float,    // what that confidence claims
    val total: Int,
    val correct: Int
) {
    val accuracy: Float? get() = if (total == 0) null else correct.toFloat() / total.toFloat()
}

data class Calibration(
    val buckets: List<CalibrationBucket>,
    val totalRated: Int,
    val overconfident: Boolean,
    val underconfident: Boolean,
    val message: String,
    val why: String
)

data class LearnerModel(
    val today: LocalDate,
    val topics: List<TopicStat>,
    val cards: List<CardStat>,
    val openWeakSpots: List<WeakSpot>,
    val moduleNames: Map<Int, String>,
    val moduleStatus: Map<Int, String>,
    val moduleOrder: Map<Int, Int>,
    val calibration: Calibration
) {
    val dueCards: List<CardStat> get() = cards.filter { it.isDue }
    val hasData: Boolean get() = topics.isNotEmpty()
}

data class BacklogDay(
    val day: Int,
    val label: String,
    val cards: Int,
    val mustKeep: Int,
    val regular: Int
)

data class BacklogPlan(
    val totalDue: Int,
    val maxOverdueDays: Int,
    val mustKeep: Int,
    val regular: Int,
    val deferrable: Int,
    val mustKeepTopics: List<String>,
    val days: List<BacklogDay>,
    val why: String
)

object Learner {
    private val mutex = Mutex()
    @Volatile private var cache: LearnerModel? = null
    @Volatile private var cacheAt: Long = 0L
    private const val CACHE_MS = 20_000L

    private const val LEECH_LAPSES = 3
    private const val STALE_AFTER_DAYS = 21
    private const val BACKLOG_DUE = 25
    private const val BACKLOG_OVERDUE_DAYS = 7
    private const val MAX_CARDS_PER_CATCHUP_DAY = 20

    fun invalidate() {
        cache = null
    }

    suspend fun loadModel(force: Boolean = false): LearnerModel = mutex.withLock {
        val c = cache
        val now = System.currentTimeMillis()
        if (!force && c != null && now - cacheAt < CACHE_MS) return@withLock c
        val fresh = build()
        if (fresh.hasData) {
            cache = fresh
            cacheAt = now
        }
        fresh
    }

    // ---- Small helpers ----------------------------------------------------

    private fun daysBetween(later: LocalDate, earlier: LocalDate): Int =
        (later.toEpochDays() - earlier.toEpochDays()).toInt()

    private fun levelNum(l: String?): Int = l?.drop(1)?.toIntOrNull() ?: 0

    private fun isConfident(c: String?): Boolean = c == "sure" || c == "certain"
    private fun isMiss(result: String?): Boolean = result == "again" || result == "hard"
    private fun isHit(result: String?): Boolean = result == "good" || result == "easy"

    private fun plural(n: Int, word: String): String = "$n $word" + if (n == 1) "" else "s"

    private fun tagLabel(tag: String): String = when (tag) {
        "concept" -> "not understanding the idea"
        "recall" -> "not recalling it"
        "careless" -> "careless slips"
        else -> "vocabulary"
    }

    // ---- Build ------------------------------------------------------------

    private suspend fun build(): LearnerModel = coroutineScope {
        val topicsJob = async { Repository.listTopics() }
        val modulesJob = async { Repository.listModules() }
        val recordsJob = async { Repository.listMasteryRecords() }
        val weakJob = async { Repository.listOpenWeakSpots() }
        val statusJob = async { Repository.listModuleStatuses() }
        // Fetch.* pages through every row, is cached briefly, and THROWS on
        // failure (async children propagate through coroutineScope).
        val cardsJob = async { Fetch.cards() }
        val reviewsJob = async { Fetch.reviews() }
        val sessionsJob = async { Fetch.sessionActs() }
        val logsJob = async { Fetch.dailyLogs() }
        val logTopicsJob = async { Fetch.logTopics() }
        compute(
            topics = topicsJob.await(),
            modules = modulesJob.await(),
            records = recordsJob.await(),
            weak = weakJob.await(),
            statuses = statusJob.await(),
            cards = cardsJob.await(),
            reviews = reviewsJob.await(),
            sessions = sessionsJob.await(),
            logs = logsJob.await(),
            logTopics = logTopicsJob.await()
        )
    }

    private fun compute(
        topics: List<Topic>,
        modules: List<Module>,
        records: List<MasteryRecord>,
        weak: List<WeakSpot>,
        statuses: Map<Int, String>,
        cards: List<RevisionCard>,
        reviews: List<RevisionReview>,
        sessions: List<SessionAct>,
        logs: List<DailyLog>,
        logTopics: List<DailyLogTopic>
    ): LearnerModel {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val topicById = topics.associateBy { it.id }
        val moduleNames = modules.associate { it.id to it.name }
        val moduleOrder = modules.associate { it.id to it.phase_id * 100 + it.order_num }
        val openWeak = weak.filter { it.cleared_at == null }
        val weakTopicIds = openWeak.map { it.topic_id }.toSet()

        val studiedIds: Set<Int> = DueRules.studiedTopicIds(
            sessions.map { it.topic_id },
            records.map { it.topic_id },
            logTopics.map { it.topic_id }
        )

        val reviewsByCard = reviews.groupBy { it.card_id }
        // ONE due rule (DueRules == what Revise shows) and ONE retrievability
        // (FSRS replay, same as Memory).
        val latestByCard = DueRules.latestReviewByCard(reviews)
        val dueIds: Set<Long> = DueRules.dueCardIds(cards, reviews, studiedIds, today).toSet()
        val memByCard = Memory.replayByCard(reviews)

        // ---- Cards ---------------------------------------------------------
        val base = cards.map { card ->
            val rs = reviewsByCard[card.id].orEmpty().sortedBy { it.reviewed_at }
            val last = latestByCard[card.id]
            val lastOn = localDateOf(last?.reviewed_at)
            val nextOn = if (last != null) localDateOf(last.next_review_date) else localDateOf(card.due_date)
            val dueOn = nextOn ?: today
            val mem = memByCard[card.id]
            // FSRS power curve on whole local days (see Fsrs.retrievabilityOn).
            val retr: Double? = if (mem != null && !mem.isNew) Fsrs.retrievabilityOn(mem, today) else null
            val lapses = rs.count { it.result == "again" }
            val confMisses = rs.count { isConfident(it.confidence) && isMiss(it.result) }
            val lastConfMiss = last != null && isConfident(last.confidence) && isMiss(last.result)
            val topic = topicById[card.topic_id]
            val overdue = max(0, daysBetween(today, dueOn))
            val openWk = card.topic_id in weakTopicIds
            val leech = lapses >= LEECH_LAPSES
            val critical = topic?.is_critical == true
            val risk = lapses * 1.0 +
                confMisses * 1.5 +
                (if (openWk) 2.0 else 0.0) +
                (if (critical) 1.5 else 0.0) +
                (if (topic?.is_core == true) 0.5 else 0.0) +
                (if (retr != null) (1.0 - retr) * 3.0 else 0.0) +
                min(2.0, overdue * 0.1)
            val high = leech || lastConfMiss ||
                (openWk && (retr ?: 1.0) < 0.8) ||
                (critical && (retr ?: 1.0) < 0.6)
            CardStat(
                card = card,
                topicId = card.topic_id,
                topicName = topic?.name ?: "Topic #${card.topic_id}",
                reviewCount = rs.size,
                lapses = lapses,
                lastResult = last?.result,
                lastConfidence = last?.confidence,
                lastReviewedOn = lastOn,
                dueOn = dueOn,
                overdueDays = overdue,
                isDue = card.id in dueIds,
                isNew = last == null,
                retrievability = retr,
                stabilityDays = mem?.takeIf { !it.isNew }?.stability,
                leech = leech,
                confidentMisses = confMisses,
                lastWasConfidentMiss = lastConfMiss,
                risk = risk,
                highRisk = high
            )
        }

        // Daily new-card cap is already applied inside DueRules.dueCardIds.
        val cardStats = base

        // ---- Topics --------------------------------------------------------
        val logDateById = logs.associate { it.id to localDateOf(it.log_date) }
        val cardsByTopic = cardStats.groupBy { it.topicId }
        val recordsByTopic = records.groupBy { it.topic_id }
        val sessionsByTopic = sessions.groupBy { it.topic_id }
        val logTopicsByTopic = logTopics.groupBy { it.topic_id }
        val weakCountByTopic = openWeak.groupingBy { it.topic_id }.eachCount()
        val cardTopic = cards.associate { it.id to it.topic_id }
        val reviewsByTopicFast = reviews.groupBy { cardTopic[it.card_id] }

        val topicStats = topics.map { t ->
            val recs = recordsByTopic[t.id].orEmpty()
            val best = Extras.effectiveLevel(recs)
            val latestRec = recs.maxByOrNull { it.recorded_at }
            val hints = recs.sortedByDescending { it.recorded_at }.take(3).mapNotNull { it.hint_used }
            val hintAvg: Double? = if (hints.isEmpty()) null else hints.sum().toDouble() / hints.size

            val tCards = cardsByTopic[t.id].orEmpty()
            val reviewed = tCards.mapNotNull { it.retrievability }
            val retr: Double? = if (reviewed.isEmpty()) null else reviewed.sum() / reviewed.size
            val tReviews = reviewsByTopicFast[t.id].orEmpty()
            val tags = tReviews.mapNotNull { it.error_tag }.groupingBy { it }.eachCount()
            val confMiss = tReviews.count { isConfident(it.confidence) && isMiss(it.result) }
            val rated = tReviews.count { isHit(it.result) || isMiss(it.result) }
            val hits = tReviews.count { isHit(it.result) }
            val acc: Float? = if (rated == 0) null else hits.toFloat() / rated.toFloat()

            val touchDates = mutableListOf<LocalDate>()
            sessionsByTopic[t.id].orEmpty().forEach { s -> localDateOf(s.session_date)?.let { touchDates += it } }
            recs.forEach { r -> localDateOf(r.recorded_at)?.let { touchDates += it } }
            tReviews.forEach { r -> localDateOf(r.reviewed_at)?.let { touchDates += it } }
            logTopicsByTopic[t.id].orEmpty().forEach { lt -> logDateById[lt.daily_log_id]?.let { touchDates += it } }
            val lastTouch = touchDates.maxByOrNull { it.toEpochDays() }
            val since: Int? = lastTouch?.let { max(0, daysBetween(today, it)) }

            val dueCount = tCards.count { it.isDue }
            val maxOver = tCards.filter { it.isDue && !it.isNew }.maxOfOrNull { it.overdueDays } ?: 0
            val openWk = weakCountByTopic[t.id] ?: 0
            val studied = t.id in studiedIds

            val parts = mutableListOf<String>()
            parts += if (best > 0) "best evidence L$best" else if (studied) "studied, no mastery evidence yet" else "not started"
            if (hintAvg != null && hintAvg >= 3.0) parts += "you needed heavy hints (avg ${(hintAvg * 10).toInt() / 10.0})"
            if (retr != null && retr < 0.7) parts += "recall fading (about ${(retr * 100).toInt()}%)"
            if (dueCount > 0) parts += plural(dueCount, "card") + " due" + if (maxOver > 0) " (oldest $maxOver d overdue)" else ""
            if (confMiss > 0) parts += "confidently wrong ${confMiss}x"
            tags.entries.maxByOrNull { it.value }?.let { if (it.value >= 2) parts += "most misses: ${tagLabel(it.key)}" }
            if (openWk > 0) parts += plural(openWk, "open weak spot")
            if (since != null && best >= 3 && since > STALE_AFTER_DAYS) parts += "not touched for $since d"

            TopicStat(
                topic = t,
                moduleId = t.module_id,
                studied = studied,
                level = best,
                latestLevel = levelNum(latestRec?.level),
                hintAvg = hintAvg,
                retrievability = retr,
                cardCount = tCards.size,
                dueCount = dueCount,
                maxOverdueDays = maxOver,
                errorTags = tags,
                confidentMisses = confMiss,
                ratedCount = rated,
                accuracy = acc,
                daysSinceTouch = since,
                openWeakSpots = openWk,
                why = parts.joinToString("; ")
            )
        }

        return LearnerModel(
            today = today,
            topics = topicStats,
            cards = cardStats,
            openWeakSpots = openWeak,
            moduleNames = moduleNames,
            moduleStatus = statuses,
            moduleOrder = moduleOrder,
            calibration = buildCalibration(reviews)
        )
    }

    // ---- Calibration ------------------------------------------------------

    private fun buildCalibration(reviews: List<RevisionReview>): Calibration {
        fun bucket(key: String, label: String, expected: Float): CalibrationBucket {
            val rs = reviews.filter { it.confidence == key && (isHit(it.result) || isMiss(it.result)) }
            return CalibrationBucket(key, label, expected, rs.size, rs.count { isHit(it.result) })
        }
        val low = bucket("guess", "Low", 0.4f)
        val med = bucket("sure", "Medium", 0.7f)
        val high = bucket("certain", "High", 0.9f)
        val buckets = listOf(low, med, high)
        val total = buckets.sumOf { it.total }

        val over = (high.total >= 5 && (high.accuracy ?: 1f) < 0.75f) ||
            (med.total >= 5 && (med.accuracy ?: 1f) < 0.55f)
        val under = low.total >= 5 && (low.accuracy ?: 0f) >= 0.8f && !over

        val why = "Each time you rate a card you can say Guess, Sure or Certain first. " +
            "Calibration compares that feeling with the result (Good/Easy = right, Again/Hard = miss). " +
            "When \"Certain\" is right far less often than 90%, the feeling of knowing is unreliable, " +
            "and those are exactly the cards that get skipped and then bite later."

        val message = when {
            total < 5 -> "Not enough rated answers yet. Pick Guess / Sure / Certain before flipping a card and this will fill in."
            over -> "You tend to be more sure than you are right. Certain-rated cards are the ones to test first: " +
                "write the answer down before you reveal it."
            under -> "You know more than you think. Your Guess answers are mostly right, so trust your first instinct a bit more."
            else -> "Your confidence roughly matches your results. Keep tagging it honestly."
        }
        return Calibration(buckets, total, over, under, message, why)
    }

    // ---- Continue policy --------------------------------------------------

    private fun effectiveMinutes(budget: Int, energy: Energy): Int {
        val b = if (budget <= 0) 30 else budget
        return when (energy) {
            Energy.LOW -> max(10, (b / 2 / 5) * 5)
            else -> b
        }
    }

    private fun cardWhy(c: CardStat): String {
        val sb = StringBuilder(c.topicName)
        sb.append(" card")
        if (c.overdueDays > 0) sb.append(" ${plural(c.overdueDays, "day")} overdue") else sb.append(" is due")
        when {
            c.lastWasConfidentMiss ->
                sb.append(" and you felt ${if (c.lastConfidence == "certain") "certain" else "sure"} but missed it last time")
            c.leech -> sb.append(" and it keeps slipping (missed ${c.lapses}x)")
            c.retrievability != null && c.retrievability < 0.7 ->
                sb.append(" and recall is fading (about ${(c.retrievability * 100).toInt()}%)")
        }
        return sb.toString()
    }

    suspend fun nextAction(budgetMinutes: Int, energy: Energy): ContinueAction {
        val m = loadModel()   // throws on fetch failure; the UI catches it
        if (!m.hasData) {
            return ContinueAction(
                ActionKind.REST, "Couldn't load your plan", 0, "planner",
                "No curriculum data came back. Check your connection and try again."
            )
        }
        val eff = effectiveMinutes(budgetMinutes, energy)
        val due = m.dueCards.sortedByDescending { it.risk }
        val today = m.today

        // Fading high-risk cards (due, or already below 70% recall).
        val riskCards = m.cards
            .filter { it.highRisk && !it.isNew && (it.isDue || (it.retrievability ?: 1.0) < 0.7) }
            .sortedByDescending { it.risk }

        // Weak spots whose revisit date has arrived, oldest first.
        val overdueWeak = m.openWeakSpots
            .mapNotNull { w ->
                val d = localDateOf(w.revisit_date)
                if (d != null && d <= today) w to daysBetween(today, d) else null
            }
            .sortedByDescending { it.second }
        val staleWeak = m.openWeakSpots
            .filter { it.revisit_date.isNullOrBlank() }
            .mapNotNull { w ->
                val opened = localDateOf(w.opened_at)
                val age = if (opened != null) daysBetween(today, opened) else 0
                if (age >= 10) w to age else null
            }
            .sortedByDescending { it.second }

        fun reviseAction(cards: List<CardStat>, lead: String?): ContinueAction {
            val n = min(cards.size.coerceAtLeast(1), max(3, eff * 2 / 3))
            val minutes = min(eff, max(5, (n * 3) / 2))
            val why = if (lead != null) lead else "${plural(cards.size, "card")} due today"
            return ContinueAction(
                ActionKind.REVISE,
                "Revise ${plural(n, "card")}",
                minutes, "revise", why, cardCount = cards.size
            )
        }

        fun weakAction(w: WeakSpot, why: String): ContinueAction {
            val name = m.topics.firstOrNull { it.topic.id == w.topic_id }?.topic?.name
            val title = w.title?.takeIf { it.isNotBlank() } ?: w.reason
            return ContinueAction(
                ActionKind.WEAK_SPOT,
                "Fix weak spot: ${title.take(60)}",
                min(eff, 25).coerceAtLeast(10), "weak_spots",
                if (name != null) "$why ($name)" else why,
                topicId = w.topic_id
            )
        }

        // Low energy: revision first (short, familiar), then weak spots.
        if (energy == Energy.LOW) {
            if (riskCards.isNotEmpty()) return reviseAction(due.ifEmpty { riskCards }, cardWhy(riskCards.first()))
            if (due.isNotEmpty()) return reviseAction(due, "${plural(due.size, "card")} due; a short review fits a low-energy day")
        }

        overdueWeak.firstOrNull()?.let { (w, days) ->
            val late = if (days > 0) "Weak spot is ${plural(days, "day")} past its revisit date" else "Weak spot revisit is due today"
            return weakAction(w, late)
        }
        if (riskCards.isNotEmpty()) {
            val pool = if (due.isNotEmpty()) due else riskCards
            return reviseAction(pool, cardWhy(riskCards.first()))
        }
        if (due.isNotEmpty()) {
            val oldest = due.filter { !it.isNew }.maxByOrNull { it.overdueDays }
            val lead = if (oldest != null && oldest.overdueDays > 0)
                "${plural(due.size, "card")} due, oldest ${plural(oldest.overdueDays, "day")} overdue"
            else "${plural(due.size, "card")} due today"
            return reviseAction(due, lead)
        }
        staleWeak.firstOrNull()?.let { (w, age) ->
            return weakAction(w, "Weak spot has been open for ${plural(age, "day")} without a revisit date")
        }

        // Recent topic, low energy: a 5-10 minute blank-page recall instead of new material.
        val lastTouched = m.topics
            .filter { it.studied && it.daysSinceTouch != null }
            .minByOrNull { it.daysSinceTouch ?: Int.MAX_VALUE }
        if (energy == Energy.LOW && lastTouched != null && lastTouched.level < 4 &&
            (lastTouched.daysSinceTouch ?: 99) <= 7
        ) {
            return ContinueAction(
                ActionKind.RECALL_WARMUP,
                "Recall warm-up: ${lastTouched.topic.name}",
                min(eff, 10), "focus",
                "Nothing is due. Write down everything you remember about ${lastTouched.topic.name} " +
                    "without looking (${lastTouched.why}).",
                topicId = lastTouched.topic.id
            )
        }

        // Next topic whose prerequisite modules are Mastered.
        val statuses = m.moduleStatus
        fun prereqsMet(moduleId: Int): Boolean =
            (Prereqs.EDGES[moduleId] ?: emptyList()).all { statuses[it] == "Mastered" }

        val next = m.topics
            .filter { !it.studied && prereqsMet(it.moduleId) && statuses[it.moduleId] != "Mastered" }
            .minByOrNull { (m.moduleOrder[it.moduleId] ?: 9999) * 10000 + it.topic.id }
        if (next != null) {
            val modName = m.moduleNames[next.moduleId] ?: "module ${next.moduleId}"
            val req = Prereqs.EDGES[next.moduleId].orEmpty()
            val prereqText = if (req.isEmpty()) "it has no prerequisites"
            else "prerequisites (" + req.joinToString(", ") { m.moduleNames[it] ?: "M$it" } + ") are Mastered"
            return ContinueAction(
                ActionKind.STUDY_TOPIC,
                "Study: ${next.topic.name}",
                max(15, if (energy == Energy.LOW) eff else min(eff, 60)),
                "module/${next.moduleId}",
                "Nothing is overdue. Next in the roadmap is $modName, and $prereqText.",
                topicId = next.topic.id
            )
        }

        // Everything reachable is started: firm up the weakest studied topic.
        val firmUp = m.topics
            .filter { it.studied && it.level < 4 }
            .minWithOrNull(compareBy<TopicStat>({ it.level }, { m.moduleOrder[it.moduleId] ?: 9999 }, { it.topic.id }))
        if (firmUp != null) {
            return ContinueAction(
                ActionKind.STUDY_TOPIC,
                "Firm up: ${firmUp.topic.name}",
                max(15, min(eff, 45)),
                "module/${firmUp.moduleId}",
                "Nothing is due and everything reachable is started. This topic is at " +
                    (if (firmUp.level > 0) "L${firmUp.level}" else "no recorded level") +
                    "; record L4 evidence to move on.",
                topicId = firmUp.topic.id
            )
        }

        return ContinueAction(
            ActionKind.REST, "You're ahead. Rest or plan tomorrow", 0, "planner",
            "No cards due, no weak spots waiting, no topic ready. Rest is a valid plan."
        )
    }

    // ---- Backlog rescue ---------------------------------------------------

    suspend fun backlogPlan(): BacklogPlan? {
        val m = loadModel()   // throws on fetch failure; the UI catches it
        val due = m.dueCards
        if (due.isEmpty()) return null
        val maxOver = due.filter { !it.isNew }.maxOfOrNull { it.overdueDays } ?: 0
        if (due.size <= BACKLOG_DUE && maxOver <= BACKLOG_OVERDUE_DAYS) return null

        val must = due.filter { it.highRisk }.sortedByDescending { it.risk }
        val rest = due.filter { !it.highRisk }
        // Low-risk and still well remembered (or brand new): safe to park.
        val deferrable = rest.filter { it.isNew || (it.retrievability ?: 0.0) >= 0.6 }
            .sortedByDescending { it.risk }
        val deferIds = deferrable.map { it.card.id }.toSet()
        val regular = rest.filter { it.card.id !in deferIds }.sortedByDescending { it.risk }

        val kept = must + regular
        val dayCount = 3
        val perDay = ArrayList<Int>()
        val base = kept.size / dayCount
        val extra = kept.size % dayCount
        var overflow = 0
        for (i in 0 until dayCount) {
            val want = base + if (i < extra) 1 else 0
            val got = min(want, MAX_CARDS_PER_CATCHUP_DAY)
            overflow += want - got
            perDay += got
        }
        var cursor = 0
        val days = perDay.mapIndexed { i, n ->
            val slice = kept.subList(cursor, min(kept.size, cursor + n))
            cursor += n
            val mk = slice.count { it.highRisk }
            BacklogDay(
                day = i + 1,
                label = if (i == 0) "Day 1 (today)" else "Day ${i + 1}",
                cards = slice.size,
                mustKeep = mk,
                regular = slice.size - mk
            )
        }

        val topicNames = must.groupingBy { it.topicName }.eachCount().entries
            .sortedByDescending { it.value }.take(5).map { it.key }

        val why = "${plural(due.size, "card")} are due" +
            (if (maxOver > 0) " and the oldest is ${plural(maxOver, "day")} overdue" else "") +
            ". Must-keep cards are leeches, ones you were sure about but missed, and ones on topics with an open weak spot. " +
            "Well-remembered, low-risk cards can wait until the backlog is clear."

        return BacklogPlan(
            totalDue = due.size,
            maxOverdueDays = maxOver,
            mustKeep = must.size,
            regular = regular.size,
            deferrable = deferrable.size + overflow,
            mustKeepTopics = topicNames,
            days = days,
            why = why
        )
    }
}
