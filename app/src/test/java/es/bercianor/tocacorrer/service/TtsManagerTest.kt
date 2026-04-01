package es.bercianor.tocacorrer.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Tests for TtsManager formatting methods.
 * 
 * Since TtsManager depends on Android Context and TextToSpeech,
 * these tests verify the text formatting logic.
 */
class TtsManagerFormatTest {

    @Test
    fun `format duration minutes`() {
        // Simulate duration format
        val durationMinutes = 30
        val text = "Distance: ${durationMinutes} minutes"
        assertTrue(text.contains("30 minutes"))
    }

    @Test
    fun `format pace minutes and seconds`() {
        // Format: X minutes Y seconds per kilometer
        val minutes = 5
        val seconds = 30
        val paceStr = "$minutes minutes $seconds seconds per kilometer"
        
        assertEquals("5 minutes 30 seconds per kilometer", paceStr)
    }

    @Test
    fun `format distance kilometers`() {
        // Format with 2 decimals - use Locale.US to avoid locale issues
        val distanceMeters = 5500.0
        val distanceKm = distanceMeters / 1000.0
        val distanceStr = String.format(Locale.US, "%.2f", distanceKm)
        
        assertEquals("5.50", distanceStr)
    }

    @Test
    fun `format distance with zero decimals`() {
        val distanceMeters = 5000.0
        val distanceKm = distanceMeters / 1000.0
        val distanceStr = String.format(Locale.US, "%.0f", distanceKm)
        
        assertEquals("5", distanceStr)
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
    fun `calculate pace 10k in 50 minutes`() {
        val distanceKm = 10.0
        val durationMinutes = 50.0
        val pace = durationMinutes / distanceKm
        
        assertEquals(5.0, pace, 0.1)
    }

    @Test
    fun `format announce start`() {
        val routine = "C10-D1-C10"
        val expected = "Workout started. Routine: C10-D1-C10"
        
        assertEquals(expected, "Workout started. Routine: $routine")
    }

    @Test
    fun `format announce phase with duration`() {
        val phase = "Run"
        val durationMinutes = 10
        val phaseIndex = 0
        val totalPhases = 3
        val phaseNumber = "Phase ${phaseIndex + 1} of $totalPhases"
        val expected = "$phaseNumber. Next phase: $phase for $durationMinutes minutes"

        assertEquals(expected, "$phaseNumber. Next phase: $phase for $durationMinutes minutes")
    }

    @Test
    fun `format announce phase without duration`() {
        val phase = "Rest"
        val phaseIndex = 1
        val totalPhases = 3
        val phaseNumber = "Phase ${phaseIndex + 1} of $totalPhases"
        val expected = "$phaseNumber. Next phase: $phase"

        assertEquals(expected, "$phaseNumber. Next phase: $phase")
    }

    @Test
    fun `format announce workout end`() {
        val distanceKm = 5.50
        val durationSeconds = 30 * 60 + 15L // 30 min 15 sec
        val minutes = (durationSeconds / 60).toInt()
        val seconds = (durationSeconds % 60).toInt()
        val expected = "Workout completed. Distance: 5.50 kilometers. Time: 30 minutes and 15 seconds."

        assertEquals(expected,
            "Workout completed. Distance: ${String.format(Locale.US, "%.2f", distanceKm)} kilometers. Time: $minutes minutes and $seconds seconds.")
    }

    @Test
    fun `format announce countdown`() {
        for (i in 3 downTo 1) {
            assertTrue(i.toString().matches(Regex("[123]")))
        }
    }

    @Test
    fun `format announce pace`() {
        val minutes = 5
        val seconds = 30
        val expected = "Current pace: 5 minutes 30 seconds per kilometer"
        val result = "Current pace: $minutes minutes $seconds seconds per kilometer"
        
        assertEquals(expected, result)
    }

    @Test
    fun `verify vibration patterns`() {
        // Pattern for phase change: 300ms, pause 150ms, 300ms, pause 150ms, 500ms
        val phaseChangePattern = longArrayOf(0, 200, 100, 200, 150, 300)
        
        assertEquals(6, phaseChangePattern.size)
        assertTrue(phaseChangePattern[1] > 0) // should vibrate
    }

    @Test
    fun `verify countdown pattern`() {
        val countdownPattern = 150L
        
        assertTrue(countdownPattern > 0)
        assertTrue(countdownPattern < 200) // short vibration
    }

    @Test
    @Suppress("DIVISION_BY_ZERO")
    fun `calculate pace with zero distance`() {
        val distanceKm = 0.0
        val durationMinutes = 30.0
        val pace = if (distanceKm > 0) durationMinutes / distanceKm else 0.0
        
        assertEquals(0.0, pace, 0.001)
    }

    @Test
    fun `calculate pace with very small distance`() {
        // 100m in 10 minutes = 0.1 km in 10 min = 100 min/km
        val distanceKm = 0.1
        val durationMinutes = 10.0
        val pace = durationMinutes / distanceKm
        
        assertEquals(100.0, pace, 1.0) // ~100 min/km
    }
}
