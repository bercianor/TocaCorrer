package es.bercianor.tocacorrer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.bercianor.tocacorrer.util.Strings
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Data for a bar in the chart.
 */
data class ChartData(
    val label: String,       // X-axis label (e.g. day of week)
    val value: Float,        // Numeric value used for bar height
    val valueLabel: String = "", // Human-readable label shown inside the bar
    val color: Color = Color(0xFF6650a4)
)

/**
 * Simple vertical bar chart.
 * When [showValueLabels] is true, each bar shows its [ChartData.valueLabel] inside.
 */
@Composable
fun BarChart(
    data: List<ChartData>,
    title: String = "",
    maxValue: Float? = null,
    showValueLabels: Boolean = true,
    modifier: Modifier = Modifier
) {
    val effectiveMaxValue = maxValue ?: (data.maxOfOrNull { it.value } ?: 1f)
    val bgColor = MaterialTheme.colorScheme.surfaceVariant
    val onBarColor = MaterialTheme.colorScheme.onPrimary

    val textPaint = remember(onBarColor) {
        android.graphics.Paint().apply {
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (title.isNotEmpty()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Chart
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
        ) {
            if (data.isEmpty()) return@Canvas

            val barWidth = (size.width - (data.size - 1) * 8.dp.toPx()) / data.size
            val maxHeight = size.height - 20.dp.toPx()

            data.forEachIndexed { index, item ->
                val barHeight = if (effectiveMaxValue > 0) {
                    (item.value / effectiveMaxValue) * maxHeight
                } else {
                    0f
                }

                val x = index * (barWidth + 8.dp.toPx())

                // Background bar
                drawRect(
                    color = bgColor,
                    topLeft = Offset(x, 0f),
                    size = Size(barWidth, maxHeight)
                )

                // Value bar
                if (barHeight > 0) {
                    drawRect(
                        color = item.color,
                        topLeft = Offset(x, maxHeight - barHeight),
                        size = Size(barWidth, barHeight)
                    )

                    // Value label inside the bar (only if bar is tall enough and label is set)
                    if (showValueLabels && item.valueLabel.isNotEmpty() && barHeight >= 20.dp.toPx()) {
                        val textSize = 10.sp.toPx()
                        textPaint.color = onBarColor.toArgb()
                        textPaint.textSize = textSize
                        drawContext.canvas.nativeCanvas.drawText(
                            item.valueLabel,
                            x + barWidth / 2f,
                            maxHeight - 4.dp.toPx(), // near the bottom of the bar
                            textPaint
                        )
                    }
                }
            }
        }

        // X-axis labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            data.forEach { item ->
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.width(32.dp),
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Generates data for weekly distance chart.
 */
fun generateWeeklyDistanceData(workouts: List<es.bercianor.tocacorrer.data.local.entity.Workout>): List<ChartData> {
    val calendar = Calendar.getInstance()
    val dayFormat = SimpleDateFormat("EEE", Strings.getEffectiveLocale())

    calendar.add(Calendar.DAY_OF_YEAR, -6)

    val result = mutableListOf<ChartData>()

    for (i in 0..6) {
        val dayStart = calendar.apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val dayEnd = calendar.apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis

        val distanceKm = workouts
            .filter { it.startTime in dayStart..dayEnd }
            .sumOf { it.totalDistanceMeters }
            .toFloat() / 1000f

        val valueLabel = if (distanceKm > 0f) String.format(Locale.getDefault(), "%.1f", distanceKm) else ""

        result.add(
            ChartData(
                label = dayFormat.format(Date(dayStart)),
                value = distanceKm,
                valueLabel = valueLabel,
                color = Color(0xFF6650a4)
            )
        )

        calendar.add(Calendar.DAY_OF_YEAR, 1)
    }

    return result
}

/**
 * Generates data for weekly time chart.
 */
fun generateWeeklyTimeData(workouts: List<es.bercianor.tocacorrer.data.local.entity.Workout>): List<ChartData> {
    val calendar = Calendar.getInstance()
    val dayFormat = SimpleDateFormat("EEE", Strings.getEffectiveLocale())

    calendar.add(Calendar.DAY_OF_YEAR, -6)

    val result = mutableListOf<ChartData>()

    for (i in 0..6) {
        val dayStart = calendar.apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val dayEnd = calendar.apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis

        val totalSeconds = workouts
            .filter { it.startTime in dayStart..dayEnd }
            .sumOf { it.totalDurationSeconds }

        val totalMinutes = totalSeconds / 60f
        val valueLabel = if (totalMinutes > 0f) {
            val h = (totalSeconds / 3600).toInt()
            val m = ((totalSeconds % 3600) / 60).toInt()
            if (h > 0) "${h}h${m}m" else "${m}m"
        } else ""

        result.add(
            ChartData(
                label = dayFormat.format(Date(dayStart)),
                value = totalMinutes,
                valueLabel = valueLabel,
                color = Color(0xFF00897B)
            )
        )

        calendar.add(Calendar.DAY_OF_YEAR, 1)
    }

    return result
}

/**
 * Generates data for weekly pace chart.
 * Computes pace correctly as totalDuration / totalDistance per day bucket
 * (NOT by averaging averagePaceMinPerKm, which produces incorrect results).
 */
fun generateWeeklyPaceData(workouts: List<es.bercianor.tocacorrer.data.local.entity.Workout>): List<ChartData> {
    val calendar = Calendar.getInstance()
    val dayFormat = SimpleDateFormat("EEE", Strings.getEffectiveLocale())

    calendar.add(Calendar.DAY_OF_YEAR, -6)

    val result = mutableListOf<ChartData>()

    for (i in 0..6) {
        val dayStart = calendar.apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val dayEnd = calendar.apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis

        val workoutsForDay = workouts.filter { it.startTime in dayStart..dayEnd }

        // Accumulate totals per day — then compute pace from aggregates (not average of averages)
        val totalDurationSeconds = workoutsForDay.sumOf { it.totalDurationSeconds }
        val totalDistanceMeters = workoutsForDay.sumOf { it.totalDistanceMeters }

        val pace = if (totalDistanceMeters > 0.0) {
            (totalDurationSeconds / 60.0) / (totalDistanceMeters / 1000.0)
        } else 0.0

        val valueLabel = if (pace > 0.0) {
            val mins = pace.toInt()
            val secs = ((pace - mins) * 60).toInt()
            "$mins:${secs.toString().padStart(2, '0')}"
        } else ""

        result.add(
            ChartData(
                label = dayFormat.format(Date(dayStart)),
                value = pace.toFloat(),
                valueLabel = valueLabel,
                color = Color(0xFF7D5260)
            )
        )

        calendar.add(Calendar.DAY_OF_YEAR, 1)
    }

    return result
}
