package es.bercianor.tocacorrer.data.repository

import androidx.room.withTransaction
import es.bercianor.tocacorrer.data.local.AppDatabase
import es.bercianor.tocacorrer.data.local.dao.WorkoutDao
import es.bercianor.tocacorrer.data.local.dao.GpsPointDao
import es.bercianor.tocacorrer.data.local.entity.Workout
import es.bercianor.tocacorrer.data.local.entity.GpsPoint
import es.bercianor.tocacorrer.domain.model.Statistics
import kotlinx.coroutines.flow.Flow

/**
 * Repository to access workout data.
 */
class WorkoutRepository(
    private val database: AppDatabase,
    private val workoutDao: WorkoutDao,
    private val gpsPointDao: GpsPointDao
) {
    /**
     * Gets all workouts as Flow.
     */
    fun getAllWorkouts(): Flow<List<Workout>> {
        return workoutDao.getAllWorkouts()
    }

    /**
     * Gets a workout by its ID.
     */
    suspend fun getWorkoutById(id: Long): Workout? {
        return workoutDao.getWorkoutById(id)
    }

    /**
     * Gets workouts between two dates.
     */
    fun getWorkoutsBetweenDates(startTime: Long, endTime: Long): Flow<List<Workout>> {
        return workoutDao.getWorkoutsBetweenDates(startTime, endTime)
    }

    /**
     * Gets the most recent workouts.
     */
    fun getRecentWorkouts(limit: Int = 10): Flow<List<Workout>> {
        return workoutDao.getRecentWorkouts(limit)
    }

    /**
     * Gets GPS points of a workout (sync).
     */
    suspend fun getPointsByWorkoutSync(workoutId: Long): List<GpsPoint> {
        return gpsPointDao.getPointsByWorkoutSync(workoutId)
    }

    /**
     * Gets GPS points of several workouts in a single query, grouped by workout ID.
     * IDs are chunked into batches of 500 to avoid SQLite's IN-clause limit of 999.
     */
    suspend fun getPointsByWorkout(workoutIds: List<Long>): Map<Long, List<GpsPoint>> {
        if (workoutIds.isEmpty()) return emptyMap()
        return workoutIds.chunked(500)
            .flatMap { batch -> gpsPointDao.getPointsByWorkoutIds(batch) }
            .groupBy { it.workoutId }
    }

    /**
     * Gets all GPS points synchronously (single query, all workouts).
     */
    suspend fun getAllGpsPointsSync(): List<GpsPoint> {
        return gpsPointDao.getAllGpsPointsSync()
    }

    /**
     * Gets all workouts synchronously (blocking).
     */
    suspend fun getAllWorkoutsSync(): List<Workout> {
        return workoutDao.getAllWorkoutsSync()
    }

    /**
     * Saves a new GPS point.
     */
    suspend fun insertGpsPoint(point: GpsPoint): Long {
        return gpsPointDao.insert(point)
    }

    /**
     * Saves a new workout.
     */
    suspend fun insertWorkout(workout: Workout): Long {
        return workoutDao.insert(workout)
    }

    /**
     * Updates a workout.
     */
    suspend fun updateWorkout(workout: Workout) {
        workoutDao.update(workout)
    }

    /**
     * Deletes a workout.
     */
    suspend fun deleteWorkout(workout: Workout) {
        workoutDao.delete(workout)
    }

    /**
     * Imports workouts and GPS points atomically in a single transaction.
     * If any insert fails, the entire import is rolled back to prevent partial state.
     */
    suspend fun importAll(workouts: List<Workout>, gpsPoints: List<GpsPoint>): Int {
        return database.withTransaction {
            workoutDao.insertAll(workouts)
            gpsPointDao.insertAll(gpsPoints)
            workouts.size
        }
    }

    /**
     * Inserts a single workout during streaming import (preserving the original ID via REPLACE).
     */
    suspend fun insertWorkoutImport(workout: Workout): Long {
        return workoutDao.insert(workout)
    }

    /**
     * Inserts a batch of GPS points during streaming import.
     */
    suspend fun insertGpsPoints(points: List<GpsPoint>) {
        gpsPointDao.insertAll(points)
    }

    /**
     * Runs [block] inside a single database transaction.
     * Used by BackupManager to make the streaming import atomic.
     */
    suspend fun <R> withImportTransaction(block: suspend () -> R): R {
        return database.withTransaction { block() }
    }

    /**
     * Gets statistics between dates.
     */
    suspend fun getStatistics(startTime: Long, endTime: Long): Statistics {
        val distance = workoutDao.getTotalDistanceBetweenDates(startTime, endTime) ?: 0.0
        val duration = workoutDao.getTotalDurationBetweenDates(startTime, endTime) ?: 0L
        val count = workoutDao.getCountBetweenDates(startTime, endTime)
        
        return Statistics(
            totalDistanceMeters = distance,
            totalDurationSeconds = duration,
            workoutCount = count
        )
    }
}

