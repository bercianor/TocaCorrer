package es.bercianor.tocacorrer.util

import java.util.Locale

/**
 * String resources for bilingual UI (English and Spanish).
 * 
 * The language can be:
 * - System default (0): Uses Android system language
 * - English (1)
 * - Spanish (2)
 */
object Strings {
    
    // Language values
    const val LANGUAGE_SYSTEM = 0
    const val LANGUAGE_ENGLISH = 1
    const val LANGUAGE_SPANISH = 2
    
    /**
     * Current language setting. Set this externally (e.g. from MainViewModel or PreferencesManager).
     * No Context needed — just the raw Int value.
     * @Volatile ensures visibility across UI and Service threads.
     */
    @Volatile private var currentLanguage: Int = LANGUAGE_SYSTEM
    
    /**
     * Get current language setting.
     */
    fun getLanguageSetting(): Int = currentLanguage
    
    /**
     * Set language preference (in-memory only; persistence is handled by PreferencesManager).
     */
    fun setLanguageSetting(language: Int) {
        currentLanguage = language
    }
    
    /**
     * Get the effective locale based on settings.
     */
    fun getEffectiveLocale(): Locale {
        return when (getLanguageSetting()) {
            LANGUAGE_ENGLISH -> Locale.ENGLISH
            LANGUAGE_SPANISH -> Locale("es")
            else -> Locale.getDefault()
        }
    }
    
    /**
     * Get localized phase name by PhaseType enum name.
     */
    fun getPhaseName(phaseTypeName: String): String {
        return get("phase_$phaseTypeName")
    }
    
    /**
     * Get a string by key.
     */
    fun get(key: String): String {
        val locale = getEffectiveLocale()
        return when (locale.language) {
            "es" -> getSpanishString(key)
            else -> getEnglishString(key)
        }
    }
    
    private fun getEnglishString(key: String): String {
        return englishStrings[key] ?: key
    }
    
    private fun getSpanishString(key: String): String {
        return spanishStrings[key] ?: getEnglishString(key)
    }
    
    // English strings
    private val englishStrings = mapOf(
        // App
        "app_name" to "TocaCorrer",
        
        // Navigation
        "home" to "Home",
        "statistics" to "Statistics",
        "settings" to "Settings",
        "back" to "Back",
        
        // Main Screen
        "no_workouts_today" to "No workouts found for today",
        "start_workout" to "Start Workout",
        "start_free_workout" to "Start Free Workout",
        "no_permissions" to "No permissions",
        "permission_required" to "Location and calendar permissions are required",
        "already_trained_today" to "Already trained today",
        "available_on" to "Available on %s",
        "upcoming_workouts" to "Upcoming Workouts",
        
        // Active Workout
        "workout_in_progress" to "Workout in Progress",
        "paused" to "Paused",
        "free_phase" to "Free running",
        "distance" to "Distance",
        "time" to "Time",
        "pace" to "Pace",
        "phase_label" to "This phase",
        "total_label" to "Total",
        "current_phase" to "Current Phase",
        "next_phase" to "Next Phase",
        "rest" to "Rest",
        "pause" to "Pause",
        "resume" to "Resume",
        "stop" to "Stop",
        "finish" to "Finish",
        
        // Statistics
        "this_week" to "This Week",
        "this_month" to "This Month",
        "workouts" to "Workouts",
        "workouts_label" to "Workouts",
        "avg_pace" to "Avg Pace",
        "avg_pace_label" to "Avg pace",
        "weekly_distance_chart" to "Weekly distance (km)",
        "weekly_time_chart" to "Weekly time (min)",
        "weekly_pace_chart" to "Weekly pace (min/km)",
        "weekly_chart_metric" to "Weekly chart",
        "chart_metric_distance" to "Distance",
        "chart_metric_time" to "Time",
        "chart_metric_pace" to "Pace",
        "without_gps" to "(without GPS)",
        "tap_to_add_distance" to "Tap to add distance",
        "hour_suffix" to "h",
        "minute_suffix" to "m",
        "pace_unit" to "/km",
        "recent_workouts" to "Recent Workouts",
        "delete_workout" to "Delete Workout",
        "delete_workout_confirm" to "Are you sure you want to delete this workout?",
        "delete" to "Delete",
        "cancel" to "Cancel",
        "export" to "Export",
        "export_all" to "Export All",
        
        // Settings
        "select_calendar" to "Select Calendar",
        "appearance" to "Appearance",
        "dark_mode" to "Dark Mode",
        "system_default" to "System",
        "light" to "Light",
        "dark" to "Dark",
        "language" to "Language",
        "language_system" to "System",
        "workout_settings" to "Workout Settings",
        "no_calendars_found" to "No calendars found.\nMake sure you have calendars synced on your device.",
        "calendar_settings" to "Calendar Settings",
        "information" to "Information",
        "about_title" to "About TocaCorrer",
        "about_body1" to "TocaCorrer reads your workout events from the selected calendar and guides you during exercise.",
        "about_body2" to "Workout format:\n" +
            "  D → Rest (walk)\n" +
            "  T → Jog\n" +
            "  R → Easy run\n" +
            "  RA → Easy run (cheerful)\n" +
            "  RF → Easy run (strong)\n" +
            "  F → Fartlek\n" +
            "  P → Progressives\n" +
            "  RC → Race pace\n\n" +
            "Time-based (minutes):\n" +
            "  T5 → jog 5 min\n" +
            "  D2 → rest 2 min\n" +
            "  R → easy run 1 min (default)\n\n" +
            "Distance-based (km, decimals ok):\n" +
            "  R2.5k → easy run 2.5 km\n" +
            "  T.5k  → jog 0.5 km\n" +
            "  F1,2k → fartlek 1.2 km\n\n" +
            "Repetitions:\n" +
            "  4x(T3 - D1) → 4 × (jog 3 min + rest 1 min)\n" +
            "  3x(R1k - D2) → 3 × (easy run 1 km + rest 2 min)\n\n" +
            "Combined:\n" +
            "  R10 - 4x(T3 - D1) - R5\n" +
            "  R5 - 3x(RF1.5k - D1) - D2",
        "seconds_unit" to "seconds",
        "decrease_days" to "Decrease days",
        "increase_days" to "Increase days",
        "auto_pause" to "Auto-Pause",
        "auto_pause_description" to "Automatically pause when you stop moving",
        "auto_pause_time" to "Pause after (seconds)",
        "backup_restore" to "Backup & Restore",
        "backup" to "Backup",
        "restore" to "Restore",
        "backup_success" to "Backup created successfully",
        "backup_error" to "Error creating backup",
        "restore_error" to "Error restoring data",
        "imported_count" to "Imported %d workouts",
        "calendar_days" to "Calendar days",
        "calendar_days_description" to "Number of upcoming days to show",
        "export_settings" to "Export",

        // Color picker
        "primary_color" to "Primary Color",
        "color_purple" to "Purple",
        "color_red" to "Red",
        "color_orange" to "Orange",
        "color_yellow" to "Yellow",
        "color_green" to "Green",
        "color_blue" to "Blue",
        "color_teal" to "Teal",
        "color_pink" to "Pink",
        
        // Units
        "km" to "km",
        "min_km" to "min/km",
        
        // Notifications
        "workout_channel" to "Workout",
        "workout_notification" to "Workout in progress",
        
        // GPX
        "export_workout" to "Export workout",
        "gpx_segmentation_mode" to "GPX Segmentation",
        "gpx_seg_none" to "None",
        "gpx_seg_tracks" to "By tracks",
        "gpx_seg_segments" to "By segments",
        
        // Main screen
        "default_workout_title" to "Workout",
        "no_gps_treadmill" to "No GPS (treadmill)",
        "duration_label" to "Duration:",

        // TTS announcements
        "tts_workout_started" to "Workout started. Routine: %s",
        "tts_free_workout_started" to "Free workout started.",
        "tts_next_phase_duration" to "Next phase: %s for %s minutes",
        "tts_next_phase_distance" to "Next phase: %s for %s kilometers",
        "tts_next_phase" to "Next phase: %s",

        // Phase types - English
        "phase_rest" to "Rest",
        "phase_trot" to "Jog",
        "phase_easy" to "Easy run",
        "phase_easy_cheerful" to "Easy run (cheerful)",
        "phase_easy_strong" to "Strong easy",
        "phase_fartlek" to "Fartlek",
        "phase_progressives" to "Progressives",
        "phase_race_pace" to "Race pace",
        "phase_extra" to "Extra",

        // Distance phase UI
        "km_remaining" to "%.2f km remaining",
        "distance_phase_manual" to "No GPS — tap Next Phase",
        "tts_phase_number" to "Phase %d of %d",
        "tts_routine_completed" to "Routine completed! Distance: %s kilometers. Time: %d minutes and %d seconds.",
        "tts_workout_completed" to "Workout completed. Distance: %s kilometers. Time: %d minutes and %d seconds. Average pace: %d minutes and %d seconds per kilometer.",
        "tts_prev_phase_stats" to "Previous phase: %s kilometers in %d minutes and %d seconds, pace %d minutes and %d seconds per kilometer.",
        "routine_summary_time_only" to "%d min",
        "routine_summary_mixed" to "%d min + %.1f km",
        "routine_summary_distance_only" to "%.1f km",

        // GPS warm-up
        "awaiting_gps" to "Waiting for GPS signal...",
        "awaiting_gps_cancel" to "Cancel",
        "treadmill_no_distance_phases" to "Treadmill mode does not support distance-based phases. Remove distance phases from the routine.",
        "gps_seconds_elapsed" to "%d / 30 seconds",

        // TTS auto-pause
        "tts_auto_pause" to "Auto-pause. Resting."
    )
    
    // Spanish strings
    private val spanishStrings = mapOf(
        // App
        "app_name" to "TocaCorrer",
        
        // Navigation
        "home" to "Inicio",
        "statistics" to "Estadisticas",
        "settings" to "Ajustes",
        "back" to "Volver",
        
        // Main Screen
        "no_workouts_today" to "No hay entrenamientos para hoy",
        "start_workout" to "Iniciar Entrenamiento",
        "start_free_workout" to "Iniciar Entrenamiento Libre",
        "no_permissions" to "Sin permisos",
        "permission_required" to "Se necesitan permisos de ubicacion y calendario",
        "already_trained_today" to "Ya has entrenado hoy",
        "available_on" to "Disponible el %s",
        "upcoming_workouts" to "Proximos Entrenamientos",
        
        // Active Workout
        "workout_in_progress" to "Entrenamiento en Marcha",
        "paused" to "Pausado",
        "free_phase" to "Tramo libre",
        "distance" to "Distancia",
        "time" to "Tiempo",
        "pace" to "Ritmo",
        "phase_label" to "Esta fase",
        "total_label" to "Total",
        "current_phase" to "Fase Actual",
        "next_phase" to "Siguiente Fase",
        "rest" to "Descansar",
        "pause" to "Pausar",
        "resume" to "Reanudar",
        "stop" to "Parar",
        "finish" to "Finalizar",
        
        // Statistics
        "this_week" to "Esta Semana",
        "this_month" to "Este Mes",
        "workouts" to "Entrenamientos",
        "workouts_label" to "Entrenamientos",
        "avg_pace" to "Ritmo Medio",
        "avg_pace_label" to "Ritmo medio",
        "weekly_distance_chart" to "Distancia semanal (km)",
        "weekly_time_chart" to "Tiempo semanal (min)",
        "weekly_pace_chart" to "Ritmo semanal (min/km)",
        "weekly_chart_metric" to "Gráfico semanal",
        "chart_metric_distance" to "Distancia",
        "chart_metric_time" to "Tiempo",
        "chart_metric_pace" to "Ritmo",
        "without_gps" to "(sin GPS)",
        "tap_to_add_distance" to "Toca para añadir distancia",
        "hour_suffix" to "h",
        "minute_suffix" to "m",
        "pace_unit" to "/km",
        "recent_workouts" to "Entrenamientos Recientes",
        "delete_workout" to "Borrar Entrenamiento",
        "delete_workout_confirm" to "Seguro que quieres borrar este entrenamiento?",
        "delete" to "Borrar",
        "cancel" to "Cancelar",
        "export" to "Exportar",
        "export_all" to "Exportar Todo",
        
        // Settings
        "select_calendar" to "Seleccionar Calendario",
        "appearance" to "Apariencia",
        "dark_mode" to "Modo Oscuro",
        "system_default" to "Sistema",
        "light" to "Claro",
        "dark" to "Oscuro",
        "language" to "Idioma",
        "language_system" to "Sistema",
        "workout_settings" to "Ajustes de Entrenamiento",
        "no_calendars_found" to "No se encontraron calendarios.\nAsegúrate de tener calendarios sincronizados en tu dispositivo.",
        "calendar_settings" to "Ajustes de calendario",
        "information" to "Información",
        "about_title" to "Acerca de TocaCorrer",
        "about_body1" to "TocaCorrer lee tus eventos de entrenamiento del calendario seleccionado y te guía durante el ejercicio.",
        "about_body2" to "Formato de entrenamiento:\n" +
            "  D → Descansar (caminar)\n" +
            "  T → Trote\n" +
            "  R → Rodaje\n" +
            "  RA → Rodaje alegre\n" +
            "  RF → Rodaje fuerte\n" +
            "  F → Fartlek\n" +
            "  P → Progresivos\n" +
            "  RC → Ritmo de competición\n\n" +
            "Por tiempo (minutos):\n" +
            "  T5 → trote 5 min\n" +
            "  D2 → descanso 2 min\n" +
            "  R → rodar 1 min (por defecto)\n\n" +
            "Por distancia (km, decimales válidos):\n" +
            "  R2.5k → rodar 2,5 km\n" +
            "  T.5k  → trote 0,5 km\n" +
            "  F1,2k → fartlek 1,2 km\n\n" +
            "Repeticiones:\n" +
            "  4x(T3 - D1) → 4 × (trote 3 min + descanso 1 min)\n" +
            "  3x(R1k - D2) → 3 × (rodar 1 km + descanso 2 min)\n\n" +
            "Combinado:\n" +
            "  R10 - 4x(T3 - D1) - R5\n" +
            "  R5 - 3x(RF1.5k - D1) - D2",
        "seconds_unit" to "segundos",
        "decrease_days" to "Reducir días",
        "increase_days" to "Aumentar días",
        "auto_pause" to "Auto-Pausa",
        "auto_pause_description" to "Pausar automaticamente cuando te detengas",
        "auto_pause_time" to "Pausar despues de (segundos)",
        "backup_restore" to "Copia y Restauracion",
        "backup" to "Copia de seguridad",
        "restore" to "Restaurar",
        "backup_success" to "Copia creada correctamente",
        "backup_error" to "Error al crear la copia",
        "restore_error" to "Error al restaurar los datos",
        "imported_count" to "Importados %d entrenamientos",
        "calendar_days" to "Dias del calendario",
        "calendar_days_description" to "Numero de dias proximos a mostrar",
        "export_settings" to "Exportación",

        // Color picker
        "primary_color" to "Color Principal",
        "color_purple" to "Morado",
        "color_red" to "Rojo",
        "color_orange" to "Naranja",
        "color_yellow" to "Amarillo",
        "color_green" to "Verde",
        "color_blue" to "Azul",
        "color_teal" to "Verde azulado",
        "color_pink" to "Rosa",
        
        // Units
        "km" to "km",
        "min_km" to "min/km",
        
        // Notifications
        "workout_channel" to "Entrenamiento",
        "workout_notification" to "Entrenamiento en curso",
        
        // GPX
        "export_workout" to "Exportar entrenamiento",
        "gpx_segmentation_mode" to "Segmentación GPX",
        "gpx_seg_none" to "Sin separar",
        "gpx_seg_tracks" to "Por tracks",
        "gpx_seg_segments" to "Por segmentos",
        
        // Main screen
        "default_workout_title" to "Entrenamiento",
        "no_gps_treadmill" to "Sin GPS (cinta)",
        "duration_label" to "Duración:",

        // TTS announcements
        "tts_workout_started" to "Entrenamiento iniciado. Rutina: %s",
        "tts_free_workout_started" to "Entrenamiento libre iniciado.",
        "tts_next_phase_duration" to "Siguiente fase: %s durante %s minutos",
        "tts_next_phase_distance" to "Siguiente fase: %s durante %s kilómetros",
        "tts_next_phase" to "Siguiente fase: %s",

        // Phase types - Spanish
        "phase_rest" to "Descansar",
        "phase_trot" to "Trote",
        "phase_easy" to "Rodaje",
        "phase_easy_cheerful" to "Rodaje alegre",
        "phase_easy_strong" to "Rodaje fuerte",
        "phase_fartlek" to "Fartlek",
        "phase_progressives" to "Progresivos",
        "phase_race_pace" to "Ritmo de competición",
        "phase_extra" to "Extra",

        // Distance phase UI
        "km_remaining" to "%.2f km restantes",
        "distance_phase_manual" to "Sin GPS — toca Siguiente",
        "tts_phase_number" to "Fase %d de %d",
        "tts_routine_completed" to "¡Rutina completada! Distancia: %s kilómetros. Tiempo: %d minutos y %d segundos.",
        "tts_workout_completed" to "Entrenamiento completado. Distancia: %s kilómetros. Tiempo: %d minutos y %d segundos. Ritmo medio: %d minutos y %d segundos por kilómetro.",
        "tts_prev_phase_stats" to "Fase anterior: %s kilómetros en %d minutos y %d segundos, ritmo %d minutos y %d segundos por kilómetro.",
        "routine_summary_time_only" to "%d min",
        "routine_summary_mixed" to "%d min + %.1f km",
        "routine_summary_distance_only" to "%.1f km",

        // GPS warm-up
        "awaiting_gps" to "Esperando señal GPS...",
        "awaiting_gps_cancel" to "Cancelar",
        "treadmill_no_distance_phases" to "El modo cinta no admite fases por distancia. Elimina las fases por distancia de la rutina.",
        "gps_seconds_elapsed" to "%d / 30 segundos",

        // TTS auto-pause
        "tts_auto_pause" to "Pausa automática. Descansando."
    )
}
