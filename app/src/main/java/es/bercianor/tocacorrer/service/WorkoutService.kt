package es.bercianor.tocacorrer.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import es.bercianor.tocacorrer.data.local.AppDatabase
import es.bercianor.tocacorrer.data.local.entity.Workout
import es.bercianor.tocacorrer.data.local.entity.GpsPoint
import es.bercianor.tocacorrer.data.repository.WorkoutRepository
import es.bercianor.tocacorrer.data.local.PreferencesManager
import es.bercianor.tocacorrer.util.Strings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean


/**
 * Foreground Service that manages the entire workout.
 * 
 * Handles:
 * - Phase timer
 * - GPS tracking
 * - Voice announcements (TTS)
 * - Notifications
 * - Room persistence
 */
class WorkoutService : Service() {

    companion object {
        const val CHANNEL_ID = "workout_channel"
        const val NOTIFICATION_ID = 1
        const val STOPPED_SPEED_THRESHOLD: Float = 0.5f // m/s (approx 1.8 km/h)

        const val ACTION_START = "es.bercianor.tocacorrer.ACTION_START"
        const val ACTION_PAUSE = "es.bercianor.tocacorrer.ACTION_PAUSE"
        const val ACTION_STOP = "es.bercianor.tocacorrer.ACTION_STOP"
        const val ACTION_RESUME = "es.bercianor.tocacorrer.ACTION_RESUME"

        const val EXTRA_ROUTINE = "routine"
        const val EXTRA_NO_GPS = "no_gps"

        private const val GPS_UPDATE_INTERVAL_MS = 1000L
        private const val GPS_MIN_DISTANCE_M = 1f
        private const val GPS_ACCURACY_THRESHOLD_METERS = 10f

        fun createStartIntent(context: Context, routine: String, noGps: Boolean = false): Intent {
            return Intent(context, WorkoutService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_ROUTINE, routine)
                putExtra(EXTRA_NO_GPS, noGps)
            }
        }
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Components
    private val locationTracker: LocationTracker by lazy { LocationTracker(this) }
    private val ttsManager: TtsManager by lazy { TtsManager(this) }
    private val phaseTimer: PhaseTimer by lazy { PhaseTimer(serviceScope) }
    private val notificationManager: NotificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }
    private lateinit var repository: WorkoutRepository

    // State
    private val _workoutStatus = MutableStateFlow(WorkoutStatus())
    val workoutStatus: StateFlow<WorkoutStatus> = _workoutStatus.asStateFlow()

    // Data
    @Volatile private var workoutId: Long = 0
    @Volatile private var startTime: Long = 0
    private var originalRoutine: String = ""
    private var noGps: Boolean = false
    @Volatile private var lastLocation: Location? = null
    @Volatile private var totalDistanceMeters: Double = 0.0
    @Volatile private var totalDurationSeconds: Long = 0L
    
    // Auto-pause
    @Volatile private var lastSpeedMs: Float = 0f
    @Volatile private var stoppedTimeSeconds: Float = 0f
    private var autoPauseEnabled: Boolean = true
    private var minStoppedTime: Int = 10 // seconds before pausing (read from preferences)

    // Duration timer pause tracking — avoids inflating elapsed time while paused
    @Volatile private var timerStartMs: Long = 0L
    @Volatile private var pausedAtMs: Long = 0L
    @Volatile private var totalPausedMs: Long = 0L

    // Distance-phase tracking
    @Volatile private var distanceAtPhaseStart: Double = 0.0
    @Volatile private var distanceInCurrentPhase: Double = 0.0
    @Volatile private var durationAtPhaseStart: Long = 0L

    // Previous phase snapshot (for TTS announcement at next phase start)
    // FIX 7: @Volatile — written in observePhaseTimer coroutine, read in TTS/announce
    @Volatile private var prevPhaseDistanceMeters: Float = 0f
    @Volatile private var prevPhaseDurationSeconds: Long = 0L

    // Guard against concurrent startWorkout() calls (double-tap or rapid intents)
    // FIX 5: compareAndSet(false, true) ensures only one invocation proceeds
    private val isStarting = AtomicBoolean(false)

    // Notification throttling — only update when visible content changes
    private var lastNotifiedDistanceM: Float = -1f
    private var lastNotifiedDurationSec: Long = -1L
    private var lastNotifiedPhaseLetter: String = ""
    private var lastNotifiedPaused: Boolean = false

    // Jobs
    private var locationJob: Job? = null
    private var timerJob: Job? = null

    inner class LocalBinder : Binder() {
        fun getService(): WorkoutService = this@WorkoutService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()
        initializeTTS()
        observePhaseTimer()

        val db = AppDatabase.getDatabase(this)
        repository = WorkoutRepository(db, db.workoutDao(), db.gpsPointDao())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // On Android 8+, the service may be restarted after being killed with a null intent.
        // Call startForeground immediately to satisfy Android's foreground service requirement,
        // then stop gracefully — there is no pending workout to resume.
        if (intent == null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(NOTIFICATION_ID, createNotification())
            }
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent.action) {
            ACTION_START -> {
                val routine = intent.getStringExtra(EXTRA_ROUTINE) ?: ""
                val noGpsValue = intent.getBooleanExtra(EXTRA_NO_GPS, false)
                startWorkout(routine, noGpsValue)
            }
            ACTION_PAUSE -> pauseWorkout()
            ACTION_RESUME -> resumeWorkout()
            ACTION_STOP -> stopWorkout()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        ttsManager.release()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                Strings.get("workout_channel"),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = Strings.get("workout_notification")
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun initializeTTS() {
        ttsManager.initialize()
    }

    private fun observePhaseTimer() {
        serviceScope.launch {
            var lastPhaseIndex = -1
            phaseTimer.state.collect { timerState ->
                val wasFreePhase = _workoutStatus.value.isFreePhase

                val isDistancePhase = timerState.currentPhase?.distanceMeters != null

                _workoutStatus.update { it.copy(
                    currentPhase = timerState.currentPhase,
                    remainingTime = timerState.remainingSeconds,
                    phaseIndex = timerState.phaseIndex,
                    totalPhases = timerState.totalPhases,
                    isPaused = timerState.isPaused,
                    isCompleted = timerState.isCompleted,
                    // Only update isFreePhase from timerState if we are NOT already in a free workout.
                    // A free workout sets isFreePhase=true in startWorkout; the timer starts with
                    // isFreePhase=false by default and would incorrectly overwrite it otherwise.
                    isFreePhase = if (it.isFreePhase) true else timerState.isFreePhase,
                    isDistancePhase = isDistancePhase,
                    remainingDistanceMeters = timerState.remainingDistanceMeters
                ) }

                // Detect phase change: reset distance tracking and announce new phase
                if (timerState.phaseIndex != lastPhaseIndex && timerState.currentPhase != null) {
                    // Snapshot previous phase stats before resetting
                    val hasPrevPhase = lastPhaseIndex >= 0
                    prevPhaseDistanceMeters = distanceInCurrentPhase.toFloat()
                    prevPhaseDurationSeconds = totalDurationSeconds - durationAtPhaseStart

                    // FIX 2: Update distanceAtPhaseStart BEFORE resetting distanceInCurrentPhase.
                    // Any concurrent GPS calculation in processNewLocation uses the new baseline,
                    // preventing stale distance from bleeding across the phase boundary.
                    // Remaining window: a GPS update between these two lines could still briefly
                    // see an inconsistent state, but it will self-correct on the next GPS update.
                    lastPhaseIndex = timerState.phaseIndex
                    distanceAtPhaseStart = totalDistanceMeters
                    distanceInCurrentPhase = 0.0
                    durationAtPhaseStart = totalDurationSeconds

                    val phase = timerState.currentPhase
                    val phaseName = Strings.getPhaseName(phase.type.name.lowercase())
                    ttsManager.announcePhase(
                        phaseName = phaseName,
                        phaseIndex = timerState.phaseIndex,
                        totalPhases = timerState.totalPhases,
                        durationMinutes = if (phase.distanceMeters == null) phase.durationSeconds / 60 else 0,
                        distanceKm = phase.distanceMeters?.let { it / 1000.0 },
                        hasPrevPhase = hasPrevPhase,
                        prevDistanceMeters = prevPhaseDistanceMeters,
                        prevDurationSeconds = prevPhaseDurationSeconds
                    )
                }

                // Notify phase change
                if (timerState.currentPhase != null && timerState.remainingSeconds == timerState.currentPhase.durationSeconds
                    && !isDistancePhase) {
                    ttsManager.vibratePhaseChange()
                }

                // Countdown — only for time-based phases
                if (!isDistancePhase && timerState.remainingSeconds in 1..3) {
                    ttsManager.announceCountdown(timerState.remainingSeconds)
                    ttsManager.vibrateCountdown()
                }

                // Transition into free phase — announce and keep recording
                if (timerState.isFreePhase && !wasFreePhase) {
                    finishRoutine()
                }

                // Update notification if workout is still active
                if (!timerState.isCompleted) {
                    updateNotification()
                }
            }
        }
    }

    /**
     * Starts a new workout.
     */
    private fun startWorkout(routineStr: String, noGpsValue: Boolean = false) {
        // FIX 5: Guard against double-tap / rapid intent replay causing duplicate starts.
        // Only the first caller wins; subsequent concurrent calls are silently dropped.
        if (!isStarting.compareAndSet(false, true)) return

        // Cancel any previously running jobs to prevent duplicate loops if startWorkout is called twice
        locationJob?.cancel()
        timerJob?.cancel()

        // Ensure TTS uses the correct language even when the service restarts in a fresh process
        val prefs = PreferencesManager(this)
        Strings.setLanguageSetting(prefs.language)
        originalRoutine = routineStr
        noGps = noGpsValue
        startTime = System.currentTimeMillis()
        totalDistanceMeters = 0.0
        totalDurationSeconds = 0L
        distanceAtPhaseStart = 0.0
        distanceInCurrentPhase = 0.0
        durationAtPhaseStart = 0L
        prevPhaseDistanceMeters = 0f
        prevPhaseDurationSeconds = 0L
        pausedAtMs = 0L
        totalPausedMs = 0L

        // Load auto-pause preferences
        autoPauseEnabled = prefs.autoPause
        minStoppedTime = prefs.autoPauseTime

        // Parse routine
        val routine = try {
            es.bercianor.tocacorrer.domain.parser.WorkoutParser.parse(routineStr)
        } catch (e: IllegalArgumentException) {
            // Malformed routine string — must call startForeground before stopSelf on Android 12+
            // to avoid ForegroundServiceDidNotStartInTimeException.
            isStarting.set(false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(NOTIFICATION_ID, createNotification())
            }
            stopSelf()
            return
        }

        // Start foreground FIRST — required on Android 14+ before any async work
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }

        // Update state
        _workoutStatus.value = WorkoutStatus(
            isRunning = true,
            routine = routineStr,
            distanceMeters = 0f,
            durationSeconds = 0,
            noGps = noGps
        )

        // FIX 6: Initialise timerStartMs synchronously BEFORE the coroutine launches.
        // If pauseWorkout() is called before the coroutine runs, timerStartMs is already set
        // to the actual start time instead of 0, preventing astronomical duration calculations.
        timerStartMs = SystemClock.elapsedRealtime()

        // Create workout in DB synchronously before starting GPS (avoids workoutId=0 race condition)
        serviceScope.launch {
            workoutId = repository.insertWorkout(
                Workout(
                    startTime = startTime,
                    originalRoutine = routineStr,
                    noGps = noGpsValue
                )
            )

            // Start components only after workoutId is set
            ttsManager.announceStart(routineStr)
            if (routine.phases.isEmpty()) {
                // Free workout — jump directly to free phase (no structured routine)
                _workoutStatus.update { it.copy(
                    isRunning = true,
                    isFreePhase = true
                ) }
            } else {
                phaseTimer.start(routine)
            }

            // Only start GPS if it's NOT a no-GPS workout
            if (!noGps) {
                startGpsTracking()
            }

            // Start duration timer (always)
            startDurationTimer()
        }
    }

    /**
     * Pauses the workout.
     */
    fun pauseWorkout() {
        if (_workoutStatus.value.isPaused) return
        phaseTimer.pause()
        pausedAtMs = SystemClock.elapsedRealtime()
        _workoutStatus.update { it.copy(isPaused = true) }
        updateNotification()
    }

    /**
     * Resumes the workout.
     */
    fun resumeWorkout() {
        phaseTimer.resume()
        if (pausedAtMs > 0L) {
            totalPausedMs += SystemClock.elapsedRealtime() - pausedAtMs
            pausedAtMs = 0L
        }
        _workoutStatus.update { it.copy(isPaused = false) }
    }

    /**
     * Manually advances to the next phase (for treadmill distance phases).
     */
    fun nextPhase() {
        ttsManager.vibratePhaseChange()
        phaseTimer.nextPhase()
    }

    /**
     * Stops the workout.
     */
    fun stopWorkout() {
        isStarting.set(false)   // FIX 5: allow a future startWorkout() call after stop
        phaseTimer.stop()
        locationJob?.cancel()
        timerJob?.cancel()

        _workoutStatus.value = WorkoutStatus()

        serviceScope.launch {
            ttsManager.announceWorkoutEnd(
                distanceMeters = totalDistanceMeters.toDouble(),
                durationSeconds = totalDurationSeconds
            )
            withContext(NonCancellable) {
                saveFinalStats()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    /**
     * Called when the structured routine ends — GPS and duration timer keep running.
     * Announces completion via TTS/vibration. The user must manually press Stop.
     */
    private fun finishRoutine() {
        ttsManager.announceRoutineEnd(
            distanceMeters = totalDistanceMeters.toDouble(),
            durationSeconds = totalDurationSeconds
        )
        ttsManager.vibratePhaseChange()
        // Do NOT stopForeground — notification stays visible
        // Do NOT cancel timer — duration keeps counting
        // Do NOT cancel locationJob — GPS keeps recording
    }

    private suspend fun saveFinalStats() {
        val finalDistance = totalDistanceMeters
        val finalDuration = totalDurationSeconds

        val pace = if (noGps) {
            0.0 // Can't calculate pace without distance
        } else {
            locationTracker.calculatePace(finalDistance.toFloat(), finalDuration)
        }

        // Use the member variable noGps directly — no need to fetch the row from DB
        repository.updateWorkout(
            Workout(
                id = workoutId,
                startTime = startTime,
                originalRoutine = originalRoutine,
                totalDistanceMeters = finalDistance,
                totalDurationSeconds = finalDuration,
                averagePaceMinPerKm = pace,
                noGps = noGps
            )
        )
    }

    private fun startGpsTracking() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
            != PackageManager.PERMISSION_GRANTED) {
            return
        }

        locationJob = serviceScope.launch {
            locationTracker.getLocationUpdates(GPS_UPDATE_INTERVAL_MS, GPS_MIN_DISTANCE_M).collect { location ->
                processNewLocation(location)
            }
        }
    }

    private fun processNewLocation(location: Location) {
        // Ignore inaccurate locations to prevent GPS jitter inflating distance
        if (location.accuracy > GPS_ACCURACY_THRESHOLD_METERS) return

        val currentPhase = _workoutStatus.value.currentPhase
        val phaseLetter = currentPhase?.letter ?: "X"

        // Calculate speed and update distance
        lastLocation?.let { previous ->
            val distance = locationTracker.calculateDistance(previous, location)
            val timeSeconds = (location.time - previous.time) / 1000f
            if (timeSeconds > 0) {
                lastSpeedMs = distance / timeSeconds
                handleAutoPause(timeSeconds)
            }
            if (!_workoutStatus.value.isPaused) {
                totalDistanceMeters += distance
            }
        }
        lastLocation = location

        // Distance phase tracking — update distanceInCurrentPhase for ALL phases (time-based and distance-based)
        distanceInCurrentPhase = totalDistanceMeters - distanceAtPhaseStart

        // Distance-based phase: handle progress tracking and auto-advance
        if (currentPhase?.distanceMeters != null) {
            handleDistancePhase(currentPhase)
        }

        // Save point to DB (only when not paused to avoid jitter points)
        if (!_workoutStatus.value.isPaused) {
            serviceScope.launch {
                repository.insertGpsPoint(
                    GpsPoint(
                        workoutId = workoutId,
                        latitude = location.latitude,
                        longitude = location.longitude,
                        altitude = location.altitude,
                        timestamp = location.time,
                        phase = phaseLetter,
                        phaseIndex = _workoutStatus.value.phaseIndex
                    )
                )
            }
        }

        // Update state
        _workoutStatus.update { it.copy(
            distanceMeters = totalDistanceMeters.toFloat(),
            lastLatitude = location.latitude,
            lastLongitude = location.longitude,
            altitude = location.altitude,
            phaseDistanceMeters = distanceInCurrentPhase.toFloat(),
            phaseDurationSeconds = totalDurationSeconds - durationAtPhaseStart
        ) }

        // Update notification with distance info
        updateNotification()
    }

    /**
     * Handles auto-pause and auto-resume logic based on current speed.
     */
    private fun handleAutoPause(timeSeconds: Float) {
        if (!autoPauseEnabled || _workoutStatus.value.isPaused) {
            if (autoPauseEnabled && _workoutStatus.value.isPaused &&
                lastSpeedMs > STOPPED_SPEED_THRESHOLD && stoppedTimeSeconds > 0
            ) {
                stoppedTimeSeconds = 0f
                resumeWorkout()
                ttsManager.speak(Strings.get("resume"))
            }
            return
        }

        if (lastSpeedMs < STOPPED_SPEED_THRESHOLD) {
            stoppedTimeSeconds += timeSeconds
            if (stoppedTimeSeconds.toInt() >= minStoppedTime) {
                // Auto-pause
                pauseWorkout()
                ttsManager.speak(Strings.get("tts_auto_pause"))
            }
        } else {
            // Moving — reset stopped counter and auto-resume if needed
            stoppedTimeSeconds = 0f
        }
    }

    /**
     * Handles distance progress tracking and auto-advance for distance-based phases.
     * Note: distanceInCurrentPhase is already updated in processNewLocation before this is called.
     */
    private fun handleDistancePhase(phase: es.bercianor.tocacorrer.domain.model.TrainingPhase) {
        val target = phase.distanceMeters ?: return
        val progress = (distanceInCurrentPhase / target).coerceIn(0.0, 1.0)
        val remaining = (target - distanceInCurrentPhase).coerceAtLeast(0.0)

        // Update PhaseTimer progress
        phaseTimer.updateDistanceProgress(progress.toFloat(), remaining)

        // Auto-advance when target reached (GPS mode only)
        if (distanceInCurrentPhase >= target && !noGps) {
            ttsManager.vibratePhaseChange()
            phaseTimer.nextPhase()
        }
    }

    private fun startDurationTimer() {
        timerStartMs = SystemClock.elapsedRealtime()

        timerJob = serviceScope.launch {
            while (true) {
                delay(1000L)
                val currentPauseMs = if (pausedAtMs > 0L) SystemClock.elapsedRealtime() - pausedAtMs else 0L
                val elapsed = SystemClock.elapsedRealtime() - timerStartMs - totalPausedMs - currentPauseMs
                val newDuration = elapsed / 1000L
                totalDurationSeconds = newDuration
                _workoutStatus.update { it.copy(
                    durationSeconds = newDuration,
                    phaseDurationSeconds = newDuration - durationAtPhaseStart
                ) }
            }
        }
    }

    private fun createNotification(): Notification {
        val statusSnapshot = _workoutStatus.value

        val intent = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent().apply { setPackage(packageName) }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseIntent = Intent(this, WorkoutService::class.java).apply {
            action = if (statusSnapshot.isPaused) ACTION_RESUME else ACTION_PAUSE
        }
        val pausePendingIntent = PendingIntent.getService(
            this, 1, pauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, WorkoutService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 2, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val phase = when {
            statusSnapshot.isFreePhase -> Strings.get("free_phase")
            statusSnapshot.currentPhase != null -> {
                val current = statusSnapshot.currentPhase
                val phaseName = Strings.getPhaseName(current.type.name.lowercase())
                "${current.letter} - $phaseName"
            }
            else -> "Waiting..."
        }

        val pauseText = if (statusSnapshot.isPaused) Strings.get("resume") else Strings.get("pause")

        val contentText = if (statusSnapshot.isDistancePhase) {
            val remaining = statusSnapshot.remainingDistanceMeters ?: 0.0
            val kmRemaining = remaining / 1000.0
            Strings.get("km_remaining").format(kmRemaining) +
                " | ${Strings.get("time")}: ${formatTime(totalDurationSeconds)}"
        } else {
            "${Strings.get("distance")}: ${String.format("%.2f", totalDistanceMeters / 1000)} ${Strings.get("km")} | ${Strings.get("time")}: ${formatTime(totalDurationSeconds)}"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TocaCorrer - $phase")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .addAction(0, pauseText, pausePendingIntent)
            .addAction(0, Strings.get("stop"), stopPendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification() {
        val currentDist = totalDistanceMeters.toFloat()
        val currentDur = totalDurationSeconds
        val currentPhase = _workoutStatus.value.currentPhase?.letter ?: ""
        val currentPaused = _workoutStatus.value.isPaused

        if (currentDist == lastNotifiedDistanceM &&
            currentDur == lastNotifiedDurationSec &&
            currentPhase == lastNotifiedPhaseLetter &&
            currentPaused == lastNotifiedPaused) return

        lastNotifiedDistanceM = currentDist
        lastNotifiedDurationSec = currentDur
        lastNotifiedPhaseLetter = currentPhase
        lastNotifiedPaused = currentPaused

        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }

    private fun formatTime(seconds: Long): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return "%02d:%02d".format(mins, secs)
    }
}

/**
 * Current status of the workout.
 */
data class WorkoutStatus(
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val currentPhase: es.bercianor.tocacorrer.domain.model.TrainingPhase? = null,
    val remainingTime: Int = 0,
    val phaseIndex: Int = 0,
    val totalPhases: Int = 0,
    val isCompleted: Boolean = false,
    val isFreePhase: Boolean = false,
    val routine: String = "",
    val distanceMeters: Float = 0f,
    val durationSeconds: Long = 0,
    val lastLatitude: Double = 0.0,
    val lastLongitude: Double = 0.0,
    val altitude: Double = 0.0,
    val noGps: Boolean = false,
    val remainingDistanceMeters: Double? = null,  // non-null during distance phases
    val isDistancePhase: Boolean = false,          // true when current phase is distance-based
    // Phase-level stats (current phase)
    val phaseDistanceMeters: Float = 0f,
    val phaseDurationSeconds: Long = 0L
) {
    val distanceKm: Float
        get() = distanceMeters / 1000f

    val paceMinKm: Double
        get() = if (distanceMeters > 0) {
            (durationSeconds / 60.0) / (distanceMeters / 1000.0)
        } else 0.0

    val phaseDistanceKm: Float
        get() = phaseDistanceMeters / 1000f

    val phasePaceMinKm: Double
        get() = if (phaseDistanceMeters > 0) {
            (phaseDurationSeconds / 60.0) / (phaseDistanceMeters / 1000.0)
        } else 0.0
}
