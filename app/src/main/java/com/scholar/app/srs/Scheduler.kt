package com.scholar.app.srs

import kotlin.math.min

enum class CardType { CHAR_RECOGNITION, WORD_RECOGNITION, SENTENCE_CLOZE, AUDIO, GRAMMAR, PRODUCTION }
enum class Rating(val value: Int) { AGAIN(1), HARD(2), GOOD(3), EASY(4) }

/** One projected outcome per button so the UI can show "Good · 6d" like Anki. */
data class GradeProjection(val rating: Rating, val intervalLabel: String)

/** Result of applying a rating. */
data class ReviewResult(val stability: Double, val difficulty: Double, val intervalDays: Double)

/**
 * Thin domain service over Fsrs6, used by the review flow.
 *
 * On top of plain FSRS it applies **early-review interval caps**: a brand-new card you rate
 * "Easy" should not vanish for 16 days — getting it right once in a quiet moment is weak
 * evidence you'll still have it in two weeks. So for a card's first few reps we clamp the
 * scheduled interval (Anki-style learning steps). The *stability* FSRS computes is left
 * untouched, so once the card graduates the curve picks up exactly where it would have — we
 * only shorten the next gap while the memory is still young and unproven.
 *
 * [Fsrs6.desiredRetention] is configurable (Settings): raising it shortens every interval.
 */
class Scheduler(
    val fsrs: Fsrs6 = Fsrs6(),
    /** Cap, in days, on a card's next interval as a function of how many reps it has had.
        Absent past the learning window → trust FSRS fully. */
    private val youngCaps: List<Map<Rating, Double>> = DEFAULT_YOUNG_CAPS,
) {

    fun project(stability: Double, difficulty: Double, elapsedDays: Double, reps: Int): List<GradeProjection> =
        Rating.entries.map { r ->
            val res = apply(stability, difficulty, elapsedDays, reps, r)
            GradeProjection(r, fsrs.formatDays(res.intervalDays))
        }

    fun apply(stability: Double, difficulty: Double, elapsedDays: Double, reps: Int, rating: Rating): ReviewResult {
        val isNew = reps == 0
        val n = nextState(stability, difficulty, elapsedDays, isNew, rating)
        val raw = fsrs.intervalDays(n.stability)
        return ReviewResult(n.stability, n.difficulty, cap(reps, rating, raw))
    }

    /** Clamp the next interval while the card is young, so confidence is earned over several
        spaced successes rather than a single lucky "Easy". */
    private fun cap(reps: Int, rating: Rating, days: Double): Double {
        val ceil = youngCaps.getOrNull(reps)?.get(rating) ?: return days
        return min(days, ceil)
    }

    private fun nextState(stability: Double, difficulty: Double, elapsedDays: Double, isNew: Boolean, r: Rating): Fsrs6.State =
        if (isNew) fsrs.initial(r.value)
        else fsrs.next(Fsrs6.State(stability, difficulty), r.value, elapsedDays)

    companion object {
        // Indexed by rep count. Reps 0..2 are the "learning" window; from rep 3 on, no cap.
        // Again is effectively a relapse (≈10 min); Good/Easy ramp up gradually.
        val DEFAULT_YOUNG_CAPS: List<Map<Rating, Double>> = listOf(
            mapOf(Rating.AGAIN to 0.007, Rating.HARD to 0.5, Rating.GOOD to 1.0, Rating.EASY to 4.0),
            mapOf(Rating.AGAIN to 0.007, Rating.HARD to 1.0, Rating.GOOD to 3.0, Rating.EASY to 7.0),
            mapOf(Rating.AGAIN to 0.007, Rating.HARD to 2.0, Rating.GOOD to 7.0, Rating.EASY to 15.0),
        )
    }
}
