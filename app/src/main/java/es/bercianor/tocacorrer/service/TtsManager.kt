package es.bercianor.tocacorrer.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import es.bercianor.tocacorrer.util.Strings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.resume

/**
 * Manager for Text-to-Speech and Vibration.
 * 
 * Provides voice announcements and haptic feedback during workout.
 */
class TtsManager(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var initialized = false
    private val pendingQueue: ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue()

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val vibrator: Vibrator by lazy {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    // Audio focus for ducking background music (e.g. Spotify) while TTS speaks
    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val audioFocusRequest: AudioFocusRequest? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setWillPauseWhenDucked(false) // duck (lower volume), not pause
                .build()
        } else null
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.requestAudioFocus(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
        }
    }

    private fun releaseAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }

    /**
     * Initializes TTS.
     */
    fun initialize() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // Guard: release() may have been called before this callback fires.
                // If tts is null at this point, the engine is already shut down — do not
                // set initialized/ready flags or flush the queue.
                if (tts == null) return@TextToSpeech
                tts?.language = Strings.getEffectiveLocale()
                tts?.setSpeechRate(1.0f)
                tts?.setPitch(1.0f)
                initialized = true
                _isReady.value = true
                // Flush any messages that arrived before TTS was ready
                while (pendingQueue.isNotEmpty()) {
                    val pending = pendingQueue.poll() ?: break
                    speakNow(pending, TextToSpeech.QUEUE_ADD)
                }
            }
        }
    }

    /**
     * Releases TTS resources.
     */
    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        initialized = false
        _isReady.value = false
        pendingQueue.clear()
    }

    /**
     * Speaks text immediately (TTS already initialized). Internal use only.
     */
    private fun speakNow(text: String, queueMode: Int) {
        val utteranceId = UUID.randomUUID().toString()
        requestAudioFocus()
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { releaseAudioFocus() }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { releaseAudioFocus() }
        })
        tts?.speak(text, queueMode, null, utteranceId)
    }

    /**
     * Speaks text, requesting audio focus (duck) before and releasing after.
     * If TTS is not yet initialized, enqueues the message for when it's ready.
     */
    fun speak(text: String, queueMode: Int = TextToSpeech.QUEUE_ADD) {
        if (!initialized) {
            pendingQueue.add(text)
            return
        }
        speakNow(text, queueMode)
    }

    /**
     * Speaks text and suspends until the utterance completes (or TTS is released).
     * Times out after 20 seconds to prevent indefinite blocking if the TTS engine hangs.
     * Use this when the caller must wait for TTS to finish before shutting down.
     */
    suspend fun speakAndWait(text: String) {
        if (!initialized) return
        val utteranceId = UUID.randomUUID().toString()
        requestAudioFocus()
        withTimeoutOrNull(20_000L) {
            suspendCancellableCoroutine { cont ->
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        releaseAudioFocus()
                        if (cont.isActive) cont.resume(Unit)
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        releaseAudioFocus()
                        if (cont.isActive) cont.resume(Unit)
                    }
                })
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
                cont.invokeOnCancellation { releaseAudioFocus() }
            }
        }
        // If timeout reached: release audio focus and continue normally
        releaseAudioFocus()
    }

    /**
     * Announces workout start.
     */
    fun announceStart(routine: String) {
        if (routine.isBlank()) {
            speak(Strings.get("tts_free_workout_started"))
        } else {
            speak(String.format(Strings.get("tts_workout_started"), routine))
        }
    }

    /**
     * Announces start of a new phase, including its number in the routine.
     * [phaseIndex] is 0-based; [totalPhases] is the total count.
     * [distanceKm] is non-null for distance-based phases.
     * If [hasPrevPhase] is true, prepends previous phase stats to the announcement.
     */
    fun announcePhase(
        phaseName: String,
        phaseIndex: Int,
        totalPhases: Int,
        durationMinutes: Int = 0,
        distanceKm: Double? = null,
        hasPrevPhase: Boolean = false,
        prevDistanceMeters: Float = 0f,
        prevDurationSeconds: Long = 0L
    ) {
        val phaseNumber = String.format(Strings.get("tts_phase_number"), phaseIndex + 1, totalPhases)
        val phaseDetail = when {
            distanceKm != null -> {
                val distStr = String.format(Locale.getDefault(), "%.1f", distanceKm)
                String.format(Strings.get("tts_next_phase_distance"), phaseName, distStr)
            }
            durationMinutes > 0 -> String.format(Strings.get("tts_next_phase_duration"), phaseName, durationMinutes.toString())
            else -> String.format(Strings.get("tts_next_phase"), phaseName)
        }

        val prevStats = if (hasPrevPhase && prevDistanceMeters > 0f && prevDurationSeconds > 0L) {
            val prevDistKm = String.format(Locale.getDefault(), "%.2f", prevDistanceMeters / 1000f)
            val prevMin = (prevDurationSeconds / 60).toInt()
            val prevSec = (prevDurationSeconds % 60).toInt()
            val paceMinKm = (prevDurationSeconds / 60.0) / (prevDistanceMeters / 1000.0)
            val paceMin = paceMinKm.toInt()
            val paceSec = ((paceMinKm - paceMin) * 60).toInt()
            " " + String.format(Strings.get("tts_prev_phase_stats"), prevDistKm, prevMin, prevSec, paceMin, paceSec)
        } else ""

        speak("$phaseNumber. $phaseDetail.$prevStats")
    }

    /**
     * Announces countdown (3, 2, 1).
     */
    fun announceCountdown(seconds: Int) {
        speak(seconds.toString())
    }

    /**
     * Announces that the workout has ended.
     * Suspends until the utterance finishes — call before stopSelf().
     */
    suspend fun announceWorkoutEnd(distanceMeters: Double, durationSeconds: Long) {
        val distanceKm = distanceMeters / 1000.0
        val distanceStr = String.format(Locale.getDefault(), "%.2f", distanceKm)
        val minutes = (durationSeconds / 60).toInt()
        val seconds = (durationSeconds % 60).toInt()
        val paceMinKm = if (distanceMeters > 0) (durationSeconds / 60.0) / distanceKm else 0.0
        val paceMin = paceMinKm.toInt()
        val paceSec = ((paceMinKm - paceMin) * 60).toInt()
        speakAndWait(String.format(Strings.get("tts_workout_completed"), distanceStr, minutes, seconds, paceMin, paceSec))
    }

    /**
     * Announces that the structured routine has finished — GPS and timer keep running.
     * The user is invited to keep going until they decide to stop manually.
     */
    fun announceRoutineEnd(distanceMeters: Double, durationSeconds: Long) {
        val distanceKm = distanceMeters / 1000.0
        val distanceStr = String.format(Locale.getDefault(), "%.2f", distanceKm)
        val minutes = (durationSeconds / 60).toInt()
        val seconds = (durationSeconds % 60).toInt()
        speak(String.format(Strings.get("tts_routine_completed"), distanceStr, minutes, seconds))
    }

    /**
     * Simple vibration.
     */
    fun vibrate(durationMs: Long = 200L) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(durationMs)
        }
    }

    /**
     * Pattern vibration (for alerts).
     */
    fun vibratePattern(pattern: LongArray = longArrayOf(0, 200, 100, 200)) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }

    /**
     * Vibration for phase change.
     */
    fun vibratePhaseChange() {
        vibratePattern(longArrayOf(0, 300, 150, 300, 150, 500))
    }

    /**
     * Short vibration for countdown.
     */
    fun vibrateCountdown() {
        vibrate(150)
    }

    /**
     * Checks if TTS is available.
     */
    fun isAvailable(): Boolean {
        return initialized && tts != null
    }

    /**
     * Temporarily silences TTS.
     */
    fun stop() {
        tts?.stop()
    }
}
