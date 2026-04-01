package es.bercianor.tocacorrer.data.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Unit tests for GpxExporter utility methods.
 *
 * Note: Tests that require Android context (saveToCache, createShareIntent) are
 * instrumented tests and not included here. Only pure-logic helpers are tested.
 */
class GpxExporterTest {

    @Test
    fun `generateFileName uses provided startTime for filename`() {
        // 2026-03-20 10:30:05 UTC → local formatting depends on JVM default
        // Use a known fixed timestamp and check the format structure
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val startTime = sdf.parse("20260320_103005")!!.time

        val fileName = GpxExporter.generateFileName(startTime = startTime)

        // Should match workout_YYYYMMDD_HHmmss.gpx
        assertTrue(
            "Filename should start with 'workout_'",
            fileName.startsWith("workout_")
        )
        assertTrue(
            "Filename should end with '.gpx'",
            fileName.endsWith(".gpx")
        )
        // Verify date portion matches
        val expectedTimestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            .format(Date(startTime))
        assertEquals("workout_${expectedTimestamp}.gpx", fileName)
    }

    @Test
    fun `generateFileName with known startTime matches expected workout timestamp format`() {
        // Parse a deterministic timestamp
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val knownTime = sdf.parse("20260320_103005")!!.time

        val fileName = GpxExporter.generateFileName(startTime = knownTime)
        val expectedTimestamp = sdf.format(Date(knownTime))

        assertEquals("workout_${expectedTimestamp}.gpx", fileName)
    }

    @Test
    fun `generateFileName without startTime uses legacy format`() {
        val fileName = GpxExporter.generateFileName()

        // Legacy format: tocacorrer_YYYY-MM-DD.gpx
        assertTrue(
            "Legacy filename should start with 'tocacorrer_'",
            fileName.startsWith("tocacorrer_")
        )
        assertTrue(
            "Legacy filename should end with '.gpx'",
            fileName.endsWith(".gpx")
        )
        // Should NOT start with "workout_"
        assertTrue(
            "Legacy filename should not use new 'workout_' prefix",
            !fileName.startsWith("workout_")
        )
    }

}
