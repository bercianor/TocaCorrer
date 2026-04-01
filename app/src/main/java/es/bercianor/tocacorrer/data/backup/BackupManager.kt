package es.bercianor.tocacorrer.data.backup

import android.content.Context
import android.net.Uri
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import es.bercianor.tocacorrer.data.local.entity.Workout
import es.bercianor.tocacorrer.data.local.entity.GpsPoint
import es.bercianor.tocacorrer.data.repository.WorkoutRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.buffer
import okio.sink
import okio.source

/**
 * Manager for backup and restore of the database.
 */
class BackupManager(
    private val context: Context,
    private val repository: WorkoutRepository
) {

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    /**
     * Exports all data to JSON using streaming output (Moshi JsonWriter) to avoid
     * building the entire JSON string in memory.
     *
     * GPS points are fetched one workout at a time so that only one workout's worth of
     * points lives in memory at once. This keeps memory usage bounded even for users
     * with hundreds of thousands of recorded GPS points.
     */
    suspend fun export(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val workoutAdapter = moshi.adapter(WorkoutBackup::class.java)
            val pointAdapter = moshi.adapter(GpsPointBackup::class.java)

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                val sink = outputStream.sink().buffer()
                val writer = JsonWriter.of(sink)
                writer.indent = "  "

                writer.beginObject()
                writer.name("version").value(1)
                writer.name("exportDate").value(System.currentTimeMillis())

                // Encode workouts one-by-one into the streaming writer
                val workouts = repository.getAllWorkoutsSync()
                writer.name("workouts")
                writer.beginArray()
                workouts.forEach { workout ->
                    workoutAdapter.toJson(writer, workout.toBackup())
                }
                writer.endArray()

                // Encode GPS points per workout to keep memory usage bounded.
                // Each workout's points are fetched, written, and discarded before
                // the next workout's points are loaded.
                writer.name("gpsPoints")
                writer.beginArray()
                workouts.forEach { workout ->
                    repository.getPointsByWorkoutSync(workout.id).forEach { point ->
                        pointAdapter.toJson(writer, point.toBackup())
                    }
                }
                writer.endArray()

                writer.endObject()
                writer.flush()
                sink.flush()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Imports data from JSON using streaming to avoid OOM with large files.
     * Workouts are inserted one by one; GPS points are buffered in chunks of 500
     * to keep memory bounded for large datasets.
     *
     * IDs from the backup JSON are stripped — Room auto-generates new IDs to
     * prevent collisions with existing data. GPS points have their workoutId
     * remapped from the backup's old workout ID to the newly-inserted workout ID.
     */
    suspend fun import(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val workoutAdapter = moshi.adapter(WorkoutBackup::class.java)
            val pointAdapter = moshi.adapter(GpsPointBackup::class.java)

            var workoutCount = 0
            // Maps backup workout ID → newly-inserted workout ID
            val workoutIdMap = mutableMapOf<Long, Long>()
            val pointBuffer = mutableListOf<GpsPointBackup>()

            suspend fun flushPoints() {
                if (pointBuffer.isNotEmpty()) {
                    repository.insertGpsPoints(pointBuffer.map { p ->
                        // Remap old workoutId to new auto-generated workoutId
                        val newWorkoutId = workoutIdMap[p.workoutId] ?: p.workoutId
                        p.toEntityWithIds(id = 0L, workoutId = newWorkoutId)
                    })
                    pointBuffer.clear()
                }
            }

            repository.withImportTransaction {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val reader = JsonReader.of(inputStream.source().buffer())

                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "workouts" -> {
                                reader.beginArray()
                                while (reader.hasNext()) {
                                    val w = workoutAdapter.fromJson(reader)
                                    if (w != null) {
                                        // Strip the original ID so Room auto-generates a new one
                                        val newId = repository.insertWorkoutImport(w.toEntityStripped())
                                        workoutIdMap[w.id] = newId
                                        workoutCount++
                                    }
                                }
                                reader.endArray()
                            }
                            "gpsPoints" -> {
                                reader.beginArray()
                                while (reader.hasNext()) {
                                    val p = pointAdapter.fromJson(reader)
                                    if (p != null) {
                                        pointBuffer.add(p)
                                        if (pointBuffer.size >= 500) {
                                            flushPoints()
                                        }
                                    }
                                }
                                reader.endArray()
                                flushPoints()
                            }
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                } ?: throw Exception("Could not read file")
            }

            Result.success(workoutCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Generates a filename for the backup.
     * Uses Locale.US to ensure ASCII digits regardless of device locale.
     */
    fun generateFileName(): String {
        val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .format(java.util.Date())
        return "tocacorrer_backup_$date.json"
    }
}

@JsonClass(generateAdapter = true)
data class WorkoutBackup(
    val id: Long,
    val startTime: Long,
    val originalRoutine: String,
    val totalDistanceMeters: Double,
    val totalDurationSeconds: Long,
    val averagePaceMinPerKm: Double,
    val noGps: Boolean = false
)

@JsonClass(generateAdapter = true)
data class GpsPointBackup(
    val id: Long,
    val workoutId: Long,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val timestamp: Long,
    val phase: String,
    val phaseIndex: Int = 0
)

// Conversion extensions
private fun Workout.toBackup() = WorkoutBackup(
    id = id,
    startTime = startTime,
    originalRoutine = originalRoutine,
    totalDistanceMeters = totalDistanceMeters,
    totalDurationSeconds = totalDurationSeconds,
    averagePaceMinPerKm = averagePaceMinPerKm,
    noGps = noGps
)

/** Converts to a Workout entity with id=0 so Room auto-generates a new primary key. */
private fun WorkoutBackup.toEntityStripped() = Workout(
    id = 0,
    startTime = startTime,
    originalRoutine = originalRoutine,
    totalDistanceMeters = totalDistanceMeters,
    totalDurationSeconds = totalDurationSeconds,
    averagePaceMinPerKm = averagePaceMinPerKm,
    noGps = noGps
)

private fun GpsPoint.toBackup() = GpsPointBackup(
    id = id,
    workoutId = workoutId,
    latitude = latitude,
    longitude = longitude,
    altitude = altitude,
    timestamp = timestamp,
    phase = phase,
    phaseIndex = phaseIndex
)

/**
 * Converts to a GpsPoint entity with the given [id] and [workoutId].
 * Use id=0 so Room auto-generates the primary key; pass the remapped workoutId
 * from the import ID-map to link to the newly-inserted workout row.
 */
private fun GpsPointBackup.toEntityWithIds(id: Long, workoutId: Long) = GpsPoint(
    id = id,
    workoutId = workoutId,
    latitude = latitude,
    longitude = longitude,
    altitude = altitude,
    timestamp = timestamp,
    phase = phase,
    phaseIndex = phaseIndex
)
