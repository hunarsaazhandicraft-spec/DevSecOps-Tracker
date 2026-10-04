package com.devsecops.tracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FsrsTest {

    private val day = 86_400_000L
    private val now = 1_700_000_000_000L

    @Test
    fun forgettingCurve_isOneAtZeroAndPointNineAtStability() {
        for (s in listOf(1.0, 5.0, 30.0)) {
            assertEquals(1.0, Fsrs.forgettingCurve(0.0, s), 1e-9)
            assertEquals(0.9, Fsrs.forgettingCurve(s, s), 1e-3)
        }
    }

    @Test
    fun forgettingCurve_decreasesWithTime() {
        var prev = Fsrs.forgettingCurve(0.0, 5.0)
        for (t in 1..40) {
            val r = Fsrs.forgettingCurve(t.toDouble(), 5.0)
            assertTrue(r < prev)
            prev = r
        }
    }

    @Test
    fun nextInterval_atNinetyPercentEqualsStability() {
        for (s in listOf(1.0, 5.0, 30.0)) {
            assertEquals(s, Fsrs.nextInterval(s, 0.9), 1e-3)
        }
    }

    @Test
    fun firstReview_usesPublishedInitialStabilities() {
        val expected = doubleArrayOf(0.5701, 1.4436, 4.1386, 10.9355)
        for (g in 1..4) {
            val m = Fsrs.review(Fsrs.NEW_CARD, g, now)
            assertEquals(expected[g - 1], m.stability, 1e-9)
        }
    }

    @Test
    fun firstReview_difficultyIsClampedAndFollowsFormula() {
        for (g in 1..4) {
            val d = Fsrs.review(Fsrs.NEW_CARD, g, now).difficulty
            assertTrue(d >= 1.0 && d <= 10.0)
            val expected = (5.1443 - 1.2006 * (g - 3)).coerceIn(1.0, 10.0)
            assertEquals(expected, d, 1e-9)
        }
    }

    @Test
    fun stabilityOrdering_againHardGoodEasy() {
        val base = Fsrs.replay(listOf(Fsrs.GOOD to now, Fsrs.GOOD to now + 3 * day))
        val at = now + 10 * day
        val a = Fsrs.review(base, Fsrs.AGAIN, at).stability
        val h = Fsrs.review(base, Fsrs.HARD, at).stability
        val g = Fsrs.review(base, Fsrs.GOOD, at).stability
        val e = Fsrs.review(base, Fsrs.EASY, at).stability
        assertTrue(a < h)
        assertTrue(h < g)
        assertTrue(g < e)
    }

    @Test
    fun replay_ofEmptyListIsNewCard() {
        val m = Fsrs.replay(emptyList())
        assertTrue(m.isNew)
        assertEquals(Fsrs.NEW_CARD, m)
    }

    // Code: elapsed 0 days => R = 1 => exp(w10*(1-R)) - 1 = 0 => stability unchanged.
    @Test
    fun sameDayGoodRepeat_doesNotIncreaseStability() {
        val first = Fsrs.review(Fsrs.NEW_CARD, Fsrs.GOOD, now)
        val again = Fsrs.review(first, Fsrs.GOOD, now + 1000L)
        assertEquals(first.stability, again.stability, 1e-6)
    }

    @Test
    fun ratingOf_mapsResultStrings() {
        assertEquals(1, Fsrs.ratingOf("again"))
        assertEquals(4, Fsrs.ratingOf(" Easy "))
        assertEquals(null, Fsrs.ratingOf("bogus"))
    }

    @Test
    fun selfTest_passes() {
        assertTrue(Fsrs.selfTest())
    }
}
