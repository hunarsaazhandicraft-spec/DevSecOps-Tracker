package com.devsecops.tracker.ui

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

private fun parseDate(iso: String?): LocalDate? =
    runCatching { LocalDate.parse(iso!!.take(10)) }.getOrNull()

// "2026-09-25" -> "25 Sep"
fun friendlyDate(iso: String?): String {
    val d = parseDate(iso) ?: return iso ?: ""
    return "${d.dayOfMonth} ${MONTHS[d.monthNumber - 1]}"
}

// True when a date is strictly before today (local time zone).
fun isOverdue(iso: String?): Boolean {
    val d = parseDate(iso) ?: return false
    return d < Clock.System.todayIn(TimeZone.currentSystemDefault())
}

// Drops internal file references like "(FIRST_7_DAYS)" from titles.
fun cleanTitle(s: String): String =
    s.replace(Regex("\\s*\\(FIRST_7_DAYS\\)"), "")
        .replace(Regex("^Day\\s*\\d+\\s*[—–-]\\s*"), "")
        .trim()
