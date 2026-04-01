package es.bercianor.tocacorrer.data.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import es.bercianor.tocacorrer.data.local.entity.GpsPoint
import es.bercianor.tocacorrer.data.local.entity.Workout
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Utilities to export and share GPX files.
 */
object GpxExporter {

    private const val GPX_DIR = "gpx"
    private const val FILE_PROVIDER_AUTHORITY = "es.bercianor.tocacorrer.fileprovider"

    /**
     * Saves a GPX file to the app's cache directory by writing directly to an
     * OutputStream — avoids holding the entire GPX content as a String in memory.
     *
     * @param context Application context
     * @param workouts List of workouts to export
     * @param pointsByWorkout Map of workout ID -> GPS points
     * @param mode Segmentation mode
     * @param phaseNames Localised phase name map
     * @param baseName Base name for the file (without extension)
     * @param startTime Optional workout start time in ms for naming
     * @return URI of the saved file
     */
    fun saveToCache(
        context: Context,
        workouts: List<Workout>,
        pointsByWorkout: Map<Long, List<GpsPoint>>,
        mode: GpxSegmentationMode = GpxSegmentationMode.NONE,
        phaseNames: Map<String, String> = emptyMap(),
        baseName: String = "workout",
        startTime: Long? = null
    ): Uri {
        val gpxDir = File(context.cacheDir, GPX_DIR)
        if (!gpxDir.exists()) {
            gpxDir.mkdirs()
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            .format(if (startTime != null) Date(startTime) else Date())
        val fileName = "${baseName}_$timestamp.gpx"
        val file = File(gpxDir, fileName)

        // Stream directly to file — no in-memory GPX String
        file.outputStream().buffered().use { out ->
            GpxGenerator.generateToStream(out, workouts, pointsByWorkout, mode, phaseNames)
        }

        return FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, file)
    }

    /**
     * Convenience overload for a single workout.
     */
    fun saveToCache(
        context: Context,
        workout: Workout,
        points: List<GpsPoint>,
        mode: GpxSegmentationMode = GpxSegmentationMode.NONE,
        phaseNames: Map<String, String> = emptyMap(),
        baseName: String = "workout",
        startTime: Long? = null
    ): Uri = saveToCache(
        context,
        listOf(workout),
        mapOf(workout.id to points),
        mode,
        phaseNames,
        baseName,
        startTime
    )

    /**
     * Creates an intent to share a GPX file for a single workout.
     *
     * @param context Application context
     * @param workout The workout to share
     * @param points GPS points for the workout
     * @param mode Segmentation mode
     * @param phaseNames Localised phase name map
     * @param startTime Optional workout start time used for the filename
     * @return Configured intent for sharing
     */
    fun createShareIntent(
        context: Context,
        workout: Workout,
        points: List<GpsPoint>,
        mode: GpxSegmentationMode = GpxSegmentationMode.NONE,
        phaseNames: Map<String, String> = emptyMap(),
        startTime: Long? = null
    ): Intent {
        val uri = saveToCache(context, workout, points, mode, phaseNames, startTime = startTime)
        return buildShareIntent(uri)
    }

    /**
     * Creates an intent to share a GPX file for multiple workouts.
     *
     * @param context Application context
     * @param workouts List of workouts to export
     * @param pointsByWorkout Map of workout ID -> GPS points
     * @param mode Segmentation mode
     * @param phaseNames Localised phase name map
     * @return Configured intent for sharing
     */
    fun createShareIntent(
        context: Context,
        workouts: List<Workout>,
        pointsByWorkout: Map<Long, List<GpsPoint>>,
        mode: GpxSegmentationMode = GpxSegmentationMode.NONE,
        phaseNames: Map<String, String> = emptyMap()
    ): Intent {
        val uri = saveToCache(context, workouts, pointsByWorkout, mode, phaseNames)
        return buildShareIntent(uri)
    }

    private fun buildShareIntent(uri: Uri): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "application/gpx+xml"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "TocaCorrer Export")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    /**
     * Generates a suggested filename based on the date.
     *
     * @param startTime Optional workout start time used for the date portion.
     *                  When provided, the filename reflects the actual workout date
     *                  (format: workout_YYYYMMDD_HHmmss.gpx).
     *                  When null, falls back to the current date (legacy behaviour).
     */
    fun generateFileName(startTime: Long? = null): String {
        return if (startTime != null) {
            // New behaviour: use workout start time with full timestamp precision
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(Date(startTime))
            "workout_$timestamp.gpx"
        } else {
            // Legacy behaviour: date-only format
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            "tocacorrer_$date.gpx"
        }
    }
}
