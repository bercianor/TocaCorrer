package es.bercianor.tocacorrer.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Tests for WorkoutRoutine with new PhaseType system.
 */
class WorkoutRoutineTest {

    @Test
    fun `empty routine has correct values`() {
        val routine = WorkoutRoutine.EMPTY
        
        assertTrue(routine.phases.isEmpty())
        assertEquals(0, routine.totalDurationSeconds)
    }

    @Test
    fun `create routine with one phase`() {
        val phase = TrainingPhase(PhaseType.EASY, 300)
        val routine = WorkoutRoutine(listOf(phase), 300)
        
        assertEquals(1, routine.phases.size)
        assertEquals(300, routine.totalDurationSeconds)
    }

    @Test
    fun `create routine with multiple phases`() {
        val phases = listOf(
            TrainingPhase(PhaseType.EASY, 300),
            TrainingPhase(PhaseType.REST, 60),
            TrainingPhase(PhaseType.TROT, 300)
        )
        val routine = WorkoutRoutine(phases, 660)
        
        assertEquals(3, routine.phases.size)
        assertEquals(660, routine.totalDurationSeconds)
    }

    @Test
    fun `total duration is correct`() {
        val phases = listOf(
            TrainingPhase(PhaseType.EASY, 600),
            TrainingPhase(PhaseType.REST, 60),
            TrainingPhase(PhaseType.TROT, 600)
        )
        val routine = WorkoutRoutine(phases, 1260)
        
        assertEquals(1260, routine.totalDurationSeconds)
    }

    @Test
    fun `routine is not empty when it has phases`() {
        val phase = TrainingPhase(PhaseType.EASY, 60)
        val routine = WorkoutRoutine(listOf(phase), 60)
        
        assertFalse(routine.phases.isEmpty())
    }

    @Test
    fun `routine with interval series`() {
        // 4x(T3-D1) = 4 * (180 + 60) = 960 seconds
        val seriesPhases = mutableListOf<TrainingPhase>()
        repeat(4) {
            seriesPhases.add(TrainingPhase(PhaseType.TROT, 180))
            seriesPhases.add(TrainingPhase(PhaseType.REST, 60))
        }
        
        val routine = WorkoutRoutine(seriesPhases, 960)
        
        assertEquals(8, routine.phases.size)
        assertEquals(960, routine.totalDurationSeconds)
    }

    @Test
    fun `phases are accessible by index`() {
        val phases = listOf(
            TrainingPhase(PhaseType.EASY, 300),
            TrainingPhase(PhaseType.REST, 60)
        )
        val routine = WorkoutRoutine(phases, 360)
        
        assertEquals(PhaseType.EASY, routine.phases[0].type)
        assertEquals(PhaseType.REST, routine.phases[1].type)
    }

    @Test
    fun `total duration with zero phase`() {
        val phases = listOf(
            TrainingPhase(PhaseType.EASY, 300),
            TrainingPhase(PhaseType.REST, 0)
        )
        val routine = WorkoutRoutine(phases, 300)
        
        assertEquals(300, routine.totalDurationSeconds)
    }

    @Test
    fun `duration in minutes`() {
        val phase = TrainingPhase(PhaseType.EASY, 600) // 10 min
        val routine = WorkoutRoutine(listOf(phase), 600)
        
        assertEquals(10, routine.totalDurationSeconds / 60)
    }

    @Test
    fun `routine with different phase types`() {
        // R10 - T5 - D2 - F10
        val phases = listOf(
            TrainingPhase(PhaseType.EASY, 600),    // R10 = 10 min
            TrainingPhase(PhaseType.TROT, 300),    // T5 = 5 min
            TrainingPhase(PhaseType.REST, 120),    // D2 = 2 min
            TrainingPhase(PhaseType.FARTLEK, 600)  // F10 = 10 min
        )
        val routine = WorkoutRoutine(phases, 1620)
        
        assertEquals(4, routine.phases.size)
        assertEquals(PhaseType.EASY, routine.phases[0].type)
        assertEquals(PhaseType.TROT, routine.phases[1].type)
        assertEquals(PhaseType.REST, routine.phases[2].type)
        assertEquals(PhaseType.FARTLEK, routine.phases[3].type)
    }

    // --- totalDistanceMeters tests ---

    @Test
    fun `totalDistanceMeters is zero for time-only routine`() {
        val phases = listOf(
            TrainingPhase(PhaseType.EASY, 300),
            TrainingPhase(PhaseType.REST, 60),
            TrainingPhase(PhaseType.TROT, 300)
        )
        val routine = WorkoutRoutine(phases, 660)

        assertEquals(0.0, routine.totalDistanceMeters, 0.001)
    }

    @Test
    fun `totalDistanceMeters sums distance phases correctly`() {
        // Pure distance routine: R1k - D0.5k = 1500m
        val phases = listOf(
            TrainingPhase(PhaseType.EASY, 0, distanceMeters = 1000.0),
            TrainingPhase(PhaseType.REST, 0, distanceMeters = 500.0)
        )
        val routine = WorkoutRoutine(phases, 0)

        assertEquals(1500.0, routine.totalDistanceMeters, 0.001)
    }

    @Test
    fun `totalDistanceMeters sums only distance phases in mixed routine`() {
        // Mixed: R5 (time) + 2x(R1k - D1) (distance+time) + R5 (time)
        // Only R1k * 2 = 2000m contribute to distance
        val phases = listOf(
            TrainingPhase(PhaseType.EASY, 300),                           // R5 time-only
            TrainingPhase(PhaseType.EASY, 0, distanceMeters = 1000.0),   // R1k first rep
            TrainingPhase(PhaseType.REST, 60),                            // D1 time-only
            TrainingPhase(PhaseType.EASY, 0, distanceMeters = 1000.0),   // R1k second rep
            TrainingPhase(PhaseType.REST, 60),                            // D1 time-only
            TrainingPhase(PhaseType.EASY, 300)                            // R5 time-only
        )
        val routine = WorkoutRoutine(phases, 720)

        assertEquals(2000.0, routine.totalDistanceMeters, 0.001)
    }
}
