package es.bercianor.tocacorrer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import es.bercianor.tocacorrer.data.local.entity.GpsPoint

@Dao
interface GpsPointDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(point: GpsPoint): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(points: List<GpsPoint>): List<Long>

    @Query("SELECT * FROM gps_points WHERE workoutId = :workoutId ORDER BY timestamp ASC")
    suspend fun getPointsByWorkoutSync(workoutId: Long): List<GpsPoint>

    @Query("SELECT * FROM gps_points WHERE workoutId IN (:workoutIds) ORDER BY workoutId ASC, timestamp ASC")
    suspend fun getPointsByWorkoutIds(workoutIds: List<Long>): List<GpsPoint>

    @Query("SELECT * FROM gps_points ORDER BY workoutId ASC, timestamp ASC")
    suspend fun getAllGpsPointsSync(): List<GpsPoint>

}
