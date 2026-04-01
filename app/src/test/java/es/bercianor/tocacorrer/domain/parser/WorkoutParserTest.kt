package es.bercianor.tocacorrer.domain.parser

import es.bercianor.tocacorrer.domain.model.TrainingPhase
import es.bercianor.tocacorrer.domain.model.PhaseType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for WorkoutParser with new token system (D, T, R, RA, RF, F, P, RC).
 */
class WorkoutParserTest {

    // -----------------------------------------------------------------------
    // Basic / empty input
    // -----------------------------------------------------------------------

    @Test
    fun `parse - empty string returns empty routine`() {
        val result = WorkoutParser.parse("")
        
        assertEquals(true, result.phases.isEmpty())
        assertEquals(0, result.totalDurationSeconds)
    }

    @Test
    fun `parse - string with only spaces returns empty routine`() {
        val result = WorkoutParser.parse("   ")
        
        assertEquals(true, result.phases.isEmpty())
    }

    // -----------------------------------------------------------------------
    // Legacy C token (backward compatibility -> EASY)
    // -----------------------------------------------------------------------

    @Test
    fun `parse - legacy C token maps to EASY`() {
        val result = WorkoutParser.parse("C10")
        
        assertEquals(1, result.phases.size)
        assertEquals(PhaseType.EASY, result.phases[0].type)
        assertEquals(600, result.phases[0].durationSeconds) // 10 min * 60
    }

    // -----------------------------------------------------------------------
    // Legacy S token (backward compatibility -> REST)
    // -----------------------------------------------------------------------

    @Test
    fun `parse - legacy S token maps to REST`() {
        val result = WorkoutParser.parse("S5")
        
        assertEquals(1, result.phases.size)
        assertEquals(PhaseType.REST, result.phases[0].type)
        assertEquals(300, result.phases[0].durationSeconds)
    }

    // -----------------------------------------------------------------------
    // New D token (REST)
    // -----------------------------------------------------------------------

    @Test
    fun `parse - simple rest phase using D token`() {
        val result = WorkoutParser.parse("D5")
        
        assertEquals(1, result.phases.size)
        assertEquals(PhaseType.REST, result.phases[0].type)
        assertEquals(300, result.phases[0].durationSeconds)
    }

    @Test
    fun `parse - D with default duration (1 min)`() {
        val result = WorkoutParser.parse("D")
        
        assertEquals(1, result.phases.size)
        assertEquals(PhaseType.REST, result.phases[0].type)
        assertEquals(60, result.phases[0].durationSeconds)
    }

    // -----------------------------------------------------------------------
    // New T token (TROT)
    // -----------------------------------------------------------------------

    @Test
    fun `parse - simple trot phase using T token`() {
        val result = WorkoutParser.parse("T10")
        
        assertEquals(1, result.phases.size)
        assertEquals(PhaseType.TROT, result.phases[0].type)
        assertEquals(600, result.phases[0].durationSeconds)
    }

    @Test
    fun `parse - T with default duration (1 min)`() {
        val result = WorkoutParser.parse("T")
        
        assertEquals(1, result.phases.size)
        assertEquals(PhaseType.TROT, result.phases[0].type)
        assertEquals(60, result.phases[0].durationSeconds)
    }

    // -----------------------------------------------------------------------
    // New R token (EASY)
    // -----------------------------------------------------------------------

    @Test
    fun `parse - simple easy run phase using R token`() {
        val result = WorkoutParser.parse("R10")
        
        assertEquals(1, result.phases.size)
        assertEquals(PhaseType.EASY, result.phases[0].type)
        assertEquals(600, result.phases[0].durationSeconds)
    }

    @Test
    fun `parse - R with default duration (1 min)`() {
        val result = WorkoutParser.parse("R")
        
        assertEquals(1, result.phases.size)
        assertEquals(PhaseType.EASY, result.phases[0].type)
        assertEquals(60, result.phases[0].durationSeconds)
    }

    // -----------------------------------------------------------------------
    // RA token (EASY_CHEERFUL)
    // -----------------------------------------------------------------------

    @Test
    fun `parse - easy cheerful phase using RA token`() {
        val result = WorkoutParser.parse("RA10")
        
        assertEquals(1, result.phases.size)
        assertEquals(PhaseType.EASY_CHEERFUL, result.phases[0].type)
        assertEquals(600, result.phases[0].durationSeconds)
    }

    @Test
    fun `parse - RA with default duration`() {
        val result = WorkoutParser.parse("RA")
        
        assertEquals(1, result.phases.size)
        assertEquals(PhaseType.EASY_CHEERFUL, result.phases[0].type)
        assertEquals(60, result.phases[0].durationSeconds)
    }

    // -----------------------------------------------------------------------
    // RF token (EASY_STRONG)
    // -----------------------------------------------------------------------

    @Test
    fun `parse - easy strong phase using RF token`() {
        val result = WorkoutParser.parse("RF10")
        
        assertEquals(1, result.phases.size)
        assertEquals(PhaseType.EASY_STRONG, result.phases[0].type)
        assertEquals(600, result.phases[0].durationSeconds)
    }

    @Test
    fun `parse - RF with default duration`() {
        val result = WorkoutParser.parse("RF")
        
        assertEquals(1, result.phases.size)
        assertEquals(PhaseType.EASY_STRONG, result.phases[0].type)
        assertEquals(60, result.phases[0].durationSeconds)
    }

    // -----------------------------------------------------------------------
    // F token (FARTLEK)
    // -----------------------------------------------------------------------

    @Test
    fun `parse - fartlek phase using F token`() {
        val result = WorkoutParser.parse("F10")
        
        assertEquals(1, result.phases.size)
        assertEquals(PhaseType.FARTLEK, result.phases[0].type)
        assertEquals(600, result.phases[0].durationSeconds)
    }

    @Test
    fun `parse - F with default duration`() {
        val result = WorkoutParser.parse("F")
        
        assertEquals(1, result.phases.size)
        assertEquals(PhaseType.FARTLEK, result.phases[0].type)
        assertEquals(60, result.phases[0].durationSeconds)
    }

    // -----------------------------------------------------------------------
    // P token (PROGRESSIVES)
    // -----------------------------------------------------------------------

    @Test
    fun `parse - progressivos phase using P token`() {
        val result = WorkoutParser.parse("P10")
        
        assertEquals(1, result.phases.size)
        assertEquals(PhaseType.PROGRESSIVES, result.phases[0].type)
        assertEquals(600, result.phases[0].durationSeconds)
    }

    // -----------------------------------------------------------------------
    // RC token (RACE_PACE)
    // -----------------------------------------------------------------------

    @Test
    fun `parse - race pace phase using RC token`() {
        val result = WorkoutParser.parse("RC10")
        
        assertEquals(1, result.phases.size)
        assertEquals(PhaseType.RACE_PACE, result.phases[0].type)
        assertEquals(600, result.phases[0].durationSeconds)
    }

    // -----------------------------------------------------------------------
    // Combined phases
    // -----------------------------------------------------------------------

    @Test
    fun `parse - phases separated by dashes`() {
        val result = WorkoutParser.parse("R10-D1-T5")
        
        assertEquals(3, result.phases.size)
        
        assertEquals(PhaseType.EASY, result.phases[0].type)
        assertEquals(600, result.phases[0].durationSeconds)
        
        assertEquals(PhaseType.REST, result.phases[1].type)
        assertEquals(60, result.phases[1].durationSeconds)
        
        assertEquals(PhaseType.TROT, result.phases[2].type)
        assertEquals(300, result.phases[2].durationSeconds)
        
        assertEquals(960, result.totalDurationSeconds)
    }

    @Test
    fun `parse - all phase types in one routine`() {
        val result = WorkoutParser.parse("D-T-R-RA-RF-F-P-RC")
        
        assertEquals(8, result.phases.size)
        assertEquals(PhaseType.REST, result.phases[0].type)
        assertEquals(PhaseType.TROT, result.phases[1].type)
        assertEquals(PhaseType.EASY, result.phases[2].type)
        assertEquals(PhaseType.EASY_CHEERFUL, result.phases[3].type)
        assertEquals(PhaseType.EASY_STRONG, result.phases[4].type)
        assertEquals(PhaseType.FARTLEK, result.phases[5].type)
        assertEquals(PhaseType.PROGRESSIVES, result.phases[6].type)
        assertEquals(PhaseType.RACE_PACE, result.phases[7].type)
    }

    // -----------------------------------------------------------------------
    // Series (Nx(...))
    // -----------------------------------------------------------------------

    @Test
    fun `parse - simple series 4x(T3-D1)`() {
        val result = WorkoutParser.parse("4x(T3 - D1)")
        
        assertEquals(8, result.phases.size) // 4 * 2 = 8 phases
        assertEquals(960, result.totalDurationSeconds) // 4 * (180 + 60)
        
        // First series
        assertEquals(PhaseType.TROT, result.phases[0].type)
        assertEquals(180, result.phases[0].durationSeconds)
        assertEquals(1, result.phases[0].seriesNumber)
        
        assertEquals(PhaseType.REST, result.phases[1].type)
        assertEquals(1, result.phases[1].seriesNumber)
    }

    @Test
    fun `parse - series with distance-based phase`() {
        val result = WorkoutParser.parse("3x(R1k - D2)")
        
        assertEquals(6, result.phases.size) // 3 * 2 = 6 phases
        
        // First phase should be distance-based
        assertEquals(PhaseType.EASY, result.phases[0].type)
        assertEquals(0, result.phases[0].durationSeconds)
        assertEquals(1000.0, result.phases[0].distanceMeters!!, 0.01)
    }

    // -----------------------------------------------------------------------
    // Distance-based (k suffix)
    // -----------------------------------------------------------------------

    @Test
    fun `parse - distance-based with k suffix`() {
        val result = WorkoutParser.parse("R5k")
        
        assertEquals(1, result.phases.size)
        assertEquals(PhaseType.EASY, result.phases[0].type)
        assertEquals(0, result.phases[0].durationSeconds)
        assertEquals(5000.0, result.phases[0].distanceMeters!!, 0.01)
    }

    @Test
    fun `parse - distance-based with decimal comma`() {
        val result = WorkoutParser.parse("R2,5k")
        
        assertEquals(1, result.phases.size)
        assertEquals(2500.0, result.phases[0].distanceMeters!!, 0.01)
    }

    @Test
    fun `parse - distance-based with leading decimal`() {
        val result = WorkoutParser.parse("R.5k")
        
        assertEquals(1, result.phases.size)
        assertEquals(500.0, result.phases[0].distanceMeters!!, 0.01)
    }

    // -----------------------------------------------------------------------
    // Combined complex routines
    // -----------------------------------------------------------------------

    @Test
    fun `parse - complex routine R5-4x(T3-D1)-R5`() {
        val result = WorkoutParser.parse("R5 - 4x(T3 - D1) - R5")
        
        // R5 (300) + 4x(T3-D1) (960) + R5 (300) = 1560
        assertEquals(1560, result.totalDurationSeconds)
        
        // First phase: R5
        assertEquals(PhaseType.EASY, result.phases[0].type)
        assertEquals(300, result.phases[0].durationSeconds)
        
        // Series starts at index 1
        assertEquals(PhaseType.TROT, result.phases[1].type)
        assertEquals(1, result.phases[1].seriesNumber)
    }

    // -----------------------------------------------------------------------
    // Extract from free text
    // -----------------------------------------------------------------------

    @Test
    fun `parse - extracts routine from surrounding text`() {
        val result = WorkoutParser.parse("Hoy corremos! T10-D1-R5 - recordar hidratarse")
        
        assertEquals(3, result.phases.size)
        assertEquals(PhaseType.TROT, result.phases[0].type)
    }

    @Test
    fun `parse - ignores non-routine text`() {
        val result = WorkoutParser.parse("Entrenamiento: R10, descanso D5, trote T5")
        
        // Should find R10 and D5, but may not find T5 because of the comma
        assertTrue(result.phases.size >= 2)
    }
}