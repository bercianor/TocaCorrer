package es.bercianor.tocacorrer.service

import es.bercianor.tocacorrer.domain.model.TrainingPhase
import es.bercianor.tocacorrer.domain.model.WorkoutRoutine
import es.bercianor.tocacorrer.domain.model.PhaseType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Tests for PhaseTimer with new PhaseType system and wall-clock (TimerClock) abstraction.
 *
 * The fake clock is backed by the TestCoroutineScheduler's currentTime so that
 * clock.nowMs() advances exactly in step with each coroutine tick (delay).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PhaseTimerTest {

    private val scheduler = TestCoroutineScheduler()
    private val testDispatcher = StandardTestDispatcher(scheduler)
    private lateinit var phaseTimer: PhaseTimer

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // The fake clock mirrors the scheduler's virtual time so every delay(1000) tick
        // advances the clock by exactly 1000ms — no manual fakeNow management needed.
        phaseTimer = PhaseTimer(
            scope = kotlinx.coroutines.CoroutineScope(testDispatcher),
            clock = TimerClock { scheduler.currentTime }
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        phaseTimer.stop()
    }

    // -------------------------------------------------------------------------
    // Existing tests
    // -------------------------------------------------------------------------

    @Test
    fun `start with empty routine does not change state`() = runTest(scheduler) {
        phaseTimer.start(WorkoutRoutine.EMPTY)
        runCurrent()

        val state = phaseTimer.state.value
        assertEquals(null, state.currentPhase)
        assertEquals(0, state.totalPhases)
    }

    @Test
    fun `start with valid routine sets first phase`() = runTest(scheduler) {
        val routine = WorkoutRoutine(
            phases = listOf(
                TrainingPhase(PhaseType.EASY, 60),
                TrainingPhase(PhaseType.REST, 30)
            ),
            totalDurationSeconds = 90
        )

        phaseTimer.start(routine)
        runCurrent()

        val state = phaseTimer.state.value
        assertEquals(PhaseType.EASY, state.currentPhase?.type)
        assertEquals(60, state.currentPhase?.durationSeconds)
        assertEquals(2, state.totalPhases)
        assertEquals(0, state.phaseIndex)
    }

    @Test
    fun `timer counts down correctly`() = runTest(scheduler) {
        val routine = WorkoutRoutine(
            phases = listOf(TrainingPhase(PhaseType.TROT, 5)),
            totalDurationSeconds = 5
        )

        phaseTimer.start(routine)
        runCurrent()

        // Initial state
        var state = phaseTimer.state.value
        assertEquals(5, state.remainingSeconds)

        // Advance 1 second
        advanceTimeBy(1000)
        runCurrent()

        state = phaseTimer.state.value
        assertEquals(4, state.remainingSeconds)
    }

    @Test
    fun `nextPhase moves to next phase`() = runTest(scheduler) {
        val routine = WorkoutRoutine(
            phases = listOf(
                TrainingPhase(PhaseType.EASY, 2),
                TrainingPhase(PhaseType.REST, 2)
            ),
            totalDurationSeconds = 4
        )

        phaseTimer.start(routine)
        runCurrent()

        var state = phaseTimer.state.value
        assertEquals(PhaseType.EASY, state.currentPhase?.type)

        // Manually advance to next phase
        phaseTimer.nextPhase()
        runCurrent()

        state = phaseTimer.state.value
        assertEquals(PhaseType.REST, state.currentPhase?.type)
        assertEquals(1, state.phaseIndex)
    }

    @Test
    fun `pause stops the timer`() = runTest(scheduler) {
        val routine = WorkoutRoutine(
            phases = listOf(TrainingPhase(PhaseType.EASY, 10)),
            totalDurationSeconds = 10
        )

        phaseTimer.start(routine)
        runCurrent()

        advanceTimeBy(2000)
        runCurrent()

        phaseTimer.pause()
        runCurrent()

        val stateBeforePause = phaseTimer.state.value
        assertTrue(stateBeforePause.isPaused)

        advanceTimeBy(5000)
        runCurrent()

        val stateAfterPause = phaseTimer.state.value
        assertEquals(stateBeforePause.remainingSeconds, stateAfterPause.remainingSeconds)
    }

    @Test
    fun `resume continues the timer`() = runTest(scheduler) {
        val routine = WorkoutRoutine(
            phases = listOf(TrainingPhase(PhaseType.EASY, 10)),
            totalDurationSeconds = 10
        )

        phaseTimer.start(routine)
        runCurrent()

        // 2 active seconds → remaining = 8
        advanceTimeBy(2000)
        runCurrent()

        phaseTimer.pause()
        runCurrent()

        // 3 seconds of pause — job is cancelled, no ticks but virtual time advances
        advanceTimeBy(3000)
        runCurrent()

        // Resume — PhaseTimer records totalPausedMs = 3000
        phaseTimer.resume()
        runCurrent()

        // 1 more active second → total active = 3s (2+1), remaining = 7
        advanceTimeBy(1000)
        runCurrent()

        val state = phaseTimer.state.value
        assertEquals(7, state.remainingSeconds) // 10 - 2 - 1 = 7
        assertFalse(state.isPaused)
    }

    @Test
    fun `complete phase moves to next`() = runTest(scheduler) {
        val routine = WorkoutRoutine(
            phases = listOf(
                TrainingPhase(PhaseType.EASY, 1),
                TrainingPhase(PhaseType.REST, 1)
            ),
            totalDurationSeconds = 2
        )

        phaseTimer.start(routine)
        runCurrent()

        // Slightly more than 1 second triggers phase auto-advance
        advanceTimeBy(1100)
        runCurrent()

        val state = phaseTimer.state.value
        assertEquals(PhaseType.REST, state.currentPhase?.type)
        assertEquals(1, state.phaseIndex)
    }

    @Test
    fun `last phase completion sets isFreePhase`() = runTest(scheduler) {
        val routine = WorkoutRoutine(
            phases = listOf(
                TrainingPhase(PhaseType.EASY, 1),
                TrainingPhase(PhaseType.REST, 1)
            ),
            totalDurationSeconds = 2
        )

        phaseTimer.start(routine)
        runCurrent()

        // First phase completes
        advanceTimeBy(1100)
        runCurrent()

        // Second phase completes
        advanceTimeBy(1100)
        runCurrent()

        val state = phaseTimer.state.value
        assertTrue(state.isFreePhase)
    }

    @Test
    fun `stop resets the timer`() = runTest(scheduler) {
        val routine = WorkoutRoutine(
            phases = listOf(TrainingPhase(PhaseType.EASY, 10)),
            totalDurationSeconds = 10
        )

        phaseTimer.start(routine)
        runCurrent()

        advanceTimeBy(2000)
        runCurrent()

        phaseTimer.stop()
        runCurrent()

        val state = phaseTimer.state.value
        assertNull(state.currentPhase)
        assertEquals(0, state.remainingSeconds)
    }

    @Test
    fun `free phase is set after all phases complete`() = runTest(scheduler) {
        val routine = WorkoutRoutine(
            phases = listOf(TrainingPhase(PhaseType.TROT, 1)),
            totalDurationSeconds = 1
        )

        phaseTimer.start(routine)
        runCurrent()

        advanceTimeBy(1100)
        runCurrent()

        val state = phaseTimer.state.value
        assertTrue(state.isFreePhase)
        assertNull(state.currentPhase)
    }

    @Test
    fun `distance phase does not auto-advance when time passes`() = runTest(scheduler) {
        // A distance phase (durationSeconds=0, distanceMeters=1000) should NOT auto-advance
        val distancePhase = TrainingPhase(PhaseType.EASY, 0, distanceMeters = 1000.0)
        val nextPhase = TrainingPhase(PhaseType.REST, 60)
        val routine = WorkoutRoutine(
            phases = listOf(distancePhase, nextPhase),
            totalDurationSeconds = 60
        )

        phaseTimer.start(routine)
        runCurrent()

        // Initial state: distance phase is active
        var state = phaseTimer.state.value
        assertEquals(PhaseType.EASY, state.currentPhase?.type)
        assertEquals(0, state.phaseIndex)
        assertEquals(1000.0, state.remainingDistanceMeters)

        // Advance a lot of time — should NOT auto-advance (no countdown for distance phases)
        advanceTimeBy(10000)
        runCurrent()

        state = phaseTimer.state.value
        assertEquals(0, state.phaseIndex) // Still on phase 0
        assertEquals(PhaseType.EASY, state.currentPhase?.type)
    }

    @Test
    fun `distance phase advances when nextPhase is called`() = runTest(scheduler) {
        val distancePhase = TrainingPhase(PhaseType.EASY, 0, distanceMeters = 500.0)
        val restPhase = TrainingPhase(PhaseType.REST, 30)
        val routine = WorkoutRoutine(
            phases = listOf(distancePhase, restPhase),
            totalDurationSeconds = 30
        )

        phaseTimer.start(routine)
        runCurrent()

        var state = phaseTimer.state.value
        assertEquals(0, state.phaseIndex)
        assertEquals(PhaseType.EASY, state.currentPhase?.type)

        // Manually call nextPhase — simulates WorkoutService detecting distance reached
        phaseTimer.nextPhase()
        runCurrent()

        state = phaseTimer.state.value
        assertEquals(1, state.phaseIndex)
        assertEquals(PhaseType.REST, state.currentPhase?.type)
    }

    @Test
    fun `updateDistanceProgress updates state correctly`() = runTest(scheduler) {
        val distancePhase = TrainingPhase(PhaseType.EASY, 0, distanceMeters = 1000.0)
        val routine = WorkoutRoutine(
            phases = listOf(distancePhase),
            totalDurationSeconds = 0
        )

        phaseTimer.start(routine)
        runCurrent()

        // Simulate 50% distance covered
        phaseTimer.updateDistanceProgress(0.5f, 500.0)
        runCurrent()

        val state = phaseTimer.state.value
        assertEquals(0.5f, state.distanceProgress, 0.001f)
        assertEquals(500.0, state.remainingDistanceMeters!!, 0.001)
        assertEquals(0.5f, state.progress, 0.001f)
    }

    // -------------------------------------------------------------------------
    // New precision tests for wall-clock accuracy
    // -------------------------------------------------------------------------

    @Test
    fun `drift elimination - uneven ticks do not accumulate error`() = runTest(scheduler) {
        // Start a 10s phase. Advance virtual time by 5000ms in one shot.
        // The scheduler's currentTime will reflect each 1000ms tick accurately.
        // Wall-clock elapsed = 5000ms = 5s → remaining = 5s. No cumulative drift.
        val routine = WorkoutRoutine(
            phases = listOf(TrainingPhase(PhaseType.EASY, 10)),
            totalDurationSeconds = 10
        )

        phaseTimer.start(routine)
        runCurrent()

        // Advance 5 ticks worth of virtual time (5000ms)
        // delay(1000) fires 5 times; after each tick, elapsed = scheduler.currentTime / 1000
        advanceTimeBy(5000)
        runCurrent()

        val state = phaseTimer.state.value
        // After 5 ticks: elapsed = 5s → remaining = 10 - 5 = 5
        assertEquals(5, state.remainingSeconds)
    }

    @Test
    fun `single pause-resume excludes pause duration from elapsed time`() = runTest(scheduler) {
        // Start a 60s phase. Run 10s active, pause 30s, resume. Remaining must still be 50s.
        val routine = WorkoutRoutine(
            phases = listOf(TrainingPhase(PhaseType.EASY, 60)),
            totalDurationSeconds = 60
        )

        phaseTimer.start(routine)
        runCurrent()

        // 10 active seconds (virtual time = 10000ms, elapsed = 10s, remaining = 50s)
        advanceTimeBy(10000)
        runCurrent()

        val stateAfterActive = phaseTimer.state.value
        assertEquals(50, stateAfterActive.remainingSeconds)

        // Pause: PhaseTimer records pauseStartMs = scheduler.currentTime = 10000
        phaseTimer.pause()
        runCurrent()

        // 30 seconds of pause (virtual time advances to 40000ms)
        // Job is cancelled — no ticks fire during pause
        advanceTimeBy(30000)
        runCurrent()

        // Resume: PhaseTimer absorbs totalPausedMs = 40000 - 10000 = 30000ms
        phaseTimer.resume()
        runCurrent()

        // Immediately after resume (before any new tick), remaining must still be 50
        val stateAfterResume = phaseTimer.state.value
        assertEquals(50, stateAfterResume.remainingSeconds)
        assertFalse(stateAfterResume.isPaused)
    }

    @Test
    fun `multiple pause-resume cycles only count active time`() = runTest(scheduler) {
        // Start a 30s phase. Do 3 pause/resume cycles.
        // Active time: 5s + 5s + 5s = 15s → remaining = 15s
        // Pause durations (10s, 20s, 7s) are excluded.
        val routine = WorkoutRoutine(
            phases = listOf(TrainingPhase(PhaseType.EASY, 30)),
            totalDurationSeconds = 30
        )

        phaseTimer.start(routine)
        runCurrent()

        // Cycle 1: 5 active seconds (virtual time 0→5000), then pause (5000→15000), resume
        advanceTimeBy(5000)
        runCurrent()
        phaseTimer.pause()
        runCurrent()
        advanceTimeBy(10000)  // 10s of pause (virtual time 5000→15000)
        runCurrent()
        phaseTimer.resume()
        runCurrent()

        // Cycle 2: 5 active seconds (virtual time 15000→20000), then pause (20000→40000), resume
        advanceTimeBy(5000)
        runCurrent()
        phaseTimer.pause()
        runCurrent()
        advanceTimeBy(20000)  // 20s of pause (virtual time 20000→40000)
        runCurrent()
        phaseTimer.resume()
        runCurrent()

        // Cycle 3: 5 active seconds (virtual time 40000→45000), then pause (45000→52000), resume
        advanceTimeBy(5000)
        runCurrent()
        phaseTimer.pause()
        runCurrent()
        advanceTimeBy(7000)   // 7s of pause (virtual time 45000→52000)
        runCurrent()
        phaseTimer.resume()
        runCurrent()

        // Total active = 15s → remaining = 30 - 15 = 15
        val state = phaseTimer.state.value
        assertEquals(15, state.remainingSeconds)
    }

    @Test
    fun `phase transition resets clock fields so second phase starts fresh`() = runTest(scheduler) {
        // Run a 2-phase routine. After phase 1 completes, phase 2 must start from its full duration.
        // Phase 1: 2s, Phase 2: 5s
        val routine = WorkoutRoutine(
            phases = listOf(
                TrainingPhase(PhaseType.EASY, 2),
                TrainingPhase(PhaseType.REST, 5)
            ),
            totalDurationSeconds = 7
        )

        phaseTimer.start(routine)
        runCurrent()

        // Verify initial state
        assertEquals(2, phaseTimer.state.value.remainingSeconds)
        assertEquals(0, phaseTimer.state.value.phaseIndex)

        // Complete phase 1 (2 seconds + small buffer to trigger transition)
        advanceTimeBy(2100)
        runCurrent()

        // Phase 2 should now be active with full duration
        val stateAfterTransition = phaseTimer.state.value
        assertEquals(PhaseType.REST, stateAfterTransition.currentPhase?.type)
        assertEquals(1, stateAfterTransition.phaseIndex)
        assertEquals(5, stateAfterTransition.remainingSeconds)

        // Advance 1 second into phase 2 — remaining should be 4, not bleed from phase 1
        advanceTimeBy(1000)
        runCurrent()

        val stateInPhase2 = phaseTimer.state.value
        assertEquals(4, stateInPhase2.remainingSeconds)
    }

    @Test
    fun `jitter - wall-clock correction produces correct remainingSeconds independent of tick count`() = runTest(scheduler) {
        // FIX 8: Use a ManualClock that advances INDEPENDENTLY of the scheduler.
        // This test would FAIL with the old tick-decrement (remainingSeconds--) approach
        // because that approach counts ticks, not elapsed wall-clock time.
        //
        // Scenario: phase = 10s. We advance the scheduler by 1000ms per tick (so 3 ticks = 3000ms
        // scheduled), but advance the manual clock by 1400ms per tick (3 ticks = 4200ms wall time).
        // Old code: remaining = 10 - 3 = 7 (wrong — counts ticks)
        // New code: remaining = 10 - 4 = 6 (correct — uses wall clock, 4200ms / 1000 = 4s elapsed)
        var manualClockMs = 0L
        val jitterTimer = PhaseTimer(
            scope = kotlinx.coroutines.CoroutineScope(testDispatcher),
            clock = TimerClock { manualClockMs }
        )

        val routine = WorkoutRoutine(
            phases = listOf(TrainingPhase(PhaseType.EASY, 10)),
            totalDurationSeconds = 10
        )

        jitterTimer.start(routine)
        runCurrent()
        assertEquals(10, jitterTimer.state.value.remainingSeconds)

        // Tick 1: scheduler advances 1000ms, manual clock advances 1400ms
        manualClockMs += 1400
        advanceTimeBy(1000)
        runCurrent()
        // Wall elapsed = 1400ms → 1s → remaining = 10 - 1 = 9
        // Tick-decrement would also give 9 here — not distinguishable yet
        assertEquals(9, jitterTimer.state.value.remainingSeconds)

        // Tick 2: scheduler advances another 1000ms (total: 2000ms), manual clock += 1400ms (total: 2800ms)
        manualClockMs += 1400
        advanceTimeBy(1000)
        runCurrent()
        // Wall elapsed = 2800ms → 2s → remaining = 10 - 2 = 8
        // Tick-decrement would give 8 — still the same
        assertEquals(8, jitterTimer.state.value.remainingSeconds)

        // Tick 3: scheduler advances another 1000ms (total: 3000ms), manual clock += 1400ms (total: 4200ms)
        manualClockMs += 1400
        advanceTimeBy(1000)
        runCurrent()
        // Wall elapsed = 4200ms → 4s → remaining = 10 - 4 = 6
        // Tick-decrement would give 10 - 3 = 7 (WRONG — this is the distinguishing assertion)
        assertEquals(6, jitterTimer.state.value.remainingSeconds)

        jitterTimer.stop()
    }
}
