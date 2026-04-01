package es.bercianor.tocacorrer.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity representing a completed workout.
 */
@Entity(
    tableName = "workouts",
    indices = [Index(value = ["startTime"])]
)
data class Workout(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startTime: Long,              // Timestamp in milliseconds
    val originalRoutine: String,      // e.g., "C10 - D1 - C10"
    val totalDistanceMeters: Double = 0.0,
    val totalDurationSeconds: Long = 0,
    val averagePaceMinPerKm: Double = 0.0,  // Minutes per kilometer
    val noGps: Boolean = false       // true if it's a no-GPS workout (treadmill)
)

/**
 * Calculates pace in min/km from the workout's distance and duration.
 * Returns 0.0 if distance is zero or unknown.
 */
fun Workout.calculatedPaceMinPerKm(): Double {
    if (totalDistanceMeters <= 0) return 0.0
    val distanceKm = totalDistanceMeters / 1000.0
    val durationMinutes = totalDurationSeconds / 60.0
    return durationMinutes / distanceKm
}
