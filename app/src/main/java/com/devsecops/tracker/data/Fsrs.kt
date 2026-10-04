package com.devsecops.tracker.data

import kotlin.math.exp
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.pow

// Memory state of one card, derived on-device by replaying its reviews.
// stability = days for retrievability to fall from 100% to 90%.
// difficulty = 1 (easy) .. 10 (hard). stability <= 0 means "never reviewed".
data class CardMemory(
    val stability: Double,
    val difficulty: Double,
    val lastReviewMs: Long
) {
    val isNew: Boolean get() = stability <= 0.0
}

// FSRS-4.5 style scheduler, default parameters. Pure Kotlin, no Android deps.
// Ratings: 1 = again, 2 = hard, 3 = good, 4 = easy.
object Fsrs {

    private val W = doubleArrayOf(
        0.5701, 1.4436, 4.1386, 10.9355, 5.1443, 1.2006, 0.8627, 0.0362,
        1.629, 0.1342, 1.0166, 2.1174, 0.0839, 0.3204, 1.4676, 0.219, 2.8237
    )
    private const val DECAY = -0.5
    private const val FACTOR = 19.0 / 81.0   // chosen so R(t = S) = 0.9
    private const val DAY_MS = 86_400_000.0
    private const val MIN_S = 0.01
    private const val MAX_INTERVAL_DAYS = 36500.0

    const val AGAIN = 1
    const val HARD = 2
    const val GOOD = 3
    const val EASY = 4

    val NEW_CARD = CardMemory(0.0, 0.0, 0L)

    // App result string -> FSRS rating (null if unknown).
    fun ratingOf(result: String?): Int? = when (result?.trim()?.lowercase()) {
        "again" -> AGAIN
        "hard" -> HARD
        "good" -> GOOD
        "easy" -> EASY
        else -> null
    }

    // Whole LOCAL calendar days between two instants (device time zone),
    // never negative. Two reviews on the same local date => 0 days, as in
    // FSRS-4.5 where elapsed time is counted in whole days.
    fun elapsedDays(fromMs: Long, toMs: Long): Int {
        val zone = TimeZone.currentSystemDefault()
        val a = Instant.fromEpochMilliseconds(fromMs).toLocalDateTime(zone).date.toEpochDays().toLong()
        val b = Instant.fromEpochMilliseconds(toMs).toLocalDateTime(zone).date.toEpochDays().toLong()
        val d = b - a
        return if (d < 0L) 0 else d.toInt()
    }

    fun forgettingCurve(elapsedDays: Double, stability: Double): Double {
        if (stability <= 0.0) return 0.0
        val t = if (elapsedDays < 0.0) 0.0 else elapsedDays
        return (1.0 + FACTOR * t / stability).pow(DECAY)
    }

    // Days until retrievability decays to targetRetention.
    fun nextInterval(stability: Double, targetRetention: Double): Double {
        val r = targetRetention.coerceIn(0.7, 0.99)
        if (stability <= 0.0) return 0.0
        val days = stability / FACTOR * (r.pow(1.0 / DECAY) - 1.0)
        return days.coerceIn(0.0, MAX_INTERVAL_DAYS)
    }

    private fun clampD(d: Double) = d.coerceIn(1.0, 10.0)

    private fun initStability(rating: Int) = maxOf(MIN_S, W[(rating - 1).coerceIn(0, 3)])

    private fun initDifficulty(rating: Int) = clampD(W[4] - W[5] * (rating - 3))

    // Apply one review to a memory state.
    fun review(mem: CardMemory, rating: Int, atMs: Long): CardMemory {
        val g = rating.coerceIn(1, 4)
        if (mem.isNew) {
            return CardMemory(initStability(g), initDifficulty(g), atMs)
        }
        val elapsed = elapsedDays(mem.lastReviewMs, atMs).toDouble()
        val s = mem.stability
        val d = mem.difficulty
        val r = forgettingCurve(elapsed, s)

        // Difficulty: linear step, then mean reversion towards the "good" start.
        val d0 = W[4] - W[5] * (3 - 3)
        val dStep = d - W[6] * (g - 3)
        val newD = clampD(W[7] * d0 + (1.0 - W[7]) * dStep)

        val newS = if (g == AGAIN) {
            val f = W[11] * d.pow(-W[12]) * ((s + 1.0).pow(W[13]) - 1.0) * exp(W[14] * (1.0 - r))
            // FSRS-4.5 has no cap here. The min(f, s) clamp only exists from
            // FSRS-5 on, so it is removed to stay consistent with this model.
            f
        } else {
            val hardPenalty = if (g == HARD) W[15] else 1.0
            val easyBonus = if (g == EASY) W[16] else 1.0
            s * (1.0 + exp(W[8]) * (11.0 - d) * s.pow(-W[9]) *
                (exp(W[10] * (1.0 - r)) - 1.0) * hardPenalty * easyBonus)
        }
        return CardMemory(maxOf(MIN_S, newS), newD, atMs)
    }

    // Replays (rating, epochMs) pairs in time order. Empty list = NEW_CARD.
    fun replay(reviews: List<Pair<Int, Long>>): CardMemory {
        var mem = NEW_CARD
        for ((rating, at) in reviews.sortedBy { it.second }) {
            mem = review(mem, rating, at)
        }
        return mem
    }

    // Retrievability at nowMs, using whole local calendar days since the
    // last review (so it only changes at local midnight).
    fun retrievability(mem: CardMemory, nowMs: Long): Double {
        if (mem.isNew) return 0.0
        return forgettingCurve(elapsedDays(mem.lastReviewMs, nowMs).toDouble(), mem.stability)
    }

    // Same number, for a given local calendar date.
    fun retrievabilityOn(mem: CardMemory, today: LocalDate): Double {
        if (mem.isNew) return 0.0
        val zone = TimeZone.currentSystemDefault()
        val last = Instant.fromEpochMilliseconds(mem.lastReviewMs).toLocalDateTime(zone).date
        val d = today.toEpochDays().toLong() - last.toEpochDays().toLong()
        return forgettingCurve(if (d < 0L) 0.0 else d.toDouble(), mem.stability)
    }

    // Days until the card would next be scheduled for each rating (1..4),
    // rounded, at least 1.
    fun previewIntervals(mem: CardMemory, nowMs: Long, targetRetention: Double): Map<Int, Int> {
        val out = LinkedHashMap<Int, Int>()
        for (g in 1..4) {
            val next = review(mem, g, nowMs)
            val days = Math.round(nextInterval(next.stability, targetRetention)).toInt()
            out[g] = maxOf(1, days)
        }
        return out
    }

    // Monotonic sanity checks. Callable from a debug path.
    fun selfTest(): Boolean = runCatching {
        val now = 1_700_000_000_000L
        val day = 86_400_000L
        var ok = true
        // Curve: R(0) = 1, R(S) = 0.9, strictly decreasing.
        ok = ok && Math.abs(forgettingCurve(0.0, 5.0) - 1.0) < 1e-9
        ok = ok && Math.abs(forgettingCurve(5.0, 5.0) - 0.9) < 1e-6
        var prev = 1.0
        for (t in 1..60) {
            val r = forgettingCurve(t.toDouble(), 5.0)
            ok = ok && r < prev && r > 0.0
            prev = r
        }
        // Interval at 90% equals stability; higher target = shorter interval.
        ok = ok && Math.abs(nextInterval(7.0, 0.9) - 7.0) < 1e-6
        ok = ok && nextInterval(7.0, 0.95) < nextInterval(7.0, 0.85)
        // Initial stability grows with rating; difficulty shrinks.
        ok = ok && initStability(1) < initStability(2) && initStability(2) < initStability(3) &&
            initStability(3) < initStability(4)
        ok = ok && initDifficulty(1) > initDifficulty(3) && initDifficulty(3) > initDifficulty(4)
        // Ordering of intervals for an established card.
        val base = replay(listOf(GOOD to now, GOOD to now + 3 * day))
        val at = now + 10 * day
        val a = review(base, AGAIN, at).stability
        val h = review(base, HARD, at).stability
        val g = review(base, GOOD, at).stability
        val e = review(base, EASY, at).stability
        ok = ok && a < h && h < g && g < e
        ok = ok && a < base.stability && g > base.stability
        // Difficulty stays in range and rises on lapse.
        ok = ok && review(base, AGAIN, at).difficulty > review(base, EASY, at).difficulty
        ok = ok && base.difficulty in 1.0..10.0
        // Retrievability decays with time; new card is 0.
        ok = ok && retrievability(base, at) > retrievability(base, at + 20 * day)
        ok = ok && retrievability(NEW_CARD, at) == 0.0
        // Preview is monotonic non-decreasing.
        val p = previewIntervals(base, at, 0.9)
        ok = ok && p.getValue(1) <= p.getValue(2) && p.getValue(2) <= p.getValue(3) &&
            p.getValue(3) <= p.getValue(4)
        ok
    }.getOrDefault(false)
}
