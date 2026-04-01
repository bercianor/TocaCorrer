package es.bercianor.tocacorrer.data.calendar

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.provider.CalendarContract
import java.util.Calendar

/**
 * Manager to access the system calendar.
 * 
 * Reads workout events synced with Nextcloud (or other calendars).
 */
class CalendarManager(private val context: Context) {

    private val contentResolver: ContentResolver = context.contentResolver

    /**
     * Gets workout events for the next N days starting from today.
     *
     * @param days Number of days to look ahead (1 = only today, 5 = today + next 4 days)
     * @param calendarId Optional calendar filter
     * @return List of workout events, each with isToday correctly set
     */
    fun getUpcomingEvents(days: Int, calendarId: Long = -1): List<CalendarEvent> {
        val calendar = Calendar.getInstance()

        // Start of today
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val dayStart = calendar.timeInMillis

        // End of day (today + days - 1)
        calendar.add(Calendar.DAY_OF_YEAR, days)
        calendar.add(Calendar.MILLISECOND, -1)
        val dayEnd = calendar.timeInMillis

        return searchEvents(dayStart, dayEnd, calendarId)
    }

    /**
     * Searches for events in the calendar between two dates.
     * Uses CalendarContract.Instances to correctly expand recurring events.
     */
    private fun searchEvents(start: Long, end: Long, calendarId: Long = -1): List<CalendarEvent> {
        val events = mutableListOf<CalendarEvent>()

        // Compute today's day boundaries for isToday check
        val todayCal = Calendar.getInstance()
        todayCal.set(Calendar.HOUR_OF_DAY, 0)
        todayCal.set(Calendar.MINUTE, 0)
        todayCal.set(Calendar.SECOND, 0)
        todayCal.set(Calendar.MILLISECOND, 0)
        val todayStart = todayCal.timeInMillis
        todayCal.set(Calendar.HOUR_OF_DAY, 23)
        todayCal.set(Calendar.MINUTE, 59)
        todayCal.set(Calendar.SECOND, 59)
        todayCal.set(Calendar.MILLISECOND, 999)
        val todayEnd = todayCal.timeInMillis

        // Build URI with time range — Instances API expands recurring events automatically
        val instancesUri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(start.toString())
            .appendPath(end.toString())
            .build()

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.EVENT_LOCATION
        )

        // Calendar filter goes in selection (range already in URI)
        val selection: String?
        val selectionArgs: Array<String>?
        if (calendarId > 0) {
            selection = "(${CalendarContract.Instances.CALENDAR_ID} = ?)"
            selectionArgs = arrayOf(calendarId.toString())
        } else {
            selection = null
            selectionArgs = null
        }

        val cursor: Cursor? = contentResolver.query(
            instancesUri,
            projection,
            selection,
            selectionArgs,
            "${CalendarContract.Instances.BEGIN} ASC"
        )

        cursor?.use {
            val idIndex = it.getColumnIndex(CalendarContract.Instances.EVENT_ID)
            val titleIndex = it.getColumnIndex(CalendarContract.Instances.TITLE)
            val descIndex = it.getColumnIndex(CalendarContract.Instances.DESCRIPTION)
            val startIndex = it.getColumnIndex(CalendarContract.Instances.BEGIN)
            val endIndex = it.getColumnIndex(CalendarContract.Instances.END)
            val calendarIdIndex = it.getColumnIndex(CalendarContract.Instances.CALENDAR_ID)
            val locationIndex = it.getColumnIndex(CalendarContract.Instances.EVENT_LOCATION)

            while (it.moveToNext()) {
                val id = it.getLong(idIndex)
                val title = it.getString(titleIndex) ?: ""
                val description = it.getString(descIndex) ?: ""
                val eventStart = it.getLong(startIndex)
                val eventEnd = if (endIndex >= 0) it.getLong(endIndex) else 0L
                val calId = it.getLong(calendarIdIndex)
                val location = if (locationIndex >= 0) it.getString(locationIndex) ?: "" else ""

                // Only include events that appear to be workouts
                // (have description with routine format or related title)
                if (isWorkoutEvent(title) || hasRoutineInDescription(description)) {
                    val eventIsToday = eventStart in todayStart..todayEnd
                    events.add(
                        CalendarEvent(
                            id = id,
                            title = title.ifEmpty { "Workout" },
                            description = description,
                            start = eventStart,
                            end = eventEnd,
                            calendarId = calId,
                            location = location,
                            noGps = isNoGpsWorkout(title),
                            isToday = eventIsToday
                        )
                    )
                }
            }
        }

        return events
    }

    /**
     * Determines if an event appears to be a running workout.
     * 
     * Considered a workout if:
     * - Title contains keywords like "running", "workout", "jogging", etc. (in multiple languages)
     * - Description contains routine format (C10, D1, x(), etc.)
     */
    private fun isWorkoutEvent(title: String): Boolean {
        val titleLower = title.lowercase()

        // Keywords in title (multiple languages supported)
        val keywords = listOf(
            "running", "workout", "jogging", "trot", "footing", 
            "cardio", "sport", "gym", "treadmill", "running machine",
            // Spanish keywords
            "correr", "entreno", "entrenamiento", "trote", "cinta"
        )

        // If title has any keyword, it's a workout
        if (keywords.any { titleLower.contains(it) }) {
            return true
        }

        return false
    }

    /**
     * Determines if an event has routine format in description.
     */
    fun hasRoutineInDescription(description: String): Boolean {
        // Matches workout DSL tokens only when they appear as standalone tokens:
        // a token letter (C, D, T, S, R, RA, RF, RC, F, P) must NOT be preceded by
        // another letter, avoiding false positives like "December 1" matching "D1".
        val descLower = description.lowercase()
        return WORKOUT_TOKEN_REGEX.containsMatchIn(descLower) ||
               descLower.contains("x(") ||                  // series
               descLower.contains("x (")                    // series with space
    }

    companion object {
        // Matches standalone DSL tokens: letter(s) followed by optional digit(s) or k-suffix,
        // but only when NOT preceded by another letter (prevents mid-word matches).
        private val WORKOUT_TOKEN_REGEX = Regex("""(?<![a-z])([cdtsrfp][a-z]{0,1}\d*k?)(?!\w)""")
    }

    /**
     * Determines if a workout is no-GPS (treadmill).
     */
    private fun isNoGpsWorkout(title: String): Boolean {
        val titleLower = title.lowercase()
        val noGpsKeywords = listOf("cinta", "treadmill", "running machine")
        return noGpsKeywords.any { titleLower.contains(it) }
    }

    /**
     * Gets the list of available calendars.
     */
    fun getCalendars(): List<CalendarInfo> {
        val calendars = mutableListOf<CalendarInfo>()

        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.IS_PRIMARY
        )

        val cursor: Cursor? = contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            null,
            null,
            null
        )

        cursor?.use {
            val idIndex = it.getColumnIndex(CalendarContract.Calendars._ID)
            val nameIndex = it.getColumnIndex(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
            val accountIndex = it.getColumnIndex(CalendarContract.Calendars.ACCOUNT_NAME)
            val primaryIndex = it.getColumnIndex(CalendarContract.Calendars.IS_PRIMARY)

            while (it.moveToNext()) {
                calendars.add(
                    CalendarInfo(
                        it.getLong(idIndex),
                        it.getString(nameIndex) ?: "",
                        it.getString(accountIndex) ?: "",
                        it.getInt(primaryIndex) == 1
                    )
                )
            }
        }

        return calendars
    }
}

/**
 * Represents a calendar event.
 */
data class CalendarEvent(
    val id: Long,
    val title: String,
    val description: String,
    val start: Long,
    val end: Long,
    val calendarId: Long,
    val location: String,
    val noGps: Boolean = false,
    val isToday: Boolean = false
) {
    /**
     * Extracts the routine from the event (description in TocaCorrer format).
     */
    val routine: String
        get() = description.trim()
}

/**
 * Represents a calendar.
 */
data class CalendarInfo(
    val id: Long,
    val name: String,
    val account: String,
    val isPrimary: Boolean = false
)
