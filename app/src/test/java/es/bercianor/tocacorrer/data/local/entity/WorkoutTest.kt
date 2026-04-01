package es.bercianor.tocacorrer.data.local.entity

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for Workout entity.
 */
class WorkoutTest {

    @Test
    fun `create workout with default values`() {
        val workout = Workout(
            startTime = System.currentTimeMillis(),
            originalRoutine = "C10-D1-C10"
        )
        
        assertEquals(0L, workout.id)
        assertEquals(0.0, workout.totalDistanceMeters, 0.001)
        assertEquals(0L, workout.totalDurationSeconds)
        assertEquals(0.0, workout.averagePaceMinPerKm, 0.001)
    }

    @Test
    fun `create workout with complete values`() {
        val timestamp = System.currentTimeMillis()
        val workout = Workout(
            id = 1L,
            startTime = timestamp,
            originalRoutine = "C10-D1-C10",
            totalDistanceMeters = 5000.0,
            totalDurationSeconds = 1800L,
            averagePaceMinPerKm = 6.0
        )
        
        assertEquals(1L, workout.id)
        assertEquals(timestamp, workout.startTime)
        assertEquals("C10-D1-C10", workout.originalRoutine)
        assertEquals(5000.0, workout.totalDistanceMeters, 0.001)
        assertEquals(1800L, workout.totalDurationSeconds)
        assertEquals(6.0, workout.averagePaceMinPerKm, 0.001)
    }

    @Test
    fun `modify workout with copy`() {
        val original = Workout(
            startTime = 1000L,
            originalRoutine = "C10"
        )
        
        val modified = original.copy(
            totalDistanceMeters = 3000.0,
            totalDurationSeconds = 1200L
        )
        
        assertEquals(0L, modified.id)
        assertEquals(1000L, modified.startTime)
        assertEquals("C10", modified.originalRoutine)
        assertEquals(3000.0, modified.totalDistanceMeters, 0.001)
        assertEquals(1200L, modified.totalDurationSeconds)
    }
}
