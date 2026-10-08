package com.devsecops.tracker.data

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.serialization.Serializable

// Pure helpers for the tutor-sync cards (no I/O, unit-tested).

// "Agla chat: <name>. Start: DAY. <rest>. Carry-over: a; b; c" from daily_logs.next_action.
data class NextChat(val name: String, val start: String?, val rest: String, val carry: List<String>)

const val MAX_CARRY = 8

fun parseNextChat(next: String?): NextChat? {
    if (next.isNullOrBlank()) return null
    val parts = next.trim().split(Regex("(?<=[.!?])\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
    var name: String? = null
    var start: String? = null
    var carry: List<String> = emptyList()
    val rest = mutableListOf<String>()
    for (p in parts) {
        val low = p.lowercase()
        when {
            low.startsWith("agla chat:") && name == null ->
                name = p.substringAfter(":").trim().trimEnd('.', '!', '?').trim()
            low.startsWith("start:") && start == null ->
                start = p.substringAfter(":").trim().trimEnd('.', '!', '?').trim().ifBlank { null }
            low.startsWith("carry-over:") || low.startsWith("carry over:") ->
                carry = p.substringAfter(":").split(";").map { it.trim().trimEnd('.') }.filter { it.isNotEmpty() }.take(MAX_CARRY)
            else -> rest.add(p)
        }
    }
    val n = name?.takeIf { it.isNotBlank() } ?: return null
    return NextChat(n, start, rest.joinToString(" "), carry)
}

@Serializable
data class HoursRow(
    val log_date: String,
    val planned_hours: Double? = null,
    val actual_hours: Double? = null
)

data class Pace(val actual: Double, val planned: Double, val pct: Int?, val days: Int)

const val DEFAULT_PLAN_HOURS = 6.5
const val SOFT_CAP_HOURS = 8.0
const val REPLAN_PCT = 70
const val REPLAN_MIN_DAYS = 7

// Sum over logged days (actual_hours present) in the last `windowDays` days incl. today.
fun paceSummary(rows: List<HoursRow>, today: LocalDate, windowDays: Int): Pace {
    val from = today.minus(DatePeriod(days = windowDays - 1)).toString()
    val to = today.toString()
    val used = rows.filter { it.log_date in from..to && it.actual_hours != null }
    val actual = used.sumOf { it.actual_hours ?: 0.0 }
    val planned = used.sumOf { it.planned_hours ?: DEFAULT_PLAN_HOURS }
    val pct = if (planned > 0) Math.round(actual / planned * 100).toInt() else null
    return Pace(actual, planned, pct, used.size)
}

// 2 weeks under 70% of plan (with enough logged days) => replan.
fun needsReplan(rows: List<HoursRow>, today: LocalDate): Boolean {
    val p = paceSummary(rows, today, 14)
    return p.days >= REPLAN_MIN_DAYS && (p.pct ?: 100) < REPLAN_PCT
}

fun overSoftCap(hours: Double): Boolean = hours > SOFT_CAP_HOURS
