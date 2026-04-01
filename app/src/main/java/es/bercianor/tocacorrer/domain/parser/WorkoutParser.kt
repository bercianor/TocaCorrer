package es.bercianor.tocacorrer.domain.parser

import es.bercianor.tocacorrer.domain.model.TrainingPhase
import es.bercianor.tocacorrer.domain.model.WorkoutRoutine
import es.bercianor.tocacorrer.domain.model.PhaseType

/**
 * Parser for TocaCorrer workout DSL.
 *
 * Supported formats:
 * - Simple phases:   R10 - D1 - T5 - RA3    (Easy 10 min, Rest 1 min, Jog 5 min, Easy cheerful 3 min)
 * - Distance-based: R5k - D2.5k - T2k      (k suffix = km)
 * - Decimal comma:   R2,5k                  (comma treated as decimal separator)
 * - Leading decimal: F.5k or F,5k         (0.5 km)
 * - Series:          4x(T3 - D1)            (4 repetitions)
 * - Combined:        R5 - 4x(T3 - D1) - T5
 *
 * Supported tokens:
 * - D  → Rest/Walk
 * - T  → Trot/Jog
 * - R  → Easy run
 * - RA → Easy run cheerful
 * - RF → Easy run strong
 * - F  → Fartlek
 * - P  → Progressives
 * - RC → Race pace
 *
 * The parser first extracts the routine from the input (ignoring surrounding text),
 * then tokenizes and builds the phases.
 *
 * Example: "Hoy corremos! T10-D1-R5 - recordar hidratarse" → parses T10-D1-R5
 */
object WorkoutParser {

    /**
     * Regex matching a single valid phase token (or series):
     * - NxSeries:       4x(...)
     * - Two-letter:     RA, RF, RC + optional digits + optional k
     * - Single-letter:  D, T, R, F, P (or legacy C, S) + optional digits + optional k
     *
     * The negative lookbehind (?<![A-Za-z]) and lookahead (?![A-Za-z]) act as word boundaries,
     * ensuring phase letters embedded inside words (e.g. "R" in "RECORDAR", "D" in "DESCANSO")
     * are not matched as phase tokens.
     */
    private val tokenRegex = Regex(
        "(?<![A-Za-z])(?:\\d+[Xx]\\s*\\((?:[^()]*|\\([^()]*\\))*\\)|R[AFC](?:\\d*[.,]\\d+|\\d+)?[Kk]?|[DTRF](?:\\d*[.,]\\d+|\\d+)?[Kk]?|[PCS](?:\\d*[.,]\\d+|\\d+)?[Kk]?)(?![A-Za-z])",
        setOf(RegexOption.IGNORE_CASE)
    )

    fun parse(input: String): WorkoutRoutine {
        if (input.isBlank()) return WorkoutRoutine.EMPTY

        val upperInput = input.uppercase()

        // Find all valid token matches.
        // The (?![A-Za-z]) lookahead in tokenRegex ensures bare letters embedded in words
        // (e.g. "R" in "RECORDAR") are not matched, so only actual phase tokens are found.
        val tokenMatches = tokenRegex.findAll(upperInput).toList()

        if (tokenMatches.isEmpty()) return WorkoutRoutine.EMPTY

        // Extract the substring spanning from first to last match.
        // This handles free-text inputs like "Hoy corremos! T10-D1-R5 - recordar hidratarse"
        // where the real routine is embedded between surrounding text.
        val startIndex = tokenMatches.first().range.first
        val endIndex = tokenMatches.last().range.last
        val routine = upperInput.substring(startIndex, endIndex + 1)

        val phases = tokenizeAndParse(routine)
        val totalDuration = phases.sumOf { it.durationSeconds }

        return WorkoutRoutine(phases, totalDuration)
    }

    /**
     * Tokenizes and parses the string into phases.
     */
    private fun tokenizeAndParse(input: String): List<TrainingPhase> {
        val result = mutableListOf<TrainingPhase>()
        var i = 0

        while (i < input.length) {
            when (input[i].uppercaseChar()) {
                'D' -> {
                    val (phase, newIndex) = readPhase(input, i + 1, PhaseType.REST)
                    result.add(phase)
                    i = newIndex
                }
                'T' -> {
                    val (phase, newIndex) = readPhase(input, i + 1, PhaseType.TROT)
                    result.add(phase)
                    i = newIndex
                }
                'R' -> {
                    // R can be: R (easy), RA (easy cheerful), RF (easy strong), R<n> (easy n minutes)
                    if (i + 1 < input.length) {
                        val nextCh = input[i + 1].uppercaseChar()
                        when (nextCh) {
                            'A' -> {
                                // RA - Easy cheerful
                                val (phase, newIndex) = readPhase(input, i + 2, PhaseType.EASY_CHEERFUL)
                                result.add(phase)
                                i = newIndex
                            }
                            'F' -> {
                                // RF - Easy strong
                                val (phase, newIndex) = readPhase(input, i + 2, PhaseType.EASY_STRONG)
                                result.add(phase)
                                i = newIndex
                            }
                            'C' -> {
                                // RC - Race pace
                                val (phase, newIndex) = readPhase(input, i + 2, PhaseType.RACE_PACE)
                                result.add(phase)
                                i = newIndex
                            }
                            else -> {
                                // Just R - easy run (may be followed by number or k)
                                val (phase, newIndex) = readPhase(input, i + 1, PhaseType.EASY)
                                result.add(phase)
                                i = newIndex
                            }
                        }
                    } else {
                        // Just R - easy run (default 1 min)
                        result.add(TrainingPhase(type = PhaseType.EASY, durationSeconds = 60))
                        i++
                    }
                }
                'F' -> {
                    val (phase, newIndex) = readPhase(input, i + 1, PhaseType.FARTLEK)
                    result.add(phase)
                    i = newIndex
                }
                'P' -> {
                    val (phase, newIndex) = readPhase(input, i + 1, PhaseType.PROGRESSIVES)
                    result.add(phase)
                    i = newIndex
                }
                'C' -> {
                    // Legacy C = Run (treat as EASY)
                    val (phase, newIndex) = readPhase(input, i + 1, PhaseType.EASY)
                    result.add(phase)
                    i = newIndex
                }
                'S' -> {
                    // Legacy S = Slow (treat as REST)
                    val (phase, newIndex) = readPhase(input, i + 1, PhaseType.REST)
                    result.add(phase)
                    i = newIndex
                }
                'X' -> {
                    // Series without prefix: x(content)
                    if (i + 1 < input.length && input[i + 1] == '(') {
                        val (content, end) = extractSeriesContent(input, i + 2)
                        val seriesPhases = tokenizeAndParse(content)

                        seriesPhases.forEachIndexed { idx, phase ->
                            result.add(phase.copy(seriesNumber = idx / seriesPhases.size + 1))
                        }
                        i = end
                    } else {
                        // Floating X or from a word like "CORREMOS"
                        i++
                    }
                }
                '-', ' ', '\t', '\n', '\r' -> i++
                else -> {
                    // May be a number followed by X for a series: 4X(...)
                    if (input[i].isDigit()) {
                        val numStart = i
                        while (i < input.length && input[i].isDigit()) {
                            i++
                        }
                        val numberString = input.substring(numStart, i)
                        val number = numberString.toInt()

                        // Check if it's a series
                        if (i < input.length && input[i] == 'X' && i + 1 < input.length && input[i + 1] == '(') {
                            val (content, end) = extractSeriesContent(input, i + 2)
                            val seriesPhases = tokenizeAndParse(content)

                            repeat(number) { rep ->
                                seriesPhases.forEach { phase ->
                                    result.add(phase.copy(seriesNumber = rep + 1))
                                }
                            }
                            i = end
                        } else {
                            // A standalone number that is not followed by X(...) is not valid in the DSL.
                            throw IllegalArgumentException(
                                "Invalid routine format: found standalone number '$numberString' at position $numStart. " +
                                "Expected 'T<n>', 'R<n>', 'D<n>', or '<n>x(...)'. " +
                                "Valid example: 'T10 - D1 - R10' or '4x(T3 - D1)'"
                            )
                        }
                    } else {
                        // Skip any other character
                        i++
                    }
                }
            }
        }

        return result
    }

    /**
     * Reads a phase value starting at [start], which may be:
     * - Empty:           default 1 minute
     * - Integer:         N minutes (time-based)
     * - Integer + K:     N km (distance-based)
     * - Decimal + K:     N.N km or N,N km (distance-based)
     * - Leading dot/comma + K: .Nk or ,Nk (distance-based, 0.something km)
     *
     * Returns a Pair of (TrainingPhase, next index).
     */
    private fun readPhase(input: String, start: Int, type: PhaseType): Pair<TrainingPhase, Int> {
        if (start >= input.length) {
            // No number at all → default 1 minute
            return Pair(TrainingPhase(type = type, durationSeconds = 60), start)
        }

        val ch = input[start]

        // Leading dot or comma before a digit (e.g., .5k or ,5k)
        if ((ch == '.' || ch == ',') && start + 1 < input.length && input[start + 1].isDigit()) {
            val (km, end) = readDecimalWithK(input, start)
            return if (km != null) {
                Pair(TrainingPhase(type = type, durationSeconds = 0, distanceMeters = km * 1000.0), end)
            } else {
                Pair(TrainingPhase(type = type, durationSeconds = 60), start)
            }
        }

        if (!ch.isDigit()) {
            // No number → default 1 minute
            return Pair(TrainingPhase(type = type, durationSeconds = 60), start)
        }

        // Read the integer part
        var i = start
        while (i < input.length && input[i].isDigit()) {
            i++
        }
        val intPart = input.substring(start, i)

        // Check for decimal part: .N or ,N
        if (i < input.length && (input[i] == '.' || input[i] == ',') && i + 1 < input.length && input[i + 1].isDigit()) {
            val (km, end) = readDecimalWithK(input, start)
            return if (km != null) {
                Pair(TrainingPhase(type = type, durationSeconds = 0, distanceMeters = km * 1000.0), end)
            } else {
                // No K suffix found — consume the decimal part to avoid standalone-number crash,
                // then treat the integer part as minutes.
                val minutes = intPart.toInt()
                var j = i + 1 // skip the '.' or ','
                while (j < input.length && input[j].isDigit()) j++ // skip fractional digits
                Pair(TrainingPhase(type = type, durationSeconds = minutes * 60), j)
            }
        }

        // No decimal — check for K suffix
        if (i < input.length && input[i] == 'K') {
            val km = intPart.toDouble()
            return Pair(TrainingPhase(type = type, durationSeconds = 0, distanceMeters = km * 1000.0), i + 1)
        }

        // Plain integer = minutes
        val minutes = intPart.toInt()
        return Pair(TrainingPhase(type = type, durationSeconds = minutes * 60), i)
    }

    /**
     * Reads a decimal number (possibly starting with dot/comma) followed by a mandatory K suffix.
     * Returns (km as Double, next index after K) or (null, start) if K is not present.
     *
     * Called when we know a dot/comma or digit+dot/comma is at [start].
     */
    private fun readDecimalWithK(input: String, start: Int): Pair<Double?, Int> {
        var i = start
        val sb = StringBuilder()

        // Collect digits, dots, commas
        while (i < input.length && (input[i].isDigit() || input[i] == '.' || input[i] == ',')) {
            val c = input[i]
            sb.append(if (c == ',') '.' else c) // normalise comma to dot
            i++
        }

        val numStr = sb.toString()

        // Must be followed by K
        if (i < input.length && input[i] == 'K') {
            val km = numStr.toDoubleOrNull() ?: return Pair(null, start)
            return Pair(km, i + 1)
        }

        // No K — caller decides what to do
        return Pair(null, start)
    }

    /**
     * Extracts the content between parentheses and returns (content, index after closing parenthesis).
     */
    private fun extractSeriesContent(input: String, start: Int): Pair<String, Int> {
        var depth = 1
        var i = start

        while (i < input.length && depth > 0) {
            when (input[i]) {
                '(' -> depth++
                ')' -> depth--
            }
            if (depth > 0) i++
        }

        if (depth != 0) {
            throw IllegalArgumentException(
                "Invalid routine format: unclosed parenthesis in series. " +
                "Check that every 'Nx(' has a matching closing ')'. " +
                "Example: '4x(R3 - D1)'"
            )
        }

        val content = input.substring(start, i)
        return Pair(content, i + 1)
    }
}
