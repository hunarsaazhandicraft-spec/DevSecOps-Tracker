package com.devsecops.tracker.data

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import org.junit.Assert.assertEquals
import org.junit.Test

class StreakTest {

    private val today = LocalDate.parse("2026-09-29")

    private fun ago(n: Int): String = today.minus(DatePeriod(days = n)).toString()

    @Test
    fun todayUnstudied_yesterdayStudied_streakContinues() {
        val studied = setOf(ago(1), ago(2), ago(3))
        assertEquals(3, Repository.computeStreak(studied, today))
    }

    @Test
    fun restDay_doesNotBreakOrAdd() {
        val studied = setOf(ago(0), ago(2), ago(3))
        assertEquals(3, Repository.computeStreak(studied, today, setOf(ago(1))))
    }

    @Test
    fun fullDayGap_breaksStreak() {
        val studied = setOf(ago(0), ago(1), ago(3), ago(4))
        assertEquals(2, Repository.computeStreak(studied, today))
    }

    @Test
    fun noStudy_isZero() {
        assertEquals(0, Repository.computeStreak(emptySet(), today))
    }

    @Test
    fun longStreak_isNotCapped() {
        val studied = (0..69).map { ago(it) }.toSet()
        assertEquals(70, Repository.computeStreak(studied, today))
    }
}
