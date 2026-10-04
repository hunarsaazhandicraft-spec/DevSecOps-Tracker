package com.devsecops.tracker.data

import kotlin.math.roundToInt

data class EvidenceClaim(
    val label: String,
    val value: String,
    val threshold: String
)

data class NextBestActionItem(
    val id: String,
    val actionType: String, // 'REVISE_TOPIC' | 'PRACTICE_QUESTIONS' | 'LEARN_TOPIC' | 'REMEDIATE_PREREQUISITE'
    val topicId: Int,
    val topicName: String,
    val subjectName: String,
    val estimatedMinutes: Int,
    val utilityScore: Double,
    val reasoningTitle: String,
    val evidenceClaims: List<EvidenceClaim>
)

object NbaEngine {

    fun computeActions(
        topics: List<Topic>,
        weakSpots: List<WeakSpot>,
        masteryRecords: List<MasteryRecord>,
        cards: List<RevisionCard>,
        reviews: List<RevisionReview>,
        todayString: String = localTodayString()
    ): List<NextBestActionItem> {
        val activeWeakSpots = weakSpots.filter { it.cleared_at == null }
        val actions = mutableListOf<NextBestActionItem>()

        val reviewsByCard = reviews.groupBy { it.card_id }
        val cardsByTopic = cards.groupBy { it.topic_id }
        val masteryByTopic = masteryRecords.groupBy { it.topic_id }

        for (topic in topics) {
            val topicWeakSpots = activeWeakSpots.filter { it.topic_id == topic.id }
            val topicCards = cardsByTopic[topic.id] ?: emptyList()
            val topicMastery = masteryByTopic[topic.id] ?: emptyList()

            // Calculate card retrievabilities
            var totalRetrievability = 0.0
            var cardCount = 0
            var overdueCardCount = 0

            for (card in topicCards) {
                val cardReviews = reviewsByCard[card.id] ?: emptyList()
                val reviewPairs = cardReviews.map { r ->
                    val rating = Fsrs.ratingOf(r.result) ?: Fsrs.GOOD
                    val ms = System.currentTimeMillis() // default approximation
                    rating to ms
                }
                val mem = if (reviewPairs.isEmpty()) Fsrs.NEW_CARD else Fsrs.replay(reviewPairs)
                val ret = Fsrs.retrievability(mem, System.currentTimeMillis())
                totalRetrievability += ret
                cardCount++
                if (card.due_date != null && card.due_date <= todayString) {
                    overdueCardCount++
                }
            }

            val avgRetrievability = if (cardCount > 0) totalRetrievability / cardCount else 0.8
            val isOverdue = overdueCardCount > 0

            // 1. Spaced Revision Action
            if (isOverdue || avgRetrievability < 0.70) {
                val weight = if (topic.is_critical) 5.0 else if (topic.is_core) 3.5 else 2.0
                val weaknessMultiplier = if (topicWeakSpots.isNotEmpty()) 2.5 else 1.2
                val utility = ((weight * weaknessMultiplier * 3.0) / (topicCards.size.coerceAtLeast(1) * 0.5 + 0.1))
                val roundedUtility = (utility * 100).roundToInt() / 100.0

                actions.add(
                    NextBestActionItem(
                        id = "nba-rev-${topic.id}",
                        actionType = "REVISE_TOPIC",
                        topicId = topic.id,
                        topicName = topic.name,
                        subjectName = "DevSecOps Curriculum",
                        estimatedMinutes = 25,
                        utilityScore = roundedUtility,
                        reasoningTitle = "Spaced Repetition & Recall: ${topic.name}",
                        evidenceClaims = listOf(
                            EvidenceClaim("Est. Retention", "${(avgRetrievability * 100).roundToInt()}%", "< 70% threshold"),
                            EvidenceClaim("Cards Due", "$overdueCardCount Cards", "Scheduled FSRS Queue"),
                            EvidenceClaim("Core Priority", if (topic.is_critical) "Critical Target" else "Standard Core", "High Impact")
                        )
                    )
                )
            }

            // 2. Targeted Weak Spot Action
            if (topicWeakSpots.isNotEmpty()) {
                val ws = topicWeakSpots.first()
                val utility = ((if (ws.severity == "critical") 8.5 else 5.5) * 2.0).roundToInt() / 10.0 + 12.0

                actions.add(
                    NextBestActionItem(
                        id = "nba-ws-${topic.id}",
                        actionType = "PRACTICE_QUESTIONS",
                        topicId = topic.id,
                        topicName = topic.name,
                        subjectName = "Bottleneck Remediation",
                        estimatedMinutes = 35,
                        utilityScore = utility,
                        reasoningTitle = "Clear Bottleneck: ${ws.title ?: topic.name}",
                        evidenceClaims = listOf(
                            EvidenceClaim("Active Weak Spot", ws.severity ?: "medium", "Requires Verification"),
                            EvidenceClaim("Opening Reason", ws.reason, "Root Cause Target"),
                            EvidenceClaim("Mastery Level", topicMastery.lastOrNull()?.level ?: "Unassessed", "Target >= L3")
                        )
                    )
                )
            }
        }

        return actions.sortedByDescending { it.utilityScore }
    }
}
