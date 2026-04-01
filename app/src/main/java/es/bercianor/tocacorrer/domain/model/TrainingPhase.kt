package es.bercianor.tocacorrer.domain.model

/**
 * Phase types for workout DSL.
 *
 * Tokens accepted by parser:
 * - D: Descansar/Walk (Spanish) / Walk/Rest (English)
 * - T: Trote/Trot (Spanish) / Jog (English)
 * - R: Rodaje/Easy run (Spanish) / Easy run (English)
 * - RA: Rodaje alegre/Easy cheerful (Spanish) / Easy cheerful (English)
 * - RF: Rodaje fuerte/Strong easy run (Spanish) / Strong easy run (English)
 * - F: Fartlek (Spanish/English)
 * - P: Progresivos/Progressives (Spanish/English)
 * - RC: Ritmo de competición/Race pace (Spanish) / Race pace (English)
 * - X: Extra (Spanish/English) — used as fallback when no phase is active
 */
enum class PhaseType(val letter: String) {
    REST         ("D"),
    TROT         ("T"),
    EASY         ("R"),
    EASY_CHEERFUL("RA"),
    EASY_STRONG  ("RF"),
    FARTLEK      ("F"),
    PROGRESSIVES ("P"),
    RACE_PACE    ("RC"),
    EXTRA        ("X");
}

/**
 * Represents an individual phase in a workout.
 *
 * A phase is either time-based (distanceMeters == null) or distance-based (distanceMeters != null).
 * When distance-based, [durationSeconds] is 0 and has no meaning.
 */
data class TrainingPhase(
    val type: PhaseType,
    val durationSeconds: Int,              // Used when distanceMeters == null
    val distanceMeters: Double? = null,   // When non-null, phase is distance-based
    val seriesNumber: Int? = null          // Null if not part of a series; otherwise series index (1-based)
) {
    /**
     * Duration in minutes (to display to user). Only meaningful for time-based phases.
     *
     * Integer division is safe here: the DSL parser always creates time-based phases with
     * [durationSeconds] = minutes * 60 (or the default of 60 seconds), so the value is
     * always an exact multiple of 60 and no precision is lost.
     */
    val durationMinutes: Int
        get() = durationSeconds / 60

    /**
     * Duration string key for localization (used by UI to get localized text).
     * Returns: "min" or "km" depending on whether distanceMeters is set.
     */
    val durationUnitKey: String
        get() = if (distanceMeters != null) "km" else "min"

    /**
     * Canonical display letter of the phase (used for GPS points storage).
     * Delegates to [PhaseType.letter] — single source of truth.
     */
    val letter: String
        get() = type.letter
}

/**
 * Represents a complete parsed workout, ready to be executed.
 *
 * [totalDurationSeconds] sums only time-based phases. Distance-based phases contribute 0.
 * [totalDistanceMeters] sums only distance-based phases. Time-only phases contribute 0.
 */
data class WorkoutRoutine(
    val phases: List<TrainingPhase>,
    val totalDurationSeconds: Int
) {
    /**
     * Total distance across all phases in meters.
     * Time-only phases contribute 0.0.
     */
    val totalDistanceMeters: Double
        get() = phases.sumOf { it.distanceMeters ?: 0.0 }

    companion object {
        val EMPTY = WorkoutRoutine(emptyList(), 0)
    }
}
