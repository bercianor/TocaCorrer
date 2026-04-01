package es.bercianor.tocacorrer.data.local

import android.content.Context
import android.content.SharedPreferences
import es.bercianor.tocacorrer.data.export.GpxSegmentationMode

/**
 * Application preferences manager.
 */
class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("tocacorrer_prefs", Context.MODE_PRIVATE)
    
    var selectedCalendarId: Long
        get() = prefs.getLong("calendar_id", -1L)
        set(value) = prefs.edit().putLong("calendar_id", value).apply()
    
    var selectedCalendarName: String
        get() = prefs.getString("calendar_name", "") ?: ""
        set(value) = prefs.edit().putString("calendar_name", value).apply()
    
    var darkMode: Int // 0 = system, 1 = light, 2 = dark
        get() = prefs.getInt("dark_mode", 0)
        set(value) = prefs.edit().putInt("dark_mode", value).apply()
    
    var language: Int // 0 = system, 1 = English, 2 = Spanish
        get() = prefs.getInt("language", 0)
        set(value) = prefs.edit().putInt("language", value).apply()
    
    var autoPause: Boolean
        get() = prefs.getBoolean("auto_pause", true)
        set(value) = prefs.edit().putBoolean("auto_pause", value).apply()
    
    var autoPauseTime: Int // seconds before pausing
        get() = prefs.getInt("auto_pause_time", 10)
        set(value) = prefs.edit().putInt("auto_pause_time", value).apply()

    var calendarDays: Int // number of days to show upcoming events
        get() = prefs.getInt("calendar_days", 5)
        set(value) = prefs.edit().putInt("calendar_days", value).apply()

    var primaryColorIndex: Int // 0 = default purple, 1..N = palette colors
        get() = prefs.getInt("primary_color_index", 0)
        set(value) = prefs.edit().putInt("primary_color_index", value).apply()

    companion object {
        private const val KEY_GPX_SEGMENTATION_MODE = "gpx_segmentation_mode"
    }

    var gpxSegmentationMode: GpxSegmentationMode
        get() {
            // Wrap in try-catch: if the legacy value was stored as Int, getString() throws
            // ClassCastException immediately instead of returning null (Android SharedPreferences
            // behaviour for type mismatches), making the getInt fallback unreachable otherwise.
            val name = try {
                prefs.getString(KEY_GPX_SEGMENTATION_MODE, null)
            } catch (e: ClassCastException) {
                // Legacy value was stored as Int — fall through to migration below
                null
            }
            return if (name != null) {
                GpxSegmentationMode.entries.firstOrNull { it.name == name } ?: GpxSegmentationMode.entries.first()
            } else {
                // Migrate old int value if present, then persist the migrated String value
                // so future reads don't repeat the migration on every access.
                val oldOrdinal = prefs.getInt(KEY_GPX_SEGMENTATION_MODE, -1)
                val migrated = if (oldOrdinal >= 0) GpxSegmentationMode.entries.getOrElse(oldOrdinal) { GpxSegmentationMode.entries.first() }
                else GpxSegmentationMode.entries.first()
                prefs.edit().putString(KEY_GPX_SEGMENTATION_MODE, migrated.name).apply()
                migrated
            }
        }
        set(value) = prefs.edit().putString(KEY_GPX_SEGMENTATION_MODE, value.name).apply()

    var weeklyChartMetric: Int // 0 = distance, 1 = time, 2 = pace
        get() = prefs.getInt("weekly_chart_metric", 0)
        set(value) = prefs.edit().putInt("weekly_chart_metric", value).apply()
}
