package com.scholar.app.srs

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * FSRS-6 (Free Spaced Repetition Scheduler), implemented natively.
 * Reference: open-spaced-repetition / FSRS. 21 learnable weights `w`; the decay of
 * the forgetting curve is itself a parameter (w[20]), the v6 change over v5.
 *
 *   Forgetting curve:  R(t,S) = (1 + FACTOR · t/S) ^ DECAY
 *   with DECAY = −w[20]  and  FACTOR = 0.9^(1/DECAY) − 1   (so R(S) ≡ 0.9)
 *   Interval for desired retention r:  I = (S/FACTOR) · ( r^(1/DECAY) − 1 )
 *
 * After ~1–2k of the user's own reviews the weights can be re-optimised; until then
 * the published defaults are used. Self-contained, no external dependency.
 */
class Fsrs6(
    val w: DoubleArray = DEFAULTS,
    val desiredRetention: Double = 0.90,
) {
    private val decay = -w[20]
    private val factor = 0.9.pow(1.0 / decay) - 1.0

    data class State(val stability: Double, val difficulty: Double)

    /** Stability/difficulty for a brand-new card given the first rating (1..4). */
    fun initial(rating: Int): State {
        val s = max(w[rating - 1], 0.1)
        val d = clampD(w[4] - exp(w[5] * (rating - 1)) + 1.0)
        return State(s, d)
    }

    /** Retrievability of a card `elapsedDays` after its last review. */
    fun retrievability(stability: Double, elapsedDays: Double): Double =
        (1.0 + factor * elapsedDays / stability).pow(decay)

    /** Next state after reviewing a card with `rating`, `elapsedDays` since last seen. */
    fun next(prev: State, rating: Int, elapsedDays: Double): State {
        val r = retrievability(prev.stability, max(elapsedDays, 0.0))
        val d = nextDifficulty(prev.difficulty, rating)
        val s = if (rating == AGAIN) lapseStability(prev, r) else recallStability(prev, r, rating)
        return State(max(s, 0.1), d)
    }

    /** Days until the card should next be shown, to hit `desiredRetention`. */
    fun intervalDays(stability: Double): Double {
        val raw = (stability / factor) * (desiredRetention.pow(1.0 / decay) - 1.0)
        return max(raw, 0.0)
    }

    fun humanInterval(stability: Double): String {
        val d = intervalDays(stability)
        return when {
            d < 1.0 / 24 -> "<1m"
            d < 1.0 -> "${(d * 24).toInt().coerceAtLeast(1)}h"
            d < 30 -> "${d.toInt().coerceAtLeast(1)}d"
            d < 365 -> "${(d / 30).toInt()}mo"
            else -> "${"%.1f".format(d / 365)}y"
        }
    }

    // --- internals ---
    private fun nextDifficulty(d: Double, rating: Int): Double {
        val delta = -w[6] * (rating - 3)
        val damped = d + delta * (10.0 - d) / 9.0          // linear damping
        val reverted = w[7] * initial(EASY).difficulty + (1 - w[7]) * damped  // mean reversion
        return clampD(reverted)
    }

    private fun recallStability(prev: State, r: Double, rating: Int): Double {
        val hard = if (rating == HARD) w[15] else 1.0
        val easy = if (rating == EASY) w[16] else 1.0
        val inc = exp(w[8]) * (11.0 - prev.difficulty) *
            prev.stability.pow(-w[9]) * (exp(w[10] * (1.0 - r)) - 1.0) * hard * easy
        return prev.stability * (1.0 + inc)
    }

    private fun lapseStability(prev: State, r: Double): Double {
        val s = w[11] * prev.difficulty.pow(-w[12]) *
            ((prev.stability + 1.0).pow(w[13]) - 1.0) * exp(w[14] * (1.0 - r))
        return min(s, prev.stability)                       // post-lapse S never exceeds prior
    }

    private fun clampD(d: Double) = min(max(d, 1.0), 10.0)

    companion object {
        const val AGAIN = 1; const val HARD = 2; const val GOOD = 3; const val EASY = 4
        // FSRS-6 published default weights.
        val DEFAULTS = doubleArrayOf(
            0.2172, 1.1771, 3.2602, 16.1507, 7.0114, 0.57, 2.0966, 0.0069, 1.5261,
            0.112, 1.0178, 1.849, 0.1133, 0.3127, 2.2934, 0.2191, 3.0004, 0.7536,
            0.3332, 0.1437, 0.2,
        ).also { require(it.size == 21) }
    }
}
