package es.bercianor.tocacorrer.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import es.bercianor.tocacorrer.data.calendar.CalendarManager
import es.bercianor.tocacorrer.data.calendar.CalendarInfo
import es.bercianor.tocacorrer.data.export.GpxSegmentationMode
import es.bercianor.tocacorrer.data.local.PreferencesManager
import es.bercianor.tocacorrer.ui.theme.availableColors
import es.bercianor.tocacorrer.util.Strings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for settings.
 */
class SettingsViewModel(
    private val calendarManager: CalendarManager,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        loadCalendars()
        _state.value = _state.value.copy(
            darkMode = preferencesManager.darkMode,
            language = preferencesManager.language,
            autoPause = preferencesManager.autoPause,
            autoPauseTime = preferencesManager.autoPauseTime,
            calendarDays = preferencesManager.calendarDays,
            primaryColorIndex = preferencesManager.primaryColorIndex,
            gpxSegmentationMode = preferencesManager.gpxSegmentationMode.ordinal,
            weeklyChartMetric = preferencesManager.weeklyChartMetric
        )
    }

    private fun loadCalendars() {
        val calendars = calendarManager.getCalendars()
        val selectedId = preferencesManager.selectedCalendarId
        val selectedName = preferencesManager.selectedCalendarName
        
        _state.value = _state.value.copy(
            calendars = calendars,
            selectedCalendarId = selectedId,
            selectedCalendarName = selectedName
        )
    }

    fun selectCalendar(calendar: CalendarInfo) {
        preferencesManager.selectedCalendarId = calendar.id
        preferencesManager.selectedCalendarName = calendar.name
        _state.value = _state.value.copy(
            selectedCalendarId = calendar.id,
            selectedCalendarName = calendar.name
        )
    }

    fun setDarkMode(mode: Int) {
        preferencesManager.darkMode = mode
        _state.value = _state.value.copy(darkMode = mode)
    }

    fun setLanguage(lang: Int) {
        preferencesManager.language = lang
        _state.value = _state.value.copy(language = lang)
    }

    fun setAutoPause(enabled: Boolean) {
        preferencesManager.autoPause = enabled
        _state.value = _state.value.copy(autoPause = enabled)
    }

    fun setAutoPauseTime(time: Int) {
        preferencesManager.autoPauseTime = time
        _state.value = _state.value.copy(autoPauseTime = time)
    }

    fun setCalendarDays(days: Int) {
        val clamped = days.coerceIn(1, 14)
        preferencesManager.calendarDays = clamped
        _state.value = _state.value.copy(calendarDays = clamped)
    }

    fun setPrimaryColorIndex(index: Int) {
        preferencesManager.primaryColorIndex = index
        _state.value = _state.value.copy(primaryColorIndex = index)
    }

    fun setGpxSegmentationMode(mode: Int) {
        val modeEnum = GpxSegmentationMode.entries[mode.coerceIn(0, GpxSegmentationMode.entries.size - 1)]
        preferencesManager.gpxSegmentationMode = modeEnum
        _state.value = _state.value.copy(gpxSegmentationMode = modeEnum.ordinal)
    }

    fun setWeeklyChartMetric(metric: Int) {
        val clamped = metric.coerceIn(0, 2)
        preferencesManager.weeklyChartMetric = clamped
        _state.value = _state.value.copy(weeklyChartMetric = clamped)
    }
}

data class SettingsState(
    val calendars: List<CalendarInfo> = emptyList(),
    val selectedCalendarId: Long = -1L,
    val selectedCalendarName: String = "",
    val darkMode: Int = 0,
    val language: Int = 0,
    val autoPause: Boolean = true,
    val autoPauseTime: Int = 10,
    val calendarDays: Int = 5,
    val primaryColorIndex: Int = 0,
    val gpxSegmentationMode: Int = 0,
    val weeklyChartMetric: Int = 0 // 0 = distance, 1 = time, 2 = pace
)

// ---------------------------------------------------------------------------
// Shared section composable
// ---------------------------------------------------------------------------

/**
 * Renders a titled settings section with an optional divider (skipped for
 * the very first section, indicated by [showDivider] = false).
 */
@Composable
private fun SettingsSection(
    title: String,
    showDivider: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    if (showDivider) {
        HorizontalDivider()
    }
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 0.dp)
            .padding(top = 12.dp, bottom = 4.dp)
    )
    Column(content = content)
}

// ---------------------------------------------------------------------------
// Settings screen
// ---------------------------------------------------------------------------

/**
 * Settings screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    mainViewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    
    val viewModel = ViewModelProvider(
        context as androidx.lifecycle.ViewModelStoreOwner,
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(
                    mainViewModel.getCalendarManager(),
                    mainViewModel.getPreferencesManager()
                ) as T
            }
        }
    )[SettingsViewModel::class.java]

    val state by viewModel.state.collectAsState()

    BackHandler { onNavigateBack() }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Launcher for export (create file)
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            scope.launch {
                val result = mainViewModel.exportBackup(it)
                if (result.isSuccess) {
                    snackbarHostState.showSnackbar(Strings.get("backup_success"))
                } else {
                    snackbarHostState.showSnackbar(Strings.get("backup_error"))
                }
            }
        }
    }

    // Launcher for import (open file)
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            scope.launch {
                val result = mainViewModel.importBackup(it)
                if (result.isSuccess) {
                    snackbarHostState.showSnackbar(String.format(Strings.get("imported_count"), result.getOrNull() ?: 0))
                } else {
                    snackbarHostState.showSnackbar(Strings.get("restore_error"))
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Strings.get("settings")) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Strings.get("back"))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // ----------------------------------------------------------------
            // Section 1 — Calendar
            // ----------------------------------------------------------------
            item(key = "calendar_${state.language}") {
                SettingsSection(
                    title = Strings.get("calendar_settings"),
                    showDivider = false
                ) {
                    // Calendar dropdown selector
                    var calendarExpanded by remember { mutableStateOf(false) }
                    val selectedCalendarName = if (state.selectedCalendarId > 0) {
                        state.selectedCalendarName.ifEmpty { Strings.get("select_calendar") }
                    } else {
                        Strings.get("select_calendar")
                    }

                    if (state.calendars.isEmpty()) {
                        Text(
                            text = Strings.get("no_calendars_found"),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        ExposedDropdownMenuBox(
                            expanded = calendarExpanded,
                            onExpandedChange = { calendarExpanded = !calendarExpanded },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            OutlinedTextField(
                                value = selectedCalendarName,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(Strings.get("select_calendar")) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = calendarExpanded) },
                                modifier = Modifier
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                    .fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = calendarExpanded,
                                onDismissRequest = { calendarExpanded = false }
                            ) {
                                state.calendars.forEach { calendar ->
                                    DropdownMenuItem(
                                        text = { Text("${calendar.name} (${calendar.account})") },
                                        onClick = {
                                            viewModel.selectCalendar(calendar)
                                            calendarExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Calendar days +/- control
                    ListItem(
                        headlineContent = { Text(Strings.get("calendar_days")) },
                        supportingContent = {
                            Text(Strings.get("calendar_days_description"))
                        },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { viewModel.setCalendarDays(state.calendarDays - 1) },
                                    enabled = state.calendarDays > 1
                                ) {
                                    Icon(Icons.Filled.Remove, contentDescription = Strings.get("decrease_days"))
                                }
                                Text(
                                    text = "${state.calendarDays}",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.width(32.dp),
                                    textAlign = TextAlign.Center
                                )
                                IconButton(
                                    onClick = { viewModel.setCalendarDays(state.calendarDays + 1) },
                                    enabled = state.calendarDays < 14
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = Strings.get("increase_days"))
                                }
                            }
                        }
                    )
                }
            }

            // ----------------------------------------------------------------
            // Section 2 — Appearance
            // ----------------------------------------------------------------
            item(key = "appearance_${state.language}") {
                SettingsSection(title = Strings.get("appearance")) {
                    // Theme — SingleChoiceSegmentedButtonRow
                    // Segment index → darkMode value: 0→1 (Light), 1→2 (Dark), 2→0 (System)
                    val themeLabels = listOf(
                        Strings.get("light"),
                        Strings.get("dark"),
                        Strings.get("system_default")
                    )
                    val themeValues = listOf(1, 2, 0) // value at each segment index
                    val currentMode = state.darkMode
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            text = Strings.get("dark_mode"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            themeLabels.forEachIndexed { index, label ->
                                SegmentedButton(
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = themeLabels.size
                                    ),
                                    onClick = {
                                        viewModel.setDarkMode(themeValues[index])
                                        mainViewModel.setDarkMode(themeValues[index])
                                    },
                                    selected = currentMode == themeValues[index],
                                    label = { Text(label) }
                                )
                            }
                        }
                    }

                    val langLabels = listOf(
                        "EN",
                        "ES",
                        Strings.get("language_system")
                    )
                    val langValues = listOf(1, 2, 0) // value at each segment index
                    val currentLang = state.language
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            text = Strings.get("language"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            langLabels.forEachIndexed { index, label ->
                                SegmentedButton(
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = langLabels.size
                                    ),
                                    onClick = {
                                        viewModel.setLanguage(langValues[index])
                                        mainViewModel.setLanguage(langValues[index])
                                    },
                                    selected = currentLang == langValues[index],
                                    label = { Text(label) }
                                )
                            }
                        }
                    }

                    // Primary color picker
                    var colorExpanded by remember { mutableStateOf(false) }
                    val colorNames = listOf(
                        Strings.get("color_purple"),
                        Strings.get("color_red"),
                        Strings.get("color_orange"),
                        Strings.get("color_yellow"),
                        Strings.get("color_green"),
                        Strings.get("color_blue"),
                        Strings.get("color_teal"),
                        Strings.get("color_pink")
                    )
                    val currentColorName = colorNames.getOrElse(state.primaryColorIndex) { colorNames[0] }

                    ExposedDropdownMenuBox(
                        expanded = colorExpanded,
                        onExpandedChange = { colorExpanded = !colorExpanded },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        OutlinedTextField(
                            value = currentColorName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(Strings.get("primary_color")) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(availableColors.getOrElse(state.primaryColorIndex) { availableColors[0] })
                                )
                            },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = colorExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = colorExpanded,
                            onDismissRequest = { colorExpanded = false }
                        ) {
                            colorNames.forEachIndexed { index, name ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clip(CircleShape)
                                                    .background(availableColors.getOrElse(index) { availableColors[0] })
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(name)
                                        }
                                    },
                                    onClick = {
                                        viewModel.setPrimaryColorIndex(index)
                                        mainViewModel.setPrimaryColorIndex(index)
                                        colorExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // ----------------------------------------------------------------
            // Section 3 — Workout
            // ----------------------------------------------------------------
            item(key = "workout_${state.language}") {
                SettingsSection(title = Strings.get("workout_settings")) {
                    // Auto-pause switch
                    ListItem(
                        headlineContent = { Text(Strings.get("auto_pause")) },
                        supportingContent = {
                            Text(Strings.get("auto_pause_description"))
                        },
                        trailingContent = {
                            Switch(
                                checked = state.autoPause,
                                onCheckedChange = { viewModel.setAutoPause(it) }
                            )
                        }
                    )

                    // Auto-pause time — only when autoPause is enabled
                    if (state.autoPause) {
                        var expanded by remember { mutableStateOf(false) }
                        val timeOptions = listOf(5, 10, 15, 20, 30)

                        ListItem(
                            headlineContent = { Text(Strings.get("auto_pause_time")) },
                            supportingContent = {
                                Text("${state.autoPauseTime} ${Strings.get("seconds_unit")}")
                            },
                            trailingContent = {
                                androidx.compose.material3.TextButton(onClick = { expanded = true }) {
                                    Text("${state.autoPauseTime}s")
                                }
                                androidx.compose.material3.DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    timeOptions.forEach { time ->
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { Text("$time ${Strings.get("seconds_unit")}") },
                                            onClick = {
                                                viewModel.setAutoPauseTime(time)
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        )
                    }

                    // Weekly chart metric — SingleChoiceSegmentedButtonRow
                    val metricLabels = listOf(
                        Strings.get("chart_metric_distance"),
                        Strings.get("chart_metric_time"),
                        Strings.get("chart_metric_pace")
                    )
                    val currentMetric = state.weeklyChartMetric
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            text = Strings.get("weekly_chart_metric"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            metricLabels.forEachIndexed { index, label ->
                                SegmentedButton(
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = metricLabels.size
                                    ),
                                    onClick = {
                                        viewModel.setWeeklyChartMetric(index)
                                        mainViewModel.setWeeklyChartMetric(index)
                                    },
                                    selected = currentMetric == index,
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                }
            }

            // ----------------------------------------------------------------
            // Section 4 — Export
            // ----------------------------------------------------------------
            item(key = "export_${state.language}") {
                SettingsSection(title = Strings.get("export_settings")) {
                    // GPX segmentation mode — SingleChoiceSegmentedButtonRow
                    val gpxLabels = listOf(
                        Strings.get("gpx_seg_none"),
                        Strings.get("gpx_seg_tracks"),
                        Strings.get("gpx_seg_segments")
                    )
                    val currentGpxMode = state.gpxSegmentationMode
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            text = Strings.get("gpx_segmentation_mode"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            gpxLabels.forEachIndexed { index, label ->
                                SegmentedButton(
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = gpxLabels.size
                                    ),
                                    onClick = { viewModel.setGpxSegmentationMode(index) },
                                    selected = currentGpxMode == index,
                                    label = { Text(label) }
                                 )
                            }
                        }
                    }

                    // Export backup
                    ListItem(
                        headlineContent = { Text(Strings.get("backup")) },
                        supportingContent = { Text(Strings.get("backup_restore")) },
                        leadingContent = {
                            Icon(Icons.Filled.Upload, contentDescription = null)
                        },
                        modifier = Modifier.clickable {
                            exportLauncher.launch(mainViewModel.generateBackupFileName())
                        }
                    )

                    // Import backup
                    ListItem(
                        headlineContent = { Text(Strings.get("restore")) },
                        supportingContent = { Text(Strings.get("backup_restore")) },
                        leadingContent = {
                            Icon(Icons.Filled.Download, contentDescription = null)
                        },
                        modifier = Modifier.clickable {
                            importLauncher.launch(arrayOf("application/json"))
                        }
                    )
                }
            }

            // ----------------------------------------------------------------
            // Section 5 — Information
            // ----------------------------------------------------------------
            item(key = "information_${state.language}") {
                SettingsSection(title = Strings.get("information")) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = Strings.get("about_title"),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = Strings.get("about_body1"),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = Strings.get("about_body2"),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}
