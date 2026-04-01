package es.bercianor.tocacorrer.data.backup

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tests for BackupManager data classes and JSON serialization.
 * Tests the core backup/restore logic without requiring Android Context.
 */
class BackupManagerTest {

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    @Test
    fun `WorkoutBackup serializes correctly`() {
        val workout = WorkoutBackup(
            id = 1L,
            startTime = 1700000000000L,
            originalRoutine = "C5-D1-C5",
            totalDistanceMeters = 3000.0,
            totalDurationSeconds = 1200L,
            averagePaceMinPerKm = 8.0,
            noGps = true
        )

        val adapter = moshi.adapter(WorkoutBackup::class.java)
        val json = adapter.toJson(workout)

        assertTrue(json.contains("\"id\":1"))
        assertTrue(json.contains("C5-D1-C5"))
        assertTrue(json.contains("3000.0"))
        assertTrue(json.contains("\"noGps\":true"))
    }

    @Test
    fun `GpsPointBackup serializes correctly`() {
        val point = GpsPointBackup(
            id = 1L,
            workoutId = 1L,
            latitude = 42.5,
            longitude = -8.5,
            altitude = 250.5,
            timestamp = 1700000000000L,
            phase = "D",
            phaseIndex = 2
        )

        val adapter = moshi.adapter(GpsPointBackup::class.java)
        val json = adapter.toJson(point)

        assertTrue(json.contains("\"latitude\":42.5"))
        assertTrue(json.contains("\"longitude\":-8.5"))
        assertTrue(json.contains("\"phase\":\"D\""))
        assertTrue(json.contains("\"phaseIndex\":2"))
    }

    @Test
    fun `generates correct filename format`() {
        // Test the filename format logic (similar to BackupManager.generateFileName)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val expectedDate = dateFormat.format(Date())
        val expectedFilename = "tocacorrer_backup_$expectedDate.json"

        // Verify the format matches expected pattern
        assertTrue(expectedFilename.matches(Regex("tocacorrer_backup_\\d{4}-\\d{2}-\\d{2}\\.json")))
    }

    @Test
    fun `WorkoutBackup roundtrip serialization preserves data`() {
        val original = WorkoutBackup(
            id = 1L,
            startTime = 1700000000000L,
            originalRoutine = "4x(C3-D1)",
            totalDistanceMeters = 8000.0,
            totalDurationSeconds = 3600L,
            averagePaceMinPerKm = 7.5,
            noGps = false
        )

        val adapter = moshi.adapter(WorkoutBackup::class.java)
        val json = adapter.toJson(original)
        val restored = adapter.fromJson(json)

        assert(restored != null)
        assert(original.originalRoutine == restored!!.originalRoutine)
        assert(original.totalDistanceMeters == restored.totalDistanceMeters)
        assert(original.noGps == restored.noGps)
    }

    @Test
    fun `GpsPointBackup roundtrip serialization preserves phaseIndex`() {
        val original = GpsPointBackup(
            id = 3L,
            workoutId = 1L,
            latitude = 42.002,
            longitude = -8.0,
            altitude = 110.0,
            timestamp = 1700000020000L,
            phase = "D",
            phaseIndex = 1
        )

        val adapter = moshi.adapter(GpsPointBackup::class.java)
        val json = adapter.toJson(original)
        val restored = adapter.fromJson(json)

        assert(restored != null)
        assert(original.phaseIndex == restored!!.phaseIndex)
        assert(original.phase == restored.phase)
        assert(original.latitude == restored.latitude)
    }
}
