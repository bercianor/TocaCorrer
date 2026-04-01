package es.bercianor.tocacorrer.ui

import android.app.Activity
import android.content.Context
import android.location.LocationManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import es.bercianor.tocacorrer.data.calendar.CalendarEvent
import es.bercianor.tocacorrer.domain.model.WorkoutRoutine
import es.bercianor.tocacorrer.domain.parser.WorkoutParser
import es.bercianor.tocacorrer.util.PermissionManager
import es.bercianor.tocacorrer.util.Strings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Main screen of the application.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel(),
    onNavigateToStatistics: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    
    // Handle Android back button
    BackHandler {
        val activity = context as? Activity
        if (state.activeWorkout) {
            // Workout in progress — move to background instead of closing
            activity?.moveTaskToBack(true)
        } else {
            // No active workout — finish the activity
            activity?.finish()
        }
    }

    var navigationIndex by remember { mutableIntStateOf(0) }
    var hasPermissions by remember { mutableStateOf(false) }
    
    // Launcher to request permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermissions = permissions.values.all { it }
        if (hasPermissions) {
            viewModel.updatePermissions(true)
            viewModel.loadCalendarEvents()
        }
    }

    // Check permissions on launch
    LaunchedEffect(Unit) {
        hasPermissions = PermissionManager.hasAllPermissions(context)
        
        if (!hasPermissions) {
            permissionLauncher.launch(PermissionManager.requiredPermissions.toTypedArray())
        } else {
            viewModel.updatePermissions(true)
            viewModel.loadCalendarEvents()
        }
        
        // Check GPS
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        viewModel.updateGpsEnabled(locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Strings.get("app_name")) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = Strings.get("settings"))
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.AutoMirrored.Filled.DirectionsRun, contentDescription = Strings.get("home")) },
                    label = { Text(Strings.get("home")) },
                    selected = navigationIndex == 0,
                    onClick = { navigationIndex = 0 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.History, contentDescription = Strings.get("statistics")) },
                    label = { Text(Strings.get("statistics")) },
                    selected = navigationIndex == 1,
                    onClick = { 
                        navigationIndex = 1
                        onNavigateToStatistics()
                    }
                )
            }
        }
    ) { paddingValues ->
        when (navigationIndex) {
            0 -> {
                // Render the active workout screen at this level so TodayScreen
                // does not receive workoutStatus and avoids recomposing every second.
                if (state.activeWorkout) {
                    ActiveWorkoutScreen(
                        status = state.workoutStatus,
                        onPause = { viewModel.pauseWorkout() },
                        onResume = { viewModel.resumeWorkout() },
                        onStop = { viewModel.stopWorkout() },
                        onNextPhase = { viewModel.nextPhase() },
                        modifier = Modifier.padding(paddingValues)
                    )
                } else {
                    TodayScreen(
                        paddingValues = paddingValues,
                        upcomingEvents = state.upcomingEvents,
                        hasPermissions = hasPermissions,
                        completedWorkoutsToday = state.completedWorkoutsToday,
                        awaitingGps = state.awaitingGps,
                        awaitingGpsElapsedSeconds = state.awaitingGpsElapsedSeconds,
                        error = state.error,
                        onStartWorkout = { routine, noGps ->
                            viewModel.requestStartWorkout(routine, noGps)
                        },
                        onCancelGpsWait = { viewModel.cancelGpsWait() },
                        onClearError = { viewModel.clearError() }
                    )
                }
            }
        }
    }
}

@Composable
private fun TodayScreen(
    paddingValues: PaddingValues,
    upcomingEvents: List<CalendarEvent>,
    hasPermissions: Boolean,
    completedWorkoutsToday: Int,
    awaitingGps: Boolean,
    awaitingGpsElapsedSeconds: Int,
    error: String?,
    onStartWorkout: (String, Boolean) -> Unit,
    onCancelGpsWait: () -> Unit = {},
    onClearError: () -> Unit = {}
) {
    // Error dialog — shown when requestStartWorkout sets an error (e.g. treadmill + distance phases)
    if (error != null) {
        AlertDialog(
            onDismissRequest = onClearError,
            title = { Text(Strings.get("stop")) },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = onClearError) {
                    Text(Strings.get("cancel"))
                }
            }
        )
    }

    // Today's events (isToday=true), sorted by start time — used for canStart index logic
    val todayEvents = upcomingEvents.filter { it.isToday }.sortedBy { it.start }
    val allTodayDone = todayEvents.isNotEmpty() && completedWorkoutsToday >= todayEvents.size

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        if (!hasPermissions) {
            RequiredPermissions()
        } else if (awaitingGps) {
            GpsWaitScreen(
                elapsedSeconds = awaitingGpsElapsedSeconds,
                onCancel = onCancelGpsWait
            )
        } else if (upcomingEvents.isEmpty()) {
            NoEventsToday(onStartFreeWorkout = { onStartWorkout("", false) })
        } else {
            EventList(
                events = upcomingEvents,
                completedWorkoutsToday = completedWorkoutsToday,
                showFreeWorkoutButton = todayEvents.isEmpty() || allTodayDone,
                onStart = onStartWorkout
            )
        }
    }
}

@Composable
private fun GpsWaitScreen(
    elapsedSeconds: Int,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.GpsFixed,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = Strings.get("awaiting_gps"),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        LinearProgressIndicator(
            progress = { (elapsedSeconds / 30f).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = String.format(Strings.get("gps_seconds_elapsed"), elapsedSeconds),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))
        OutlinedButton(onClick = onCancel) {
            Text(Strings.get("awaiting_gps_cancel"))
        }
    }
}

@Composable
private fun RequiredPermissions() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.DirectionsRun,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = Strings.get("no_permissions"),
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = Strings.get("permission_required"),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun NoEventsToday(onStartFreeWorkout: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.DirectionsRun,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = Strings.get("rest"),
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = Strings.get("no_workouts_today"),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onStartFreeWorkout) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(Strings.get("start_free_workout"))
        }
    }
}

@Composable
private fun EventList(
    events: List<CalendarEvent>,
    completedWorkoutsToday: Int,
    showFreeWorkoutButton: Boolean,
    onStart: (String, Boolean) -> Unit
) {
    // Today's events sorted by time — index determines which ones are still available
    val todayEventsSorted = events.filter { it.isToday }.sortedBy { it.start }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = Strings.get("upcoming_workouts"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        
        items(events) { event ->
            // For today events: enabled only if its sorted position >= completedWorkoutsToday
            val todayIndex = if (event.isToday) todayEventsSorted.indexOf(event) else -1
            val canStart = when {
                !event.isToday -> false
                else -> todayIndex >= completedWorkoutsToday
            }
            EventCard(
                event = event,
                canStart = canStart,
                completedWorkoutsToday = completedWorkoutsToday,
                onStart = onStart
            )
        }

        if (showFreeWorkoutButton) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = { onStart("", false) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(Strings.get("start_free_workout"))
                }
            }
        }
    }
}

@Composable
private fun EventCard(
    event: CalendarEvent,
    canStart: Boolean,
    completedWorkoutsToday: Int,
    onStart: (String, Boolean) -> Unit
) {
    val routine = remember(event.routine) {
        try {
            WorkoutParser.parse(event.routine)
        } catch (e: IllegalArgumentException) {
            // Malformed calendar event description — treat as empty/free workout
            es.bercianor.tocacorrer.domain.model.WorkoutRoutine.EMPTY
        }
    }
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val dateFormat = SimpleDateFormat("EEE d MMM", Locale.getDefault())
    var noGps by remember { mutableStateOf(event.noGps) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = event.title.ifEmpty { Strings.get("default_workout_title") },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = dateFormat.format(Date(event.start)),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (event.isToday) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = timeFormat.format(Date(event.start)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Toggle for no GPS workout
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Switch(
                    checked = noGps,
                    onCheckedChange = { noGps = it }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = Strings.get("no_gps_treadmill"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (event.routine.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = event.routine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${Strings.get("duration_label")} ${formatRoutineSummary(routine)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))

            // Disabled reason text (shown when button is disabled)
            if (!canStart) {
                val disabledReason = when {
                    completedWorkoutsToday > 0 && event.isToday ->
                        Strings.get("already_trained_today")
                    !event.isToday ->
                        String.format(Strings.get("available_on"), dateFormat.format(Date(event.start)))
                    else -> ""
                }
                if (disabledReason.isNotEmpty()) {
                    Text(
                        text = disabledReason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }
            
            Button(
                onClick = { onStart(event.routine, noGps) },
                enabled = canStart,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(Strings.get("start_workout"))
            }
        }
    }
}

@Composable
private fun ActiveWorkoutScreen(
    status: es.bercianor.tocacorrer.service.WorkoutStatus,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onNextPhase: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Derived from fields that change only on phase switch or pause/resume — not every second
        val phaseHeaderText by remember(status.currentPhase, status.isFreePhase, status.isPaused) { derivedStateOf {
            status.currentPhase?.let {
                "${it.letter} - ${Strings.getPhaseName(it.type.name.lowercase())}"
            } ?: when {
                status.isFreePhase -> Strings.get("free_phase")
                status.isPaused -> Strings.get("paused")
                else -> Strings.get("workout_in_progress")
            }
        } }

        val phaseCounterText by remember(status.phaseIndex, status.totalPhases) { derivedStateOf {
            "${Strings.get("current_phase")} ${status.phaseIndex + 1} / ${status.totalPhases}"
        } }

        val isPaused = status.isPaused

        Text(
            text = phaseHeaderText,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Timer area: show countdown for time phases, remaining distance for distance phases
        if (status.isDistancePhase) {
            val kmRemaining = (status.remainingDistanceMeters ?: 0.0) / 1000.0
            Text(
                text = String.format("%.2f km", kmRemaining),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary
            )
            // Treadmill hint for distance phases without GPS
            if (status.noGps) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = Strings.get("distance_phase_manual"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Text(
                text = when {
                    status.isFreePhase -> "+ ${formatTime(status.phaseDurationSeconds.toInt())}"
                    else -> formatTime(status.remainingTime)
                },
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Progress indicator: distance-based or time-based (hidden during free/extra phase)
        if (!status.isFreePhase) {
            val progress = when {
                status.isDistancePhase -> {
                    val total = status.currentPhase?.distanceMeters ?: 0.0
                    val remaining = status.remainingDistanceMeters ?: 0.0
                    if (total > 0.0) ((total - remaining) / total).toFloat().coerceIn(0f, 1f) else 0f
                }
                else -> {
                    val total = status.currentPhase?.durationSeconds ?: 0
                    if (total > 0) 1f - (status.remainingTime.toFloat() / total) else 0f
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = phaseCounterText,
            style = MaterialTheme.typography.titleMedium
        )
        
        Spacer(modifier = Modifier.height(32.dp))

        // Phase stats
        if (!status.isFreePhase && (status.phaseDistanceMeters > 0f || status.phaseDurationSeconds > 0L)) {
            Text(
                text = Strings.get("phase_label"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("${Strings.get("distance")}: ${String.format("%.2f", status.phaseDistanceKm)} ${Strings.get("km")}")
                Text("${Strings.get("pace")}: ${formatPace(status.phasePaceMinKm)}")
            }
            Text(
                text = "${Strings.get("time")}: ${formatTime(status.phaseDurationSeconds.toInt())}",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Total stats
        Text(
            text = Strings.get("total_label"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("${Strings.get("distance")}: ${String.format("%.2f", status.distanceKm)} ${Strings.get("km")}")
            Text("${Strings.get("pace")}: ${formatPace(status.paceMinKm)}")
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "${Strings.get("time")}: ${formatTime(status.durationSeconds.toInt())}",
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(40.dp))

        // Treadmill "Next Phase" button for distance phases without GPS
        if (status.noGps && status.isDistancePhase) {
            Button(
                onClick = onNextPhase,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(Strings.get("next_phase"))
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Active/paused/free-phase workout controls
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Pause / Resume button
            FilledTonalButton(
                onClick = { if (isPaused) onResume() else onPause() },
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = if (isPaused) Strings.get("resume") else Strings.get("pause")
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isPaused) Strings.get("resume") else Strings.get("pause"))
            }

            // Stop button — saves immediately, no dialog
            OutlinedButton(
                onClick = { onStop() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = Strings.get("stop")
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(Strings.get("stop"))
            }
        }
    }
}

private fun formatRoutineSummary(routine: WorkoutRoutine): String {
    val totalMin = routine.totalDurationSeconds / 60
    val totalKm = routine.totalDistanceMeters / 1000.0
    return when {
        routine.totalDistanceMeters == 0.0 -> Strings.get("routine_summary_time_only").format(totalMin)
        routine.totalDurationSeconds == 0 -> Strings.get("routine_summary_distance_only").format(totalKm)
        else -> Strings.get("routine_summary_mixed").format(totalMin, totalKm)
    }
}

private fun formatTime(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format("%02d:%02d", mins, secs)
}

private fun formatPace(minKm: Double): String {
    if (minKm <= 0 || minKm.isNaN() || minKm.isInfinite()) return "--:--"
    val mins = minKm.toInt()
    val secs = ((minKm - mins) * 60).toInt()
    return String.format("%d:%02d %s", mins, secs, Strings.get("min_km"))
}
