package es.bercianor.tocacorrer.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import es.bercianor.tocacorrer.data.local.AppDatabase
import es.bercianor.tocacorrer.data.repository.WorkoutRepository
import es.bercianor.tocacorrer.ui.theme.TocaCorrerTheme
import es.bercianor.tocacorrer.util.Strings
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: MainViewModel
    private lateinit var statisticsViewModel: StatisticsViewModel
    
    enum class Screen {
        HOME, STATISTICS, SETTINGS
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        // Create StatisticsViewModel at Activity level to avoid passing the repository down to
        // the composable. The repository is constructed here using the same AppDatabase instance.
        val db = AppDatabase.getDatabase(this)
        val repository = WorkoutRepository(db, db.workoutDao(), db.gpsPointDao())
        statisticsViewModel = ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    StatisticsViewModel(repository) as T
            }
        )[StatisticsViewModel::class.java]

        enableEdgeToEdge()

        // Observe workout state to show over lockscreen while workout is active
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    if (state.activeWorkout) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                            setShowWhenLocked(true)
                            setTurnScreenOn(true)
                        } else {
                            @Suppress("DEPRECATION")
                            window.addFlags(
                                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                            )
                        }
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                            setShowWhenLocked(false)
                            setTurnScreenOn(false)
                        } else {
                            @Suppress("DEPRECATION")
                            window.clearFlags(
                                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                            )
                        }
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }
            }
        }

        // Observe export intents from the ViewModel and launch the chooser
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.exportIntent.collect { intent ->
                    startActivity(Intent.createChooser(intent, Strings.get("export_workout")))
                }
            }
        }

        setContent {
            val appState by viewModel.state.collectAsState()
            val darkTheme = when (appState.darkMode) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }

            TocaCorrerTheme(darkTheme = darkTheme, primaryColorIndex = appState.primaryColorIndex) {
                var currentScreen by remember { mutableStateOf(Screen.HOME) }

                when (currentScreen) {
                    Screen.HOME -> {
                        MainScreen(
                            viewModel = viewModel,
                            onNavigateToStatistics = { currentScreen = Screen.STATISTICS },
                            onNavigateToSettings = { currentScreen = Screen.SETTINGS }
                        )
                    }
                    Screen.STATISTICS -> {
                        StatisticsScreen(
                            viewModel = statisticsViewModel,
                            onNavigateBack = { currentScreen = Screen.HOME },
                            onExportOne = { workout -> viewModel.exportWorkout(workout) },
                            onExportAll = { viewModel.exportAllWorkouts() },
                            language = appState.language,
                            weeklyChartMetric = appState.weeklyChartMetric
                        )
                    }
                    Screen.SETTINGS -> {
                        SettingsScreen(
                            mainViewModel = viewModel,
                            onNavigateBack = { currentScreen = Screen.HOME }
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Re-bind to the service if a workout is already running (e.g. app reopened mid-workout)
        viewModel.bindService()
    }

    override fun onStop() {
        super.onStop()
        // Only unbind if currently bound — the bind is initiated by startWorkout, not here
        viewModel.unbindService()
    }
}
