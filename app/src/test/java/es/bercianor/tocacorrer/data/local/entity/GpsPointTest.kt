package es.bercianor.tocacorrer.data.local.entity

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for GpsPoint entity.
 */
class GpsPointTest {

    @Test
    fun `create GPS point with default values`() {
        val point = GpsPoint(
            workoutId = 1L,
            latitude = 42.123456,
            longitude = -8.654321,
            altitude = 150.5,
            timestamp = System.currentTimeMillis(),
            phase = "C"
        )
        
        assertEquals(0L, point.id)
        assertEquals(1L, point.workoutId)
        assertEquals(42.123456, point.latitude, 0.000001)
        assertEquals(-8.654321, point.longitude, 0.000001)
        assertEquals(150.5, point.altitude, 0.001)
        assertEquals("C", point.phase)
        assertEquals(0, point.phaseIndex) // phaseIndex defaults to 0
    }

    @Test
    fun `create GPS point for rest phase`() {
        val point = GpsPoint(
            workoutId = 5L,
            latitude = 42.0,
            longitude = -8.0,
            altitude = 100.0,
            timestamp = 1000L,
            phase = "D"
        )
        
        assertEquals("D", point.phase)
    }

    @Test
    fun `modify GPS point with copy`() {
        val original = GpsPoint(
            workoutId = 1L,
            latitude = 42.0,
            longitude = -8.0,
            altitude = 100.0,
            timestamp = 1000L,
            phase = "C"
        )
        
        val modified = original.copy(
            latitude = 42.5,
            phase = "D"
        )
        
        assertEquals(1L, modified.workoutId)
        assertEquals(42.5, modified.latitude, 0.001)
        assertEquals(-8.0, modified.longitude, 0.001)
        assertEquals("D", modified.phase)
    }

    @Test
    fun `point with extreme values`() {
        val point = GpsPoint(
            workoutId = 1L,
            latitude = 90.0,       // North Pole
            longitude = 180.0,     // Date line
            altitude = 8848.0,    // Everest
            timestamp = Long.MAX_VALUE,
            phase = "C"
        )
        
        assertEquals(90.0, point.latitude, 0.001)
        assertEquals(180.0, point.longitude, 0.001)
        assertEquals(8848.0, point.altitude, 0.001)
    }

    @Test
    fun `phaseIndex field exists and defaults to 0`() {
        val point = GpsPoint(
            workoutId = 1L,
            latitude = 42.0,
            longitude = -8.0,
            altitude = 100.0,
            timestamp = 1000L,
            phase = "C"
        )
        assertEquals(0, point.phaseIndex)
    }

    @Test
    fun `phaseIndex can be set to non-zero values`() {
        val point = GpsPoint(
            workoutId = 1L,
            latitude = 42.0,
            longitude = -8.0,
            altitude = 100.0,
            timestamp = 1000L,
            phase = "D",
            phaseIndex = 3
        )
        assertEquals(3, point.phaseIndex)
        assertEquals("D", point.phase)
    }

    @Test
    fun `copy with changed phaseIndex preserves other fields`() {
        val original = GpsPoint(
            workoutId = 1L,
            latitude = 42.0,
            longitude = -8.0,
            altitude = 100.0,
            timestamp = 1000L,
            phase = "C",
            phaseIndex = 0
        )
        val modified = original.copy(phaseIndex = 2)
        assertEquals(2, modified.phaseIndex)
        assertEquals("C", modified.phase)
        assertEquals(1L, modified.workoutId)
    }
}
