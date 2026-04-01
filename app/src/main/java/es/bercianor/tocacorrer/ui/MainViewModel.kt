package es.bercianor.tocacorrer.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import es.bercianor.tocacorrer.data.backup.BackupManager
import es.bercianor.tocacorrer.data.calendar.CalendarEvent
import es.bercianor.tocacorrer.data.calendar.CalendarManager
import es.bercianor.tocacorrer.data.export.GpxExporter
import es.bercianor.tocacorrer.data.export.GpxSegmentationMode
import es.bercianor.tocacorrer.data.local.AppDatabase
import es.bercianor.tocacorrer.data.local.PreferencesManager
import es.bercianor.tocacorrer.data.local.entity.Workout
import es.bercianor.tocacorrer.data.repository.WorkoutRepository
import es.bercianor.tocacorrer.domain.model.PhaseType
import es.bercianor.tocacorrer.domain.parser.WorkoutParser
import es.bercianor.tocacorrer.service.LocationTracker
import es.bercianor.tocacorrer.service.WorkoutService
import es.bercianor.tocacorrer.service.WorkoutStatus
import es.bercianor.tocacorrer.util.Strings
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Dispatchers
import java.util.Calendar

/**
 * Global state of the application.
 */
data class AppState(
    val activeWorkout: Boolean = false,
    val workoutStatus: WorkoutStatus = WorkoutStatus(),
    val recentWorkouts: List<Workout> = emptyList(),
    val hasPermissions: Boolean = false,
    val gpsEnabled: Boolean = false,
    val error: String? = null,
    val upcomingEvents: List<CalendarEvent> = emptyList(),
    val completedWorkoutsToday: Int = 0,
    val awaitingGps: Boolean = false,              // true while waiting for GPS lock
    val awaitingGpsElapsedSeconds: Int = 0,        // seconds waited (0..30 for progress display)
    val pendingRoutine: String? = null,            // routine string to start once GPS is ready
    val pendingNoGps: Boolean = false,             // noGps flag for the pending routine
    val darkMode: Int = 0,                         // 0=system, 1=light, 2=dark
    val primaryColorIndex: Int = 0,                // index into availableColors
    val language: Int = 0,                         // 0=system, 1=English, 2=Spanish
    val weeklyChartMetric: Int = 0                 // 0=distance, 1=time, 2=pace
)

/**
 * Main ViewModel of the application.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val GPS_WAIT_TIMEOUT_SECONDS = 30
        private const val GPS_ACCURACY_THRESHOLD_METERS = 20f
    }

    private val repository: WorkoutRepository
    private val preferencesManager: PreferencesManager
    private val calendarManager: CalendarManager
    private val backupManager: BackupManager
    private var workoutService: WorkoutService? = null
    private var serviceBound = false

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    // Job for GPS warm-up wait — kept so cancelGpsWait() can cancel it
    private var gpsWaitJob: Job? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as WorkoutService.LocalBinder
            workoutService = binder.getService()
            serviceBound = true
            observeService()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            workoutService = null
            serviceBound = false
        }
    }

    init {
        val db = AppDatabase.getDatabase(application)
        repository = WorkoutRepository(db, db.workoutDao(), db.gpsPointDao())
        preferencesManager = PreferencesManager(application)
        calendarManager = CalendarManager(application)
        backupManager = BackupManager(application, repository)
        _state.value = _state.value.copy(
            darkMode = preferencesManager.darkMode,
            primaryColorIndex = preferencesManager.primaryColorIndex,
            language = preferencesManager.language,
            weeklyChartMetric = preferencesManager.weeklyChartMetric
        )
        // Sync Strings language with persisted preference (no Context kept in Strings)
        Strings.setLanguageSetting(preferencesManager.language)
        loadRecentWorkouts()
        observeTodayCompletedWorkouts()
    }

    /**
     * Binds the service. Uses flag 0 so the bind does NOT create the service if not running.
     */
    fun bindService() {
        val appContext = getApplication<Application>()
        Intent(appContext, WorkoutService::class.java).also { intent ->
            appContext.bindService(intent, serviceConnection, 0)
        }
    }

    /**
     * Unbinds the service.
     */
    fun unbindService() {
        if (serviceBound) {
            val appContext = getApplication<Application>()
            appContext.unbindService(serviceConnection)
            serviceBound = false
        }
    }

    private fun observeService() {
        viewModelScope.launch {
            workoutService?.workoutStatus?.collect { serviceState ->
                _state.value = _state.value.copy(
                    activeWorkout = serviceState.isRunning || serviceState.isPaused,
                    workoutStatus = serviceState
                )
            }
        }
    }

    /**
     * Loads recent workouts.
     */
    private fun loadRecentWorkouts() {
        viewModelScope.launch {
            repository.getRecentWorkouts(10).collect { workouts ->
                _state.value = _state.value.copy(recentWorkouts = workouts)
            }
        }
    }

    /**
     * Observes today's completed workouts via a Flow to keep the UI reactive.
     */
    private fun observeTodayCompletedWorkouts() {
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

        viewModelScope.launch {
            repository.getWorkoutsBetweenDates(todayStart, todayEnd).collect { workouts ->
                _state.value = _state.value.copy(completedWorkoutsToday = workouts.size)
            }
        }
    }

    /**
     * Loads upcoming calendar events based on the configured number of days.
     * Should be called after calendar permission is granted.
     */
    fun loadCalendarEvents() {
        viewModelScope.launch {
            val days = preferencesManager.calendarDays
            val calendarId = preferencesManager.selectedCalendarId
            try {
                val events = withContext(Dispatchers.IO) {
                    calendarManager.getUpcomingEvents(days = days, calendarId = calendarId)
                }
                _state.value = _state.value.copy(upcomingEvents = events)
            } catch (e: SecurityException) {
                _state.value = _state.value.copy(
                    error = "Calendar permission was revoked. Please grant it in Settings."
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    error = "Failed to load calendar events: ${e.message}"
                )
            }
        }
    }

    /**
     * Entry point for starting a workout. Handles treadmill validation and GPS warm-up.
     *
     * Case A — treadmill mode (noGps=true):
     *   - If the routine has any distance-based phase: show an error and do NOT start.
     *   - Otherwise: start immediately.
     *
     * Case B — GPS mode (noGps=false):
     *   - Show GPS warm-up screen and wait for an accurate fix (accuracy < 20m) before starting.
     *   - Times out after 30 seconds and starts anyway.
     */
    fun requestStartWorkout(routine: String, noGps: Boolean) {
        // Free workout (empty routine) always uses GPS — skip treadmill validation and go straight to warm-up
        if (routine.isBlank()) {
            startGpsWait(routine)
            return
        }

        val parsed = try {
            WorkoutParser.parse(routine)
        } catch (e: IllegalArgumentException) {
            // Malformed calendar event description — report error instead of crashing
            _state.value = _state.value.copy(error = e.message ?: "Invalid routine format")
            return
        }
        val hasDistancePhases = parsed.phases.any { it.distanceMeters != null }

        if (noGps && hasDistancePhases) {
            _state.value = _state.value.copy(
                error = Strings.get("treadmill_no_distance_phases")
            )
            return
        }

        if (noGps) {
            startWorkout(routine, noGps = true)
        } else {
            startGpsWait(routine)
        }
    }

    /**
     * Starts the GPS warm-up phase. Shows the waiting indicator and collects location updates
     * until either accuracy < 20m is reached or 30 seconds elapse (timeout).
     */
    private fun startGpsWait(routine: String) {
        _state.value = _state.value.copy(
            awaitingGps = true,
            awaitingGpsElapsedSeconds = 0,
            pendingRoutine = routine,
            pendingNoGps = false
        )

        gpsWaitJob = viewModelScope.launch {
            val locationTracker = LocationTracker(getApplication())

            // Tick the elapsed-seconds counter every second in parallel
            val tickJob = launch {
                repeat(GPS_WAIT_TIMEOUT_SECONDS) { second ->
                    delay(1_000L)
                    _state.value = _state.value.copy(awaitingGpsElapsedSeconds = second + 1)
                }
            }

            try {
                // Wait for first accurate fix — timeout means no lock obtained, start in noGps mode
                withTimeoutOrNull(GPS_WAIT_TIMEOUT_SECONDS * 1_000L) {
                    locationTracker.getLocationUpdates(minDistanceM = 0f)
                        .filter { it.accuracy <= GPS_ACCURACY_THRESHOLD_METERS }
                        .first()
                }
                clearGpsWaitState()
                startWorkout(routine, noGps = true)
            } catch (e: IllegalStateException) {
                clearGpsWaitState()
                _state.value = _state.value.copy(error = "GPS is disabled. Please enable it in Settings.")
            } catch (e: SecurityException) {
                clearGpsWaitState()
                _state.value = _state.value.copy(error = "Location permission was revoked.")
            } finally {
                tickJob.cancel()
            }
        }
    }

    /**
     * Cancels the GPS warm-up. Does NOT start the workout — user goes back to event list.
     */
    fun cancelGpsWait() {
        gpsWaitJob?.cancel()
        gpsWaitJob = null
        clearGpsWaitState()
    }

    private fun clearGpsWaitState() {
        _state.value = _state.value.copy(
            awaitingGps = false,
            awaitingGpsElapsedSeconds = 0,
            pendingRoutine = null,
            pendingNoGps = false
        )
    }

    /**
     * Starts a workout.
     */
    fun startWorkout(routine: String, noGps: Boolean = false) {
        val appContext = getApplication<Application>()
        val intent = WorkoutService.createStartIntent(appContext, routine, noGps)
        appContext.startForegroundService(intent)
        
        if (!serviceBound) {
            // Use BIND_AUTO_CREATE only here — we want to connect to the just-started service
            Intent(appContext, WorkoutService::class.java).also { serviceIntent ->
                appContext.bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
            }
        }
    }

    /**
     * Pauses the workout.
     */
    fun pauseWorkout() {
        workoutService?.pauseWorkout()
    }

    /**
     * Resumes the workout.
     */
    fun resumeWorkout() {
        workoutService?.resumeWorkout()
    }

    /**
     * Manually advances to the next phase (used for treadmill distance phases).
     */
    fun nextPhase() {
        workoutService?.nextPhase()
    }

    /**
     * Stops the workout and saves it. Unbinds and stops the service.
     * NOTE: We do NOT call context.stopService() here — the service manages its own lifecycle
     * via stopSelf() after completing the save. Calling stopService() from outside would race
     * with the save coroutine and cancel serviceScope before the DB write completes.
     */
    fun stopWorkout() {
        workoutService?.stopWorkout()
        unbindService()
        _state.value = _state.value.copy(activeWorkout = false)
    }

    /**
     * Updates the permission state.
     */
    fun updatePermissions(hasPermissions: Boolean) {
        _state.value = _state.value.copy(hasPermissions = hasPermissions)
    }

    /**
     * Updates the GPS state.
     */
    fun updateGpsEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(gpsEnabled = enabled)
    }

    private val _exportIntent = MutableSharedFlow<Intent>(extraBufferCapacity = 1)
    val exportIntent: SharedFlow<Intent> = _exportIntent.asSharedFlow()

    /**
     * Builds a GPX share intent for a single workout and emits it via [exportIntent].
     */
    fun exportWorkout(workout: Workout) {
        val appContext = getApplication<Application>()
        viewModelScope.launch {
            val points = repository.getPointsByWorkoutSync(workout.id)
            val mode = preferencesManager.gpxSegmentationMode
            val phaseNames = phaseNamesForCurrentLanguage()
            val intent = withContext(Dispatchers.IO) {
                GpxExporter.createShareIntent(appContext, workout, points, mode, phaseNames, startTime = workout.startTime)
            }
            _exportIntent.emit(intent)
        }
    }

    /**
     * Builds a GPX share intent for the last 20 workouts and emits it via [exportIntent].
     */
    fun exportAllWorkouts() {
        val appContext = getApplication<Application>()
        viewModelScope.launch {
            val workouts = repository.getRecentWorkouts(20).first()
            if (workouts.isEmpty()) return@launch

            val workoutIds = workouts.map { it.id }
            val pointsByWorkout = repository.getPointsByWorkout(workoutIds)

            val mode = preferencesManager.gpxSegmentationMode
            val phaseNames = phaseNamesForCurrentLanguage()
            val intent = withContext(Dispatchers.IO) {
                GpxExporter.createShareIntent(appContext, workouts, pointsByWorkout, mode, phaseNames)
            }
            _exportIntent.emit(intent)
        }
    }

    private fun phaseNamesForCurrentLanguage(): Map<String, String> =
        PhaseType.entries.associate { phase ->
            phase.letter to Strings.getPhaseName(phase.name.lowercase())
        }

    /**
     * Clears the current error.
     */
    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    /**
     * Updates dark mode preference and propagates it to the UI immediately.
     */
    fun setDarkMode(mode: Int) {
        preferencesManager.darkMode = mode
        _state.value = _state.value.copy(darkMode = mode)
    }

    /**
     * Updates the primary color index and propagates it to the UI immediately.
     */
    fun setPrimaryColorIndex(index: Int) {
        preferencesManager.primaryColorIndex = index
        _state.value = _state.value.copy(primaryColorIndex = index)
    }

    /**
     * Updates language preference and propagates it to the UI immediately.
     */
    fun setLanguage(lang: Int) {
        preferencesManager.language = lang
        Strings.setLanguageSetting(lang)
        _state.value = _state.value.copy(language = lang)
    }

    /**
     * Updates the weekly chart metric preference and propagates it to the UI immediately.
     */
    fun setWeeklyChartMetric(metric: Int) {
        _state.value = _state.value.copy(weeklyChartMetric = metric)
    }

    /**
     * Exports the database to a JSON backup file at the given URI.
     */
    suspend fun exportBackup(uri: Uri): Result<Unit> = backupManager.export(uri)

    /**
     * Imports a JSON backup file from the given URI into the database.
     */
    suspend fun importBackup(uri: Uri): Result<Int> = backupManager.import(uri)

    /**
     * Returns the filename for a new backup file.
     */
    fun generateBackupFileName(): String = backupManager.generateFileName()

    /**
     * Exposes CalendarManager so SettingsViewModel can be constructed without re-instantiating it.
     */
    fun getCalendarManager(): CalendarManager = calendarManager

    /**
     * Exposes PreferencesManager so SettingsViewModel can be constructed without re-instantiating it.
     */
    fun getPreferencesManager(): PreferencesManager = preferencesManager
}
