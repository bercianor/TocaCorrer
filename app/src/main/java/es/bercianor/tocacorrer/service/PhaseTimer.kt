package es.bercianor.tocacorrer.service

import es.bercianor.tocacorrer.domain.model.TrainingPhase
import es.bercianor.tocacorrer.domain.model.WorkoutRoutine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Abstraction for the system clock used by PhaseTimer.
 * Allows injection of a fake clock in tests for deterministic behavior.
 */
fun interface TimerClock {
    fun nowMs(): Long
}

/**
 * Timer to manage workout phases.
 *
 * Handles phase transitions, remaining time, and notifies when a phase changes.
 * Uses [TimerClock] for wall-clock accuracy — drift-free even if coroutine ticks are uneven.
 */
@OptIn(kotlin.ExperimentalStdlibApi::class)
class PhaseTimer(
    private val scope: CoroutineScope,
    private val clock: TimerClock = TimerClock { android.os.SystemClock.elapsedRealtime() }
) {
    // Timer state
    private val _state = MutableStateFlow(TimerState())
    val state: StateFlow<TimerState> = _state.asStateFlow()

    private var job: Job? = null
    @Volatile private var routine: WorkoutRoutine = WorkoutRoutine.EMPTY
    @Volatile private var currentPhaseIndex: Int = 0
    @Volatile private var remainingSeconds: Int = 0
    @Volatile private var distancePhaseGate: CompletableDeferred<Unit>? = null

    // Wall-clock tracking for drift-free countdown.
    // AtomicLong provides both visibility AND atomicity for compound read-modify-write ops
    // (e.g. totalPausedMs.addAndGet(delta)). @Volatile alone is insufficient for those.
    // FIX 3: pauseStartMs sentinel is -1L (not 0L) so T=0 pauses are correctly tracked.
    private val phaseStartMs = AtomicLong(0L)
    private val totalPausedMs = AtomicLong(0L)
    private val pauseStartMs = AtomicLong(-1L)

    // FIX 4: isStopped flag prevents zombie timer relaunches after stop()
    @Volatile private var isStopped = false

    // Guard against concurrent goToNextPhase calls (timer loop vs nextPhase() public call)
    private val transitioning = AtomicBoolean(false)

    // Extract dispatcher from scope, fallback to Default if not found
    private val dispatcher: CoroutineDispatcher = scope.coroutineContext[CoroutineDispatcher]
        ?: kotlinx.coroutines.Dispatchers.Default

    /**
     * Starts the timer with a workout routine.
     */
    fun start(routine: WorkoutRoutine) {
        this.routine = routine
        this.currentPhaseIndex = 0
        this.remainingSeconds = routine.phases.firstOrNull()?.durationSeconds ?: 0

        if (routine.phases.isEmpty()) return

        val firstPhase = routine.phases[0]
        _state.value = TimerState(
            currentPhase = firstPhase,
            remainingSeconds = remainingSeconds,
            phaseIndex = 0,
            totalPhases = routine.phases.size,
            isPaused = false,
            isCompleted = false,
            remainingDistanceMeters = firstPhase.distanceMeters
        )

        // Initialise wall-clock tracking for the first phase
        phaseStartMs.set(clock.nowMs())
        totalPausedMs.set(0L)
        pauseStartMs.set(-1L)       // FIX 3: -1L = "not paused" sentinel
        isStopped = false           // FIX 4: clear stop flag on fresh start
        transitioning.set(false)
        startCounter()
    }

    /**
     * Pauses the timer.
     * Idempotent: calling pause() when already paused is a no-op.
     */
    fun pause() {
        // FIX 2: Idempotency guard — do not overwrite pauseStartMs if already paused
        if (_state.value.isPaused) return
        pauseStartMs.set(clock.nowMs())    // FIX 1: atomic set; FIX 3: always >= 0 at pause time
        job?.cancel()
        distancePhaseGate?.cancel()
        distancePhaseGate = null
        _state.update { it.copy(isPaused = true) }
    }

    /**
     * Resumes the timer.
     * Idempotent: calling resume() when not paused is a no-op (FIX 2).
     * Only accumulates pausedMs if a prior pause() was called (FIX 1).
     */
    fun resume() {
        // FIX 2: Idempotency guard — do not double-count paused time
        if (!_state.value.isPaused) return
        // FIX 1 + FIX 3: guard is >= 0L (not > 0L) so T=0 pauses are correctly absorbed
        val ps = pauseStartMs.get()
        if (ps >= 0L) {
            totalPausedMs.addAndGet(clock.nowMs() - ps)  // FIX 1: atomic compound update
            pauseStartMs.set(-1L)  // FIX 3: reset to "not paused" sentinel
        }
        _state.update { it.copy(isPaused = false) }
        startCounter()
    }

    /**
     * Stops and resets the timer.
     */
    fun stop() {
        isStopped = true    // FIX 4: prevent zombie timer if goToNextPhase() is already running
        job?.cancel()
        distancePhaseGate?.cancel()
        distancePhaseGate = null
        _state.value = TimerState()
    }

    /**
     * Manually advances to the next phase.
     * For distance phases, this completes the gate that suspends [startCounter].
     */
    fun nextPhase() {
        // Guard: do nothing if the timer is not active (stopped or never started)
        if (_state.value.currentPhase == null && !_state.value.isFreePhase) return
        distancePhaseGate?.complete(Unit)
        distancePhaseGate = null
        goToNextPhase()
    }

    /**
     * Updates progress for a distance-based phase.
     * Called by WorkoutService on each GPS update.
     * [progress] is in range 0..1.
     */
    fun updateDistanceProgress(progress: Float, remainingDistanceMeters: Double? = null) {
        val currentPhase = _state.value.currentPhase ?: return
        if (currentPhase.distanceMeters == null) return

        val remaining = remainingDistanceMeters
            ?: ((currentPhase.distanceMeters) * (1f - progress.coerceIn(0f, 1f)))

        _state.update { it.copy(
            distanceProgress = progress.coerceIn(0f, 1f),
            remainingDistanceMeters = remaining
        ) }
    }

    private fun startCounter() {
        if (isStopped) return   // FIX 4: bail out if stop() was called before we got here
        job?.cancel()
        val phase = routine.phases.getOrNull(currentPhaseIndex)

        // Distance phase: block until WorkoutService calls nextPhase() or gate completes
        if (phase != null && phase.durationSeconds == 0 && phase.distanceMeters != null) {
            distancePhaseGate = CompletableDeferred()
            _state.update { it.copy(
                remainingSeconds = 0,
                distanceProgress = 0f,
                remainingDistanceMeters = phase.distanceMeters
            ) }
            job = scope.launch(dispatcher) {
                try {
                    distancePhaseGate?.await()
                } catch (_: Exception) {
                    // Cancelled (pause/stop) — just return
                }
            }
            return
        }

        // Time phase: wall-clock-based countdown (drift-free)
        distancePhaseGate = null
        val phaseDuration = phase?.durationSeconds ?: 0

        job = scope.launch(dispatcher) {
            while (remainingSeconds > 0 && !_state.value.isCompleted && !_state.value.isFreePhase) {
                delay(1000)

                val elapsed = ((clock.nowMs() - phaseStartMs.get() - totalPausedMs.get()) / 1000).coerceAtLeast(0L)
                remainingSeconds = (phaseDuration - elapsed).coerceIn(0L, phaseDuration.toLong()).toInt()

                _state.update { it.copy(
                    remainingSeconds = remainingSeconds
                ) }
            }

            // Phase ended
            if (remainingSeconds <= 0) {
                goToNextPhase()
            }
        }
    }

    private fun goToNextPhase() {
        // FIX 6: Prevent concurrent calls (timer loop + nextPhase() public call).
        // If transitioning is already true, another caller got here first — bail out.
        if (!transitioning.compareAndSet(false, true)) return

        // FIX 4: If stop() was already called while we were in the timer loop, bail out.
        if (isStopped) {
            transitioning.set(false)
            return
        }

        try {
            currentPhaseIndex++

            if (currentPhaseIndex >= routine.phases.size) {
                // Routine completed — enter free phase (user must stop manually)
                _state.update { it.copy(
                    isFreePhase = true,
                    isCompleted = false,
                    currentPhase = null,
                    remainingSeconds = 0,
                    distanceProgress = 0f,
                    remainingDistanceMeters = null
                ) }
                job?.cancel()
            } else {
                // Next phase — reset clock fields for fresh tracking
                phaseStartMs.set(clock.nowMs())
                totalPausedMs.set(0L)
                pauseStartMs.set(-1L)   // FIX 3: "not paused" sentinel

                val nextPhase = routine.phases[currentPhaseIndex]
                remainingSeconds = nextPhase.durationSeconds

                _state.update { it.copy(
                    currentPhase = nextPhase,
                    remainingSeconds = remainingSeconds,
                    phaseIndex = currentPhaseIndex,
                    isPaused = false,
                    distanceProgress = 0f,
                    remainingDistanceMeters = nextPhase.distanceMeters
                ) }

                startCounter()
            }
        } finally {
            transitioning.set(false)
        }
    }
}

/**
 * Current state of the timer.
 */
data class TimerState(
    val currentPhase: TrainingPhase? = null,
    val remainingSeconds: Int = 0,
    val phaseIndex: Int = 0,
    val totalPhases: Int = 0,
    val isPaused: Boolean = false,
    val isCompleted: Boolean = false,
    val isFreePhase: Boolean = false,
    val distanceProgress: Float = 0f,          // externally-set progress for distance phases (0..1)
    val remainingDistanceMeters: Double? = null // non-null only when current phase is distance-based
) {
    val progress: Float
        get() = if (totalPhases == 0 || currentPhase == null) 0f
                else if (currentPhase.distanceMeters != null) {
                    // Distance phase: use externally-provided progress
                    distanceProgress
                } else {
                    currentPhase.durationSeconds.let { duration ->
                        if (duration == 0) 0f
                        else 1f - (remainingSeconds.toFloat() / duration)
                    }
                }
}
