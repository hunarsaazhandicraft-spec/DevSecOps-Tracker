package com.devsecops.tracker.data

import android.content.Context
import com.devsecops.tracker.Supa
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

// Read-only memory model. Nothing here writes to the database: each card's
// FSRS state is rebuilt on the device by replaying its revision_reviews rows.

data class TopicStrength(
    val topicId: Int,
    val topicName: String,
    val strength: Double,      // mean retrievability 0..1
    val cardCount: Int
)

data class StrengthSummary(
    val averageRetrievability: Double,   // 0..1 across all reviewed cards
    val fadingSoonCount: Int,
    val reviewedCards: Int,
    val topics: List<TopicStrength>      // weakest first
)

data class FadingItem(
    val cardId: Long,
    val topicId: Int,
    val topicName: String,
    val question: String,
    val retrievabilityNow: Double,
    val hoursUntilBelow80: Double        // 0 if already below 80%
)

data class FsrsDue(
    val card: RevisionCard,
    val topicName: String,
    val retrievability: Double,
    val overdueDays: Double              // days past the target-retention point
)

data class CardDraft(
    val topicId: Int,
    val topicName: String,
    val question: String,
    val answer: String
)

object Memory {

    private const val PREFS = "memory_prefs"
    private const val KEY_TARGET = "target_retention"
    const val DEFAULT_TARGET_RETENTION = 0.9
    private const val DAY_MS = 86_400_000L
    private const val FADE_LOW = 0.70
    private const val FADE_HIGH = 0.85
    private const val FADE_WINDOW_DAYS = 3.0

    // ---- Target retention (device-local) ----------------------------------

    fun getTargetRetention(context: Context): Double = runCatching {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getFloat(KEY_TARGET, DEFAULT_TARGET_RETENTION.toFloat()).toDouble()
            .coerceIn(0.7, 0.97)
    }.getOrDefault(DEFAULT_TARGET_RETENTION)

    fun setTargetRetention(context: Context, value: Double) {
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putFloat(KEY_TARGET, value.coerceIn(0.7, 0.97).toFloat())
                .apply()
        }
    }

    // ---- Loading + replay -------------------------------------------------

    private class CardState(
        val card: RevisionCard,
        val topicName: String,
        val mem: CardMemory
    )

    // Pure: FSRS state per card id, rebuilt by replaying its reviews. Cards
    // with no usable review (unknown result / unparseable time) are absent.
    // This is the single source of retrievability for Memory AND Learner.
    fun replayByCard(reviews: List<RevisionReview>): Map<Long, CardMemory> {
        val out = HashMap<Long, CardMemory>()
        for ((cardId, list) in reviews.groupBy { it.card_id }) {
            val pairs = list.mapNotNull { r ->
                val rating = Fsrs.ratingOf(r.result) ?: return@mapNotNull null
                val at = runCatching { Instant.parse(r.reviewed_at).toEpochMilliseconds() }.getOrNull()
                    ?: return@mapNotNull null
                rating to at
            }
            if (pairs.isNotEmpty()) out[cardId] = Fsrs.replay(pairs)
        }
        return out
    }

    // Throws on any fetch failure (nothing is silently turned into "no cards").
    private suspend fun loadStates(): List<CardState> = coroutineScope {
        val cardsJob = async { Fetch.cards() }
        val reviewsJob = async { Fetch.reviews() }
        val topicsJob = async { Repository.listTopics() }

        val cards = cardsJob.await()
        val mems = replayByCard(reviewsJob.await())
        val names = topicsJob.await().associate { it.id to it.name }

        cards.mapNotNull { card ->
            val mem = mems[card.id] ?: return@mapNotNull null
            CardState(card, names[card.topic_id] ?: "Topic #${card.topic_id}", mem)
        }
    }

    private fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()

    private fun isFading(mem: CardMemory, now: Long): Boolean {
        val rNow = Fsrs.retrievability(mem, now)
        val rLater = Fsrs.retrievability(mem, now + (FADE_WINDOW_DAYS * DAY_MS).toLong())
        return rNow >= FADE_LOW && rLater < FADE_HIGH
    }

    // ---- Public API -------------------------------------------------------

    suspend fun strengthSummary(): StrengthSummary {
        val now = nowMs()
        val states = loadStates()
        if (states.isEmpty()) return StrengthSummary(0.0, 0, 0, emptyList())
        val rs = states.map { it to Fsrs.retrievability(it.mem, now) }
        val avg = rs.sumOf { it.second } / rs.size
        val fading = states.count { isFading(it.mem, now) }
        val topics = rs.groupBy { it.first.card.topic_id }.map { (tid, list) ->
            TopicStrength(
                topicId = tid,
                topicName = list.first().first.topicName,
                strength = list.sumOf { it.second } / list.size,
                cardCount = list.size
            )
        }.sortedBy { it.strength }
        return StrengthSummary(avg, fading, states.size, topics)
    }

    // Cards that are still remembered but will slip under 85% within 3 days,
    // soonest to hit 80% first.
    suspend fun fadingSoon(limit: Int): List<FadingItem> {
        val now = nowMs()
        val states = loadStates()
        return states.filter { isFading(it.mem, now) }.map { s ->
            val elapsedDays = Fsrs.elapsedDays(s.mem.lastReviewMs, now).toDouble()
            val t80 = Fsrs.nextInterval(s.mem.stability, 0.8)
            FadingItem(
                cardId = s.card.id,
                topicId = s.card.topic_id,
                topicName = s.topicName,
                question = s.card.question,
                retrievabilityNow = Fsrs.retrievability(s.mem, now),
                hoursUntilBelow80 = maxOf(0.0, (t80 - elapsedDays) * 24.0)
            )
        }.sortedBy { it.hoursUntilBelow80 }.take(limit.coerceAtLeast(0))
    }

    // Reviewed cards that Revise treats as due today. "Due" is decided by
    // DueRules (SM-2 schedule + studied-topic gate + new-card cap), the same
    // rule Home/Continue/Backlog use, so the counts agree. FSRS only supplies
    // the retrievability shown / used to sort. targetRetention no longer
    // decides due-ness (kept for API compatibility). overdueDays = whole days
    // past the scheduled date.
    @Suppress("UNUSED_PARAMETER")
    suspend fun dueByFsrs(targetRetention: Double = DEFAULT_TARGET_RETENTION): List<FsrsDue> = coroutineScope {
        val cardsJob = async { Fetch.cards() }
        val reviewsJob = async { Fetch.reviews() }
        val topicsJob = async { Repository.listTopics() }
        val sessionsJob = async { Fetch.sessions() }
        val recordsJob = async { Fetch.masteryRecords() }
        val logTopicsJob = async { Fetch.logTopics() }

        val cards = cardsJob.await()
        val reviews = reviewsJob.await()
        val names = topicsJob.await().associate { it.id to it.name }
        val studied = DueRules.studiedTopicIds(
            sessionsJob.await().map { it.topic_id },
            recordsJob.await().map { it.topic_id },
            logTopicsJob.await().map { it.topic_id }
        )
        val today = localTodayDate()
        val dueIds = DueRules.dueCardIds(cards, reviews, studied, today).toSet()
        val mems = replayByCard(reviews)
        val latest = DueRules.latestReviewByCard(reviews)

        cards.mapNotNull { card ->
            val mem = mems[card.id]
            val last = latest[card.id]
            if (card.id !in dueIds || mem == null || last == null) null
            else {
                val dueOn = localDateOf(last.next_review_date)
                val overdue = if (dueOn == null) 0
                else maxOf(0, today.toEpochDays().toLong().minus(dueOn.toEpochDays().toLong()).toInt())
                FsrsDue(
                    card,
                    names[card.topic_id] ?: "Topic #${card.topic_id}",
                    Fsrs.retrievabilityOn(mem, today),
                    overdue.toDouble()
                )
            }
        }.sortedBy { it.retrievability }
    }

    // Suggestion only; the caller shows it in the Add Card form. No DB write.
    fun weakSpotToCardDraft(ws: WeakSpot, topicName: String = ""): CardDraft {
        val subject = (ws.title ?: "").trim().ifBlank { ws.reason.trim().lineSequence().firstOrNull().orEmpty() }
        val question = if (subject.isBlank()) "What do you keep getting wrong here?"
        else "Explain from memory: $subject"
        val answer = listOfNotNull(
            ws.required_action?.trim()?.takeIf { it.isNotEmpty() },
            ws.practice_needed?.trim()?.takeIf { it.isNotEmpty() },
            ws.reason.trim().takeIf { it.isNotEmpty() }
        ).firstOrNull().orEmpty()
        return CardDraft(ws.topic_id, topicName, question, answer)
    }
}
