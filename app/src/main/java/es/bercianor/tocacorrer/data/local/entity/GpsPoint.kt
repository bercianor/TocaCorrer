package es.bercianor.tocacorrer.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity representing a GPS point recorded during a workout.
 */
@Entity(
    tableName = "gps_points",
    foreignKeys = [
        ForeignKey(
            entity = Workout::class,
            parentColumns = ["id"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["workoutId"])]
)
data class GpsPoint(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val workoutId: Long,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val timestamp: Long,                 // Timestamp in milliseconds
    val phase: String,                 // Phase letter from PhaseType.letter, e.g. "R" for EASY, "D" for REST
    @ColumnInfo(name = "phase_index")
    val phaseIndex: Int = 0            // 0-based index into the routine phases list
)
