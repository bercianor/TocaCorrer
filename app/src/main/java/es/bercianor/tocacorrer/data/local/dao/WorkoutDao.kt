package es.bercianor.tocacorrer.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import es.bercianor.tocacorrer.data.local.entity.Workout
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(workout: Workout): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(workouts: List<Workout>): List<Long>

    @Update
    suspend fun update(workout: Workout)

    @Delete
    suspend fun delete(workout: Workout)

    @Query("SELECT * FROM workouts ORDER BY startTime DESC")
    fun getAllWorkouts(): Flow<List<Workout>>

    @Query("SELECT * FROM workouts ORDER BY startTime DESC")
    suspend fun getAllWorkoutsSync(): List<Workout>

    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun getWorkoutById(id: Long): Workout?

    @Query("SELECT * FROM workouts WHERE startTime >= :startTime AND startTime <= :endTime ORDER BY startTime DESC")
    fun getWorkoutsBetweenDates(startTime: Long, endTime: Long): Flow<List<Workout>>

    @Query("SELECT * FROM workouts ORDER BY startTime DESC LIMIT :limit")
    fun getRecentWorkouts(limit: Int): Flow<List<Workout>>

    @Query("SELECT SUM(totalDistanceMeters) FROM workouts WHERE startTime >= :startTime AND startTime <= :endTime")
    suspend fun getTotalDistanceBetweenDates(startTime: Long, endTime: Long): Double?

    @Query("SELECT SUM(totalDurationSeconds) FROM workouts WHERE startTime >= :startTime AND startTime <= :endTime")
    suspend fun getTotalDurationBetweenDates(startTime: Long, endTime: Long): Long?

    @Query("SELECT COUNT(*) FROM workouts WHERE startTime >= :startTime AND startTime <= :endTime")
    suspend fun getCountBetweenDates(startTime: Long, endTime: Long): Int
}
