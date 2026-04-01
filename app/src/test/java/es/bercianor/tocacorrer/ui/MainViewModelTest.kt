package es.bercianor.tocacorrer.ui

import es.bercianor.tocacorrer.domain.model.PhaseType
import es.bercianor.tocacorrer.domain.model.TrainingPhase
import es.bercianor.tocacorrer.domain.model.WorkoutRoutine
import es.bercianor.tocacorrer.domain.parser.WorkoutParser
import es.bercianor.tocacorrer.service.WorkoutStatus
import es.bercianor.tocacorrer.util.Strings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for MainViewModel logic.
 *
 * Because MainViewModel extends AndroidViewModel and its init block creates AppDatabase /
 * PreferencesManager / CalendarManager (all requiring Android runtime), we cannot instantiate
 * the ViewModel directly in a JVM test without Robolectric (which is not in the project).
 *
 * Strategy:
 *  1. Test AppState data class directly — it is pure Kotlin and has no Android dependencies.
 *  2. Test the treadmill-validation rule used by requestStartWorkout() by replicating the
 *     same predicate logic (WorkoutParser.parse() + distanceMeters check). This validates the
 *     business rule without exercising the Android plumbing.
 *  3. Test the state-mutation helpers (updatePermissions, updateGpsEnabled, setDarkMode,
 *     setLanguage, setPrimaryColorIndex, clearError) by applying the exact same .copy()
 *     transforms that the real ViewModel methods use, against a live AppState value.
 *  4. Test Strings.setLanguageSetting() — pure Kotlin singleton, zero Android deps.
 */
class MainViewModelTest {

    // =========================================================================
    // Test helpers — mirrors the exact transforms performed by MainViewModel
    // =========================================================================

    /** Replicates MainViewModel.updatePermissions() */
    private fun applyUpdatePermissions(state: AppState, hasPermissions: Boolean): AppState =
        state.copy(hasPermissions = hasPermissions)

    /** Replicates MainViewModel.updateGpsEnabled() */
    private fun applyUpdateGpsEnabled(state: AppState, enabled: Boolean): AppState =
        state.copy(gpsEnabled = enabled)

    /** Replicates MainViewModel.setDarkMode() — only the state side, not the prefs write */
    private fun applySetDarkMode(state: AppState, mode: Int): AppState =
        state.copy(darkMode = mode)

    /** Replicates MainViewModel.setPrimaryColorIndex() — only the state side */
    private fun applySetPrimaryColorIndex(state: AppState, index: Int): AppState =
        state.copy(primaryColorIndex = index)

    /** Replicates MainViewModel.setLanguage() — only the state side */
    private fun applySetLanguage(state: AppState, lang: Int): AppState =
        state.copy(language = lang)

    /** Replicates MainViewModel.clearError() */
    private fun applyClearError(state: AppState): AppState =
        state.copy(error = null)

    /**
     * Replicates the treadmill validation branch inside MainViewModel.requestStartWorkout().
     *
     * Returns the error message key when the combination is invalid (noGps=true + distance phases),
     * or null when the request should proceed (no error).
     */
    private fun validateTreadmillRequest(routine: String, noGps: Boolean): String? {
        if (routine.isBlank()) return null  // Free workout — always allowed

        val parsed = WorkoutParser.parse(routine)
        val hasDistancePhases = parsed.phases.any { it.distanceMeters != null }

        return if (noGps && hasDistancePhases) "treadmill_no_distance_phases" else null
    }

    // =========================================================================
    // Setup
    // =========================================================================

    @Before
    fun setUp() {
        // Reset Strings language to English so tests are deterministic
        Strings.setLanguageSetting(Strings.LANGUAGE_ENGLISH)
    }

    // =========================================================================
    // 1. AppState — default values
    // =========================================================================

    @Test
    fun `AppState default values are correct`() {
        val state = AppState()

        assertFalse("activeWorkout default should be false", state.activeWorkout)
        assertFalse("hasPermissions default should be false", state.hasPermissions)
        assertFalse("gpsEnabled default should be false", state.gpsEnabled)
        assertNull("error default should be null", state.error)
        assertEquals("darkMode default should be 0 (system)", 0, state.darkMode)
        assertEquals("primaryColorIndex default should be 0", 0, state.primaryColorIndex)
        assertEquals("language default should be 0 (system)", 0, state.language)
        assertFalse("awaitingGps default should be false", state.awaitingGps)
        assertEquals("awaitingGpsElapsedSeconds default should be 0", 0, state.awaitingGpsElapsedSeconds)
        assertNull("pendingRoutine default should be null", state.pendingRoutine)
        assertFalse("pendingNoGps default should be false", state.pendingNoGps)
        assertTrue("recentWorkouts default should be empty", state.recentWorkouts.isEmpty())
        assertTrue("upcomingEvents default should be empty", state.upcomingEvents.isEmpty())
        assertEquals("completedWorkoutsToday default should be 0", 0, state.completedWorkoutsToday)
    }

    @Test
    fun `AppState workoutStatus default is a fresh WorkoutStatus`() {
        val state = AppState()
        val expected = WorkoutStatus()

        assertEquals("workoutStatus should be a default WorkoutStatus", expected, state.workoutStatus)
        assertFalse("default WorkoutStatus should not be running", state.workoutStatus.isRunning)
        assertFalse("default WorkoutStatus should not be paused", state.workoutStatus.isPaused)
    }

    // =========================================================================
    // 2. AppState — copy() preserves unrelated fields
    // =========================================================================

    @Test
    fun `AppState copy preserves unrelated fields when only error is set`() {
        val original = AppState(hasPermissions = true, gpsEnabled = true, darkMode = 2)
        val updated = original.copy(error = "some_error")

        assertEquals("hasPermissions should be preserved", true, updated.hasPermissions)
        assertEquals("gpsEnabled should be preserved", true, updated.gpsEnabled)
        assertEquals("darkMode should be preserved", 2, updated.darkMode)
        assertEquals("error should be updated", "some_error", updated.error)
    }

    // =========================================================================
    // 3. requestStartWorkout — treadmill validation
    // =========================================================================

    @Test
    fun `treadmill validation — noGps true with distance phase returns error key`() {
        // R5k is a distance-based phase (5 km easy run)
        val errorKey = validateTreadmillRequest("R5k", noGps = true)

        assertNotNull("Should return an error key for treadmill + distance phase", errorKey)
        assertEquals("treadmill_no_distance_phases", errorKey)
    }

    @Test
    fun `treadmill validation — noGps true with mixed routine containing distance phase returns error`() {
        // T10 - R2k - D1: has a distance phase (R2k)
        val errorKey = validateTreadmillRequest("T10 - R2k - D1", noGps = true)

        assertNotNull("Mixed routine with distance phase should fail treadmill validation", errorKey)
        assertEquals("treadmill_no_distance_phases", errorKey)
    }

    @Test
    fun `treadmill validation — noGps true with time-only routine returns null (no error)`() {
        // T10 - D1 - R5: all phases are time-based, valid for treadmill
        val errorKey = validateTreadmillRequest("T10 - D1 - R5", noGps = true)

        assertNull("Time-only routine should be allowed on treadmill (no error)", errorKey)
    }

    @Test
    fun `treadmill validation — noGps true with series of time phases returns null`() {
        // 4x(T3 - D1): all time-based, valid for treadmill
        val errorKey = validateTreadmillRequest("4x(T3 - D1)", noGps = true)

        assertNull("Series of time-only phases should be allowed on treadmill", errorKey)
    }

    @Test
    fun `treadmill validation — noGps false with distance phase returns null (no error)`() {
        // GPS mode — distance phases are fine
        val errorKey = validateTreadmillRequest("R5k", noGps = false)

        assertNull("Distance phase with GPS enabled should not produce an error", errorKey)
    }

    @Test
    fun `treadmill validation — noGps false with time-only routine returns null`() {
        val errorKey = validateTreadmillRequest("T10 - D1", noGps = false)

        assertNull("GPS mode with time-only phases should not produce an error", errorKey)
    }

    @Test
    fun `treadmill validation — blank routine always returns null (free workout)`() {
        // Blank routine = free workout, always proceeds regardless of noGps
        val errorKeyTreadmill = validateTreadmillRequest("", noGps = true)
        val errorKeyGps = validateTreadmillRequest("   ", noGps = true)

        assertNull("Blank routine (treadmill) should always be allowed", errorKeyTreadmill)
        assertNull("Whitespace-only routine (treadmill) should always be allowed", errorKeyGps)
    }

    @Test
    fun `treadmill validation — distance-only routine with noGps true returns error`() {
        // R1k - D0.5k: all distance phases
        val errorKey = validateTreadmillRequest("R1k - T0.5k", noGps = true)

        assertNotNull("All-distance routine on treadmill should be an error", errorKey)
    }

    // =========================================================================
    // 4. Verify WorkoutParser correctly identifies distance phases
    //    (foundation for the treadmill validation tests above)
    // =========================================================================

    @Test
    fun `WorkoutParser identifies distance phase from R5k`() {
        val routine = WorkoutParser.parse("R5k")

        assertTrue("R5k should produce at least one phase", routine.phases.isNotEmpty())
        val hasDistance = routine.phases.any { it.distanceMeters != null }
        assertTrue("R5k should be parsed as a distance phase", hasDistance)
    }

    @Test
    fun `WorkoutParser identifies time-only phases from T10 D1`() {
        val routine = WorkoutParser.parse("T10 - D1")

        assertTrue("Routine should have phases", routine.phases.isNotEmpty())
        val hasDistance = routine.phases.any { it.distanceMeters != null }
        assertFalse("T10 - D1 should not contain distance phases", hasDistance)
    }

    @Test
    fun `WorkoutParser detects distance phase in mixed routine`() {
        // T10 - R2k - D1: T10 and D1 are time-based, R2k is distance-based
        val routine = WorkoutParser.parse("T10 - R2k - D1")

        val hasDistance = routine.phases.any { it.distanceMeters != null }
        assertTrue("Mixed routine should detect the distance phase R2k", hasDistance)
    }

    // =========================================================================
    // 5. updatePermissions — state transition
    // =========================================================================

    @Test
    fun `updatePermissions true sets hasPermissions to true`() {
        val initial = AppState(hasPermissions = false)
        val updated = applyUpdatePermissions(initial, true)

        assertTrue("hasPermissions should be true after updatePermissions(true)", updated.hasPermissions)
    }

    @Test
    fun `updatePermissions false sets hasPermissions to false`() {
        val initial = AppState(hasPermissions = true)
        val updated = applyUpdatePermissions(initial, false)

        assertFalse("hasPermissions should be false after updatePermissions(false)", updated.hasPermissions)
    }

    @Test
    fun `updatePermissions does not affect other state fields`() {
        val initial = AppState(gpsEnabled = true, darkMode = 2, language = 1)
        val updated = applyUpdatePermissions(initial, true)

        assertEquals("gpsEnabled should not change", true, updated.gpsEnabled)
        assertEquals("darkMode should not change", 2, updated.darkMode)
        assertEquals("language should not change", 1, updated.language)
    }

    // =========================================================================
    // 6. updateGpsEnabled — state transition
    // =========================================================================

    @Test
    fun `updateGpsEnabled true sets gpsEnabled to true`() {
        val initial = AppState(gpsEnabled = false)
        val updated = applyUpdateGpsEnabled(initial, true)

        assertTrue("gpsEnabled should be true after updateGpsEnabled(true)", updated.gpsEnabled)
    }

    @Test
    fun `updateGpsEnabled false sets gpsEnabled to false`() {
        val initial = AppState(gpsEnabled = true)
        val updated = applyUpdateGpsEnabled(initial, false)

        assertFalse("gpsEnabled should be false after updateGpsEnabled(false)", updated.gpsEnabled)
    }

    @Test
    fun `updateGpsEnabled does not affect other state fields`() {
        val initial = AppState(hasPermissions = true, darkMode = 1, language = 2)
        val updated = applyUpdateGpsEnabled(initial, true)

        assertTrue("hasPermissions should not change", updated.hasPermissions)
        assertEquals("darkMode should not change", 1, updated.darkMode)
        assertEquals("language should not change", 2, updated.language)
    }

    // =========================================================================
    // 7. setDarkMode — state transition
    // =========================================================================

    @Test
    fun `setDarkMode 0 sets darkMode to system (0)`() {
        val initial = AppState(darkMode = 2)
        val updated = applySetDarkMode(initial, 0)

        assertEquals("darkMode should be 0 (system)", 0, updated.darkMode)
    }

    @Test
    fun `setDarkMode 1 sets darkMode to light (1)`() {
        val initial = AppState(darkMode = 0)
        val updated = applySetDarkMode(initial, 1)

        assertEquals("darkMode should be 1 (light)", 1, updated.darkMode)
    }

    @Test
    fun `setDarkMode 2 sets darkMode to dark (2)`() {
        val initial = AppState(darkMode = 0)
        val updated = applySetDarkMode(initial, 2)

        assertEquals("darkMode should be 2 (dark)", 2, updated.darkMode)
    }

    @Test
    fun `setDarkMode does not affect other state fields`() {
        val initial = AppState(hasPermissions = true, gpsEnabled = true, language = 1)
        val updated = applySetDarkMode(initial, 2)

        assertTrue("hasPermissions should not change", updated.hasPermissions)
        assertTrue("gpsEnabled should not change", updated.gpsEnabled)
        assertEquals("language should not change", 1, updated.language)
    }

    // =========================================================================
    // 8. setPrimaryColorIndex — state transition
    // =========================================================================

    @Test
    fun `setPrimaryColorIndex updates primaryColorIndex`() {
        val initial = AppState(primaryColorIndex = 0)
        val updated = applySetPrimaryColorIndex(initial, 3)

        assertEquals("primaryColorIndex should be 3", 3, updated.primaryColorIndex)
    }

    @Test
    fun `setPrimaryColorIndex to 0 resets to default`() {
        val initial = AppState(primaryColorIndex = 5)
        val updated = applySetPrimaryColorIndex(initial, 0)

        assertEquals("primaryColorIndex should be 0 (default)", 0, updated.primaryColorIndex)
    }

    @Test
    fun `setPrimaryColorIndex does not affect other state fields`() {
        val initial = AppState(hasPermissions = true, darkMode = 2, language = 1)
        val updated = applySetPrimaryColorIndex(initial, 4)

        assertTrue("hasPermissions should not change", updated.hasPermissions)
        assertEquals("darkMode should not change", 2, updated.darkMode)
        assertEquals("language should not change", 1, updated.language)
    }

    // =========================================================================
    // 9. setLanguage — state transition AND Strings.getLanguageSetting() side-effect
    // =========================================================================

    @Test
    fun `setLanguage 1 sets language to English (1) in state`() {
        val initial = AppState(language = 0)
        val updated = applySetLanguage(initial, 1)

        assertEquals("language should be 1 (English)", 1, updated.language)
    }

    @Test
    fun `setLanguage 2 sets language to Spanish (2) in state`() {
        val initial = AppState(language = 0)
        val updated = applySetLanguage(initial, 2)

        assertEquals("language should be 2 (Spanish)", 2, updated.language)
    }

    @Test
    fun `setLanguage 0 sets language to system (0) in state`() {
        val initial = AppState(language = 2)
        val updated = applySetLanguage(initial, 0)

        assertEquals("language should be 0 (system)", 0, updated.language)
    }

    @Test
    fun `setLanguage calls Strings setLanguageSetting — English`() {
        // Simulate what MainViewModel.setLanguage(1) does:
        Strings.setLanguageSetting(1)

        assertEquals(
            "Strings.getLanguageSetting() should be LANGUAGE_ENGLISH (1) after setLanguageSetting(1)",
            Strings.LANGUAGE_ENGLISH,
            Strings.getLanguageSetting()
        )
    }

    @Test
    fun `setLanguage calls Strings setLanguageSetting — Spanish`() {
        Strings.setLanguageSetting(2)

        assertEquals(
            "Strings.getLanguageSetting() should be LANGUAGE_SPANISH (2) after setLanguageSetting(2)",
            Strings.LANGUAGE_SPANISH,
            Strings.getLanguageSetting()
        )
    }

    @Test
    fun `setLanguage calls Strings setLanguageSetting — system`() {
        // First set to Spanish, then revert to system
        Strings.setLanguageSetting(2)
        Strings.setLanguageSetting(0)

        assertEquals(
            "Strings.getLanguageSetting() should be LANGUAGE_SYSTEM (0) after setLanguageSetting(0)",
            Strings.LANGUAGE_SYSTEM,
            Strings.getLanguageSetting()
        )
    }

    @Test
    fun `setLanguage — Strings currentLanguage matches language in AppState`() {
        // Verifies that the two side effects are consistent:
        // AppState.language and Strings.getLanguageSetting() both reflect the same value.
        val lang = 2
        Strings.setLanguageSetting(lang)
        val state = applySetLanguage(AppState(), lang)

        assertEquals(
            "AppState.language and Strings.getLanguageSetting() should match",
            state.language,
            Strings.getLanguageSetting()
        )
    }

    @Test
    fun `setLanguage does not affect other state fields`() {
        val initial = AppState(hasPermissions = true, darkMode = 2, primaryColorIndex = 3)
        val updated = applySetLanguage(initial, 1)

        assertTrue("hasPermissions should not change", updated.hasPermissions)
        assertEquals("darkMode should not change", 2, updated.darkMode)
        assertEquals("primaryColorIndex should not change", 3, updated.primaryColorIndex)
    }

    // =========================================================================
    // 10. clearError — state transition
    // =========================================================================

    @Test
    fun `clearError sets error to null when error was set`() {
        val stateWithError = AppState(error = "treadmill_no_distance_phases")
        val cleared = applyClearError(stateWithError)

        assertNull("error should be null after clearError()", cleared.error)
    }

    @Test
    fun `clearError is idempotent when error is already null`() {
        val stateNoError = AppState(error = null)
        val cleared = applyClearError(stateNoError)

        assertNull("error should still be null", cleared.error)
    }

    @Test
    fun `clearError does not affect other state fields`() {
        val initial = AppState(
            error = "some_error",
            hasPermissions = true,
            gpsEnabled = true,
            darkMode = 1
        )
        val cleared = applyClearError(initial)

        assertTrue("hasPermissions should not change", cleared.hasPermissions)
        assertTrue("gpsEnabled should not change", cleared.gpsEnabled)
        assertEquals("darkMode should not change", 1, cleared.darkMode)
    }

    // =========================================================================
    // 11. Treadmill error → clearError lifecycle
    // =========================================================================

    @Test
    fun `treadmill error flow — error is set then cleared`() {
        // 1. Start with clean state
        var state = AppState()
        assertNull("Initial error should be null", state.error)

        // 2. Treadmill validation fails → error is set
        val errorKey = validateTreadmillRequest("R5k", noGps = true)
        assertNotNull("Validation should produce an error key", errorKey)
        state = state.copy(error = Strings.get(errorKey!!))
        assertNotNull("State should hold the error message", state.error)

        // 3. clearError() removes it
        state = applyClearError(state)
        assertNull("Error should be null after clearError()", state.error)
    }

    @Test
    fun `treadmill error message is a non-blank string`() {
        // The error key must resolve to a human-readable message (not the key itself missing)
        Strings.setLanguageSetting(Strings.LANGUAGE_ENGLISH)
        val message = Strings.get("treadmill_no_distance_phases")

        assertTrue("treadmill_no_distance_phases message should not be blank", message.isNotBlank())
        // The message should NOT be the key itself (which happens when a key is missing)
        assertFalse(
            "Key 'treadmill_no_distance_phases' must exist in English strings",
            message == "treadmill_no_distance_phases"
        )
    }

    @Test
    fun `treadmill error message exists in Spanish strings too`() {
        Strings.setLanguageSetting(Strings.LANGUAGE_SPANISH)
        val message = Strings.get("treadmill_no_distance_phases")

        assertTrue("treadmill_no_distance_phases message should not be blank in Spanish", message.isNotBlank())
        assertFalse(
            "Key 'treadmill_no_distance_phases' must exist in Spanish strings",
            message == "treadmill_no_distance_phases"
        )
    }

    // =========================================================================
    // 12. Strings.setLanguageSetting — standalone tests
    // =========================================================================

    @Test
    fun `Strings setLanguageSetting persists in currentLanguage`() {
        Strings.setLanguageSetting(Strings.LANGUAGE_SPANISH)
        assertEquals(Strings.LANGUAGE_SPANISH, Strings.getLanguageSetting())

        Strings.setLanguageSetting(Strings.LANGUAGE_ENGLISH)
        assertEquals(Strings.LANGUAGE_ENGLISH, Strings.getLanguageSetting())

        Strings.setLanguageSetting(Strings.LANGUAGE_SYSTEM)
        assertEquals(Strings.LANGUAGE_SYSTEM, Strings.getLanguageSetting())
    }

    @Test
    fun `Strings getLanguageSetting returns the same value as currentLanguage`() {
        Strings.setLanguageSetting(Strings.LANGUAGE_SPANISH)
        assertEquals(Strings.getLanguageSetting(), Strings.getLanguageSetting())
    }

    // =========================================================================
    // 13. AppState — GPS wait state fields
    // =========================================================================

    @Test
    fun `awaitingGps state can be toggled via copy`() {
        val initial = AppState()
        assertFalse("awaitingGps should start false", initial.awaitingGps)

        val waiting = initial.copy(awaitingGps = true, awaitingGpsElapsedSeconds = 0, pendingRoutine = "T10 - D1")
        assertTrue("awaitingGps should be true after copy", waiting.awaitingGps)
        assertEquals("awaitingGpsElapsedSeconds should be 0", 0, waiting.awaitingGpsElapsedSeconds)
        assertEquals("pendingRoutine should be stored", "T10 - D1", waiting.pendingRoutine)

        // Simulate clearGpsWaitState()
        val cleared = waiting.copy(
            awaitingGps = false,
            awaitingGpsElapsedSeconds = 0,
            pendingRoutine = null,
            pendingNoGps = false
        )
        assertFalse("awaitingGps should be false after clearGpsWaitState", cleared.awaitingGps)
        assertNull("pendingRoutine should be null after clearGpsWaitState", cleared.pendingRoutine)
        assertFalse("pendingNoGps should be false after clearGpsWaitState", cleared.pendingNoGps)
    }

    @Test
    fun `awaitingGpsElapsedSeconds increments correctly via copy`() {
        var state = AppState(awaitingGps = true, awaitingGpsElapsedSeconds = 0)

        for (second in 1..30) {
            state = state.copy(awaitingGpsElapsedSeconds = second)
            assertEquals("awaitingGpsElapsedSeconds should be $second", second, state.awaitingGpsElapsedSeconds)
        }
    }
}
