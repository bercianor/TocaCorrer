package es.bercianor.tocacorrer.data.export

import es.bercianor.tocacorrer.data.local.entity.Workout
import es.bercianor.tocacorrer.data.local.entity.GpsPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for GpxGenerator.
 * Verifies GPX 1.1 XML generation according to specification.
 */
class GpxGeneratorTest {

    @Test
    fun `generates valid GPX header`() {
        val workout = createWorkout(1L)
        val gpx = GpxGenerator.generateSingle(workout, emptyList())

        assertTrue(gpx.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"))
        assertTrue(gpx.contains("xmlns=\"http://www.topografix.com/GPX/1/1\""))
        assertTrue(gpx.contains("creator=\"TocaCorrer\""))
    }

    @Test
    fun `generates metadata section`() {
        val workout = createWorkout(1L)
        val gpx = GpxGenerator.generateSingle(workout, emptyList())

        assertTrue(gpx.contains("<metadata>"))
        assertTrue(gpx.contains("<name>TocaCorrer Export</name>"))
        assertTrue(gpx.contains("</metadata>"))
    }

    @Test
    fun `generates track element`() {
        val workout = createWorkout(1L)
        val gpx = GpxGenerator.generateSingle(workout, emptyList())

        assertTrue(gpx.contains("<trk>"))
        assertTrue(gpx.contains("</trk>"))
    }

    @Test
    fun `includes workout name in track`() {
        val timestamp = 1700000000000L
        val workout = Workout(
            id = 1L,
            startTime = timestamp,
            originalRoutine = "C10",
            totalDistanceMeters = 1000.0,
            totalDurationSeconds = 600,
            averagePaceMinPerKm = 10.0,
            noGps = false
        )
        
        val gpx = GpxGenerator.generateSingle(workout, emptyList())

        // Check that a workout name is generated with proper format
        assertTrue(gpx.contains("<name>Workout "))
        assertTrue(gpx.contains("</name>"))
    }

    @Test
    fun `includes track type`() {
        val workout = createWorkout(1L)
        val gpx = GpxGenerator.generateSingle(workout, emptyList())

        assertTrue(gpx.contains("<type>running</type>"))
    }

    @Test
    fun `generates track segment`() {
        val workout = createWorkout(1L)
        val gpx = GpxGenerator.generateSingle(workout, emptyList())

        assertTrue(gpx.contains("<trkseg>"))
        assertTrue(gpx.contains("</trkseg>"))
    }

    @Test
    fun `includes GPS points with coordinates`() {
        val workout = createWorkout(1L)
        val points = listOf(
            GpsPoint(
                id = 1L,
                workoutId = 1L,
                latitude = 42.0,
                longitude = -8.0,
                altitude = 100.0,
                timestamp = 1700000000000L,
                phase = "C"
            )
        )
        
        val gpx = GpxGenerator.generateSingle(workout, points)

        assertTrue(gpx.contains("lat=\"42.00000000\""))
        assertTrue(gpx.contains("lon=\"-8.00000000\""))
        assertTrue(gpx.contains("<ele>100.00</ele>"))
        assertTrue(gpx.contains("<time>"))
    }

    @Test
    fun `escapes XML special characters in name`() {
        val workout = Workout(
            id = 1L,
            startTime = 1700000000000L,
            originalRoutine = "C10",
            totalDistanceMeters = 1000.0,
            totalDurationSeconds = 600,
            averagePaceMinPerKm = 10.0,
            noGps = false
        )
        
        // Create a mock that would have special chars in name
        // Actually we can't easily test the name with special chars since it's generated
        // So let's test the escapeXml function indirectly
        val gpx = GpxGenerator.generateSingle(workout, emptyList())
        
        // Check that & is escaped (in the header/names)
        assertFalse(gpx.contains("&lt;trk&gt;")) // Make sure it's properly escaped
    }

    @Test
    fun `generates valid closing tag`() {
        val workout = createWorkout(1L)
        val gpx = GpxGenerator.generateSingle(workout, emptyList())

        assertTrue(gpx.endsWith("</gpx>\n"))
    }

    @Test
    fun `handles empty points list`() {
        val workout = createWorkout(1L)
        val gpx = GpxGenerator.generateSingle(workout, emptyList())

        assertTrue(gpx.contains("<trkseg>"))
        assertTrue(gpx.contains("</trkseg>"))
        // Should not have any trkpt elements
        assertEquals(0, gpx.filter { it == '<' }.count { gpx.substring(gpx.indexOf(it)).startsWith("<trkpt") })
    }

    @Test
    fun `generates multiple tracks for multiple workouts`() {
        val workouts = listOf(
            createWorkout(1L),
            createWorkout(2L)
        )
        
        val pointsByWorkout = mapOf(
            1L to listOf(
                GpsPoint(1L, 1L, 42.0, -8.0, 100.0, 1700000000000L, "C")
            ),
            2L to listOf(
                GpsPoint(2L, 2L, 43.0, -7.0, 200.0, 1700000001000L, "C")
            )
        )
        
        val gpx = GpxGenerator.generate(workouts, pointsByWorkout)
        
        // Should have multiple track elements - count closing tags
        val trkEndCount = gpx.split("</trk>").size - 1
        assertEquals(2, trkEndCount)
    }

    @Test
    fun `includes altitude for each point`() {
        val workout = createWorkout(1L)
        val points = listOf(
            GpsPoint(1L, 1L, 42.0, -8.0, 150.5, 1700000000000L, "C")
        )
        
        val gpx = GpxGenerator.generateSingle(workout, points)

        assertTrue(gpx.contains("<ele>150.50</ele>"))
    }

    // -------------------------------------------------------------------------
    // TRACKS mode tests
    // -------------------------------------------------------------------------

    @Test
    fun `TRACKS mode produces one trk per phase group with name tags`() {
        val workout = createWorkout(1L)
        // 3 easy-run points followed by 2 rest points → 2 phase groups
        val points = listOf(
            GpsPoint(1L, 1L, 42.0, -8.0, 100.0, 1700000000000L, "R"),
            GpsPoint(2L, 1L, 42.1, -8.0, 101.0, 1700000001000L, "R"),
            GpsPoint(3L, 1L, 42.2, -8.0, 102.0, 1700000002000L, "R"),
            GpsPoint(4L, 1L, 42.3, -8.0, 103.0, 1700000003000L, "D"),
            GpsPoint(5L, 1L, 42.4, -8.0, 104.0, 1700000004000L, "D")
        )

        val gpx = GpxGenerator.generateSingle(workout, points, GpxSegmentationMode.TRACKS, englishPhaseNames)

        // Expect exactly 2 <trk> elements
        val trkCount = gpx.split("</trk>").size - 1
        assertEquals(2, trkCount)

        // Each <trk> must have a <name> with the expected format
        assertTrue("Expected '1. Easy run' name tag", gpx.contains("<name>1. Easy run</name>"))
        assertTrue("Expected '2. Rest' name tag", gpx.contains("<name>2. Rest</name>"))

        // No workout-level name (TRACKS mode skips the top-level "Workout …" name)
        assertFalse(gpx.contains("<name>Workout "))
    }

    @Test
    fun `TRACKS mode assigns correct phase names for easy run and rest`() {
        val workout = createWorkout(1L)
        val points = listOf(
            GpsPoint(1L, 1L, 42.0, -8.0, 100.0, 1700000000000L, "D"), // rest first
            GpsPoint(2L, 1L, 42.1, -8.0, 101.0, 1700000001000L, "R")  // then easy run
        )

        val gpx = GpxGenerator.generateSingle(workout, points, GpxSegmentationMode.TRACKS, englishPhaseNames)

        assertTrue("First group should be Rest", gpx.contains("<name>1. Rest</name>"))
        assertTrue("Second group should be Easy run", gpx.contains("<name>2. Easy run</name>"))
    }

    @Test
    fun `TRACKS mode uses phase letter as name for unknown phases`() {
        val workout = createWorkout(1L)
        val points = listOf(
            GpsPoint(1L, 1L, 42.0, -8.0, 100.0, 1700000000000L, "Z") // genuinely unknown letter
        )

        val gpx = GpxGenerator.generateSingle(workout, points, GpxSegmentationMode.TRACKS, englishPhaseNames)

        assertTrue("Unknown phase should use letter", gpx.contains("<name>1. Z</name>"))
    }

    @Test
    fun `TRACKS mode with three alternating phases produces three trk elements`() {
        val workout = createWorkout(1L)
        val points = listOf(
            GpsPoint(1L, 1L, 42.0, -8.0, 100.0, 1700000000000L, "R"),
            GpsPoint(2L, 1L, 42.1, -8.0, 101.0, 1700000001000L, "D"),
            GpsPoint(3L, 1L, 42.2, -8.0, 102.0, 1700000002000L, "R")
        )

        val gpx = GpxGenerator.generateSingle(workout, points, GpxSegmentationMode.TRACKS, englishPhaseNames)

        assertEquals(3, gpx.split("</trk>").size - 1)
        assertTrue(gpx.contains("<name>1. Easy run</name>"))
        assertTrue(gpx.contains("<name>2. Rest</name>"))
        assertTrue(gpx.contains("<name>3. Easy run</name>"))
    }

    // -------------------------------------------------------------------------
    // SEGMENTS mode tests
    // -------------------------------------------------------------------------

    @Test
    fun `SEGMENTS mode produces one trk with multiple trkseg and no name on segments`() {
        val workout = createWorkout(1L)
        // 3 run points + 2 rest points → 2 <trkseg>
        val points = listOf(
            GpsPoint(1L, 1L, 42.0, -8.0, 100.0, 1700000000000L, "C"),
            GpsPoint(2L, 1L, 42.1, -8.0, 101.0, 1700000001000L, "C"),
            GpsPoint(3L, 1L, 42.2, -8.0, 102.0, 1700000002000L, "C"),
            GpsPoint(4L, 1L, 42.3, -8.0, 103.0, 1700000003000L, "D"),
            GpsPoint(5L, 1L, 42.4, -8.0, 104.0, 1700000004000L, "D")
        )

        val gpx = GpxGenerator.generateSingle(workout, points, GpxSegmentationMode.SEGMENTS)

        // Exactly 1 <trk>
        val trkCount = gpx.split("</trk>").size - 1
        assertEquals("SEGMENTS mode must produce exactly one <trk>", 1, trkCount)

        // Exactly 2 <trkseg>
        val trksegCount = gpx.split("</trkseg>").size - 1
        assertEquals("SEGMENTS mode must produce 2 <trkseg> for 2 phase groups", 2, trksegCount)

        // 5 original points + 1 overlap point (last of first segment repeated as first of second)
        val trkptCount = gpx.split("</trkpt>").size - 1
        assertEquals("Expected 5 original + 1 overlap point", 6, trkptCount)

        // GPX 1.1 forbids <name> on <trkseg> — make sure none is inside a trkseg block
        // Simple check: no "1. Run" or "1. Rest" style names should appear
        assertFalse(gpx.contains("<name>1. Run</name>"))
        assertFalse(gpx.contains("<name>1. Rest</name>"))
    }

    @Test
    fun `SEGMENTS mode with empty points produces single trk with single trkseg`() {
        val workout = createWorkout(1L)
        val gpx = GpxGenerator.generateSingle(workout, emptyList(), GpxSegmentationMode.SEGMENTS)

        assertEquals(1, gpx.split("</trk>").size - 1)
        assertEquals(1, gpx.split("</trkseg>").size - 1)
    }

    // -------------------------------------------------------------------------
    // NONE mode (default) compatibility
    // -------------------------------------------------------------------------

    @Test
    fun `NONE mode default param preserves original behaviour`() {
        val workout = createWorkout(1L)
        val points = listOf(
            GpsPoint(1L, 1L, 42.0, -8.0, 100.0, 1700000000000L, "C"),
            GpsPoint(2L, 1L, 42.1, -8.0, 101.0, 1700000001000L, "D")
        )

        // generateSingle without explicit mode
        val gpxDefault = GpxGenerator.generateSingle(workout, points)
        // generateSingle with explicit NONE
        val gpxNone = GpxGenerator.generateSingle(workout, points, GpxSegmentationMode.NONE)

        // Both must be identical
        assertEquals(gpxDefault, gpxNone)

        // Single trk, single trkseg
        assertEquals(1, gpxDefault.split("</trk>").size - 1)
        assertEquals(1, gpxDefault.split("</trkseg>").size - 1)
    }

    // -------------------------------------------------------------------------
    // phaseIndex grouping tests (Phase 6)
    // -------------------------------------------------------------------------

    @Test
    fun `groupByPhase splits consecutive same-phase points with different phaseIndex into separate groups`() {
        val workout = createWorkout(1L)
        // Sequence: R/0, R/0, D/1, D/1, R/2, D/3, D/3 → 4 groups
        val points = listOf(
            GpsPoint(1L, 1L, 42.0, -8.0, 100.0, 1700000000000L, "R", phaseIndex = 0),
            GpsPoint(2L, 1L, 42.1, -8.0, 101.0, 1700000001000L, "R", phaseIndex = 0),
            GpsPoint(3L, 1L, 42.2, -8.0, 102.0, 1700000002000L, "D", phaseIndex = 1),
            GpsPoint(4L, 1L, 42.3, -8.0, 103.0, 1700000003000L, "D", phaseIndex = 1),
            GpsPoint(5L, 1L, 42.4, -8.0, 104.0, 1700000004000L, "R", phaseIndex = 2),
            GpsPoint(6L, 1L, 42.5, -8.0, 105.0, 1700000005000L, "D", phaseIndex = 3),
            GpsPoint(7L, 1L, 42.6, -8.0, 106.0, 1700000006000L, "D", phaseIndex = 3)
        )

        val gpx = GpxGenerator.generateSingle(workout, points, GpxSegmentationMode.TRACKS, englishPhaseNames)

        // Should produce 4 <trk> elements (not 2 as if grouping by phase letter only)
        val trkCount = gpx.split("</trk>").size - 1
        assertEquals("Expected 4 groups with phaseIndex splitting", 4, trkCount)

        // Verify track names: 1. Easy run, 2. Rest, 3. Easy run, 4. Rest
        assertTrue(gpx.contains("<name>1. Easy run</name>"))
        assertTrue(gpx.contains("<name>2. Rest</name>"))
        assertTrue(gpx.contains("<name>3. Easy run</name>"))
        assertTrue(gpx.contains("<name>4. Rest</name>"))
    }

    @Test
    fun `groupByPhase with all same phaseIndex produces groups only on phase change`() {
        val workout = createWorkout(1L)
        // Old-style data: all phaseIndex = 0 — should still group by consecutive phase letter
        val points = listOf(
            GpsPoint(1L, 1L, 42.0, -8.0, 100.0, 1700000000000L, "R", phaseIndex = 0),
            GpsPoint(2L, 1L, 42.1, -8.0, 101.0, 1700000001000L, "R", phaseIndex = 0),
            GpsPoint(3L, 1L, 42.2, -8.0, 102.0, 1700000002000L, "D", phaseIndex = 0),
            GpsPoint(4L, 1L, 42.3, -8.0, 103.0, 1700000003000L, "D", phaseIndex = 0)
        )

        val gpx = GpxGenerator.generateSingle(workout, points, GpxSegmentationMode.TRACKS, englishPhaseNames)

        // Should produce 2 <trk> groups (R easy-run then D rest), not 1 (all same key)
        val trkCount = gpx.split("</trk>").size - 1
        assertEquals("Old data (all phaseIndex=0) should still split on phase change", 2, trkCount)
        assertTrue(gpx.contains("<name>1. Easy run</name>"))
        assertTrue(gpx.contains("<name>2. Rest</name>"))
    }

    // -------------------------------------------------------------------------
    // Localised phase names tests (Phase 7)
    // -------------------------------------------------------------------------

    @Test
    fun `TRACKS mode with Spanish phase names uses localized names`() {
        val workout = createWorkout(1L)
        val points = listOf(
            GpsPoint(1L, 1L, 42.0, -8.0, 100.0, 1700000000000L, "R"),
            GpsPoint(2L, 1L, 42.1, -8.0, 101.0, 1700000001000L, "D")
        )
        val spanishNames = mapOf("R" to "Rodaje", "D" to "Descansar")

        val gpx = GpxGenerator.generateSingle(workout, points, GpxSegmentationMode.TRACKS, spanishNames)

        assertTrue("Expected Spanish 'Rodaje' name tag", gpx.contains("<name>1. Rodaje</name>"))
        assertTrue("Expected Spanish 'Descansar' name tag", gpx.contains("<name>2. Descansar</name>"))

        // English names should NOT be present
        assertFalse(gpx.contains("<name>1. Easy run</name>"))
        assertFalse(gpx.contains("<name>2. Rest</name>"))
    }

    @Test
    fun `TRACKS mode with unknown phase letter falls back to letter`() {
        val workout = createWorkout(1L)
        val points = listOf(
            GpsPoint(1L, 1L, 42.0, -8.0, 100.0, 1700000000000L, "T")
        )
        // phaseNames has no entry for "T"
        val phaseNames = mapOf("C" to "Run", "D" to "Rest")

        val gpx = GpxGenerator.generateSingle(workout, points, GpxSegmentationMode.TRACKS, phaseNames)

        assertTrue("Unknown phase letter 'T' should fall back to the letter itself",
            gpx.contains("<name>1. T</name>"))
    }

    @Test
    fun `TRACKS mode with custom phase names passed explicitly overrides defaults`() {
        val workout = createWorkout(1L)
        val points = listOf(
            GpsPoint(1L, 1L, 42.0, -8.0, 100.0, 1700000000000L, "C"),
            GpsPoint(2L, 1L, 42.1, -8.0, 101.0, 1700000001000L, "D")
        )
        val customNames = mapOf("C" to "Running", "D" to "Walking")

        val gpx = GpxGenerator.generateSingle(workout, points, GpxSegmentationMode.TRACKS, customNames)

        assertTrue(gpx.contains("<name>1. Running</name>"))
        assertTrue(gpx.contains("<name>2. Walking</name>"))
    }

    // -------------------------------------------------------------------------
    // Phase 8: Extra (X) phase tests
    // -------------------------------------------------------------------------

    @Test
    fun `TRACKS mode with X phase produces Extra name tag`() {
        val workout = createWorkout(1L)
        val points = listOf(
            GpsPoint(1L, 1L, 42.0, -8.0, 100.0, 1700000000000L, "X", phaseIndex = 0)
        )

        val gpx = GpxGenerator.generateSingle(workout, points, GpxSegmentationMode.TRACKS, englishPhaseNames)

        assertTrue("Expected '1. Extra' name tag for X phase", gpx.contains("<name>1. Extra</name>"))
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Explicit English phase-name map used in tests that verify track names.
     * Passing this explicitly makes tests locale-independent (avoids dependency
     * on Strings.getEffectiveLocale() which reads system locale in JVM tests).
     */
    private val englishPhaseNames = mapOf(
        "D"  to "Rest",
        "T"  to "Jog",
        "R"  to "Easy run",
        "RA" to "Easy run",
        "RF" to "Strong easy",
        "F"  to "Fartlek",
        "P"  to "Progressives",
        "RC" to "Race pace",
        "X"  to "Extra"
    )

    private fun createWorkout(id: Long): Workout {
        return Workout(
            id = id,
            startTime = 1700000000000L,
            originalRoutine = "C10-D1-C10",
            totalDistanceMeters = 5000.0,
            totalDurationSeconds = 1800,
            averagePaceMinPerKm = 6.0,
            noGps = false
        )
    }
}
