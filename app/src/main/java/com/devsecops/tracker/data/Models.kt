package com.devsecops.tracker.data

import kotlinx.serialization.Serializable

@Serializable
data class Topic(
    val id: Int,
    val module_id: Int,
    val name: String,
    val depth_target: String? = null,
    val is_core: Boolean = false,
    val is_critical: Boolean = false
)

@Serializable
data class Module(
    val id: Int,
    val phase_id: Int,
    val name: String,
    val order_num: Int
)

@Serializable
data class StudySessionInsert(
    val topic_id: Int,
    val duration_min: Int,
    val activity_type: String, // learning | practice | lab | assessment
    val notes: String? = null,
    // Device-local date. The column default is CURRENT_DATE on the DB server
    // (UTC), which is yesterday for IST before 05:30, so always send it.
    val session_date: String = localTodayString()
)

@Serializable
data class StudySessionDuration(
    val duration_min: Int,
    val session_date: String
)

@Serializable
data class WeakSpot(
    val id: Long,
    val topic_id: Int,
    val reason: String,
    val opened_at: String,
    val cleared_at: String? = null,
    val title: String? = null,
    val module_id: Int? = null,
    val severity: String? = null,
    val category: String? = null,
    val current_level: String? = null,
    val required_action: String? = null,
    val practice_needed: String? = null,
    val revisit_date: String? = null
)

@Serializable
data class RevisionCard(
    val id: Long,
    val topic_id: Int,
    val weak_spot_id: Long? = null,
    val question: String,
    val answer: String,
    val due_date: String? = null
)

@Serializable
data class RevisionCardInsert(
    val topic_id: Int,
    val question: String,
    val answer: String,
    val weak_spot_id: Long? = null
)

@Serializable
data class RevisionReview(
    val id: Long,
    val card_id: Long,
    val reviewed_at: String,
    val result: String,
    val next_review_date: String,
    val confidence: String? = null,
    val error_tag: String? = null
)

@Serializable
data class RevisionReviewInsert(
    val card_id: Long,
    val result: String,
    val next_review_date: String,
    val confidence: String? = null,
    val error_tag: String? = null
)

@Serializable
data class WeakSpotInsert(
    val topic_id: Int,
    val reason: String
)

@Serializable
data class DailyTargetUpsert(
    val target_date: String,
    val target_text: String
)

// A card that's due right now, with enough context to show it.
data class DueCard(
    val card: RevisionCard,
    val topicName: String,
    val lastReview: RevisionReview?,
    // How many times this card has been rated "again" (3+ = a leech).
    val lapses: Int = 0
)

@Serializable
data class RevisionReviewDue(
    val id: Long,
    val card_id: Long,
    val next_review_date: String
)

@Serializable
data class RevisionCardPreview(
    val id: Long,
    val topic_id: Int,
    val question: String
)

@Serializable
data class DailyTarget(
    val target_date: String,
    val target_text: String
)

@Serializable
data class StreakDay(
    val day: String,
    val studied: Boolean,
    val rest: Boolean = false
)

// L1-L5, evidence_quote is required (never blank) per the "no tick without
// a quote" rule; hint_used is optional (1-5) and this table is append-only
// on the DB side — RLS grants SELECT + INSERT only, no UPDATE/DELETE.
@Serializable
data class MasteryRecord(
    val id: Long,
    val topic_id: Int,
    val level: String,
    val evidence_quote: String,
    val hint_used: Int? = null,
    val recorded_at: String,
    val session_id: Long? = null,
    val evidence_url: String? = null
)

@Serializable
data class MasteryRecordInsert(
    val topic_id: Int,
    val level: String,
    val evidence_quote: String,
    val hint_used: Int? = null,
    val evidence_url: String? = null
)

@Serializable
data class MasteryTopicIdOnly(val topic_id: Int)

data class MasterySummary(
    val assessedCount: Int,
    val totalCount: Int
)

data class DayStatus(
    val date: String,
    val label: String,
    val studied: Boolean,
    val isToday: Boolean
)

data class NextRevision(
    val topicName: String,
    val question: String,
    val dueDate: String
)

@Serializable
data class StudySessionExport(
    val id: Long,
    val topic_id: Int,
    val session_date: String,
    val duration_min: Int,
    val activity_type: String,
    val notes: String? = null
)

// Everything the account owns, in one file. Reference data (topics) is
// included too so a backup can still be read years later even if the
// curriculum tables have moved on.
@Serializable
data class BackupBundle(
    val exported_at: String,
    val topics: List<Topic>,
    val study_sessions: List<StudySessionExport>,
    val weak_spots: List<WeakSpot>,
    val mastery_records: List<MasteryRecord>,
    val streak_days: List<StreakDay>,
    val revision_cards: List<RevisionCard> = emptyList(),
    val revision_reviews: List<RevisionReview> = emptyList(),
    val daily_logs: List<DailyLog> = emptyList(),
    val daily_log_topics: List<DailyLogTopic> = emptyList(),
    val progress_items: List<ProgressItem> = emptyList(),
    val glossary_terms: List<GlossaryTerm> = emptyList(),
    val commands: List<CommandItem> = emptyList(),
    val bandit_levels: List<BanditLevel> = emptyList(),
    val projects_tracker: List<ProjectItem> = emptyList(),
    val interview_questions: List<InterviewQ> = emptyList()
)

@Serializable
data class StudySessionProgress(
    val topic_id: Int,
    val duration_min: Int,
    val session_date: String
)

data class TopicTime(
    val topicName: String,
    val minutes: Int
)

data class ProgressData(
    val totalMinutes: Int,
    val totalSessions: Int,
    val currentStreak: Int,
    val last30Days: List<DayStatus>,
    val timeByTopic: List<TopicTime>
)

data class HomeDashboard(
    val todaysTarget: String?,
    val dueRevisionCount: Int,
    val nextRevision: NextRevision?,
    val currentStreak: Int,
    val last7Days: List<DayStatus>,
    val weekMinutes: Int,
    val weekSessionCount: Int,
    val topWeakSpots: List<WeakSpot>,
    val mastery: MasterySummary,
    // Computed over the FULL open weak-spot list (not just the top 3):
    // revisit_date <= today (device-local). overdueWeakSpot = the one that
    // has been waiting longest.
    val overdueWeakSpotCount: Int = 0,
    val overdueWeakSpot: WeakSpot? = null
)


// ---- Study history (daily logs in Supabase) ------------------------

@Serializable
data class DailyLog(
    val id: Long,
    val study_day: Int,
    val log_date: String,
    val module_id: Int? = null,
    val chapter_topic: String? = null,
    val status: String? = null,
    val actual_hours: Double? = null,
    val confidence: Int? = null,
    val learning: String? = null,
    val practice: String? = null,
    val lab: String? = null,
    val revision: String? = null,
    val blocker: String? = null,
    val notes: String? = null,
    val next_action: String? = null,
    val session_handoff: String? = null
)

@Serializable
data class DailyLogTopic(
    val daily_log_id: Long,
    val topic_id: Int,
    val covered_note: String? = null
)

@Serializable
data class ProgressItem(
    val id: Long,
    val item: String,
    val item_type: String? = null,
    val module_id: Int? = null,
    val status: String? = null,
    val mastery_level: String? = null,
    val target_level: String? = null,
    val highest_hint: Int? = null,
    val gate_result: String? = null,
    val start_date: String? = null,
    val completion_date: String? = null,
    val evidence: String? = null,
    val notes: String? = null,
    val superseded: Boolean = false
)

data class CardStatus(
    val card: RevisionCard,
    val topicName: String,
    val lastResult: String?,
    val nextDue: String?
)

// Everything one chapter (module) screen needs, assembled in one go.
data class ModuleDetail(
    val module: Module,
    val progress: ProgressItem?,
    val logs: List<DailyLog>,
    val topics: List<Topic>,
    val coveredNotes: Map<Int, List<String>>,   // topic_id -> "Day N: note"
    val coveredTopicIds: Set<Int>,
    val records: List<MasteryRecord>,
    val weakSpots: List<WeakSpot>,
    val cards: List<CardStatus>,
    val minutes: Int
)
