package com.scholar.app.srs

enum class CardType { CHAR_RECOGNITION, WORD_RECOGNITION, SENTENCE_CLOZE, AUDIO, GRAMMAR, PRODUCTION }
enum class Rating(val value: Int) { AGAIN(1), HARD(2), GOOD(3), EASY(4) }

/** One projected outcome per button so the UI can show "Good · 6d" like Anki. */
data class GradeProjection(val rating: Rating, val intervalLabel: String)

/** Result of applying a rating. */
data class ReviewResult(val stability: Double, val difficulty: Double, val intervalDays: Double)

/** Thin domain service over Fsrs6, used by the review flow. */
class Scheduler(val fsrs: Fsrs6 = Fsrs6()) {

    fun project(stability: Double, difficulty: Double, elapsedDays: Double, isNew: Boolean): List<GradeProjection> =
        Rating.entries.map { r ->
            val s = nextState(stability, difficulty, elapsedDays, isNew, r).stability
            GradeProjection(r, fsrs.humanInterval(s))
        }

    fun apply(stability: Double, difficulty: Double, elapsedDays: Double, isNew: Boolean, rating: Rating): ReviewResult {
        val n = nextState(stability, difficulty, elapsedDays, isNew, rating)
        return ReviewResult(n.stability, n.difficulty, fsrs.intervalDays(n.stability))
    }

    private fun nextState(stability: Double, difficulty: Double, elapsedDays: Double, isNew: Boolean, r: Rating): Fsrs6.State =
        if (isNew) fsrs.initial(r.value)
        else fsrs.next(Fsrs6.State(stability, difficulty), r.value, elapsedDays)
}
