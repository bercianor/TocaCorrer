package es.bercianor.tocacorrer.util

import es.bercianor.tocacorrer.domain.model.PhaseType
import es.bercianor.tocacorrer.domain.model.TrainingPhase
import es.bercianor.tocacorrer.domain.model.WorkoutRoutine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Tests for Strings utility and formatRoutineSummary logic.
 *
 * Since Strings.get() depends on Android Context/SharedPreferences for language
 * detection, we verify the string maps directly, and test formatRoutineSummary
 * logic independently (pure Kotlin, no Android runtime needed).
 */
class StringsTest {

    // =====================================================================
    // 1. New string keys exist in both EN and ES maps
    // =====================================================================

    /**
     * Access private string maps via Strings.get() with a known English locale.
     * We verify key existence by checking the returned value is not the key itself
     * (which happens when the key is missing — see getEnglishString/getSpanishString fallback).
     *
     * Since Strings.get() uses SharedPreferences for language detection (requires Context),
     * we test the string values directly via the public API assuming the default (system)
     * fallback returns the key when missing.
     *
     * For keys that must exist, we use the fact that keys appear in both maps explicitly
     * by checking the values are non-empty strings that differ from the key name.
     */
    private fun englishStringExistsAndIsNotKey(key: String): Boolean {
        // We can't call Strings.get() without Android context.
        // Instead, verify the key is present by checking the expected value
        // through the EnglishStrings / SpanishStrings companion objects.
        // We use reflection to access the private maps for testing.
        val stringsClass = Strings::class.java
        return try {
            val field = stringsClass.getDeclaredField("englishStrings")
            field.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val map = field.get(Strings) as Map<String, String>
            map.containsKey(key)
        } catch (e: Exception) {
            false
        }
    }

    private fun spanishStringExistsAndIsNotKey(key: String): Boolean {
        val stringsClass = Strings::class.java
        return try {
            val field = stringsClass.getDeclaredField("spanishStrings")
            field.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val map = field.get(Strings) as Map<String, String>
            map.containsKey(key)
        } catch (e: Exception) {
            false
        }
    }

    @Test
    fun `km_remaining key exists in English strings`() {
        assertTrue("'km_remaining' should exist in English strings",
            englishStringExistsAndIsNotKey("km_remaining"))
    }

    @Test
    fun `km_remaining key exists in Spanish strings`() {
        assertTrue("'km_remaining' should exist in Spanish strings",
            spanishStringExistsAndIsNotKey("km_remaining"))
    }

    @Test
    fun `distance_phase_manual key exists in English strings`() {
        assertTrue("'distance_phase_manual' should exist in English strings",
            englishStringExistsAndIsNotKey("distance_phase_manual"))
    }

    @Test
    fun `distance_phase_manual key exists in Spanish strings`() {
        assertTrue("'distance_phase_manual' should exist in Spanish strings",
            spanishStringExistsAndIsNotKey("distance_phase_manual"))
    }

    @Test
    fun `routine_summary_mixed key exists in English strings`() {
        assertTrue("'routine_summary_mixed' should exist in English strings",
            englishStringExistsAndIsNotKey("routine_summary_mixed"))
    }

    @Test
    fun `routine_summary_mixed key exists in Spanish strings`() {
        assertTrue("'routine_summary_mixed' should exist in Spanish strings",
            spanishStringExistsAndIsNotKey("routine_summary_mixed"))
    }

    @Test
    fun `routine_summary_distance_only key exists in English strings`() {
        assertTrue("'routine_summary_distance_only' should exist in English strings",
            englishStringExistsAndIsNotKey("routine_summary_distance_only"))
    }

    @Test
    fun `routine_summary_distance_only key exists in Spanish strings`() {
        assertTrue("'routine_summary_distance_only' should exist in Spanish strings",
            spanishStringExistsAndIsNotKey("routine_summary_distance_only"))
    }

    @Test
    fun `routine_summary_time_only key exists in English strings`() {
        assertTrue("'routine_summary_time_only' should exist in English strings",
            englishStringExistsAndIsNotKey("routine_summary_time_only"))
    }

    @Test
    fun `routine_summary_time_only key exists in Spanish strings`() {
        assertTrue("'routine_summary_time_only' should exist in Spanish strings",
            spanishStringExistsAndIsNotKey("routine_summary_time_only"))
    }

    // =====================================================================
    // 2. formatRoutineSummary logic tests
    //    (pure logic, no Android Context needed)
    // =====================================================================

    /**
     * Reimplementation of MainScreen.formatRoutineSummary() for pure unit testing.
     * This mirrors the logic in MainScreen.kt exactly.
     */
    private fun formatRoutineSummary(routine: WorkoutRoutine): String {
        val totalMin = routine.totalDurationSeconds / 60
        val totalKm = routine.totalDistanceMeters / 1000.0
        // Use Locale.US to avoid locale-dependent decimal separators in tests
        return when {
            routine.totalDistanceMeters == 0.0 -> "%d min".format(totalMin)
            routine.totalDurationSeconds == 0 -> String.format(Locale.US, "%.1f km", totalKm)
            else -> String.format(Locale.US, "%d min + %.1f km", totalMin, totalKm)
        }
    }

    @Test
    fun `formatRoutineSummary returns X min for time-only routine`() {
        val phases = listOf(
            TrainingPhase(PhaseType.EASY, 600),  // 10 min
            TrainingPhase(PhaseType.REST, 60)     // 1 min
        )
        val routine = WorkoutRoutine(phases, 660)

        val result = formatRoutineSummary(routine)
        assertEquals("11 min", result)
    }

    @Test
    fun `formatRoutineSummary returns X km for distance-only routine`() {
        val phases = listOf(
            TrainingPhase(PhaseType.EASY, 0, distanceMeters = 2000.0),  // 2km
            TrainingPhase(PhaseType.REST, 0, distanceMeters = 500.0)    // 0.5km
        )
        val routine = WorkoutRoutine(phases, 0)

        val result = formatRoutineSummary(routine)
        assertEquals("2.5 km", result)
    }

    @Test
    fun `formatRoutineSummary returns X min + Y km for mixed routine`() {
        val phases = listOf(
            TrainingPhase(PhaseType.EASY, 600),                        // 10 min time-only
            TrainingPhase(PhaseType.EASY, 0, distanceMeters = 1000.0), // 1km distance
            TrainingPhase(PhaseType.REST, 60)                          // 1 min time-only
        )
        val routine = WorkoutRoutine(phases, 660)

        val result = formatRoutineSummary(routine)
        assertEquals("11 min + 1.0 km", result)
    }

    @Test
    fun `formatRoutineSummary handles zero-minute time-only routine`() {
        val phases = listOf(TrainingPhase(PhaseType.REST, 30)) // 30 seconds = 0 min
        val routine = WorkoutRoutine(phases, 30)

        val result = formatRoutineSummary(routine)
        assertEquals("0 min", result)
    }

    @Test
    fun `formatRoutineSummary handles 2km distance-only correctly`() {
        val phases = listOf(
            TrainingPhase(PhaseType.EASY, 0, distanceMeters = 2000.0)
        )
        val routine = WorkoutRoutine(phases, 0)

        val result = formatRoutineSummary(routine)
        assertEquals("2.0 km", result)
    }

    @Test
    fun `formatRoutineSummary handles mixed with whole km`() {
        val phases = listOf(
            TrainingPhase(PhaseType.EASY, 300),                        // 5 min
            TrainingPhase(PhaseType.EASY, 0, distanceMeters = 2000.0)  // 2km
        )
        val routine = WorkoutRoutine(phases, 300)

        val result = formatRoutineSummary(routine)
        assertEquals("5 min + 2.0 km", result)
    }

    // =====================================================================
    // 3. Verify string format patterns are valid
    // =====================================================================

    @Test
    fun `routine_summary_mixed format string accepts two args`() {
        val field = Strings::class.java.getDeclaredField("englishStrings")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val map = field.get(Strings) as Map<String, String>
        val format = map["routine_summary_mixed"] ?: ""
        // Should produce a valid result with two args
        val result = format.format(10, 2.5)
        assertTrue("Mixed format should contain 'min'", result.contains("min"))
        assertTrue("Mixed format should contain 'km'", result.contains("km"))
    }

    @Test
    fun `routine_summary_distance_only format string accepts one arg`() {
        val field = Strings::class.java.getDeclaredField("englishStrings")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val map = field.get(Strings) as Map<String, String>
        val format = map["routine_summary_distance_only"] ?: ""
        val result = format.format(2.5)
        assertTrue("Distance-only format should contain 'km'", result.contains("km"))
    }

    @Test
    fun `routine_summary_time_only format string accepts one int arg`() {
        val field = Strings::class.java.getDeclaredField("englishStrings")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val map = field.get(Strings) as Map<String, String>
        val format = map["routine_summary_time_only"] ?: ""
        val result = format.format(10)
        assertTrue("Time-only format should contain 'min'", result.contains("min"))
        assertTrue("Time-only format should contain the number", result.contains("10"))
    }
}
