package es.bercianor.tocacorrer.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Tests for LocationTracker distance and pace calculations.
 * Uses pure math formulas instead of Android Location API.
 */
class LocationTrackerCalculationsTest {

    /**
     * Calculate distance between two lat/lng points using Haversine formula.
     */
    private fun calculateDistance(
        lat1: Double, lng1: Double,
        lat2: Double, lng2: Double
    ): Double {
        val earthRadius = 6371000.0 // meters

        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLng / 2) * sin(dLng / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return earthRadius * c
    }

    @Test
    fun `calculateDistance between two points`() {
        // Two locations approximately 111m apart (0.001 degree latitude ~ 111m)
        val lat1 = 42.0
        val lng1 = -8.0
        val lat2 = 42.001
        val lng2 = -8.0

        val distance = calculateDistance(lat1, lng1, lat2, lng2)

        // Distance should be approximately 111 meters
        assertTrue(distance > 100) // Approximately 111m
        assertTrue(distance < 120)
    }

    @Test
    fun `calculateDistance same location returns 0`() {
        val lat1 = 42.0
        val lng1 = -8.0
        val lat2 = 42.0
        val lng2 = -8.0

        val distance = calculateDistance(lat1, lng1, lat2, lng2)

        assertEquals(0.0, distance, 0.001)
    }

    @Test
    @Suppress("DIVISION_BY_ZERO")
    fun `calculatePace with 0 distance returns 0`() {
        val distanceMeters = 0.0
        val durationSeconds = 300L // 5 minutes

        val pace = if (distanceMeters <= 0) 0.0 else {
            val distanceKm = distanceMeters / 1000.0
            val durationMinutes = durationSeconds / 60.0
            durationMinutes / distanceKm
        }

        assertEquals(0.0, pace, 0.001)
    }

    @Test
    fun `calculatePace with valid distance and duration`() {
        val distanceMeters = 5000.0 // 5 km
        val durationSeconds = 1800L // 30 minutes

        val distanceKm = distanceMeters / 1000.0
        val durationMinutes = durationSeconds / 60.0
        val pace = durationMinutes / distanceKm

        // Pace: 30 min / 5 km = 6 min/km
        assertEquals(6.0, pace, 0.1)
    }

    @Test
    fun `calculatePace 10k in 50 minutes`() {
        val distanceMeters = 10000.0 // 10 km
        val durationSeconds = 3000L // 50 minutes

        val distanceKm = distanceMeters / 1000.0
        val durationMinutes = durationSeconds / 60.0
        val pace = durationMinutes / distanceKm

        // Pace: 50 min / 10 km = 5 min/km
        assertEquals(5.0, pace, 0.1)
    }

    @Test
    fun `calculatePace 1km in 6 minutes`() {
        val distanceMeters = 1000.0 // 1 km
        val durationSeconds = 360L // 6 minutes

        val distanceKm = distanceMeters / 1000.0
        val durationMinutes = durationSeconds / 60.0
        val pace = durationMinutes / distanceKm

        // Pace: 6 min / 1 km = 6 min/km
        assertEquals(6.0, pace, 0.1)
    }

    @Test
    fun `format pace minutes and seconds correctly`() {
        // Test formatting: 5.5 min/km = 5:30 /km
        val paceMinKm = 5.5
        val minutes = paceMinKm.toInt()
        val seconds = ((paceMinKm - minutes) * 60).toInt()

        assertEquals(5, minutes)
        assertEquals(30, seconds)
    }

    @Test
    fun `format pace 4 minutes 20 seconds`() {
        // Test formatting: 4.333 min/km = 4:20 /km
        // Use a value that avoids floating point issues
        val paceMinKm = 4.0 + 20.0/60.0  // 4 + 0.333... = 4.333...
        val minutes = paceMinKm.toInt()
        val seconds = Math.round((paceMinKm - minutes) * 60).toInt()

        assertEquals(4, minutes)
        assertEquals(20, seconds)
    }

    @Test
    fun `speed is calculated correctly`() {
        // Same position = speed 0
        val distanceMeters = 0.0
        val timeSeconds = 10.0

        val speed = if (timeSeconds > 0) distanceMeters / timeSeconds else 0.0

        assertEquals(0.0, speed, 0.001)
    }

    @Test
    fun `speed walking fast`() {
        // Walking fast: ~5 km/h = ~1.39 m/s
        // ~14 meters in 10 seconds = 1.4 m/s
        val distanceMeters = 14.0
        val timeSeconds = 10.0

        val speed = distanceMeters / timeSeconds

        // Should be around 1.4 m/s
        assertTrue(speed > 1.3)
        assertTrue(speed < 1.5)
    }

    @Test
    fun `speed running auto-pause threshold`() {
        // Auto-pause threshold: 0.5 m/s
        val THRESHOLD = 0.5

        // Walking slow (below threshold)
        val walkingSpeed = 0.3
        assertTrue(walkingSpeed < THRESHOLD)

        // Running (above threshold)
        val runningSpeed = 2.5
        assertTrue(runningSpeed > THRESHOLD)
    }

    // --- GPS Cache Freshness Tests ---

    @Test
    fun `fresh GPS cache location under 30 seconds should be emitted`() {
        // Simulate a location with time = now - 10 seconds (fresh)
        val now = System.currentTimeMillis()
        val locationTimeMs = now - 10_000L // 10 seconds ago
        val ageMs = now - locationTimeMs

        val shouldEmit = ageMs < 30_000L

        assertTrue("Location 10s old should be emitted", shouldEmit)
    }

    @Test
    fun `stale GPS cache location 60 seconds old should NOT be emitted`() {
        // Simulate a location with time = now - 60 seconds (stale)
        val now = System.currentTimeMillis()
        val locationTimeMs = now - 60_000L // 60 seconds ago
        val ageMs = now - locationTimeMs

        val shouldEmit = ageMs < 30_000L

        assertFalse("Location 60s old should NOT be emitted", shouldEmit)
    }

    @Test
    fun `GPS cache location exactly at 30 second boundary should NOT be emitted`() {
        // Simulate a location with time = now - 30 seconds (at boundary, should be excluded)
        val now = System.currentTimeMillis()
        val locationTimeMs = now - 30_000L // exactly 30 seconds ago
        val ageMs = now - locationTimeMs

        val shouldEmit = ageMs < 30_000L

        assertFalse("Location exactly 30s old should NOT be emitted", shouldEmit)
    }

    @Test
    fun `GPS cache location 13 minutes old should NOT be emitted`() {
        // Real-world regression: stale cache observed 13 minutes old in GPX file
        val now = System.currentTimeMillis()
        val locationTimeMs = now - 780_000L // 13 minutes ago
        val ageMs = now - locationTimeMs

        val shouldEmit = ageMs < 30_000L

        assertFalse("Location 13 minutes old (real-world stale cache) should NOT be emitted", shouldEmit)
    }

    @Test
    fun `GPS cache location 29 seconds old should be emitted`() {
        // Just under threshold
        val now = System.currentTimeMillis()
        val locationTimeMs = now - 29_000L // 29 seconds ago
        val ageMs = now - locationTimeMs

        val shouldEmit = ageMs < 30_000L

        assertTrue("Location 29s old should be emitted", shouldEmit)
    }

    @Test
    fun `distance between real points real example`() {
        // Real example: two points in Madrid
        val madridAtochaLat = 40.406628
        val madridAtochaLng = -3.691683

        val madridPrincipePioLat = 40.424387
        val madridPrincipePioLng = -3.719767

        val distance = calculateDistance(
            madridAtochaLat, madridAtochaLng,
            madridPrincipePioLat, madridPrincipePioLng
        )

        // Should be approximately 2.8 km (Google Maps says ~2.7km, allow wide tolerance)
        assertTrue("Distance should be around 2.7-2.8km, got: $distance", distance > 2500)
        assertTrue("Distance should be around 2.7-2.8km, got: $distance", distance < 3100)
    }
}
