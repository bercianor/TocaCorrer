package es.bercianor.tocacorrer.data.export

import es.bercianor.tocacorrer.data.local.entity.Workout
import es.bercianor.tocacorrer.data.local.entity.GpsPoint
import java.io.OutputStream
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * GPX 1.1 file generator.
 *
 * Produces valid GPX files according to the specification:
 * - https://www.topografix.com/GPX/1/1/
 *
 * A GPX file can contain multiple tracks (<trk>), one for each workout (NONE mode),
 * or one <trk> per phase group (TRACKS mode), or multiple <trkseg> in a single <trk>
 * (SEGMENTS mode).
 *
 * TRACKS mode: each <trk> includes a <name> formatted as "{1-based index}. {PhaseName}".
 *   Phase "C" → "Run", Phase "D" → "Rest", others → the letter itself.
 * SEGMENTS mode: single <trk> with multiple <trkseg>. GPX 1.1 does NOT support
 *   <name> on <trkseg>, so none is added.
 */
object GpxGenerator {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    /**
     * Writes a GPX file directly to [outputStream], avoiding building the entire
     * XML content in memory. Suitable for large datasets (20,000+ GPS points).
     *
     * @param workouts List of workouts to export
     * @param pointsByWorkout Map of workout ID -> list of GPS points
     * @param mode Segmentation mode (default NONE = single trk/trkseg per workout)
     * @param phaseNames Caller-supplied map of phase letter to localised display name.
     */
    fun generateToStream(
        outputStream: OutputStream,
        workouts: List<Workout>,
        pointsByWorkout: Map<Long, List<GpsPoint>>,
        mode: GpxSegmentationMode = GpxSegmentationMode.NONE,
        phaseNames: Map<String, String> = emptyMap()
    ) {
        val writer = BufferedWriter(OutputStreamWriter(outputStream, Charsets.UTF_8))
        writeGpx(writer, workouts, pointsByWorkout, mode, phaseNames)
        writer.flush()
    }

    /**
     * Writes a GPX file for a single workout directly to [outputStream].
     */
    fun generateSingleToStream(
        outputStream: OutputStream,
        workout: Workout,
        points: List<GpsPoint>,
        mode: GpxSegmentationMode = GpxSegmentationMode.NONE,
        phaseNames: Map<String, String> = emptyMap()
    ) {
        generateToStream(outputStream, listOf(workout), mapOf(workout.id to points), mode, phaseNames)
    }

    /**
     * Generates a GPX file with the selected workouts.
     *
     * @param workouts List of workouts to export
     * @param pointsByWorkout Map of workout ID -> list of GPS points
     * @param mode Segmentation mode (default NONE = single trk/trkseg per workout)
     * @param phaseNames Caller-supplied map of phase letter to localised display name.
     *   Defaults to English. Unknown keys fall back to the letter itself.
     * @return String with the XML content of the GPX file
     */
    fun generate(
        workouts: List<Workout>,
        pointsByWorkout: Map<Long, List<GpsPoint>>,
        mode: GpxSegmentationMode = GpxSegmentationMode.NONE,
        phaseNames: Map<String, String> = emptyMap()
    ): String {
        val sw = java.io.StringWriter()
        val bw = BufferedWriter(sw)
        writeGpx(bw, workouts, pointsByWorkout, mode, phaseNames)
        bw.flush()
        return sw.toString()
    }

    /**
     * Generates a GPX file for a single workout.
     *
     * @param workout The workout to export
     * @param points GPS points for this workout
     * @param mode Segmentation mode (default NONE)
     * @param phaseNames Caller-supplied map of phase letter to localised display name.
     *   Defaults to English. Unknown keys fall back to the letter itself.
     */
    fun generateSingle(
        workout: Workout,
        points: List<GpsPoint>,
        mode: GpxSegmentationMode = GpxSegmentationMode.NONE,
        phaseNames: Map<String, String> = emptyMap()
    ): String {
        return generate(listOf(workout), mapOf(workout.id to points), mode, phaseNames)
    }

    // -------------------------------------------------------------------------
    // Internal implementation — writes GPX to a BufferedWriter
    // -------------------------------------------------------------------------

    private fun writeGpx(
        writer: BufferedWriter,
        workouts: List<Workout>,
        pointsByWorkout: Map<Long, List<GpsPoint>>,
        mode: GpxSegmentationMode,
        phaseNames: Map<String, String>
    ) {
        // GPX Header
        writer.appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        writer.appendLine("<gpx version=\"1.1\" creator=\"TocaCorrer\"")
        writer.appendLine("  xmlns=\"http://www.topografix.com/GPX/1/1\"")
        writer.appendLine("  xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"")
        writer.appendLine("  xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd\">")

        // Global metadata
        writer.appendLine("  <metadata>")
        writer.appendLine("    <name>TocaCorrer Export</name>")
        writer.appendLine("    <time>${dateFormat.format(Date())}</time>")
        writer.appendLine("  </metadata>")

        // One or more tracks per workout depending on mode
        for (workout in workouts) {
            val points = pointsByWorkout[workout.id] ?: emptyList()
            writeTrack(writer, workout, points, mode, phaseNames)
        }

        // Footer
        writer.appendLine("</gpx>")
    }

    /**
     * Groups consecutive GPS points by (phase, phaseIndex) pair.
     *
     * Consecutive points with the same (phase, phaseIndex) key are accumulated into the same group.
     * A new group is created each time the key changes — this correctly handles repeated phase
     * letters such as C/0→D/1→C/2 (three separate groups, not two).
     *
     * Old rows with phaseIndex = 0 (pre-migration) degrade gracefully: consecutive same-phase
     * points share key (phase, 0) and stay in one group, matching the original export behaviour.
     *
     * @return List of (phase, points) pairs, maintaining input order
     */
    private fun groupByPhase(points: List<GpsPoint>): List<Pair<String, List<GpsPoint>>> =
        points.fold(mutableListOf<Pair<Pair<String, Int>, MutableList<GpsPoint>>>()) { acc, pt ->
            val key = pt.phase to pt.phaseIndex
            if (acc.isEmpty() || acc.last().first != key) {
                acc.also { it.add(key to mutableListOf(pt)) }
            } else {
                acc.also { acc.last().second.add(pt) }
            }
        }.map { (key, pts) -> key.first to pts }

    /**
     * Resolves a phase letter to a human-readable name using the caller-supplied map.
     * Falls back to the letter itself if the map has no entry for it.
     *
     *   "C" → phaseNames["C"] (e.g. "Run" or "Correr")
     *   "D" → phaseNames["D"] (e.g. "Rest" or "Descanso")
     *   other → the letter itself
     */
    private fun resolvePhaseName(phase: String, phaseNames: Map<String, String>): String =
        phaseNames[phase] ?: phase

    /**
     * Generates the track XML for a workout according to the requested mode.
     *
     * NONE:     single <trk> with one <trkseg> (original behaviour)
     * TRACKS:   one <trk> per consecutive same-phase segment, each with <name>
     * SEGMENTS: single <trk> with one <trkseg> per consecutive same-phase segment
     */
    private fun writeTrack(
        writer: BufferedWriter,
        workout: Workout,
        points: List<GpsPoint>,
        mode: GpxSegmentationMode,
        phaseNames: Map<String, String>
    ) = when (mode) {
        GpxSegmentationMode.NONE -> writeTrackNone(writer, workout, points)
        GpxSegmentationMode.TRACKS -> writeTracksByPhase(writer, workout, points, phaseNames)
        GpxSegmentationMode.SEGMENTS -> writeSegmentsByPhase(writer, workout, points)
    }

    /** NONE mode — original single-track, single-segment behaviour. */
    private fun writeTrackNone(writer: BufferedWriter, workout: Workout, points: List<GpsPoint>) {
        writer.appendLine("  <trk>")
        val name = "Workout ${formatDate(workout.startTime)}"
        writer.appendLine("    <name>${escapeXml(name)}</name>")
        writer.appendLine("    <type>running</type>")
        writer.appendLine("    <trkseg>")
        for (point in points) {
            writeTrkpt(writer, point)
        }
        writer.appendLine("    </trkseg>")
        writer.appendLine("  </trk>")
    }

    /**
     * TRACKS mode — one <trk> per consecutive same-phase group.
     * Each <trk> has a <name> formatted as "{1-based index}. {PhaseName}".
     *
     * The last point of each group is repeated as the first point of the next group so that
     * GPX viewers do not render a gap between consecutive phases.
     */
    private fun writeTracksByPhase(
        writer: BufferedWriter,
        workout: Workout,
        points: List<GpsPoint>,
        phaseNames: Map<String, String>
    ) {
        if (points.isEmpty()) {
            // Fallback: emit a single empty track (consistent with NONE behaviour)
            writeTrackNone(writer, workout, points)
            return
        }

        val groups = groupByPhase(points)
        groups.forEachIndexed { index, (phase, groupPoints) ->
            val phaseName = resolvePhaseName(phase, phaseNames)
            val trackName = "${index + 1}. $phaseName"
            // Prepend last point of previous group to bridge the gap between tracks
            val prevLastPoint = if (index > 0) groups[index - 1].second.lastOrNull() else null
            writer.appendLine("  <trk>")
            writer.appendLine("    <name>${escapeXml(trackName)}</name>")
            writer.appendLine("    <type>running</type>")
            writer.appendLine("    <trkseg>")
            if (prevLastPoint != null) writeTrkpt(writer, prevLastPoint)
            for (point in groupPoints) {
                writeTrkpt(writer, point)
            }
            writer.appendLine("    </trkseg>")
            writer.appendLine("  </trk>")
        }
    }

    /**
     * SEGMENTS mode — single <trk> with one <trkseg> per consecutive same-phase group.
     * GPX 1.1 does NOT support <name> on <trkseg>, so none is added.
     *
     * The last point of each segment is repeated as the first point of the next segment so that
     * GPX viewers do not render a gap between consecutive phases.
     */
    private fun writeSegmentsByPhase(writer: BufferedWriter, workout: Workout, points: List<GpsPoint>) {
        writer.appendLine("  <trk>")
        val name = "Workout ${formatDate(workout.startTime)}"
        writer.appendLine("    <name>${escapeXml(name)}</name>")
        writer.appendLine("    <type>running</type>")

        if (points.isEmpty()) {
            writer.appendLine("    <trkseg>")
            writer.appendLine("    </trkseg>")
        } else {
            val groups = groupByPhase(points)
            groups.forEachIndexed { index, (_, groupPoints) ->
                // Prepend last point of previous segment to bridge the gap between segments
                val prevLastPoint = if (index > 0) groups[index - 1].second.lastOrNull() else null
                writer.appendLine("    <trkseg>")
                if (prevLastPoint != null) writeTrkpt(writer, prevLastPoint)
                for (point in groupPoints) {
                    writeTrkpt(writer, point)
                }
                writer.appendLine("    </trkseg>")
            }
        }

        writer.appendLine("  </trk>")
    }

    /** Renders a single <trkpt> element directly to [writer]. */
    private fun writeTrkpt(writer: BufferedWriter, point: GpsPoint) {
        val lat = String.format(Locale.US, "%.8f", point.latitude)
        val lon = String.format(Locale.US, "%.8f", point.longitude)
        val ele = String.format(Locale.US, "%.2f", point.altitude)
        writer.appendLine("      <trkpt lat=\"$lat\" lon=\"$lon\">")
        writer.appendLine("        <ele>$ele</ele>")
        writer.appendLine("        <time>${dateFormat.format(Date(point.timestamp))}</time>")
        writer.appendLine("      </trkpt>")
    }

    /**
     * Formats a timestamp to a readable date.
     */
    private fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    /**
     * Escapes special characters for XML.
     */
    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
