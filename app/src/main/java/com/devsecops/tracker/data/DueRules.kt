package com.devsecops.tracker.data

import kotlinx.datetime.LocalDate

// =========================================================================
// DueRules — the ONE definition of "is this card due?".
//
// SCHEDULE vs STRENGTH (read this first):
//   * The SM-2 schedule in Repository (nextReviewDate, stored as
//     revision_reviews.next_review_date / revision_cards.due_date) is the
//     source of due DATES. Nothing here changes a date.
//   * FSRS (Fsrs.kt / Memory.kt) is used ONLY for strength / risk display
//     (retrievability, "Fading soon", topic strength). It never decides
//     whether a card is due.
//
// These functions are pure (no I/O) so they can be unit-tested, and they
// mirror exactly what Repository.assembleDueCards does (Repository is left
// untouched and keeps its own private copy of MAX_NEW_CARDS_PER_DAY = 5, so
// keep the two values identical):
//   1. a card enters rotation only if its topic is "studied" (a study
//      session, a mastery record or a daily-log topic on it);
//   2. reviewed card: due when the LATEST review's next_review_date <= today;
//      never-reviewed card: due when (card.due_date ?: today) <= today
//      (plain yyyy-MM-dd string comparison, same as Repository);
//   3. reviewed cards come first, oldest next_review_date first; then at
//      most MAX_NEW_CARDS_PER_DAY minus "cards whose FIRST review happened
//      today" brand-new cards, ordered by due_date.
// Home ring, Continue, Backlog and Revise all count from dueCardIds().
// =========================================================================
object DueRules {

    const val MAX_NEW_CARDS_PER_DAY = 5

    fun studiedTopicIds(
        sessionTopicIds: Iterable<Int>,
        masteryTopicIds: Iterable<Int>,
        logTopicIds: Iterable<Int>
    ): Set<Int> = (sessionTopicIds.toList() + masteryTopicIds.toList() + logTopicIds.toList()).toSet()

    // Latest review per card (by reviewed_at).
    fun latestReviewByCard(reviews: List<RevisionReview>): Map<Long, RevisionReview> =
        reviews.groupBy { it.card_id }
            .mapValues { (_, list) -> list.maxByOrNull { it.reviewed_at }!! }

    // Date (yyyy-MM-dd) the card is next due: last review's next_review_date,
    // else the card's own due_date, else today.
    fun dueDateString(card: RevisionCard, latestReview: RevisionReview?, today: LocalDate): String =
        latestReview?.next_review_date ?: (card.due_date ?: today.toString())

    // The per-card date rule only (no studied-topic gating, no new-card cap).
    fun isDueOn(card: RevisionCard, latestReview: RevisionReview?, today: LocalDate): Boolean =
        dueDateString(card, latestReview, today) <= today.toString()

    // Cards whose FIRST review happened on `today` (local date).
    fun newCardsIntroducedOn(reviews: List<RevisionReview>, today: LocalDate): Int =
        reviews.groupBy { it.card_id }.count { (_, list) ->
            val first = list.minByOrNull { it.reviewed_at } ?: return@count false
            localDateStringOf(first.reviewed_at) == today.toString()
        }

    fun newCardsAllowed(reviews: List<RevisionReview>, today: LocalDate): Int =
        (MAX_NEW_CARDS_PER_DAY - newCardsIntroducedOn(reviews, today)).coerceAtLeast(0)

    // Ids of the cards Revise would show today, in Revise order.
    fun dueCardIds(
        cards: List<RevisionCard>,
        reviews: List<RevisionReview>,
        studiedTopicIds: Set<Int>,
        today: LocalDate
    ): List<Long> {
        if (cards.isEmpty()) return emptyList()
        val latest = latestReviewByCard(reviews)
        val due = cards
            .filter { it.topic_id in studiedTopicIds }
            .filter { isDueOn(it, latest[it.id], today) }
        val reviewed = due.filter { latest[it.id] != null }
            .sortedBy { latest[it.id]!!.next_review_date }
        val fresh = due.filter { latest[it.id] == null }
            .sortedBy { it.due_date ?: "" }
            .take(newCardsAllowed(reviews, today))
        return (reviewed + fresh).map { it.id }
    }
}
