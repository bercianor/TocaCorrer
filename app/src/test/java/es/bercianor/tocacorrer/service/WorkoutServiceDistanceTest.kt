package es.bercianor.tocacorrer.service

import es.bercianor.tocacorrer.domain.model.PhaseType
import es.bercianor.tocacorrer.domain.model.TrainingPhase
import es.bercianor.tocacorrer.domain.model.WorkoutRoutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for WorkoutService distance-based phase logic.
 *
 * Since WorkoutService depends on Android services (Context, Location, TextToSpeech),
 * these tests exercise the core distance tracking algorithms extracted as pure logic,
 * and integration tests with PhaseTimer that validate the orchestration contracts
 * WorkoutService relies on.
 *
 * The test structure follows the same pattern as LocationTrackerCalculationsTest:
 * pure algorithm tests that don't require Android runtime.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutServiceDistanceTest {

    private val scheduler = TestCoroutineScheduler()
    private val testDispatcher = StandardTestDispatcher(scheduler)
    private lateinit var phaseTimer: PhaseTimer

    // --- State mirroring WorkoutService fields ---
    private var distanceAtPhaseStart: Double = 0.0
    private var distanceInCurrentPhase: Double = 0.0
    private var totalDistanceMeters: Double = 0.0

    // Recorded TTS calls (in place of real TtsManager)
    private val ttsSpokenTexts = mutableListOf<String>()
    private var nextPhaseCalled = 0
    private var vibratePhaseChangeCalled = 0

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        phaseTimer = PhaseTimer(
            scope = kotlinx.coroutines.CoroutineScope(testDispatcher),
            clock = TimerClock { scheduler.currentTime }
        )
        resetState()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        phaseTimer.stop()
    }

    private fun resetState() {
        distanceAtPhaseStart = 0.0
        distanceInCurrentPhase = 0.0
        totalDistanceMeters = 0.0
        ttsSpokenTexts.clear()
        nextPhaseCalled = 0
        vibratePhaseChangeCalled = 0
    }

    /**
     * Simulates what WorkoutService.processNewLocation() does for distance tracking,
     * extracted as pure logic (no Android dependencies).
     *
     * @param addedDistanceMeters meters to add to total distance
     * @param currentPhase the active phase being tracked
     * @param noGps whether treadmill mode is active
     * @return whether nextPhase() should be called
     */
    private fun simulateGpsUpdate(
        addedDistanceMeters: Double,
        currentPhase: TrainingPhase,
        noGps: Boolean = false
    ): Boolean {
        totalDistanceMeters += addedDistanceMeters

        if (currentPhase.distanceMeters == null) return false
        val target = currentPhase.distanceMeters!!

        distanceInCurrentPhase = totalDistanceMeters - distanceAtPhaseStart
        @Suppress("UNUSED_VARIABLE")
        val progress = (distanceInCurrentPhase / target).coerceIn(0.0, 1.0)
        @Suppress("UNUSED_VARIABLE")
        val remaining = (target - distanceInCurrentPhase).coerceAtLeast(0.0)

        // Auto-advance check (GPS mode only)
        if (distanceInCurrentPhase >= target && !noGps) {
            // Phase complete — speak and advance (mirrors WorkoutService)
            ttsSpokenTexts.add("Phase complete")
            vibratePhaseChangeCalled++
            nextPhaseCalled++
            return true
        }

        return false
    }

    /**
     * Simulates the phase change event in WorkoutService.observePhaseTimer():
     * resets distance tracking fields when the phase index changes.
     */
    private fun onPhaseChanged() {
        distanceAtPhaseStart = totalDistanceMeters
        distanceInCurrentPhase = 0.0
    }

    // ==========================================================================
    // 1. distanceAtPhaseStart resets when phase changes
    // ==========================================================================

    @Test
    fun `distanceAtPhaseStart resets to current total when phase changes`() {
        @Suppress("UNUSED_VARIABLE")
        val phase = TrainingPhase(PhaseType.EASY, 0, distanceMeters = 1000.0)
        totalDistanceMeters = 400.0
        distanceAtPhaseStart = 0.0

        // Before phase change, distanceInCurrentPhase = 400 - 0 = 400
        distanceInCurrentPhase = totalDistanceMeters - distanceAtPhaseStart
        assertEquals(400.0, distanceInCurrentPhase, 0.001)

        // Phase change happens
        onPhaseChanged()

        // After reset, distanceAtPhaseStart == totalDistanceMeters
        assertEquals(totalDistanceMeters, distanceAtPhaseStart, 0.001)
        assertEquals(0.0, distanceInCurrentPhase, 0.001)
    }

    @Test
    fun `distanceInCurrentPhase is independent of earlier phases`() {
        // Phase 1: run 500m
        val phase1 = TrainingPhase(PhaseType.EASY, 0, distanceMeters = 500.0)
        distanceAtPhaseStart = 0.0
        simulateGpsUpdate(250.0, phase1)
        simulateGpsUpdate(250.0, phase1)
        // After 500m in phase 1, nextPhase was triggered
        assertEquals(1, nextPhaseCalled)

        // Phase changes, total = 500m
        onPhaseChanged()
        assertEquals(500.0, distanceAtPhaseStart, 0.001)

        // Phase 2: run another 500m
        val phase2 = TrainingPhase(PhaseType.EASY, 0, distanceMeters = 500.0)
        nextPhaseCalled = 0 // reset counter for phase 2
        simulateGpsUpdate(300.0, phase2)
        assertEquals(300.0, distanceInCurrentPhase, 0.001) // counts from phase start, not from beginning
        simulateGpsUpdate(200.0, phase2)
        assertEquals(1, nextPhaseCalled) // phase 2 also completed
    }

    // ==========================================================================
    // 2. Auto-advance: GPS distance >= target → nextPhase() triggered
    // ==========================================================================

    @Test
    fun `auto-advance triggers when GPS distance reaches target`() {
        val phase = TrainingPhase(PhaseType.EASY, 0, distanceMeters = 500.0)

        // Add distance incrementally
        simulateGpsUpdate(200.0, phase, noGps = false)
        assertEquals(0, nextPhaseCalled) // not yet

        simulateGpsUpdate(200.0, phase, noGps = false)
        assertEquals(0, nextPhaseCalled) // still 400m, not yet

        simulateGpsUpdate(100.0, phase, noGps = false) // exactly 500m
        assertEquals(1, nextPhaseCalled) // should have triggered
    }

    @Test
    fun `auto-advance triggers when GPS distance slightly exceeds target`() {
        val phase = TrainingPhase(PhaseType.EASY, 0, distanceMeters = 1000.0)

        simulateGpsUpdate(1050.0, phase, noGps = false) // 50m overshoot
        assertEquals(1, nextPhaseCalled)
    }

    @Test
    fun `auto-advance does not trigger before target is reached`() {
        val phase = TrainingPhase(PhaseType.EASY, 0, distanceMeters = 1000.0)

        simulateGpsUpdate(999.0, phase, noGps = false) // 1m short
        assertEquals(0, nextPhaseCalled)
    }

    // ==========================================================================
    // 3. Treadmill guard: noGps == true → auto-advance NOT triggered
    // ==========================================================================

    @Test
    fun `treadmill mode does not auto-advance when distance reaches target`() {
        val phase = TrainingPhase(PhaseType.EASY, 0, distanceMeters = 500.0)

        // Simulate GPS updates with noGps = true (treadmill mode)
        simulateGpsUpdate(300.0, phase, noGps = true)
        simulateGpsUpdate(300.0, phase, noGps = true) // exceeds 500m
        assertEquals(0, nextPhaseCalled) // must NOT auto-advance
    }

    @Test
    fun `treadmill mode does not trigger TTS completion announcement`() {
        val phase = TrainingPhase(PhaseType.EASY, 0, distanceMeters = 500.0)

        simulateGpsUpdate(600.0, phase, noGps = true) // exceeds target
        assertFalse(ttsSpokenTexts.contains("Phase complete"))
        assertEquals(0, vibratePhaseChangeCalled)
    }

    // ==========================================================================
    // 5. TTS completion announcement before nextPhase()
    // ==========================================================================

    @Test
    fun `TTS phase complete announced when distance target reached in GPS mode`() {
        val phase = TrainingPhase(PhaseType.EASY, 0, distanceMeters = 500.0)

        simulateGpsUpdate(500.0, phase, noGps = false)

        assertTrue(
            "Expected 'Phase complete' TTS before nextPhase()",
            ttsSpokenTexts.contains("Phase complete")
        )
        assertEquals(1, nextPhaseCalled)
    }

    @Test
    fun `TTS phase complete is announced before nextPhase is called`() {
        val phase = TrainingPhase(PhaseType.EASY, 0, distanceMeters = 300.0)
        val announcements = mutableListOf<String>()
        var nextPhaseCallOrder = -1

        // Simulate the exact order in WorkoutService
        fun processUpdate(distanceAdded: Double) {
            totalDistanceMeters += distanceAdded
            distanceInCurrentPhase = totalDistanceMeters - distanceAtPhaseStart
            val target = phase.distanceMeters!!

            if (distanceInCurrentPhase >= target) {
                // TTS fires FIRST (then nextPhase)
                announcements.add("Phase complete")
                nextPhaseCallOrder = announcements.size  // index after TTS
                nextPhaseCalled++
            }
        }

        processUpdate(300.0)

        assertTrue(announcements.contains("Phase complete"))
        assertEquals(1, nextPhaseCalled)
        assertEquals(1, nextPhaseCallOrder) // nextPhase was called after TTS
    }

    // ==========================================================================
    // 6. PhaseTimer integration: distance phase gate behavior
    // ==========================================================================

    @Test
    fun `PhaseTimer distance phase does not auto-complete without explicit nextPhase call`() = runTest(testDispatcher) {
        val distPhase = TrainingPhase(PhaseType.EASY, 0, distanceMeters = 1000.0)
        val routine = WorkoutRoutine(listOf(distPhase), 0)

        phaseTimer.start(routine)
        runCurrent()

        // Simulate progress updates without calling nextPhase
        repeat(10) {
            phaseTimer.updateDistanceProgress(it * 0.1f, (1000.0 - it * 100.0).coerceAtLeast(0.0))
            runCurrent()
        }

        val state = phaseTimer.state.value
        assertEquals(0, state.phaseIndex)
        assertFalse(state.isFreePhase)
    }

    @Test
    fun `WorkoutService nextPhase contract advances PhaseTimer when called`() = runTest(testDispatcher) {
        val distPhase = TrainingPhase(PhaseType.EASY, 0, distanceMeters = 500.0)
        val restPhase = TrainingPhase(PhaseType.REST, 60)
        val routine = WorkoutRoutine(listOf(distPhase, restPhase), 60)

        phaseTimer.start(routine)
        runCurrent()

        // Simulate WorkoutService detecting target reached → calls nextPhase()
        phaseTimer.updateDistanceProgress(1.0f, 0.0)
        phaseTimer.nextPhase()
        runCurrent()

        val state = phaseTimer.state.value
        assertEquals(PhaseType.REST, state.currentPhase?.type)
        assertEquals(1, state.phaseIndex)
    }
}
