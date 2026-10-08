package com.devsecops.tracker.data

import java.util.TimeZone
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DueRulesTest {

    private val today = LocalDate.parse("2026-09-29")
    private val studied = setOf(1)
    private var savedTz: TimeZone? = null

    @Before fun setUp() {
        savedTz = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After fun tearDown() {
        savedTz?.let { TimeZone.setDefault(it) }
    }

    private fun card(id: Long, topic: Int = 1, due: String? = null) =
        RevisionCard(id = id, topic_id = topic, question = "q$id", answer = "a$id", due_date = due)

    private fun review(id: Long, cardId: Long, at: String, next: String) =
        RevisionReview(id = id, card_id = cardId, reviewed_at = at, result = "good", next_review_date = next)

    @Test
    fun newCards_cappedAtMaxPerDay() {
        val cards = (1L..8L).map { card(it) }
        val ids = DueRules.dueCardIds(cards, emptyList(), studied, today)
        assertEquals(DueRules.MAX_NEW_CARDS_PER_DAY, ids.size)
    }

    @Test
    fun newCards_allowanceReducedByIntroducedToday() {
        val cards = (1L..8L).map { card(it) }
        // cards 100 and 101 were first reviewed today, scheduled in the future.
        val reviews = listOf(
            review(1, 100, "2026-09-29T08:00:00+00:00", "2026-10-05"),
            review(2, 101, "2026-09-29T09:00:00+00:00", "2026-10-05")
        )
        assertEquals(2, DueRules.newCardsIntroducedOn(reviews, today))
        assertEquals(DueRules.MAX_NEW_CARDS_PER_DAY - 2, DueRules.newCardsAllowed(reviews, today))
        assertEquals(3, DueRules.dueCardIds(cards, reviews, studied, today).size)
    }

    @Test
    fun futureReview_notDue_pastAndTodayDue() {
        val cards = listOf(card(1), card(2), card(3))
        val reviews = listOf(
            review(1, 1, "2026-09-20T08:00:00+00:00", "2026-10-03"),
            review(2, 2, "2026-09-20T08:00:00+00:00", "2026-09-25"),
            review(3, 3, "2026-09-20T08:00:00+00:00", "2026-09-29")
        )
        val ids = DueRules.dueCardIds(cards, reviews, studied, today)
        assertTrue(1L !in ids)
        assertTrue(2L in ids && 3L in ids)
    }

    @Test
    fun nonStudiedTopics_neverAppear() {
        val cards = listOf(card(1, topic = 1), card(2, topic = 2))
        val ids = DueRules.dueCardIds(cards, emptyList(), studied, today)
        assertEquals(listOf(1L), ids)
    }

    @Test
    fun reviewedCards_orderedOldestDueFirst() {
        val cards = listOf(card(1), card(2), card(3))
        val reviews = listOf(
            review(1, 1, "2026-09-10T08:00:00+00:00", "2026-09-28"),
            review(2, 2, "2026-09-10T08:00:00+00:00", "2026-09-20"),
            review(3, 3, "2026-09-10T08:00:00+00:00", "2026-09-25")
        )
        assertEquals(listOf(2L, 3L, 1L), DueRules.dueCardIds(cards, reviews, studied, today))
    }

    @Test
    fun latestReviewByCard_picksNewestReviewedAt() {
        val reviews = listOf(
            review(1, 1, "2026-09-10T08:00:00+00:00", "2026-09-11"),
            review(2, 1, "2026-09-20T08:00:00+00:00", "2026-10-20"),
            review(3, 1, "2026-09-15T08:00:00+00:00", "2026-09-16")
        )
        assertEquals(2L, DueRules.latestReviewByCard(reviews).getValue(1L).id)
    }

    @Test
    fun reviewCap_limitsQueueAndShrinksAsReviewsAreDone() {
        // 20 old reviewed cards, all overdue by different amounts.
        val cards = (1L..20L).map { card(it) }
        val reviews = (1L..20L).map { review(it, it, "2026-09-01T08:00:00+00:00", "2026-09-%02d".format(it.toInt() + 5)) }
        val ids = DueRules.dueCardIds(cards, reviews, studied, today)
        assertEquals(DueRules.MAX_REVIEWS_PER_DAY, ids.size)
        // most overdue (earliest next_review_date) first
        assertEquals(1L, ids.first())
        // 4 reviews done today use up 4 of today's 15
        val doneToday = reviews + (101L..104L).map { review(it, it, "2026-09-29T08:00:00+00:00", "2026-10-30") }
        assertEquals(4, DueRules.reviewsDoneOn(doneToday, today))
        assertEquals(11, DueRules.reviewBudget(doneToday, today))
        assertEquals(11, DueRules.dueCardIds(cards, doneToday, studied, today).size)
    }

    @Test
    fun overdueCount_isUncappedAndStrictlyBeforeToday() {
        val cards = (1L..40L).map { card(it, due = "2026-09-01") } + card(99, due = "2026-09-29")
        assertEquals(40, DueRules.overdueCount(cards, emptyList(), studied, today))
        assertTrue(DueRules.isDebt(31))
        assertTrue(!DueRules.isDebt(30))
    }
}
