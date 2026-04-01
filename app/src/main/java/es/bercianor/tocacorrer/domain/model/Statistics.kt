package es.bercianor.tocacorrer.domain.model

/**
 * Workout statistics for a date range.
 */
data class Statistics(
    val totalDistanceMeters: Double,
    val totalDurationSeconds: Long,
    val workoutCount: Int
)
