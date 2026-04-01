package es.bercianor.tocacorrer.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for TrainingPhase with new PhaseType system.
 */
class TrainingPhaseTest {

    @Test
    fun `create phase with type and duration`() {
        val phase = TrainingPhase(PhaseType.EASY, 300)
        
        assertEquals(PhaseType.EASY, phase.type)
        assertEquals(300, phase.durationSeconds)
    }

    @Test
    fun `durationMinutes calculated correctly`() {
        val phase = TrainingPhase(PhaseType.EASY, 300)
        
        assertEquals(5, phase.durationMinutes)
    }

    @Test
    fun `letter for EASY phase is R`() {
        val phase = TrainingPhase(PhaseType.EASY, 300)
        
        assertEquals("R", phase.letter)
    }

    @Test
    fun `letter for REST phase is D`() {
        val phase = TrainingPhase(PhaseType.REST, 60)
        
        assertEquals("D", phase.letter)
    }

    @Test
    fun `letter for TROT phase is T`() {
        val phase = TrainingPhase(PhaseType.TROT, 300)
        
        assertEquals("T", phase.letter)
    }

    @Test
    fun `letter for EASY_CHEERFUL phase is RA`() {
        val phase = TrainingPhase(PhaseType.EASY_CHEERFUL, 300)
        
        assertEquals("RA", phase.letter)
    }

    @Test
    fun `letter for EASY_STRONG phase is RF`() {
        val phase = TrainingPhase(PhaseType.EASY_STRONG, 300)
        
        assertEquals("RF", phase.letter)
    }

    @Test
    fun `letter for FARTLEK phase is F`() {
        val phase = TrainingPhase(PhaseType.FARTLEK, 300)
        
        assertEquals("F", phase.letter)
    }

    @Test
    fun `letter for PROGRESSIVES phase is P`() {
        val phase = TrainingPhase(PhaseType.PROGRESSIVES, 300)
        
        assertEquals("P", phase.letter)
    }

    @Test
    fun `letter for RACE_PACE phase is RC`() {
        val phase = TrainingPhase(PhaseType.RACE_PACE, 300)
        
        assertEquals("RC", phase.letter)
    }

    @Test
    fun `letter for EXTRA phase is X`() {
        val phase = TrainingPhase(PhaseType.EXTRA, 0)
        
        assertEquals("X", phase.letter)
    }

    @Test
    fun `PhaseType has all expected values`() {
        assertEquals(9, PhaseType.entries.size)
        assertTrue(PhaseType.entries.contains(PhaseType.REST))
        assertTrue(PhaseType.entries.contains(PhaseType.TROT))
        assertTrue(PhaseType.entries.contains(PhaseType.EASY))
        assertTrue(PhaseType.entries.contains(PhaseType.EASY_CHEERFUL))
        assertTrue(PhaseType.entries.contains(PhaseType.EASY_STRONG))
        assertTrue(PhaseType.entries.contains(PhaseType.FARTLEK))
        assertTrue(PhaseType.entries.contains(PhaseType.PROGRESSIVES))
        assertTrue(PhaseType.entries.contains(PhaseType.RACE_PACE))
        assertTrue(PhaseType.entries.contains(PhaseType.EXTRA))
    }

    @Test
    fun `phase with zero duration`() {
        val phase = TrainingPhase(PhaseType.REST, 0)
        
        assertEquals(0, phase.durationSeconds)
        assertEquals(0, phase.durationMinutes)
    }

    @Test
    fun `phase with maximum duration`() {
        val phase = TrainingPhase(PhaseType.EASY, 3600) // 1 hour
        
        assertEquals(3600, phase.durationSeconds)
        assertEquals(60, phase.durationMinutes)
    }

    @Test
    fun `seriesNumber is null by default`() {
        val phase = TrainingPhase(PhaseType.EASY, 300)
        
        assertNull(phase.seriesNumber)
    }

    @Test
    fun `seriesNumber can be set`() {
        val phase = TrainingPhase(PhaseType.EASY, 300, seriesNumber = 1)
        
        assertEquals(1, phase.seriesNumber)
    }

    // --- distanceMeters field ---

    @Test
    fun `distanceMeters is null by default`() {
        val phase = TrainingPhase(PhaseType.EASY, 300)
        
        assertNull(phase.distanceMeters)
    }

    @Test
    fun `distanceMeters can be set for distance-based phase`() {
        val phase = TrainingPhase(PhaseType.EASY, 0, distanceMeters = 5000.0)
        
        assertEquals(5000.0, phase.distanceMeters!!, 0.01)
    }

    @Test
    fun `durationUnitKey is min for time-based phase`() {
        val phase = TrainingPhase(PhaseType.EASY, 300)
        
        assertEquals("min", phase.durationUnitKey)
    }

    @Test
    fun `durationUnitKey is km for distance-based phase`() {
        val phase = TrainingPhase(PhaseType.EASY, 0, distanceMeters = 5000.0)
        
        assertEquals("km", phase.durationUnitKey)
    }

    @Test
    fun `distance-based phase has zero durationSeconds`() {
        val phase = TrainingPhase(PhaseType.EASY, 0, distanceMeters = 3000.0)
        
        assertEquals(0, phase.durationSeconds)
    }
}