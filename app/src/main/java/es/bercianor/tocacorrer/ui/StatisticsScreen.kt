package es.bercianor.tocacorrer.ui

import androidx.compose.foundation.clickable
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.bercianor.tocacorrer.data.local.entity.Workout
import es.bercianor.tocacorrer.data.local.entity.calculatedPaceMinPerKm
import es.bercianor.tocacorrer.data.repository.WorkoutRepository
import es.bercianor.tocacorrer.domain.model.Statistics
import es.bercianor.tocacorrer.ui.components.ChartData
import es.bercianor.tocacorrer.ui.components.BarChart
import es.bercianor.tocacorrer.ui.components.generateWeeklyDistanceData
import es.bercianor.tocacorrer.ui.components.generateWeeklyTimeData
import es.bercianor.tocacorrer.ui.components.generateWeeklyPaceData
import es.bercianor.tocacorrer.util.Strings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * ViewModel for statistics.
 */
class StatisticsViewModel(
    private val repository: WorkoutRepository
) : ViewModel() {

    private val _state = MutableStateFlow(StatisticsState())
    val state: StateFlow<StatisticsState> = _state.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        val now = System.currentTimeMillis()

        val weekStart = getWeekStart()
        val weekEnd = now

        val monthStart = getMonthStart()

        viewModelScope.launch {
            repository.getRecentWorkouts(20).collect { workouts ->
                val weeklyStats = repository.getStatistics(weekStart, weekEnd)
                val monthlyStats = repository.getStatistics(monthStart, now)
                _state.value = _state.value.copy(
                    recentWorkouts = workouts,
                    weeklyStats = weeklyStats,
                    monthlyStats = monthlyStats,
                    distanceChartData = generateWeeklyDistanceData(workouts),
                    timeChartData = generateWeeklyTimeData(workouts),
                    paceChartData = generateWeeklyPaceData(workouts)
                )
            }
        }
    }

    private fun getWeekStart(): Long {
        val cal = Calendar.getInstance()
        cal.firstDayOfWeek = Calendar.MONDAY
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun getMonthStart(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun deleteWorkout(workout: Workout) {
        viewModelScope.launch {
            repository.deleteWorkout(workout)
        }
    }

    fun reloadChartData() {
        loadData()
    }

    fun updateDistance(workout: Workout, distanceMeters: Double) {
        viewModelScope.launch {
            val updated = workout.copy(totalDistanceMeters = distanceMeters)
            repository.updateWorkout(
                updated.copy(
                    averagePaceMinPerKm = updated.calculatedPaceMinPerKm()
                )
            )
        }
    }
}

data class StatisticsState(
    val weeklyStats: Statistics = Statistics(0.0, 0L, 0),
    val monthlyStats: Statistics = Statistics(0.0, 0L, 0),
    val recentWorkouts: List<Workout> = emptyList(),
    val distanceChartData: List<ChartData> = emptyList(),
    val timeChartData: List<ChartData> = emptyList(),
    val paceChartData: List<ChartData> = emptyList()
)

/**
 * Statistics screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    viewModel: StatisticsViewModel,
    onNavigateBack: () -> Unit,
    onExportOne: (Workout) -> Unit = {},
    onExportAll: () -> Unit = {},
    language: Int = 0,
    weeklyChartMetric: Int = 0
) {
    val state by viewModel.state.collectAsState()

    // Reload chart data whenever language changes so day labels update immediately
    LaunchedEffect(language) {
        viewModel.reloadChartData()
    }

    BackHandler { onNavigateBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Strings.get("statistics")) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = Strings.get("back")
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Weekly statistics
            item {
                Text(
                    text = Strings.get("this_week"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.AutoMirrored.Filled.DirectionsRun,
                        title = Strings.get("distance"),
                        value = "${String.format("%.1f", state.weeklyStats.totalDistanceMeters / 1000)} ${Strings.get("km")}"
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Schedule,
                        title = Strings.get("time"),
                        value = formatDuration(state.weeklyStats.totalDurationSeconds)
                    )
                }
            }
            
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.CalendarMonth,
                        title = Strings.get("workouts"),
                        value = "${state.weeklyStats.workoutCount}"
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Speed,
                        title = Strings.get("avg_pace"),
                        value = formatPace(state.weeklyStats)
                    )
                }
            }

            // Monthly statistics
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = Strings.get("this_month"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.AutoMirrored.Filled.DirectionsRun,
                        title = Strings.get("distance"),
                        value = "${String.format("%.1f", state.monthlyStats.totalDistanceMeters / 1000)} ${Strings.get("km")}"
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Schedule,
                        title = Strings.get("time"),
                        value = formatDuration(state.monthlyStats.totalDurationSeconds)
                    )
                }
            }
            
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.CalendarMonth,
                        title = Strings.get("workouts_label"),
                        value = "${state.monthlyStats.workoutCount}"
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Speed,
                        title = Strings.get("avg_pace_label"),
                        value = formatPace(state.monthlyStats)
                    )
                }
            }

            // Weekly chart
            item {
                Spacer(modifier = Modifier.height(16.dp))
                val chartData = when (weeklyChartMetric) {
                    1 -> state.timeChartData
                    2 -> state.paceChartData
                    else -> state.distanceChartData
                }
                val chartTitle = when (weeklyChartMetric) {
                    1 -> Strings.get("weekly_time_chart")
                    2 -> Strings.get("weekly_pace_chart")
                    else -> Strings.get("weekly_distance_chart")
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    BarChart(
                        data = chartData,
                        title = chartTitle,
                        showValueLabels = true,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            // Recent history
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = Strings.get("recent_workouts"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (state.recentWorkouts.isNotEmpty()) {
                        TextButton(onClick = { onExportAll() }) {
                            Text(Strings.get("export_all"))
                        }
                    }
                }
            }

            items(state.recentWorkouts) { workout ->
                WorkoutCard(
                    workout = workout,
                    onDelete = { viewModel.deleteWorkout(it) },
                    onExport = { onExportOne(workout) },
                    onEditDistance = { w, dist -> viewModel.updateDistance(w, dist) }
                )
            }
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    value: String
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun WorkoutCard(
    workout: Workout,
    onDelete: (Workout) -> Unit,
    onExport: (Workout) -> Unit = {},
    onEditDistance: (Workout, Double) -> Unit = { _, _ -> }
) {
    val dateFormat = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDistanceDialog by remember { mutableStateOf(false) }
    var distanceInput by remember { mutableStateOf("") }
    
    // Delete dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(Strings.get("delete_workout")) },
            text = { Text(Strings.get("delete_workout_confirm")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(workout)
                        showDeleteDialog = false
                    }
                ) {
                    Text(Strings.get("delete"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(Strings.get("cancel"))
                }
            }
        )
    }

    // Distance edit dialog
    if (showDistanceDialog) {
        AlertDialog(
            onDismissRequest = { showDistanceDialog = false },
            title = { Text(Strings.get("distance")) },
            text = {
                Column {
                    Text("${Strings.get("distance")} (${Strings.get("km")}):")
                    Spacer(modifier = Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = distanceInput,
                        onValueChange = { distanceInput = it },
                        label = { Text(Strings.get("km")) },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val distanceKm = distanceInput.toDoubleOrNull() ?: 0.0
                        if (distanceKm > 0) {
                            onEditDistance(workout, distanceKm * 1000) // convert to meters
                        }
                        showDistanceDialog = false
                        distanceInput = ""
                    }
                ) {
                    Text(Strings.get("finish"))
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showDistanceDialog = false
                    distanceInput = ""
                }) {
                    Text(Strings.get("cancel"))
                }
            }
        )
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = workout.originalRoutine,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    if (workout.noGps) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = Strings.get("without_gps"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = dateFormat.format(Date(workout.startTime)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                // Distance - editable if withoutGPS
                Text(
                    text = if (workout.noGps && workout.totalDistanceMeters == 0.0) {
                        Strings.get("tap_to_add_distance")
                    } else {
                        "${String.format("%.2f", workout.totalDistanceMeters / 1000)} km"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (workout.noGps && workout.totalDistanceMeters == 0.0) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.then(
                        if (workout.noGps) {
                            Modifier.clickable { 
                                distanceInput = if (workout.totalDistanceMeters > 0) {
                                    (workout.totalDistanceMeters / 1000).toString()
                                } else {
                                    ""
                                }
                                showDistanceDialog = true 
                            }
                        } else {
                            Modifier
                        }
                    )
                )
                Text(
                    text = formatDuration(workout.totalDurationSeconds),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = { onExport(workout) }) {
                Icon(
                    Icons.Filled.Share,
                    contentDescription = Strings.get("export"),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = { showDeleteDialog = true }) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = Strings.get("delete"),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val mins = (seconds % 3600) / 60
    return if (hours > 0) {
        "${hours}${Strings.get("hour_suffix")} ${mins}${Strings.get("minute_suffix")}"
    } else {
        "${mins}${Strings.get("minute_suffix")}"
    }
}

private fun formatPace(stats: Statistics): String {
    if (stats.totalDistanceMeters <= 0 || stats.totalDurationSeconds <= 0) {
        return "--:--"
    }
    val pace = (stats.totalDurationSeconds / 60.0) / (stats.totalDistanceMeters / 1000.0)
    val mins = pace.toInt()
    val secs = ((pace - mins) * 60).toInt()
    return String.format("%d:%02d %s", mins, secs, Strings.get("pace_unit"))
}
